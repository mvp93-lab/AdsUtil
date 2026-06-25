package com.mvp93.adsutil.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.utils.AdsConstants

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    key: String = "default",
    adUnitId: String = AdsConstants.AD_BANNER_TEST_ID
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()
    val isInitialized by AdManager.isInitialized.collectAsState()

    if (!isAdsEnabled || !isInitialized) return

    // Retrieve or create the AdView from the Manager cache
    val adView = remember(key, isInitialized) {
        AdManager.getOrCreateBanner(context, key, adUnitId)
    }

    if (adView == null) return

    // Handle Lifecycle events (only clean up on global destroy if needed, 
    // or just let it stay cached for persistence)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                // For cached ads, we might not want to destroy immediately on screen exit
                // but we forward the event anyway if the activity is dying.
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Note: We don't call adView.destroy() here because we want to reuse it 
            // from the AdManager cache when the user switches back to this tab.
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView }
    )
}
