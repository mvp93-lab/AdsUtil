package com.mvp93.adsutil

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.ExperimentalApi
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAd
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdOptions
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPosition
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPresentationScope
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.ump.UserMessagingPlatform
import com.mvp93.adsutil.utils.AdsConstants
import com.mvp93.adsutil.utils.findActivity
import com.mvp93.adsutil.utils.isDebug
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

@OptIn(ExperimentalApi::class)
object AdManager {
    internal const val APP_OPEN_AD_EXPIRATION_MS = 4 * 60 * 60 * 1000L

    private data class CachedAppOpenAd(
        val ad: AppOpenAd,
        val loadedAtMs: Long
    )

    private data class CachedBanner(
        val adView: AdView,
        val adUnitId: String,
        val adSize: AdSize
    )

    private val interstitialCache = ConcurrentHashMap<String, InterstitialAd>()
    private val interstitialLoads = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val bannerCache = ConcurrentHashMap<String, CachedBanner>()
    private val nativeAdCache = ConcurrentHashMap<String, NativeAd>()
    private val rewardedCache = ConcurrentHashMap<String, RewardedAd>()
    private val rewardedLoads = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pipCache = ConcurrentHashMap<String, PictureInPictureAd>()
    private val pipLoads = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile
    private var appOpenAdCache: CachedAppOpenAd? = null
    private val appOpenLoadMutex = Mutex()
    internal var clockMs: () -> Long = System::currentTimeMillis

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val initMutex = Mutex()

    private val _isAdsEnabled = MutableStateFlow(true)
    val isAdsEnabled = _isAdsEnabled.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    fun setAdsEnabled(enabled: Boolean) {
        _isAdsEnabled.value = enabled
        if (!enabled) {
            val bannersToDestroy = bannerCache.values.map { it.adView }
            bannerCache.clear()
            destroyAdViewsOnMain(bannersToDestroy)
            val pipsToDestroy = pipCache.values.toList()
            pipCache.clear()
            destroyPipAdsOnMain(pipsToDestroy)
            nativeAdCache.clear()
            interstitialCache.clear()
            rewardedCache.clear()
            appOpenAdCache = null
            AdPreloaderManager.stopAll()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            scope.launch { block() }
        }
    }

    private fun destroyAdViewsOnMain(adViews: List<AdView>) {
        if (adViews.isEmpty()) return
        runOnMain { adViews.forEach { it.destroy() } }
    }

    private fun destroyPipAdsOnMain(pipAds: List<PictureInPictureAd>) {
        if (pipAds.isEmpty()) return
        runOnMain {
            pipAds.forEach {
                it.hide()
                it.destroy()
            }
        }
    }

    fun destroyBanner(key: String) {
        bannerCache.remove(key)?.let { cached ->
            destroyAdViewsOnMain(listOf(cached.adView))
        }
    }

    fun getCachedNativeAd(screenTag: String): NativeAd? = nativeAdCache[screenTag]

    suspend fun initialize(context: Context, appId: String) {
        if (_isInitialized.value) return

        initMutex.withLock {
            if (_isInitialized.value) return

            val appContext = context.applicationContext
            // Final safety check for GDPR
            val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
            val status = consentInformation.consentStatus
            val canAds = consentInformation.canRequestAds()

            Log.d("AdManager", "Initialization attempt. Status: $status, canRequestAds: $canAds")

            if (!canAds) {
                Log.e("AdManager", "ABORTING initialization: SDK says we cannot request ads.")
                return
            }

            val finalAppId = if (appContext.isDebug()) AdsConstants.ADS_APP_TEST_ID else appId
            Log.d("AdManager", "Calling MobileAds.initialize with ID: $finalAppId")
            try {
                val config = InitializationConfig.Builder(finalAppId).build()
                MobileAds.initialize(appContext, config)
                _isInitialized.value = true
                Log.d("AdManager", "MobileAds Next-Gen initialized successfully")
            } catch (e: Exception) {
                Log.e("AdManager", "MobileAds Next-Gen initialization failed", e)
            }
        }
    }

    private suspend fun awaitInitialization(): Boolean {
        if (!_isInitialized.value) {
            withTimeoutOrNull(5000L) {
                _isInitialized.first { it }
            }
        }
        return _isInitialized.value
    }

    private suspend fun canLoadAd(tag: String): Boolean {
        if (!awaitInitialization()) {
            Log.e("AdManager", "Aborting $tag load: AdManager is not initialized.")
            return false
        }
        if (!_isAdsEnabled.value) {
            Log.d("AdManager", "Aborting $tag load: Ads are disabled.")
            return false
        }
        return true
    }

