package com.mvp93.adsutil.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.ConsentManager
import com.mvp93.adsutil.sample.ui.theme.AdsUtilExampleTheme
import com.mvp93.adsutil.utils.AdsConstants
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val consentManager = ConsentManager(this)
        
        setContent {
            AdsUtilExampleTheme {
                LaunchedEffect(Unit) {
                    consentManager.requestConsentInfoUpdate(object : ConsentManager.OnConsentCheckListener {
                        override fun onConsentCheckCompleted() {
                            if (consentManager.canRequestAds) {
                                lifecycleScope.launch {
                                    AdManager.initialize(applicationContext, AdsConstants.ADS_APP_TEST_ID)
                                }
                            }
                        }

                        override fun onConsentFormError(error: String) {
                            // Even on error, we can try to initialize if we have consent from before
                            if (consentManager.canRequestAds) {
                                lifecycleScope.launch {
                                    AdManager.initialize(applicationContext, AdsConstants.ADS_APP_TEST_ID)
                                }
                            }
                        }
                    })
                }
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TestAdsScreen()
                    }
                }
            }
        }
    }
}
