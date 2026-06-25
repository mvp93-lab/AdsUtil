package com.mvp93.adsutil.sample

import android.app.Application
import com.mvp93.adsutil.AppOpenManager
import com.mvp93.adsutil.utils.AdsConstants

class AdsSampleApp : Application() {
    
    lateinit var appOpenManager: AppOpenManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize AppOpenManager to handle ads on app start/foreground
        appOpenManager = AppOpenManager(this)
        appOpenManager.setAdUnitId(AdsConstants.ADS_OPEN_APP_TEST_ID)
    }
}
