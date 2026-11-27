package com.mvp93.adsutil

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mvp93.adsutil.utils.showDebugToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Manager for App Open Ads to show them when the app is brought to the foreground.
 */
class AppOpenManager(private val application: Application) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentActivity: Activity? = null
    private var adUnitId: String = ""
    private var isLoadingAd: Boolean = false
    private var isShowingAd: Boolean = false
    private var lastAdDismissedTimeMs: Long = 0L
    private var cooldownMillis: Long = 0L
    private val excludedActivityClasses = mutableSetOf<Class<out Activity>>()

    init {
        application.registerActivityLifecycleCallbacks(this)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } else {
            scope.launch {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this@AppOpenManager)
            }
        }

        // Automatically preload once AdManager finishes initialization (after UMP consent)
        // or when ads are re-enabled after being disabled.
        scope.launch {
            combine(AdManager.isInitialized, AdManager.isAdsEnabled) { initialized, enabled ->
                initialized && enabled
            }
                .distinctUntilChanged()
                .collect { canLoad ->
                    if (canLoad && adUnitId.isNotEmpty() && !AdManager.isAppOpenAdAvailable(adUnitId)) {
                        preloadAd("App Open Ad Preloaded")
                    }
                }
        }
    }

    fun setAdUnitId(id: String) {
        this.adUnitId = id
        preloadAd("App Open Ad Preloaded")
    }

    /**
     * Sets a minimum cooldown duration between consecutive App Open Ad displays.
     */
    fun setCooldownMillis(cooldownMs: Long) {
        this.cooldownMillis = cooldownMs.coerceAtLeast(0L)
    }

    /**
     * Excludes specific [Activity] classes (e.g., Splash, Onboarding, Consent) from showing App Open Ads.
     */
    fun setExcludedActivities(vararg activityClasses: Class<out Activity>) {
        excludedActivityClasses.clear()
        excludedActivityClasses.addAll(activityClasses)
    }

    private fun preloadAd(debugToastMessage: String) {
        if (adUnitId.isEmpty() ||
            isLoadingAd ||
            !AdManager.isInitialized.value ||
            !AdManager.isAdsEnabled.value ||
            AdPreloaderManager.isAppOpenActive(adUnitId) ||
            AdManager.isAppOpenAdAvailable(adUnitId)
        ) {
            return
        }
        isLoadingAd = true
        scope.launch {
            try {
                val loaded = AdManager.loadAppOpenAd(application, adUnitId)
                if (loaded) {
                    application.showDebugToast(debugToastMessage)
                }
            } finally {
                isLoadingAd = false
            }
        }
    }

    /**
     * Shows the app open ad if one is available and the app is in the foreground.
     */
    private fun showAdIfAvailable() {
        if (isShowingAd ||
            !AdManager.isInitialized.value ||
            !AdManager.isAdsEnabled.value ||
            adUnitId.isEmpty()
        ) {
            return
        }

        if (cooldownMillis > 0L && lastAdDismissedTimeMs > 0L) {
            val elapsed = System.currentTimeMillis() - lastAdDismissedTimeMs
            if (elapsed < cooldownMillis) {
                Log.d("AppOpenManager", "Skipping App Open Ad: cooldown active (${elapsed}ms < ${cooldownMillis}ms)")
                return
            }
        }

        val activity = currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (excludedActivityClasses.any { it.isInstance(activity) }) {
            Log.d("AppOpenManager", "Skipping App Open Ad: ${activity::class.java.simpleName} is excluded")
            return
        }

        if (AdManager.isAppOpenAdAvailable(adUnitId)) {
            Log.d("AppOpenManager", "Showing App Open Ad")
            isShowingAd = true
            AdManager.showAppOpenAdWithOutcome(activity, adUnitId) { wasShown ->
                isShowingAd = false
                if (wasShown) {
                    lastAdDismissedTimeMs = System.currentTimeMillis()
                }
                preloadAd("App Open Ad Reloaded")
            }
        } else {
            Log.d("AppOpenManager", "App Open Ad not available, loading one...")
            preloadAd("App Open Ad Loaded")
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d("AppOpenManager", "App moved to foreground, showing ad if available")
        showAdIfAvailable()
    }

    // ActivityLifecycleCallbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
