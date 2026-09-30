# AdsUtil 📱

[![JitPack](https://jitpack.io/v/mvp93-lab/ads-util.svg)](https://jitpack.io/#mvp93-lab/ads-util)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2+-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![AdMob SDK](https://img.shields.io/badge/Google%20Ads%20Next--Gen-1.2.1-red.svg)](https://developers.google.com/admob)

**AdsUtil** là thư viện Android hiện đại giúp tích hợp và quản lý **Google AdMob** trong ứng dụng **Jetpack Compose** một cách tinh gọn, tự động và tuân thủ chặt chẽ các chính sách mới nhất của Google.

---

## 🌟 Tính Năng Nổi Bật

- 🚀 **Google Next-Generation Ads SDK:** Tiên phong áp dụng SDK thế hệ mới (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1`), hoạt động bất đồng bộ mượt mà với Kotlin Coroutines.
- 🎨 **Jetpack Compose Native:** Cung cấp sẵn các Composable (`BannerAd`, `SmallNativeAd`, `BigNativeAd`) hiển thị tức thì, không cần tự dựng `AndroidView`.
- ✨ **Shimmer Skeleton Loading:** Tích hợp sẵn hiệu ứng hoạt họa Shimmer mượt mà trong lúc tải quảng cáo Native.
- 🛡️ **Tự Động Quản Lý GDPR / UMP (Consent):** Bọc sẵn quy trình xin quyền riêng tư (User Messaging Platform SDK 4.0.0), kiểm tra trạng thái IAB TCF Purpose 1 trước khi tải quảng cáo.
- ⚡ **Hỗ Trợ Toàn Diện Các Định Dạng Ads:**
  - **App Open Ads:** Tự động lắng nghe vòng đời (`ProcessLifecycleOwner`) để bật quảng cáo khi mở/trở lại app.
  - **Banner Ads:** Tự động cache theo key, không lo reload khi recompose / đổi tab.
  - **Native Ads:** 2 định dạng (Nhỏ & Lớn với MediaView), tuân thủ chuẩn AdChoices và nhãn "Ad".
  - **Interstitial Ads:** Tải bất đồng bộ coroutine, hỗ trợ cờ tự động preload lại sau khi xem.
  - **Rewarded Ads:** Quảng cáo nhận thưởng chuẩn opt-in, phân tách rõ ràng giữa nhận thưởng và đóng quảng cáo.
- 🔒 **Debug Safety:** Tự động nhận diện môi trường Debug để hoán đổi ID thật sang Google Test ID, kèm cơ chế `showDebugToast` không bao giờ làm phiền người dùng bản Production/Release.
- 💎 **Quản Lý Trạng Thái Toàn Cục:** Bật/tắt quảng cáo toàn app (`setAdsEnabled`) chỉ bằng 1 dòng lệnh (hỗ trợ tính năng In-App Purchase / Mua gói VIP gỡ ads).

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

Tra cứu phiên bản mới nhất tại badge JitPack ở đầu trang hoặc mục [Releases](https://github.com/mvp93-lab/ads-util/releases):

**Cách 1: Sử dụng Version Catalog (`gradle/libs.versions.toml`) - Khuyên dùng**
```toml
[versions]
adsUtil = "<latest_version>" # ví dụ: "1.0.4"

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
    implementation("com.github.mvp93-lab:ads-util:<latest_version>")
}
```

### 3. Cấu hình `AndroidManifest.xml`

Khai báo quyền Internet và AdMob App ID trong `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <!-- Thay ca-app-pub-xxx bằng App ID thực tế của bạn -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
    </application>
</manifest>
```

---

## 🚀 Hướng Dẫn Sử Dụng (Quick Start)

### Bước 1: Cấu hình App Open Ads trong `Application`

Để hiển thị quảng cáo ngay khi ứng dụng khởi động hoặc từ background quay lại:

```kotlin
class MyApplication : Application() {

    lateinit var appOpenManager: AppOpenManager

    override fun onCreate() {
        super.onCreate()
        
        appOpenManager = AppOpenManager(this)
        appOpenManager.setAdUnitId("YOUR_APP_OPEN_AD_UNIT_ID")
    }
}
```

---

### Bước 2: Xin Consent (GDPR/UMP) và Khởi Tạo `AdManager`

Tại `MainActivity`, xin quyền riêng tư người dùng trước, sau đó khởi tạo SDK:

```kotlin
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
            MyMainScreen()
        }
    }
}
```

---

### Bước 3: Banner Ads trong Compose

Chỉ cần nhúng Composable `BannerAd`:

```kotlin
BannerAd(
    modifier = Modifier.fillMaxWidth(),
    key = "home_screen_banner", // Key giúp cache và tái sử dụng AdView
    adUnitId = "YOUR_BANNER_AD_UNIT_ID"
)
```

---

### Bước 4: Native Ads trong Compose

Thư viện cung cấp sẵn 2 loại bố cục Native Ad chuẩn chính sách:

#### Small Native Ad (Bố cục hàng ngang nhỏ gọn)
```kotlin
SmallNativeAd(
    modifier = Modifier.fillMaxWidth(),
    adUnitId = "YOUR_NATIVE_AD_UNIT_ID",
    screenTag = "profile_screen" // Tag lưu cache
)
```

#### Big Native Ad (Bố cục lớn tích hợp MediaView cho video / ảnh to)
```kotlin
BigNativeAd(
    modifier = Modifier.fillMaxWidth(),
    adUnitId = "YOUR_NATIVE_AD_UNIT_ID",
    screenTag = "feed_screen"
)
```

---

### Bước 5: Interstitial Ads (Quảng cáo xen kẽ)

```kotlin
val coroutineScope = rememberCoroutineScope()
val context = LocalContext.current

