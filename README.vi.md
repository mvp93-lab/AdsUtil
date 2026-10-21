# AdsUtil 📱

[English](README.md) | [Tiếng Việt](README.vi.md)

[![JitPack](https://jitpack.io/v/mvp93-lab/ads-util.svg)](https://jitpack.io/#mvp93-lab/ads-util)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![AdMob SDK](https://img.shields.io/badge/Google%20Ads%20Next--Gen-1.2.1-red.svg)](https://developers.google.com/admob)

**AdsUtil** là thư viện Android hiện đại giúp tích hợp và quản lý **Google AdMob** trong ứng dụng **Jetpack Compose** một cách tinh gọn, tự động và tuân thủ chặt chẽ các chính sách mới nhất của Google.

Thư viện được tối ưu hóa đặc biệt với tài liệu chi tiết, tường minh về kiểu dữ liệu và import để cả **lập trình viên** lẫn các **AI Coding Agent** (như Cursor, Claude Code, GitHub Copilot, Gemini) có thể tự động tích hợp chính xác vào bất kỳ dự án Android nào.

---

## 🌟 Tính Năng Nổi Bật

- 🚀 **Google Next-Generation Ads SDK:** Tiên phong áp dụng SDK thế hệ mới (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1`), hoạt động bất đồng bộ mượt mà với Kotlin Coroutines.
- 🎨 **Jetpack Compose Native:** Cung cấp sẵn các Composable (`BannerAd`, `SmallNativeAd`, `BigNativeAd`) hiển thị tức thì, không cần tự dựng `AndroidView`.
- ✨ **Shimmer Skeleton Loading:** Tích hợp sẵn hiệu ứng hoạt họa Shimmer mượt mà trong lúc tải quảng cáo Native.
- 🛡️ **Tự Động Quản Lý GDPR / UMP (Consent):** Bọc sẵn quy trình xin quyền riêng tư (Google User Messaging Platform SDK 4.0.0), kiểm tra trạng thái IAB TCF Purpose 1 trước khi tải quảng cáo, cung cấp sẵn hàm mở Privacy Options Form cho người dùng EEA.
- ⚡ **Hỗ Trợ Toàn Diện Các Định Dạng Ads:**
  - **App Open Ads:** Tự động lắng nghe vòng đời (`ProcessLifecycleOwner`) để bật quảng cáo khi mở/trở lại app.
  - **Banner Ads:** Tự động cache theo key, không lo reload khi recompose / đổi tab.
  - **Native Ads:** 2 định dạng (Small & Big tích hợp MediaView), tuân thủ chuẩn AdChoices và nhãn "Ad".
  - **Interstitial Ads:** Tải bất đồng bộ coroutine, hỗ trợ cờ tự động preload lại sau khi xem.
  - **Rewarded Ads:** Quảng cáo nhận thưởng chuẩn opt-in, phân tách rõ ràng giữa nhận thưởng và đóng quảng cáo.
- 🔒 **Debug Safety:** Tự động nhận diện môi trường Debug để hoán đổi ID thật sang Google Test ID, kèm cơ chế `showDebugToast` không bao giờ làm phiền người dùng bản Production/Release.
- 💎 **Quản Lý Trạng Thái Toàn Cục:** Bật/tắt quảng cáo toàn app (`setAdsEnabled`) chỉ bằng 1 dòng lệnh (hỗ trợ tính năng In-App Purchase / Mua gói VIP gỡ ads).

---

## 📋 Yêu Cầu Dự Án (Prerequisites)

Trước khi tích hợp vào dự án của bạn, hãy đảm bảo các yêu cầu sau:

- **Android Min SDK:** `24` trở lên
- **Compile SDK:** `34` trở lên (Khuyên dùng `35` hoặc `36`)
- **Java Compatibility:** `Java 17`
- **Kotlin:** `2.0+` & Jetpack Compose
- ⚠️ **LƯU Ý QUAN TRỌNG VỀ XUNG ĐỘT SDK:** 
  Thư viện sử dụng **Google Next-Generation Ads SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`). Nếu dự án của bạn đang có thư viện AdMob truyền thống (`com.google.android.gms:play-services-ads`), **bắt buộc phải gỡ bỏ nó** để tránh xung đột trùng lặp lớp (Duplicate class collision).

---

## 📦 Cài Đặt (Installation)

### 1. Thêm JitPack Repository

Thêm repository JitPack vào file `settings.gradle.kts` (hoặc `build.gradle.kts` gốc):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Thêm Thư Viện

Phiên bản phát hành ổn định hiện tại: **`1.0.5`**

**Cách 1: Sử dụng Version Catalog (`gradle/libs.versions.toml`) - Khuyên dùng**
```toml
[versions]
adsUtil = "1.0.5"

[libraries]
ads-util = { group = "com.github.mvp93-lab", name = "ads-util", version.ref = "adsUtil" }
```
Sau đó thêm vào `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.ads.util)
}
```

**Cách 2: Thêm trực tiếp vào `app/build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.github.mvp93-lab:ads-util:1.0.5")
}
```

### 3. Cấu hình `AndroidManifest.xml`

Khai báo quyền Internet, AdMob App ID và Application class trong `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".MyApplication"
        android:allowBackup="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApp">

        <!-- Thay ca-app-pub-xxx bằng App ID AdMob thực tế của bạn -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
    </application>
</manifest>
```

---

## 🚀 Hướng Dẫn Sử Dụng (Quick Start)

### Bước 1: Khởi tạo App Open Ads trong `Application`

Tạo lớp `MyApplication` kế thừa từ `android.app.Application` (đảm bảo đã đăng ký `android:name=".MyApplication"` trong `AndroidManifest.xml`):

```kotlin
package com.example.myapp

import android.app.Application
import com.mvp93.adsutil.AppOpenManager
import com.mvp93.adsutil.utils.AdsConstants

class MyApplication : Application() {

    lateinit var appOpenManager: AppOpenManager

    override fun onCreate() {
        super.onCreate()
        
        // Khởi tạo AppOpenManager để tự động hiển thị ad khi mở app hoặc từ background quay lại
        appOpenManager = AppOpenManager(this)
        appOpenManager.setAdUnitId("YOUR_APP_OPEN_AD_UNIT_ID") 
        // Trong môi trường debug, bạn có thể truyền AdsConstants.ADS_OPEN_APP_TEST_ID
    }
}
```

---

### Bước 2: Xin Consent (GDPR/UMP) và Khởi Tạo `AdManager`

Tại `MainActivity`, xin quyền riêng tư người dùng theo chuẩn Google UMP trước, sau đó khởi tạo SDK:

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
                        // Nếu có lỗi mạng hoặc lỗi form nhưng phiên trước đã cấp consent:
                        if (consentManager.canRequestAds) {
                            lifecycleScope.launch {
                                AdManager.initialize(applicationContext, "YOUR_ADMOB_APP_ID")
                            }
                        }
                    }
                })
            }

            // Giao diện Compose của bạn
            MainScreen(consentManager)
        }
    }
}
```

---

### Bước 3: Nút Cài Đặt Quyền Riêng Tư (Privacy Options Form)

Google Play và luật GDPR bắt buộc ứng dụng phải cung cấp nút cho người dùng (thường đặt ở màn hình Settings) để họ có thể xem lại hoặc thu hồi quyền riêng tư:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mvp93.adsutil.ConsentManager

@Composable
fun PrivacySettingsButton(consentManager: ConsentManager) {
    // Chỉ hiển thị nút khi Google UMP yêu cầu (ví dụ người dùng thuộc khu vực EEA/UK)
    if (consentManager.isPrivacyOptionsRequired) {
        Button(onClick = {
            consentManager.showPrivacyOptionsForm { errorMessage ->
                if (errorMessage != null) {
                    // Xử lý lỗi nếu có
                }
            }
        }) {
            Text("Cài đặt quyền riêng tư (Privacy Settings)")
        }
    }
}
```

