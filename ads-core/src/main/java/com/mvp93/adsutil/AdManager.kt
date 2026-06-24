package com.mvp93.adsutil

import android.content.Context
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
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object AdManager {
    private val interstitialCache = mutableMapOf<String, InterstitialAd>()
    private val bannerCache = mutableMapOf<String, AdView>()
    private val nativeAdCache = mutableMapOf<String, NativeAd>()
    private var appOpenAdCache: AppOpenAd? = null

    private val _isAdsEnabled = MutableStateFlow(true)
    val isAdsEnabled = _isAdsEnabled.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    fun setAdsEnabled(enabled: Boolean) {
        _isAdsEnabled.value = enabled
        if (!enabled) {
            // Clear caches if ads are disabled
            bannerCache.clear()
            nativeAdCache.clear()
            interstitialCache.clear()
            appOpenAdCache = null
        }
    }

    fun getCachedNativeAd(screenTag: String): NativeAd? = nativeAdCache[screenTag]

    private fun isDebug(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    suspend fun initialize(context: Context, appId: String) {
        if (_isInitialized.value) return
        
        // Final safety check for GDPR
        val consentInformation = com.google.android.ump.UserMessagingPlatform.getConsentInformation(context)
        val status = consentInformation.consentStatus
        val canAds = consentInformation.canRequestAds()
        
        Log.d("AdManager", "Initialization attempt. Status: $status, canRequestAds: $canAds")
        
        if (!canAds) {
            Log.e("AdManager", "ABORTING initialization: SDK says we cannot request ads.")
            return
        }
        
        val finalAppId = if (isDebug(context)) com.mvp93.adsutil.utils.AdsConstants.ADS_APP_TEST_ID else appId
        Log.d("AdManager", "Calling MobileAds.initialize with ID: $finalAppId")
        try {
            val config = com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig.Builder(finalAppId).build()
            MobileAds.initialize(context, config)
            _isInitialized.value = true
            Log.d("AdManager", "MobileAds Next-Gen initialized successfully")
        } catch (e: Exception) {
            Log.e("AdManager", "MobileAds Next-Gen initialization failed", e)
        }
    }

    fun getOrCreateBanner(context: Context, key: String, adUnitId: String): AdView? {
        if (!_isAdsEnabled.value) return null

        val finalAdUnitId = if (isDebug(context)) com.mvp93.adsutil.utils.AdsConstants.AD_BANNER_TEST_ID else adUnitId
        return bannerCache.getOrPut(key) {
            Log.d("AdManager", "Creating new BannerAd instance for key: $key with ID: $finalAdUnitId")
            AdView(context).apply {
                val bannerRequest = BannerAdRequest.Builder(finalAdUnitId, AdSize.BANNER).build()
                this.loadAd(bannerRequest, object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        Log.d("AdManager", "BannerAd ($key) loaded successfully")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdManager", "BannerAd ($key) failed to load: ${error.message}")
                    }
                })
            }
        }
    }

    suspend fun loadInterstitial(
        context: Context, 
        adUnitId: String
    ): Boolean {
        Log.d("AdManager", "loadInterstitial called for: $adUnitId")
        if (!_isInitialized.value) {
            Log.d("AdManager", "Waiting for MobileAds initialization before loading interstitial...")
            withTimeoutOrNull(5000L) {
                _isInitialized.first { it }
            }
        }
        
        if (!_isInitialized.value) {
            Log.e("AdManager", "Aborting load: AdManager is not initialized.")
            return false
        }

        if (!_isAdsEnabled.value) {
            Log.d("AdManager", "Aborting load: Ads are disabled.")
            return false
        }

        val finalAdUnitId = if (isDebug(context)) com.mvp93.adsutil.utils.AdsConstants.AD_INTERSTITIAL_TEST_ID else adUnitId
        val request = AdRequest.Builder(finalAdUnitId).build()
        
        return suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(request, object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialCache[adUnitId] = ad
                    Log.d("AdManager", "Interstitial loaded for ID: $adUnitId")
                    if (continuation.isActive) continuation.resume(true, null)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialCache.remove(adUnitId)
                    Log.e("AdManager", "Interstitial failed to load for ID $adUnitId: ${error.message}")
                    if (continuation.isActive) continuation.resume(false, null)
                }
            })
        }
    }

    fun showInterstitial(
        context: Context,
        adUnitId: String,
        reloadAfterShow: Boolean = true,
        onAdClosed: (() -> Unit)? = null
    ) {
        val safeOnAdClosed = {
            (context as? android.app.Activity)?.runOnUiThread {
                onAdClosed?.invoke()
            } ?: onAdClosed?.invoke()
        }

        if (!_isAdsEnabled.value) {
            safeOnAdClosed()
            return
        }
        val ad = interstitialCache[adUnitId]
        if (ad != null) {
            ad.adEventCallback = object : com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdManager", "Interstitial dismissed for ID: $adUnitId")
                    interstitialCache.remove(adUnitId)
                    if (reloadAfterShow) {
                        CoroutineScope(Dispatchers.Main).launch {
                            loadInterstitial(context, adUnitId)
                        }
                    }
                    safeOnAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("AdManager", "Interstitial failed to show: ${error.message}")
                    interstitialCache.remove(adUnitId)
                    safeOnAdClosed()
                }

                override fun onAppEvent(name: String, data: String?) {
                    Log.d("AdManager", "App event: $name, $data")
                }
            }
            ad.show(context as android.app.Activity)
        } else {
            Log.e("AdManager", "Interstitial not ready for ID: $adUnitId")
            safeOnAdClosed()
        }
    }

    suspend fun loadNativeAd(
        context: Context,
        adUnitId: String,
        screenTag: String,
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailed: (LoadAdError) -> Unit
    ) {
        if (!_isInitialized.value) {
            withTimeoutOrNull(5000L) { _isInitialized.first { it } }
        }
        
        if (!_isInitialized.value) {
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

        val finalAdUnitId = if (isDebug(context)) com.mvp93.adsutil.utils.AdsConstants.AD_NATIVE_TEST_ID else adUnitId
        val request = NativeAdRequest.Builder(finalAdUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
        NativeAdLoader.load(request, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                nativeAdCache[screenTag] = nativeAd
                onAdLoaded(nativeAd)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onAdFailed(error)
            }
        })
    }

    suspend fun loadAppOpenAd(
        context: Context,
        adUnitId: String
    ): Boolean {
        if (!_isInitialized.value) {
            Log.d("AdManager", "Waiting for MobileAds initialization...")
            // Wait for initialization with timeout
            withTimeoutOrNull(5000L) {
                _isInitialized.first { it }
            }
        }

        if (!_isInitialized.value || !_isAdsEnabled.value) {
            return false
        }

        val finalAdUnitId = if (isDebug(context)) com.mvp93.adsutil.utils.AdsConstants.ADS_OPEN_APP_TEST_ID else adUnitId
        val request = AdRequest.Builder(finalAdUnitId).build()
        
        return suspendCancellableCoroutine { continuation ->
            AppOpenAd.load(request, object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAdCache = ad
                    Log.d("AdManager", "AppOpenAd loaded successfully")
                    if (continuation.isActive) continuation.resume(true, null)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAdCache = null
                    Log.e("AdManager", "AppOpenAd failed to load: ${error.message}")
                    if (continuation.isActive) continuation.resume(false, null)
                }
            })
        }
    }

    fun showAppOpenAd(
        activity: android.app.Activity,
        onAdDismissed: (() -> Unit)? = null
    ) {
        if (!_isAdsEnabled.value || appOpenAdCache == null) {
            onAdDismissed?.invoke()
            return
        }

        appOpenAdCache?.let { ad ->
            ad.adEventCallback = object : AppOpenAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdManager", "AppOpenAd dismissed")
                    appOpenAdCache = null
                    onAdDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                    Log.e("AdManager", "AppOpenAd failed to show: ${error.message}")
                    appOpenAdCache = null
                    onAdDismissed?.invoke()
                }
            }
            ad.show(activity)
        }
    }

    fun isAppOpenAdAvailable(): Boolean = appOpenAdCache != null
}