Button(onClick = {
    coroutineScope.launch {
        // Tải quảng cáo
        val isLoaded = AdManager.loadInterstitial(context, "YOUR_INTERSTITIAL_AD_UNIT_ID")
        if (isLoaded) {
            // Hiển thị quảng cáo
            AdManager.showInterstitial(
                context = context,
                adUnitId = "YOUR_INTERSTITIAL_AD_UNIT_ID",
                reloadAfterShow = true, // Tự động preload lại cho lần sau
                onAdClosed = {
                    // Tiếp tục luồng xử lý của ứng dụng (chuyển màn hình, bắt đầu game,...)
                }
            )
        }
    }
}) {
    Text("Xem Interstitial Ad")
}
```

---

### Bước 6: Rewarded Ads (Quảng cáo nhận thưởng)

```kotlin
val coroutineScope = rememberCoroutineScope()
val context = LocalContext.current
var points by remember { mutableIntStateOf(0) }

Button(onClick = {
    coroutineScope.launch {
        val isLoaded = AdManager.loadRewarded(context, "YOUR_REWARDED_AD_UNIT_ID")
        if (isLoaded) {
            AdManager.showRewarded(
                context = context,
                adUnitId = "YOUR_REWARDED_AD_UNIT_ID",
                reloadAfterShow = true,
                onUserEarnedReward = { rewardItem ->
                    // CHỈ trao thưởng khi callback này được gọi!
                    points += rewardItem.amount
                    Log.d("Ads", "Cộng thưởng: ${rewardItem.amount} ${rewardItem.type}")
                },
                onAdClosed = {
                    // Quảng cáo đã đóng
                }
            )
        }
    }
}) {
    Text("Xem Video (+Xu)")
}
```

---

### Bước 7: Quản Lý Trạng Thái VIP / Tắt Quảng Cáo (Remove Ads)

Khi người dùng mua gói Premium hoặc tắt ads qua Remote Config:

```kotlin
// Tắt toàn bộ ads và xóa cache bộ nhớ:
AdManager.setAdsEnabled(false)

// Bật lại:
AdManager.setAdsEnabled(true)
```
Tất cả các Composable `BannerAd`, `SmallNativeAd`, `BigNativeAd` sẽ tự động ẩn ngay lập tức mà không cần viết lại logic ẩn/hiện thủ công!

---

## 🏛️ Kiến Trúc & API Reference

| Lớp (Class / Object) | Trách nhiệm chính |
| :--- | :--- |
| [`AdManager`](ads-core/src/main/java/com/mvp93/adsutil/AdManager.kt) | Singleton trung tâm: quản lý khởi tạo, bộ nhớ đệm (Cache), tải và hiển thị Banner, Native, Interstitial, App Open, Rewarded. |
| [`ConsentManager`](ads-core/src/main/java/com/mvp93/adsutil/ConsentManager.kt) | Quản lý quy trình Google UMP SDK, kiểm tra consent IAB TCF, hiển thị Privacy Options Form. |
| [`AppOpenManager`](ads-core/src/main/java/com/mvp93/adsutil/AppOpenManager.kt) | Lắng nghe vòng đời ứng dụng bằng `ProcessLifecycleOwner` để kích hoạt App Open Ads khi đưa app lên Foreground. |
| [`BannerAd`](ads-core/src/main/java/com/mvp93/adsutil/ui/BannerAd.kt) | Jetpack Compose Wrapper cho AdMob Banner, tự động cache và gắn liền lifecycle. |
| [`NativeAds`](ads-core/src/main/java/com/mvp93/adsutil/ui/NativeAds.kt) | Composable `SmallNativeAd`, `BigNativeAd`, `ShimmerLoading` hỗ trợ hiển thị quảng cáo tự nhiên chuẩn UI/UX. |
| [`Extensions`](ads-core/src/main/java/com/mvp93/adsutil/utils/Extensions.kt) | Tiện ích `Context.isDebug()` và `Context.showDebugToast()` an toàn cho môi trường Release. |
| [`AdsConstants`](ads-core/src/main/java/com/mvp93/adsutil/utils/Constants.kt) | Chứa danh sách đầy đủ Google Official Test Ad Unit IDs. |

---

## 🧪 Google Official Test IDs có sẵn trong thư viện

| Loại Quảng Cáo | Hằng Số (`AdsConstants`) | Test Ad Unit ID |
| :--- | :--- | :--- |
| **App ID** | `ADS_APP_TEST_ID` | `ca-app-pub-3940256099942544~3347511713` |
| **Banner** | `AD_BANNER_TEST_ID` | `ca-app-pub-3940256099942544/6300978111` |
| **Native** | `AD_NATIVE_TEST_ID` | `ca-app-pub-3940256099942544/2247696110` |
| **Interstitial** | `AD_INTERSTITIAL_TEST_ID` | `ca-app-pub-3940256099942544/1033173712` |
| **App Open** | `ADS_OPEN_APP_TEST_ID` | `ca-app-pub-3940256099942544/9257395921` |
| **Rewarded** | `AD_REWARDED_TEST_ID` | `ca-app-pub-3940256099942544/5224354917` |

---

## 📄 License

Dự án được phân phối dưới giấy phép mã nguồn mở hoặc giấy phép nội bộ của bạn.
Mọi đóng góp (Issues & Pull Requests) đều được hoan nghênh!
