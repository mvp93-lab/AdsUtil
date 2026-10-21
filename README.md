# AdsUtil 📱

[English](README.md) | [Tiếng Việt](README.vi.md)

[![JitPack](https://jitpack.io/v/mvp93-lab/ads-util.svg)](https://jitpack.io/#mvp93-lab/ads-util)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![AdMob SDK](https://img.shields.io/badge/Google%20Ads%20Next--Gen-1.2.1-red.svg)](https://developers.google.com/admob)

**AdsUtil** is a modern, lightweight Android library designed to integrate and manage **Google AdMob** within **Jetpack Compose** applications seamlessly, automatically, and in strict accordance with the latest Google Play and privacy policies.

Engineered with full type safety and explicit code references, this documentation is optimized for both human engineers and **AI Coding Agents** (such as Cursor, Claude Code, GitHub Copilot, Gemini) to integrate into any Android project without ambiguity.

---

## 🌟 Key Features

- 🚀 **Google Next-Generation Ads SDK:** Built on top of Google's official Next-Gen Ads SDK (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1`), offering non-blocking asynchronous operations powered by Kotlin Coroutines.
- 🎨 **Jetpack Compose Native:** First-class Composable components (`BannerAd`, `SmallNativeAd`, `BigNativeAd`) ready for instant use without manually constructing an `AndroidView`.
- ✨ **Shimmer Skeleton Loading:** Built-in animated Shimmer placeholders while native ads load.
- 🛡️ **Automated GDPR / UMP Consent:** Complete encapsulation of Google User Messaging Platform (UMP SDK 4.0.0), checking IAB TCF Purpose 1 consent before requesting ads, and providing an out-of-the-box Privacy Options Form for EEA/UK regulations.
- ⚡ **Comprehensive Ad Format Support:**
  - **App Open Ads:** Automatically listens to `ProcessLifecycleOwner` to trigger ads when opening or resuming the application.
  - **Banner Ads:** Memory-cached by unique keys to prevent reloading across recompositions or tab transitions.
  - **Native Ads:** 2 layouts (Small compact bar & Big card with `MediaView`), adhering to AdChoices requirements and mandatory "Ad" attribution.
  - **Interstitial Ads:** Asynchronously loaded via coroutines with optional auto-preload flags.
  - **Rewarded Ads:** Opt-in rewarded format separating reward disbursement from ad dismissal.
- 🔒 **Debug Safety:** Automatically replaces production IDs with Google's official Test IDs in debug builds. The `showDebugToast` utility is guaranteed to be a no-op in Release/Production.
- 💎 **Global Ad Toggle (VIP Mode):** Enable or disable ads across the entire app with a single call (`AdManager.setAdsEnabled(false)`), instantly clearing caches for In-App Purchase / Premium subscriptions.

---

## 📋 Prerequisites

Before adding the library to your project, ensure your environment meets the following requirements:

- **Android Min SDK:** `24` or higher
- **Compile SDK:** `34` or higher (Recommended: `35` or `36`)
- **Java Compatibility:** `Java 17`
- **Kotlin:** `2.0+` with Jetpack Compose
- ⚠️ **CRITICAL SDK CONFLICT WARNING:**
  This library is built on the **Google Next-Generation Ads SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`). If your target project contains the legacy AdMob dependency (`com.google.android.gms:play-services-ads`), **you MUST remove it** to prevent duplicate class runtime errors.

---

## 📦 Installation

### 1. Add JitPack Repository

Add the JitPack repository to your root `settings.gradle.kts` (or root `build.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add the Dependency

Latest stable release: **`1.0.5`**

**Option 1: Using Version Catalog (`gradle/libs.versions.toml`) - Recommended**
```toml
[versions]
adsUtil = "1.0.5"

[libraries]
ads-util = { group = "com.github.mvp93-lab", name = "ads-util", version.ref = "adsUtil" }
```

Then add to your `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.ads.util)
}
```

**Option 2: Direct dependency in `app/build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.github.mvp93-lab:ads-util:1.0.5")
}
```

### 3. Configure `AndroidManifest.xml`

Declare internet permission, your AdMob Application ID, and your custom `Application` class inside `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".MyApplication"
        android:allowBackup="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApp">

        <!-- Replace with your actual AdMob App ID -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
    </application>
</manifest>
```

---

## 🚀 Quick Start Guide

### Step 1: Initialize App Open Ads in `Application`

Create an `Application` subclass (ensure `android:name=".MyApplication"` is set in `AndroidManifest.xml`):

```kotlin
package com.example.myapp

import android.app.Application
import com.mvp93.adsutil.AppOpenManager
import com.mvp93.adsutil.utils.AdsConstants

class MyApplication : Application() {

    lateinit var appOpenManager: AppOpenManager

    override fun onCreate() {
        super.onCreate()
        
        // AppOpenManager automatically triggers ads when app launches or returns from background
        appOpenManager = AppOpenManager(this)
        appOpenManager.setAdUnitId("YOUR_APP_OPEN_AD_UNIT_ID")
        // For testing, you can pass AdsConstants.ADS_OPEN_APP_TEST_ID
    }
}
```

---

### Step 2: Request Consent (GDPR/UMP) & Initialize `AdManager`

In your `MainActivity`, request user consent using Google's UMP SDK before initializing `AdManager`:

```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.ConsentManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val consentManager = ConsentManager(this)
        
        setContent {
            LaunchedEffect(Unit) {
                consentManager.requestConsentInfoUpdate(object : ConsentManager.OnConsentCheckListener {
                    override fun onConsentCheckCompleted() {
                        if (consentManager.canRequestAds) {
                            lifecycleScope.launch {
                                AdManager.initialize(applicationContext, "YOUR_ADMOB_APP_ID")
                            }
                        }
                    }

                    override fun onConsentFormError(error: String) {
                        // If error occurred but consent was previously granted
                        if (consentManager.canRequestAds) {
                            lifecycleScope.launch {
                                AdManager.initialize(applicationContext, "YOUR_ADMOB_APP_ID")
                            }
                        }
                    }
                })
            }

            // Your Compose UI
            MainScreen(consentManager)
        }
    }
}
```

---

### Step 3: Implement Privacy Options Button (Required by Google Play)

Google Play policy requires applications using UMP to offer users a way to change their consent choices at any time (typically placed in Settings or About screen):

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mvp93.adsutil.ConsentManager

@Composable
fun PrivacySettingsButton(consentManager: ConsentManager) {
    // Only display if Google UMP requires privacy options (e.g., users in EEA/UK)
    if (consentManager.isPrivacyOptionsRequired) {
        Button(onClick = {
            consentManager.showPrivacyOptionsForm { errorMessage ->
                if (errorMessage != null) {
                    // Handle error if any
                }
            }
        }) {
            Text("Privacy Settings")
        }
    }
}
```

---

### Step 4: Display Banner Ads

Embed the `BannerAd` Composable. Use a unique `key` for each ad slot to retain and cache the underlying view across recompositions:

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mvp93.adsutil.ui.BannerAd

@Composable
fun HomeScreenBanner() {
    BannerAd(
        modifier = Modifier.fillMaxWidth(),
        key = "home_screen_bottom_banner", // Unique key to cache and prevent reload during recomposition
        adUnitId = "YOUR_BANNER_AD_UNIT_ID"
    )
}
```

---

### Step 5: Display Native Ads

The library provides 2 ready-to-use layouts compliant with Google's Native Ad policies:

#### Small Native Ad (Horizontal compact layout)
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mvp93.adsutil.ui.SmallNativeAd

@Composable
fun ProfileNativeAdSection() {
    SmallNativeAd(
        modifier = Modifier.fillMaxWidth(),
        adUnitId = "YOUR_NATIVE_AD_UNIT_ID",
        screenTag = "profile_screen_native" // Unique cache tag per screen
    )
}
```

#### Big Native Ad (Vertical layout featuring MediaView)
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mvp93.adsutil.ui.BigNativeAd

@Composable
fun FeedNativeAdSection() {
    BigNativeAd(
        modifier = Modifier.fillMaxWidth(),
        adUnitId = "YOUR_NATIVE_AD_UNIT_ID",
        screenTag = "feed_screen_native"
    )
}
```

---

### Step 6: Interstitial Ads

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.mvp93.adsutil.AdManager
import kotlinx.coroutines.launch

@Composable
fun GameOverScreen(onNextLevel: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Button(onClick = {
        coroutineScope.launch {
            // Load interstitial asynchronously
            val isLoaded = AdManager.loadInterstitial(context, "YOUR_INTERSTITIAL_AD_UNIT_ID")
            if (isLoaded) {
                // Show ad (context must be or wrap an Activity)
                AdManager.showInterstitial(
                    context = context,
                    adUnitId = "YOUR_INTERSTITIAL_AD_UNIT_ID",
                    reloadAfterShow = true, // Preloads next ad automatically
                    onAdClosed = {
                        onNextLevel()
                    }
                )
            } else {
                onNextLevel()
            }
        }
    }) {
        Text("Continue to Next Level")
    }
}
```

---

### Step 7: Rewarded Ads

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.mvp93.adsutil.AdManager
import kotlinx.coroutines.launch

@Composable
fun RewardButton(onRewardGranted: (Int) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Button(onClick = {
        coroutineScope.launch {
            val isLoaded = AdManager.loadRewarded(context, "YOUR_REWARDED_AD_UNIT_ID")
            if (isLoaded) {
                AdManager.showRewarded(
                    context = context,
                    adUnitId = "YOUR_REWARDED_AD_UNIT_ID",
                    reloadAfterShow = true,
                    onUserEarnedReward = { rewardItem ->
                        // ONLY grant reward when this callback is invoked!
                        // rewardItem.amount, rewardItem.type
                        onRewardGranted(rewardItem.amount)
                    },
                    onAdClosed = {
                        // Callback when ad screen is closed
                    }
                )
            }
        }
    }) {
        Text("Watch Video (+50 Coins)")
    }
}
```

---

### Step 8: VIP Mode / Remove Ads Toggle

When a user purchases a VIP / Ad-free subscription:

```kotlin
import com.mvp93.adsutil.AdManager

// Disable all ads across the app & purge memory caches:
AdManager.setAdsEnabled(false)

// Re-enable ads if subscription expires:
AdManager.setAdsEnabled(true)
```
All Composables (`BannerAd`, `SmallNativeAd`, `BigNativeAd`) observe the `isAdsEnabled` StateFlow and disappear immediately without layout shifting or blank boxes.

---

## 🔍 Debug vs. Release Safety

To protect your AdMob account from policy violations and accidental self-clicks during development:

1. **Automatic Test ID Replacement:** In debug builds (`ApplicationInfo.FLAG_DEBUGGABLE != 0`), `AdManager` automatically swaps all ad unit IDs and the AdMob App ID with Google's official Test IDs, even if you pass production IDs in code.
2. **Automated EEA Consent Testing:** In debug builds, `ConsentManager` automatically resets the consent state and simulates `DEBUG_GEOGRAPHY_EEA` so that consent forms can be tested reliably on every launch.
3. **Safe Debug Toasts:** The extension `context.showDebugToast(...)` only executes on debug builds; it is completely silent in Release/Production.

---

## 🏛️ Architecture & API Reference

| Class / Object | Full Package | Responsibility |
| :--- | :--- | :--- |
| `AdManager` | `com.mvp93.adsutil.AdManager` | Central singleton: initialization, caching, coroutine loading & showing for all ad formats. |
| `ConsentManager` | `com.mvp93.adsutil.ConsentManager` | Google UMP SDK manager, IAB TCF Purpose 1 consent verification, and Privacy Options form display. |
| `AppOpenManager` | `com.mvp93.adsutil.AppOpenManager` | Observes `ProcessLifecycleOwner` to trigger App Open Ads on foreground transitions. |
| `BannerAd` | `com.mvp93.adsutil.ui.BannerAd` | Jetpack Compose wrapper for AdMob Banner, with key-based view caching. |
| `SmallNativeAd` | `com.mvp93.adsutil.ui.SmallNativeAd` | Composable for compact horizontal Native Ads with shimmer loading. |
| `BigNativeAd` | `com.mvp93.adsutil.ui.BigNativeAd` | Composable for large card Native Ads with `MediaView` and shimmer loading. |
| `AdsConstants` | `com.mvp93.adsutil.utils.AdsConstants` | Official Google AdMob test IDs for all formats. |
| `Extensions` | `com.mvp93.adsutil.utils.Extensions` | Safe utilities: `Context.isDebug()` and `Context.showDebugToast()`. |

---

## 🧪 Google Official Test IDs Available in Library

Accessible via `com.mvp93.adsutil.utils.AdsConstants`:

| Ad Format | Constant (`AdsConstants`) | Test Ad Unit ID |
| :--- | :--- | :--- |
| **App ID** | `ADS_APP_TEST_ID` | `ca-app-pub-3940256099942544~3347511713` |
| **Banner** | `AD_BANNER_TEST_ID` | `ca-app-pub-3940256099942544/6300978111` |
| **Adaptive Banner** | `ADS_ADAPTIVE_BANNER_TEST_ID` | `ca-app-pub-3940256099942544/9214589741` |
| **Native** | `AD_NATIVE_TEST_ID` | `ca-app-pub-3940256099942544/2247696110` |
| **Native Video** | `AD_NATIVE_VIDEO_TEST_ID` | `ca-app-pub-3940256099942544/1044960115` |
| **Interstitial** | `AD_INTERSTITIAL_TEST_ID` | `ca-app-pub-3940256099942544/1033173712` |
| **App Open** | `ADS_OPEN_APP_TEST_ID` | `ca-app-pub-3940256099942544/9257395921` |
| **Rewarded** | `AD_REWARDED_TEST_ID` | `ca-app-pub-3940256099942544/5224354917` |

---

## 📄 License

Distributed under your open-source or proprietary licensing terms.
Contributions, issues, and feature requests are welcome!
