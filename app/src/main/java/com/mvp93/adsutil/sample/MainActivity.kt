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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.ConsentManager
import com.mvp93.adsutil.sample.ui.theme.AdsUtilExampleTheme
import com.mvp93.adsutil.utils.AdsConstants
import com.mvp93.adsutil.utils.showDebugToast
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val consentManager = ConsentManager(this)
        
        setContent {
            AdsUtilExampleTheme {
                var isPrivacyOptionsRequired by remember {
                    mutableStateOf(consentManager.isPrivacyOptionsRequired)
                }

                LaunchedEffect(Unit) {
                    consentManager.requestConsentInfoUpdate(object : ConsentManager.OnConsentCheckListener {
                        override fun onConsentCheckCompleted() {
                            isPrivacyOptionsRequired = consentManager.isPrivacyOptionsRequired
                            if (consentManager.canRequestAds) {
                                lifecycleScope.launch {
                                    AdManager.initialize(applicationContext, AdsConstants.ADS_APP_TEST_ID)
                                }
                            }
                        }

                        override fun onConsentFormError(error: String) {
                            isPrivacyOptionsRequired = consentManager.isPrivacyOptionsRequired
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
                        TestAdsScreen(
                            isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                            onOpenPrivacyOptions = {
                                consentManager.showPrivacyOptionsForm { error ->
                                    isPrivacyOptionsRequired = consentManager.isPrivacyOptionsRequired
                                    if (error != null) {
                                        showDebugToast("Privacy form error: $error")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
