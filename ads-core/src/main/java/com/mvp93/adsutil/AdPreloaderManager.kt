package com.mvp93.adsutil

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoadResult
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdPreloader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader
import com.mvp93.adsutil.utils.AdsConstants
import com.mvp93.adsutil.utils.isDebug
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional opt-in add-on wrapping Google Next-Gen SDK's `*AdPreloader` APIs.
 *
 * When unused, no background preload requests are made. For any placement started via
 * `start*`, [AdManager] automatically polls preloaded ads in `showInterstitial`,
 * `showRewarded`, and `loadNativeAd`, and clears all active preloaders when
 * `AdManager.setAdsEnabled(false)` is called.
 */
object AdPreloaderManager {

    private val hasStartedAnyPreloader = AtomicBoolean(false)
    private val activeInterstitialIds = ConcurrentHashMap.newKeySet<String>()
    private val activeRewardedIds = ConcurrentHashMap.newKeySet<String>()
    private val activeAppOpenIds = ConcurrentHashMap.newKeySet<String>()
    private val activeNativeIds = ConcurrentHashMap.newKeySet<String>()

    private fun startPreload(
        tag: String,
        context: Context,
        adUnitId: String,
        testAdUnitId: String,
        bufferSize: Int,
        activeIds: MutableSet<String>,
        buildRequest: (String) -> AdRequest,
        starter: (String, PreloadConfiguration, PreloadCallback?) -> Boolean,
        callback: PreloadCallback?
    ): Boolean {
        if (!AdManager.isInitialized.value || !AdManager.isAdsEnabled.value) {
            Log.d("AdPreloaderManager", "Skipping $tag preload: AdManager not initialized or ads disabled")
            return false
        }

        val appContext = context.applicationContext
        val finalAdUnitId = if (appContext.isDebug()) testAdUnitId else adUnitId
        val config = PreloadConfiguration(buildRequest(finalAdUnitId), bufferSize.coerceAtLeast(1))
        val started = starter(adUnitId, config, callback)
        if (started) {
            activeIds.add(adUnitId)
            hasStartedAnyPreloader.set(true)
            Log.d("AdPreloaderManager", "Started $tag preloader for ID: $adUnitId (buffer=${config.bufferSize})")
        }
        return started
    }

    @JvmOverloads
    fun startInterstitial(
        context: Context,
        adUnitId: String,
        bufferSize: Int = 1,
        callback: PreloadCallback? = null
    ): Boolean = startPreload(
        tag = "interstitial",
        context = context,
        adUnitId = adUnitId,
        testAdUnitId = AdsConstants.AD_INTERSTITIAL_TEST_ID,
        bufferSize = bufferSize,
        activeIds = activeInterstitialIds,
        buildRequest = { AdRequest.Builder(it).build() },
        starter = { id, cfg, cb ->
            if (cb != null) InterstitialAdPreloader.start(id, cfg, cb)
            else InterstitialAdPreloader.start(id, cfg)
        },
        callback = callback
    )

    fun isInterstitialAvailable(adUnitId: String): Boolean =
        activeInterstitialIds.contains(adUnitId) && InterstitialAdPreloader.isAdAvailable(adUnitId)

    internal fun pollInterstitialIfActive(adUnitId: String): InterstitialAd? =
        if (activeInterstitialIds.contains(adUnitId)) InterstitialAdPreloader.pollAd(adUnitId) else null

    fun stopInterstitial(adUnitId: String): Boolean {
        if (!activeInterstitialIds.remove(adUnitId)) return false
        return InterstitialAdPreloader.destroy(adUnitId)
    }

    @JvmOverloads
    fun startRewarded(
        context: Context,
        adUnitId: String,
        bufferSize: Int = 1,
        callback: PreloadCallback? = null
    ): Boolean = startPreload(
        tag = "rewarded",
        context = context,
        adUnitId = adUnitId,
        testAdUnitId = AdsConstants.AD_REWARDED_TEST_ID,
        bufferSize = bufferSize,
        activeIds = activeRewardedIds,
        buildRequest = { AdRequest.Builder(it).build() },
        starter = { id, cfg, cb ->
            if (cb != null) RewardedAdPreloader.start(id, cfg, cb)
            else RewardedAdPreloader.start(id, cfg)
        },
        callback = callback
    )