    private fun invokeOnUiThread(activity: Activity?, callback: (() -> Unit)?) {
        if (callback == null) return
        if (activity != null && !activity.isDestroyed) {
            activity.runOnUiThread { callback() }
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
        } else {
            scope.launch { callback() }
        }
    }

    private fun Activity.isUsableForAd(): Boolean = !isFinishing && !isDestroyed

    internal fun resolveBannerTestAdUnitId(adSize: AdSize): String {
        val isAdaptive = adSize.isAnchoredAdaptiveBanner ||
            adSize.isInlineAdaptiveBanner ||
            adSize.isLargeAnchoredAdaptiveBanner
        return if (isAdaptive) {
            AdsConstants.ADS_ADAPTIVE_BANNER_TEST_ID
        } else {
            AdsConstants.AD_BANNER_TEST_ID
        }
    }

    @JvmOverloads
    fun getOrCreateBanner(
        context: Context,
        key: String,
        adUnitId: String,
        adSize: AdSize = AdSize.BANNER
    ): AdView? {
        if (!_isInitialized.value || !_isAdsEnabled.value) return null

        val finalAdUnitId = if (context.isDebug()) resolveBannerTestAdUnitId(adSize) else adUnitId
        val currentActivity = context.findActivity()
        bannerCache[key]?.let { cached ->
            val cachedActivity = cached.adView.context.findActivity()
            val isStale = cached.adUnitId != finalAdUnitId ||
                cached.adSize != adSize ||
                cachedActivity == null ||
                !cachedActivity.isUsableForAd() ||
                (currentActivity != null && cachedActivity !== currentActivity)
            if (!isStale) {
                return cached.adView
            }
            if (bannerCache.remove(key, cached)) {
                destroyAdViewsOnMain(listOf(cached.adView))
            }
        }

        Log.d("AdManager", "Creating new BannerAd instance for key: $key with ID: $finalAdUnitId, size: $adSize")
        val newAdView = AdView(context)
        val newEntry = CachedBanner(adView = newAdView, adUnitId = finalAdUnitId, adSize = adSize)
        val existing = bannerCache.putIfAbsent(key, newEntry)
        if (existing != null) {
            newAdView.destroy()
            return existing.adView
        }

        val bannerRequest = BannerAdRequest.Builder(finalAdUnitId, adSize).build()
        newAdView.loadAd(bannerRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                Log.d("AdManager", "BannerAd ($key) loaded successfully")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdManager", "BannerAd ($key) failed to load: ${adError.message}")
                bannerCache.remove(key, newEntry)
            }
        })
        return newAdView
    }

    internal suspend fun <T : Any> deduplicateLoad(
        key: String,
        cache: ConcurrentHashMap<String, T>,
        inFlightLoads: ConcurrentHashMap<String, CompletableDeferred<Boolean>>,
        shouldStore: () -> Boolean = { _isAdsEnabled.value },
        startLoad: (onLoaded: (T) -> Unit, onFailed: () -> Unit) -> Unit
    ): Boolean {
        if (cache.containsKey(key)) return true

        val newDeferred = CompletableDeferred<Boolean>()
        val existing = inFlightLoads.putIfAbsent(key, newDeferred)
        if (existing != null) {
            return existing.await()
        }

        if (cache.containsKey(key)) {
            inFlightLoads.remove(key, newDeferred)
            newDeferred.complete(true)
            return true
        }

        try {
            startLoad(
                { ad ->
                    val stored = shouldStore()
                    if (stored) {
                        cache[key] = ad
                    }
                    inFlightLoads.remove(key, newDeferred)
                    newDeferred.complete(stored)
                },
                {
                    inFlightLoads.remove(key, newDeferred)
                    newDeferred.complete(false)
                }
            )
        } catch (t: Throwable) {
            inFlightLoads.remove(key, newDeferred)
            newDeferred.complete(false)
            throw t
        }
        return newDeferred.await()
    }

    suspend fun loadInterstitial(
        context: Context,
        adUnitId: String
    ): Boolean {
        Log.d("AdManager", "loadInterstitial called for: $adUnitId")
        if (!canLoadAd("interstitial")) return false

        val appContext = context.applicationContext
        val finalAdUnitId = if (appContext.isDebug()) AdsConstants.AD_INTERSTITIAL_TEST_ID else adUnitId
        val request = AdRequest.Builder(finalAdUnitId).build()

        return deduplicateLoad(
            key = adUnitId,
            cache = interstitialCache,
            inFlightLoads = interstitialLoads
        ) { onLoaded, onFailed ->
            InterstitialAd.load(request, object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdManager", "Interstitial loaded for ID: $adUnitId")
                    onLoaded(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "Interstitial failed to load for ID $adUnitId: ${adError.message}")
                    onFailed()
                }
            })
        }
    }

    fun isInterstitialLoaded(adUnitId: String): Boolean =
        interstitialCache.containsKey(adUnitId) || AdPreloaderManager.isInterstitialAvailable(adUnitId)

    fun showInterstitial(
        context: Context,
        adUnitId: String,
        reloadAfterShow: Boolean = true,
        onAdClosed: (() -> Unit)? = null
    ) {
        val activity = context.findActivity()
        if (!_isAdsEnabled.value) {
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        if (activity == null || !activity.isUsableForAd()) {
            Log.e("AdManager", "Cannot show Interstitial: valid Activity not found or already destroyed")
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        val cachedAd = interstitialCache.remove(adUnitId)
        val preloadedAd = if (cachedAd == null) AdPreloaderManager.pollInterstitialIfActive(adUnitId) else null
        val ad = cachedAd ?: preloadedAd
        if (ad == null) {
            Log.e("AdManager", "Interstitial not ready for ID: $adUnitId")
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        val appContext = context.applicationContext
        val shouldReloadManually = reloadAfterShow && preloadedAd == null
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Interstitial dismissed for ID: $adUnitId")
                if (shouldReloadManually) {
                    scope.launch {
                        loadInterstitial(appContext, adUnitId)
                    }
                }
                invokeOnUiThread(activity, onAdClosed)
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e("AdManager", "Interstitial failed to show: ${fullScreenContentError.message}")
                if (shouldReloadManually) {
                    scope.launch {
                        loadInterstitial(appContext, adUnitId)
                    }
                }
                invokeOnUiThread(activity, onAdClosed)
            }

            override fun onAppEvent(name: String, data: String?) {
                Log.d("AdManager", "App event: $name, $data")
            }
        }
        ad.show(activity)
    }

    suspend fun loadNativeAd(
        context: Context,
        adUnitId: String,
        screenTag: String,
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailed: (LoadAdError) -> Unit
    ) {
        if (!awaitInitialization()) {
            onAdFailed(LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "SDK not initialized"))
            return
        }

        if (!_isAdsEnabled.value) {
            onAdFailed(LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "Ads disabled by remote config"))
            return
        }

        // Return cached ad for this specific screen if available
        nativeAdCache[screenTag]?.let {
            onAdLoaded(it)
            return
        }

        // Use opt-in preloaded native ad immediately if available for this adUnitId
        AdPreloaderManager.pollNativeIfActive(adUnitId)?.let { preloaded ->
            nativeAdCache[screenTag] = preloaded
            onAdLoaded(preloaded)
            return
        }

        val appContext = context.applicationContext
        val finalAdUnitId = if (appContext.isDebug()) AdsConstants.AD_NATIVE_TEST_ID else adUnitId
        val request = NativeAdRequest.Builder(finalAdUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
        NativeAdLoader.load(request, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                nativeAdCache[screenTag] = nativeAd
                onAdLoaded(nativeAd)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                onAdFailed(adError)
            }
        })
    }

    internal fun isAppOpenAdValid(loadedAtMs: Long, nowMs: Long = clockMs()): Boolean {
        val elapsed = nowMs - loadedAtMs
        return elapsed in 0 until APP_OPEN_AD_EXPIRATION_MS
    }

    private fun getValidAppOpenAd(): AppOpenAd? {
        val cached = appOpenAdCache ?: return null
        if (!isAppOpenAdValid(cached.loadedAtMs)) {
            appOpenAdCache = null
            Log.d("AdManager", "Cached AppOpenAd expired, discarding")
            return null
        }
        return cached.ad
    }

    suspend fun loadAppOpenAd(
        context: Context,
        adUnitId: String
    ): Boolean {
        if (!canLoadAd("appOpen")) return false

        return appOpenLoadMutex.withLock {
            if (!canLoadAd("appOpen")) return@withLock false
            if (getValidAppOpenAd() != null) {
                Log.d("AdManager", "AppOpenAd already cached and valid, skipping duplicate load")
                return@withLock true
            }

            val appContext = context.applicationContext
            val finalAdUnitId = if (appContext.isDebug()) AdsConstants.ADS_OPEN_APP_TEST_ID else adUnitId
            val request = AdRequest.Builder(finalAdUnitId).build()

            suspendCancellableCoroutine { continuation ->
                AppOpenAd.load(request, object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAdCache = CachedAppOpenAd(ad = ad, loadedAtMs = clockMs())
                        Log.d("AdManager", "AppOpenAd loaded successfully")
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        appOpenAdCache = null
                        Log.e("AdManager", "AppOpenAd failed to load: ${adError.message}")
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
            }
        }
    }

    fun showAppOpenAd(
        activity: Activity,
        onAdDismissed: (() -> Unit)? = null
    ) {
        val ad = getValidAppOpenAd()
        if (!_isAdsEnabled.value || ad == null || !activity.isUsableForAd()) {
            invokeOnUiThread(activity, onAdDismissed)
            return
        }
        attachAndShowAppOpenAd(activity, ad, clearCacheOnClose = true, onAdDismissed = onAdDismissed)
    }

    fun showAppOpenAd(
        activity: Activity,
        adUnitId: String,
        onAdDismissed: (() -> Unit)? = null
    ) {
        val ad = getValidAppOpenAd()
        val preloadedAd = if (ad == null) AdPreloaderManager.pollAppOpenIfActive(adUnitId) else null
        val targetAd = ad ?: preloadedAd
        if (!_isAdsEnabled.value || targetAd == null || !activity.isUsableForAd()) {
            invokeOnUiThread(activity, onAdDismissed)
            return
        }
        attachAndShowAppOpenAd(
            activity = activity,
            ad = targetAd,
            clearCacheOnClose = ad != null,
            onAdDismissed = onAdDismissed
        )
    }

    private fun attachAndShowAppOpenAd(
        activity: Activity,
        ad: AppOpenAd,
        clearCacheOnClose: Boolean,
        onAdDismissed: (() -> Unit)?
    ) {
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "AppOpenAd dismissed")
                if (clearCacheOnClose) appOpenAdCache = null
                invokeOnUiThread(activity, onAdDismissed)
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e("AdManager", "AppOpenAd failed to show: ${fullScreenContentError.message}")
                if (clearCacheOnClose) appOpenAdCache = null
                invokeOnUiThread(activity, onAdDismissed)
            }
        }
        ad.show(activity)
    }

    fun isAppOpenAdAvailable(): Boolean = getValidAppOpenAd() != null

    fun isRewardedAdLoaded(adUnitId: String): Boolean =
        rewardedCache.containsKey(adUnitId) || AdPreloaderManager.isRewardedAvailable(adUnitId)

    suspend fun loadRewarded(
        context: Context,
        adUnitId: String
    ): Boolean {
        Log.d("AdManager", "loadRewarded called for: $adUnitId")
        if (!canLoadAd("rewarded")) return false

        val appContext = context.applicationContext
        val finalAdUnitId = if (appContext.isDebug()) AdsConstants.AD_REWARDED_TEST_ID else adUnitId
        val request = AdRequest.Builder(finalAdUnitId).build()

        return deduplicateLoad(
            key = adUnitId,
            cache = rewardedCache,
            inFlightLoads = rewardedLoads
        ) { onLoaded, onFailed ->
            RewardedAd.load(request, object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Rewarded ad loaded for ID: $adUnitId")
                    onLoaded(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "Rewarded ad failed to load for ID $adUnitId: ${adError.message}")
                    onFailed()
                }
            })
        }
    }

    fun showRewarded(
        context: Context,
        adUnitId: String,
        reloadAfterShow: Boolean = true,
        onUserEarnedReward: (RewardItem) -> Unit,
        onAdClosed: (() -> Unit)? = null
    ) {
        val activity = context.findActivity()
        if (!_isAdsEnabled.value) {
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        if (activity == null || !activity.isUsableForAd()) {
            Log.e("AdManager", "Cannot show Rewarded Ad: valid Activity not found or already destroyed")
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        val cachedAd = rewardedCache.remove(adUnitId)
        val preloadedAd = if (cachedAd == null) AdPreloaderManager.pollRewardedIfActive(adUnitId) else null
        val ad = cachedAd ?: preloadedAd
        if (ad == null) {
            Log.e("AdManager", "Rewarded ad not ready for ID: $adUnitId")
            invokeOnUiThread(activity, onAdClosed)
            return
        }

        val appContext = context.applicationContext
        val shouldReloadManually = reloadAfterShow && preloadedAd == null
        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Rewarded ad dismissed for ID: $adUnitId")
                if (shouldReloadManually) {
                    scope.launch {
                        loadRewarded(appContext, adUnitId)
                    }
                }
                invokeOnUiThread(activity, onAdClosed)
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e("AdManager", "Rewarded ad failed to show: ${fullScreenContentError.message}")
                if (shouldReloadManually) {
                    scope.launch {
                        loadRewarded(appContext, adUnitId)
                    }
                }
                invokeOnUiThread(activity, onAdClosed)
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            activity.runOnUiThread {
                onUserEarnedReward(rewardItem)
            }
        }
    }

    fun isPictureInPictureAdLoaded(adUnitId: String = AdsConstants.AD_PIP_TEST_ID): Boolean =
        pipCache.containsKey(adUnitId)

    suspend fun loadPictureInPictureAd(
        context: Context,
        adUnitId: String = AdsConstants.AD_PIP_TEST_ID
    ): Boolean {
        Log.d("AdManager", "loadPictureInPictureAd called for: $adUnitId")
        if (!canLoadAd("pip")) return false

        val appContext = context.applicationContext
        val finalAdUnitId = if (appContext.isDebug()) AdsConstants.AD_PIP_TEST_ID else adUnitId
        val request = PictureInPictureAdRequest.Builder(finalAdUnitId).build()

        return deduplicateLoad(
            key = adUnitId,
            cache = pipCache,
            inFlightLoads = pipLoads
        ) { onLoaded, onFailed ->
            PictureInPictureAd.load(request, object : AdLoadCallback<PictureInPictureAd> {
                override fun onAdLoaded(ad: PictureInPictureAd) {
                    Log.d("AdManager", "PictureInPictureAd loaded for ID: $adUnitId")
                    onLoaded(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "PictureInPictureAd failed to load for ID $adUnitId: ${adError.message}")
                    onFailed()
                }
            })
        }
    }

    @JvmOverloads
    fun showPictureInPictureAd(
        context: Context,
        adUnitId: String = AdsConstants.AD_PIP_TEST_ID,
        position: PictureInPictureAdPosition = PictureInPictureAdPosition.DEFAULT,
        presentationScope: PictureInPictureAdPresentationScope = PictureInPictureAdPresentationScope.SCREEN,
        onAdShown: (() -> Unit)? = null,
        onAdHidden: (() -> Unit)? = null,
        onAdFailedToShow: ((FullScreenContentError) -> Unit)? = null
    ): Boolean {
        val activity = context.findActivity()
        if (!_isAdsEnabled.value || activity == null || !activity.isUsableForAd()) {
            Log.e("AdManager", "Cannot show PictureInPictureAd: ads disabled or Activity unavailable")
            return false
        }

        val ad = pipCache[adUnitId]
        if (ad == null) {
            Log.e("AdManager", "PictureInPictureAd not ready for ID: $adUnitId")
            return false
        }

        val options = PictureInPictureAdOptions.Builder()
            .setPosition(position)
            .setPresentationScope(presentationScope)
            .build()

        ad.adEventCallback = object : PictureInPictureAdEventCallback {
            override fun onAdShown() {
                Log.d("AdManager", "PictureInPictureAd shown for ID: $adUnitId")
                invokeOnUiThread(activity, onAdShown)
            }

            override fun onAdHidden() {
                Log.d("AdManager", "PictureInPictureAd hidden for ID: $adUnitId")
                invokeOnUiThread(activity, onAdHidden)
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e("AdManager", "PictureInPictureAd failed to show: ${fullScreenContentError.message}")
                pipCache.remove(adUnitId, ad)
                destroyPipAdsOnMain(listOf(ad))
                invokeOnUiThread(activity) { onAdFailedToShow?.invoke(fullScreenContentError) }
            }
        }
        ad.show(activity, options)
        return true
    }

    fun hidePictureInPictureAd(adUnitId: String = AdsConstants.AD_PIP_TEST_ID) {
        val ad = pipCache[adUnitId] ?: return
        runOnMain { ad.hide() }
    }

    fun destroyPictureInPictureAd(adUnitId: String = AdsConstants.AD_PIP_TEST_ID) {
        pipCache.remove(adUnitId)?.let { ad ->
            destroyPipAdsOnMain(listOf(ad))
        }
    }
}
