package com.mvp93.adsutil

import com.mvp93.adsutil.utils.AdsConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `default test device ids are preserved in ConsentManager`() {
        assertEquals(
            listOf("C6F069CF4A4DC8F0272DA62232522711"),
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
}