    fun isRewardedAvailable(adUnitId: String): Boolean =
        activeRewardedIds.contains(adUnitId) && RewardedAdPreloader.isAdAvailable(adUnitId)

    internal fun pollRewardedIfActive(adUnitId: String): RewardedAd? =
        if (activeRewardedIds.contains(adUnitId)) RewardedAdPreloader.pollAd(adUnitId) else null

    fun stopRewarded(adUnitId: String): Boolean {
        if (!activeRewardedIds.remove(adUnitId)) return false
        return RewardedAdPreloader.destroy(adUnitId)
    }

    @JvmOverloads
    fun startAppOpen(
        context: Context,
        adUnitId: String,
        bufferSize: Int = 1,
        callback: PreloadCallback? = null
    ): Boolean = startPreload(
        tag = "appOpen",
        context = context,
        adUnitId = adUnitId,
        testAdUnitId = AdsConstants.ADS_OPEN_APP_TEST_ID,
        bufferSize = bufferSize,
        activeIds = activeAppOpenIds,
        buildRequest = { AdRequest.Builder(it).build() },
        starter = { id, cfg, cb ->
            if (cb != null) AppOpenAdPreloader.start(id, cfg, cb)
            else AppOpenAdPreloader.start(id, cfg)
        },
        callback = callback
    )

    fun isAppOpenAvailable(adUnitId: String): Boolean =
        activeAppOpenIds.contains(adUnitId) && AppOpenAdPreloader.isAdAvailable(adUnitId)

    internal fun pollAppOpenIfActive(adUnitId: String): AppOpenAd? =
        if (activeAppOpenIds.contains(adUnitId)) AppOpenAdPreloader.pollAd(adUnitId) else null

    fun stopAppOpen(adUnitId: String): Boolean {
        if (!activeAppOpenIds.remove(adUnitId)) return false
        return AppOpenAdPreloader.destroy(adUnitId)
    }

    @JvmOverloads
    fun startNative(
        context: Context,
        adUnitId: String,
        bufferSize: Int = 1,
        callback: PreloadCallback? = null
    ): Boolean = startPreload(
        tag = "native",
        context = context,
        adUnitId = adUnitId,
        testAdUnitId = AdsConstants.AD_NATIVE_TEST_ID,
        bufferSize = bufferSize,
        activeIds = activeNativeIds,
        buildRequest = {
            NativeAdRequest.Builder(it, listOf(NativeAd.NativeAdType.NATIVE)).build()
        },
        starter = { id, cfg, cb ->
            if (cb != null) NativeAdPreloader.start(id, cfg, cb)
            else NativeAdPreloader.start(id, cfg)
        },
        callback = callback
    )

    fun isNativeAvailable(adUnitId: String): Boolean =
        activeNativeIds.contains(adUnitId) && NativeAdPreloader.isAdAvailable(adUnitId)

    internal fun pollNativeIfActive(adUnitId: String): NativeAd? {
        if (!activeNativeIds.contains(adUnitId)) return null
        val result = NativeAdPreloader.pollAd(adUnitId)
        return (result as? NativeAdLoadResult.NativeAdSuccess)?.ad
    }

    fun stopNative(adUnitId: String): Boolean {
        if (!activeNativeIds.remove(adUnitId)) return false
        return NativeAdPreloader.destroy(adUnitId)
    }

    /**
     * Stops and destroys all active preloaders started via [AdPreloaderManager].
     */
    fun stopAll() {
        if (!hasStartedAnyPreloader.compareAndSet(true, false)) return

        if (activeInterstitialIds.isNotEmpty()) {
            activeInterstitialIds.clear()
            InterstitialAdPreloader.destroyAll()
        }
        if (activeRewardedIds.isNotEmpty()) {
            activeRewardedIds.clear()
            RewardedAdPreloader.destroyAll()
        }
        if (activeAppOpenIds.isNotEmpty()) {
            activeAppOpenIds.clear()
            AppOpenAdPreloader.destroyAll()
        }
        if (activeNativeIds.isNotEmpty()) {
            activeNativeIds.clear()
            NativeAdPreloader.destroyAll()
        }
    }
}
