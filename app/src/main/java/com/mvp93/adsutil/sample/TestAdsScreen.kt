package com.mvp93.adsutil.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.libraries.ads.mobile.sdk.common.ExperimentalApi
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPosition
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPresentationScope
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.ui.BannerAd
import com.mvp93.adsutil.ui.BigNativeAd
import com.mvp93.adsutil.ui.SmallNativeAd
import com.mvp93.adsutil.utils.AdsConstants
import com.mvp93.adsutil.utils.showDebugToast
import kotlinx.coroutines.launch

@OptIn(ExperimentalApi::class)
@Composable
fun TestAdsScreen(
    isPrivacyOptionsRequired: Boolean = false,
    onOpenPrivacyOptions: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()

    var showBanner by remember { mutableStateOf(false) }
    var useAdaptiveBanner by remember { mutableStateOf(false) }
    var showSmallNative by remember { mutableStateOf(false) }
    var showBigNative by remember { mutableStateOf(false) }
    var rewardCoins by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Ads Util Test Screen", style = MaterialTheme.typography.headlineMedium)

        // Global VIP / Remove Ads Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAdsEnabled) "Ads Enabled" else "VIP Mode (Ads Disabled)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Toggle AdManager.setAdsEnabled()",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isAdsEnabled,
                    onCheckedChange = { enabled ->
                        AdManager.setAdsEnabled(enabled)
                    }
                )
            }
        }

        if (isPrivacyOptionsRequired) {
            OutlinedButton(onClick = onOpenPrivacyOptions) {
                Text("Privacy Settings (UMP)")
            }
        }

        HorizontalDivider()

        // Banner Ad
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { showBanner = !showBanner }) {
                Text(if (showBanner) "Hide Banner" else "Load Banner")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useAdaptiveBanner,
                    onCheckedChange = { useAdaptiveBanner = it }
                )
                Text("Adaptive")
            }
        }
        if (showBanner) {
            BannerAd(
                modifier = Modifier.fillMaxWidth(),
                key = if (useAdaptiveBanner) "sample_adaptive_banner" else "sample_standard_banner",
                adUnitId = if (useAdaptiveBanner) {
                    AdsConstants.ADS_ADAPTIVE_BANNER_TEST_ID
                } else {
                    AdsConstants.AD_BANNER_TEST_ID
                },
                isAdaptive = useAdaptiveBanner
            )
        }

        HorizontalDivider()

        // Small Native Ad
        Button(onClick = { showSmallNative = !showSmallNative }) {
            Text(if (showSmallNative) "Hide Small Native" else "Load Small Native")
        }
        if (showSmallNative) {
            SmallNativeAd(modifier = Modifier.fillMaxWidth())
        }

        HorizontalDivider()

        // Big Native Ad
        Button(onClick = { showBigNative = !showBigNative }) {
            Text(if (showBigNative) "Hide Big Native" else "Load Big Native")
        }
        if (showBigNative) {
            BigNativeAd(modifier = Modifier.fillMaxWidth())
        }

        HorizontalDivider()

        // Interstitial Ad
        Button(onClick = {
            coroutineScope.launch {
                val loaded = AdManager.loadInterstitial(context, AdsConstants.AD_INTERSTITIAL_TEST_ID)
                if (loaded) {
                    AdManager.showInterstitial(context, AdsConstants.AD_INTERSTITIAL_TEST_ID)
                }
            }
        }) {
            Text("Load & Show Interstitial")
        }

        HorizontalDivider()

        // App Open Ad
        Button(onClick = {
            coroutineScope.launch {
                val loaded = AdManager.loadAppOpenAd(context, AdsConstants.ADS_OPEN_APP_TEST_ID)
                if (loaded) {
                    context.showDebugToast("App Open Ad Loaded (will show on next app return)")
                } else {
                    context.showDebugToast("App Open Ad Load Failed")
                }
            }
        }) {
            Text("Load App Open Ad")
        }

        HorizontalDivider()

        // Picture-in-Picture (PiP) Ad
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                coroutineScope.launch {
                    val loaded = AdManager.loadPictureInPictureAd(context, AdsConstants.AD_PIP_TEST_ID)
                    if (loaded) {
                        val shown = AdManager.showPictureInPictureAd(
                            context = context,
                            adUnitId = AdsConstants.AD_PIP_TEST_ID,
                            position = PictureInPictureAdPosition.BOTTOM_RIGHT,
                            presentationScope = PictureInPictureAdPresentationScope.SCREEN,
                            onAdShown = { context.showDebugToast("PiP Ad Shown") },
                            onAdHidden = { context.showDebugToast("PiP Ad Hidden") },
                            onAdFailedToShow = { err -> context.showDebugToast("PiP Show Failed: ${err.message}") }
                        )
                        if (!shown) {
                            context.showDebugToast("PiP Ad Could Not Show")
                        }
                    } else {
                        context.showDebugToast("PiP Ad Load Failed")
                    }
                }
            }) {
                Text("Load & Show PiP Ad")
            }
            OutlinedButton(onClick = {
                AdManager.destroyPictureInPictureAd(AdsConstants.AD_PIP_TEST_ID)
            }) {
                Text("Close PiP")
            }
        }

        HorizontalDivider()

        // Rewarded Ad
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🪙 Reward Balance: $rewardCoins points",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = {
                    coroutineScope.launch {
                        context.showDebugToast("Loading Rewarded Ad...")
                        val loaded = AdManager.loadRewarded(context, AdsConstants.AD_REWARDED_TEST_ID)
                        if (loaded) {
                            AdManager.showRewarded(
                                context = context,
                                adUnitId = AdsConstants.AD_REWARDED_TEST_ID,
                                onUserEarnedReward = { rewardItem ->
                                    rewardCoins += rewardItem.amount
                                    context.showDebugToast("🎉 Earned +${rewardItem.amount} ${rewardItem.type}!")
                                },
                                onAdClosed = {
                                    context.showDebugToast("Rewarded ad closed")
                                }
                            )
                        } else {
                            context.showDebugToast("Rewarded Ad Load Failed")
                        }
                    }
                }) {
                    Text("Watch Video for +Points")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
