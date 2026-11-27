package com.mvp93.adsutil

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.mvp93.adsutil.utils.AdsConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AdManagerTest {

    @After
    fun tearDown() {
        AdManager.setAdsEnabled(true)
    }

    @Test
    fun `setAdsEnabled updates isAdsEnabled StateFlow`() {
        assertTrue(AdManager.isAdsEnabled.value)

        AdManager.setAdsEnabled(false)
        assertFalse(AdManager.isAdsEnabled.value)

        AdManager.setAdsEnabled(true)
        assertTrue(AdManager.isAdsEnabled.value)
    }

    @Test
    fun `initial state has no cached ads and is not initialized`() {
        assertFalse(AdManager.isInitialized.value)
        assertFalse(AdManager.isAppOpenAdAvailable())
        assertFalse(AdManager.isRewardedAdLoaded(AdsConstants.AD_REWARDED_TEST_ID))
        assertNull(AdManager.getCachedNativeAd("default"))
    }

    @Test
    fun `default test device ids are empty by default in ConsentManager`() {
        assertEquals(
            emptyList<String>(),
            ConsentManager.DEFAULT_TEST_DEVICE_IDS
        )
    }

    @Test
    fun `isAppOpenAdValid enforces 4 hour expiration window`() {
        val loadedAt = 1_000_000L
        assertTrue(AdManager.isAppOpenAdValid(loadedAt, nowMs = loadedAt))
        assertTrue(AdManager.isAppOpenAdValid(loadedAt, nowMs = loadedAt + AdManager.APP_OPEN_AD_EXPIRATION_MS - 1))
        assertFalse(AdManager.isAppOpenAdValid(loadedAt, nowMs = loadedAt + AdManager.APP_OPEN_AD_EXPIRATION_MS))
        assertFalse(AdManager.isAppOpenAdValid(loadedAt, nowMs = loadedAt - 1))
    }

    @Test
    fun `deduplicateLoad returns true immediately when ad is already cached`() = runBlocking {
        val cache = ConcurrentHashMap<String, String>()
        val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        cache["unit_1"] = "cached_ad"
        val invocations = AtomicInteger(0)

        val loaded = AdManager.deduplicateLoad(
            key = "unit_1",
            cache = cache,
            inFlightLoads = inFlight
        ) { _, _ ->
            invocations.incrementAndGet()
        }

        assertTrue(loaded)
        assertEquals(0, invocations.get())
    }

    @Test
    fun `deduplicateLoad shares single in-flight request for same adUnitId`() = runBlocking {
        val cache = ConcurrentHashMap<String, String>()
        val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        val invocations = AtomicInteger(0)
        var completeSuccess: ((String) -> Unit)? = null

        val first = async {
            AdManager.deduplicateLoad(
                key = "unit_1",
                cache = cache,
                inFlightLoads = inFlight
            ) { onLoaded, _ ->
                invocations.incrementAndGet()
                completeSuccess = onLoaded
            }
        }
        val second = async {
            AdManager.deduplicateLoad(
                key = "unit_1",
                cache = cache,
                inFlightLoads = inFlight
            ) { _, _ ->
                invocations.incrementAndGet()
            }
        }

        // Allow coroutines to reach the suspended await point
        while (completeSuccess == null) {
            kotlinx.coroutines.yield()
        }
        completeSuccess!!.invoke("loaded_ad")

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, invocations.get())
        assertEquals("loaded_ad", cache["unit_1"])
        assertTrue(inFlight.isEmpty())
    }

    @Test
    fun `deduplicateLoad does not store ad when shouldStore returns false`() = runBlocking {
        val cache = ConcurrentHashMap<String, String>()
        val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        val loaded = AdManager.deduplicateLoad(
            key = "unit_1",
            cache = cache,
            inFlightLoads = inFlight,
            shouldStore = { false }
        ) { onLoaded, _ ->
            onLoaded("loaded_ad")
        }

        assertFalse(loaded)
        assertFalse(cache.containsKey("unit_1"))
        assertTrue(inFlight.isEmpty())
    }

    @Test
    fun `resolveBannerTestAdUnitId selects adaptive test id for inline adaptive size`() {
        assertEquals(AdsConstants.AD_BANNER_TEST_ID, AdManager.resolveBannerTestAdUnitId(AdSize.BANNER))
        assertEquals(
            AdsConstants.ADS_ADAPTIVE_BANNER_TEST_ID,
            AdManager.resolveBannerTestAdUnitId(AdSize.getInlineAdaptiveBannerAdSize(320, 100))
        )
    }
}
