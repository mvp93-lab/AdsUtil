package com.mvp93.adsutil.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.utils.AdsConstants
import com.mvp93.adsutil.utils.findActivity

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    key: String = "default",
    adUnitId: String = AdsConstants.AD_BANNER_TEST_ID,
    isAdaptive: Boolean = false,
    adSize: AdSize? = null
) {
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()
    val isInitialized by AdManager.isInitialized.collectAsState()

    if (!isAdsEnabled || !isInitialized) return

    if (adSize != null || !isAdaptive) {
        BannerAdContent(
            modifier = modifier.fillMaxWidth(),
            key = key,
            adUnitId = adUnitId,
            targetAdSize = adSize ?: AdSize.BANNER,
            isInitialized = isInitialized
        )
    } else {
        val context = LocalContext.current
        val fallbackWidthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt().takeIf { it > 0 } ?: fallbackWidthDp
            val adaptiveSize = remember(widthDp, context) {
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
            }
            BannerAdContent(
                modifier = Modifier.fillMaxWidth(),
                key = key,
                adUnitId = adUnitId,
                targetAdSize = adaptiveSize,
                isInitialized = isInitialized
            )
        }
    }
}

@Composable
private fun BannerAdContent(
    modifier: Modifier,
    key: String,
    adUnitId: String,
    targetAdSize: AdSize,
    isInitialized: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Retrieve or create the AdView from the Manager cache
    val adView = remember(key, adUnitId, targetAdSize, isInitialized) {
        AdManager.getOrCreateBanner(context, key, adUnitId, targetAdSize)
    }

    if (adView == null) return

    // Destroy cached banner only when the hosting Activity is actually being destroyed
    // so tab/screen switches can still reuse it without leaking the Activity.
    DisposableEffect(lifecycleOwner, key) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                val activity = context.findActivity()
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    AdManager.destroyBanner(key)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            (adView.parent as? ViewGroup)?.removeView(adView)
            adView
        },
        onRelease = { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
    )
}
