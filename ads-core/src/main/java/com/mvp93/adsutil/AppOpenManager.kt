package com.mvp93.adsutil

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

import com.mvp93.adsutil.utils.showDebugToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manager for App Open Ads to show them when the app is brought to the foreground.
 */
class AppOpenManager(private val application: Application) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentActivity: Activity? = null
    private var adUnitId: String = ""

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setAdUnitId(id: String) {
        this.adUnitId = id
        // Preload if possible
        if (adUnitId.isNotEmpty()) {
            scope.launch {
                val loaded = AdManager.loadAppOpenAd(application, adUnitId)
                if (loaded) {
                    application.showDebugToast("App Open Ad Preloaded")
                }
            }
        }
    }

    /**
     * Shows the app open ad if one is available and the app is in the foreground.
     */
    private fun showAdIfAvailable() {
        if (!AdManager.isAdsEnabled.value) return

        currentActivity?.let { activity ->
            // Don't show if we are on SplashActivity or similar if you want
            // if (activity is SplashActivity) return

            if (AdManager.isAppOpenAdAvailable()) {
                Log.d("AppOpenManager", "Showing App Open Ad")
                AdManager.showAppOpenAd(activity) {
                    // Reload for next time
                    scope.launch {
                        val loaded = AdManager.loadAppOpenAd(application, adUnitId)
                        if (loaded) {
                            application.showDebugToast("App Open Ad Reloaded")
                        }
                    }
                }
            } else {
                Log.d("AppOpenManager", "App Open Ad not available, loading one...")
                scope.launch {
                    val loaded = AdManager.loadAppOpenAd(application, adUnitId)
                    if (loaded) {
                        application.showDebugToast("App Open Ad Loaded")
                    }
                }
            }
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