---

### Bước 4: Banner Ads trong Compose

Nhúng trực tiếp Composable `BannerAd`. Mỗi vị trí quảng cáo nên có một `key` riêng biệt để thư viện quản lý cache hiệu quả:

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mvp93.adsutil.ui.BannerAd

@Composable
fun HomeScreenBanner() {
    BannerAd(
        modifier = Modifier.fillMaxWidth(),
        key = "home_screen_bottom_banner", // Key định danh giúp cache và tránh reload khi recompose
        adUnitId = "YOUR_BANNER_AD_UNIT_ID"
    )
}
```

---

### Bước 5: Native Ads trong Compose

Thư viện cung cấp sẵn 2 loại bố cục Native Ad đạt chuẩn chính sách của Google (AdChoices, nhãn Ad, MediaView):

#### Small Native Ad (Bố cục hàng ngang nhỏ gọn)
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
        screenTag = "profile_screen_native" // screenTag định danh cache riêng cho màn hình này
    )
}
```

#### Big Native Ad (Bố cục lớn tích hợp MediaView cho video / ảnh lớn)
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

### Bước 6: Interstitial Ads (Quảng cáo xen kẽ)

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
            // Tải quảng cáo bất đồng bộ
            val isLoaded = AdManager.loadInterstitial(context, "YOUR_INTERSTITIAL_AD_UNIT_ID")
            if (isLoaded) {
                // Hiển thị quảng cáo (context cần là một Activity)
                AdManager.showInterstitial(
                    context = context,
                    adUnitId = "YOUR_INTERSTITIAL_AD_UNIT_ID",
                    reloadAfterShow = true, // Tự động preload lại cho lần mở sau
                    onAdClosed = {
                        onNextLevel()
                    }
                )
            } else {
                onNextLevel()
            }
        }
    }) {
        Text("Qua màn tiếp theo")
    }
}
```

---

### Bước 7: Rewarded Ads (Quảng cáo nhận thưởng)

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
                        // CHỈ trao thưởng khi callback này được kích hoạt!
                        // rewardItem.amount, rewardItem.type
                        onRewardGranted(rewardItem.amount)
                    },
                    onAdClosed = {
                        // Callback khi người dùng tắt màn hình quảng cáo
                    }
                )
            }
        }
    }) {
        Text("Xem Video (+50 Xu)")
    }
}
```

---

### Bước 8: Quản Lý Trạng Thái VIP / Tắt Quảng Cáo (Remove Ads)

Khi người dùng mua gói Premium hoặc tắt ads qua Remote Config:

```kotlin
import com.mvp93.adsutil.AdManager

// Tắt toàn bộ ads và xóa sạch cache trong bộ nhớ:
AdManager.setAdsEnabled(false)

// Bật lại khi hết hạn gói:
AdManager.setAdsEnabled(true)
```
Tất cả các Composable `BannerAd`, `SmallNativeAd`, `BigNativeAd` sẽ tự động ẩn ngay lập tức theo Flow trạng thái mà không để lại khoảng trắng hay giật màn hình!

---

## 🔍 Cơ Chế Debug An Toàn (Debug vs Release Safety)

Để đảm bảo tài khoản AdMob của bạn không bao giờ bị khóa do vi phạm chính sách click tặc hoặc tự hiển thị ad thật trong lúc phát triển:

1. **Tự Động Tráo Google Test ID:** Khi ứng dụng chạy ở môi trường Debug (`ApplicationInfo.FLAG_DEBUGGABLE != 0`), `AdManager` tự động thay thế mọi Ad Unit ID và AdMob App ID thành Test ID chính thức của Google, ngay cả khi bạn truyền ID thật vào hàm.
2. **Kiểm Thử GDPR / EEA Tự Động:** Trong bản Debug, `ConsentManager` tự động `reset()` trạng thái consent và ép khu vực địa lý `DEBUG_GEOGRAPHY_EEA` để hộp thoại xin quyền luôn xuất hiện mỗi lần bạn test.
3. **Debug Toast An Toàn:** Tiện ích `context.showDebugToast(...)` chỉ hiển thị Toast trong bản Debug; trên bản Release/Production, hàm này sẽ hoàn toàn im lặng.

---

## 🏛️ Kiến Trúc & API Reference

| Lớp (Class / Object) | Package Đầy Đủ | Trách nhiệm chính |
| :--- | :--- | :--- |
| `AdManager` | `com.mvp93.adsutil.AdManager` | Singleton trung tâm: quản lý khởi tạo, bộ nhớ đệm (Cache), tải và hiển thị Banner, Native, Interstitial, App Open, Rewarded. |
| `ConsentManager` | `com.mvp93.adsutil.ConsentManager` | Quản lý quy trình Google UMP SDK, kiểm tra consent IAB TCF, hiển thị Privacy Options Form. |
| `AppOpenManager` | `com.mvp93.adsutil.AppOpenManager` | Lắng nghe vòng đời ứng dụng bằng `ProcessLifecycleOwner` để kích hoạt App Open Ads khi đưa app lên Foreground. |
| `BannerAd` | `com.mvp93.adsutil.ui.BannerAd` | Jetpack Compose Wrapper cho AdMob Banner, tự động cache và quản lý recomposition. |
| `SmallNativeAd` | `com.mvp93.adsutil.ui.SmallNativeAd` | Composable hiển thị Native Ad dạng thanh ngang nhỏ gọn, có shimmer loading. |
| `BigNativeAd` | `com.mvp93.adsutil.ui.BigNativeAd` | Composable hiển thị Native Ad dạng thẻ lớn với MediaView, có shimmer loading. |
| `AdsConstants` | `com.mvp93.adsutil.utils.AdsConstants` | Chứa danh sách đầy đủ Google Official Test Ad Unit IDs. |
| `Extensions` | `com.mvp93.adsutil.utils.Extensions` | Tiện ích `Context.isDebug()` và `Context.showDebugToast()`. |

---

## 🧪 Google Official Test IDs có sẵn trong thư viện

Các ID này được định nghĩa sẵn trong `com.mvp93.adsutil.utils.AdsConstants`:

| Loại Quảng Cáo | Hằng Số (`AdsConstants`) | Test Ad Unit ID |
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

Dự án được phân phối dưới giấy phép mã nguồn mở hoặc giấy phép nội bộ của bạn.
Mọi đóng góp (Issues & Pull Requests) đều được hoan nghênh!
