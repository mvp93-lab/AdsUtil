package com.mvp93.adsutil

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mvp93.adsutil.utils.isDebug
import java.util.concurrent.atomic.AtomicBoolean

class ConsentManager(private val activity: Activity) {
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    interface OnConsentCheckListener {
        fun onConsentCheckCompleted()
        fun onConsentFormError(error: String)
    }

    fun requestConsentInfoUpdate(
        onConsentCheckListener: OnConsentCheckListener
    ) {
        if (activity.isDebug()) {
            // Reset consent state for testing purposes in debug builds
            // This ensures the dialog shows up every time you test
            consentInformation.reset() 
        }

        val debugSettings = if (activity.isDebug()) {
            ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId("C6F069CF4A4DC8F0272DA62232522711")
                .build()
        } else {
            null
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (debugSettings != null) {
                    setConsentDebugSettings(debugSettings)
                }
            }
            .build()

        Log.d("ConsentManager", "Requesting consent info update...")
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d("ConsentManager", "Consent info update successful. Status: ${consentInformation.consentStatus}, Can request ads: ${consentInformation.canRequestAds()}")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.e("ConsentManager", "Consent form error: [${formError.errorCode}] ${formError.message}")
                        onConsentCheckListener.onConsentFormError(formError.message)
                    } else {
                        Log.d("ConsentManager", "Consent form handled. New Status: ${consentInformation.consentStatus}, Can request ads: $canRequestAds")
                        // Always notify that the process is finished
                        onConsentCheckListener.onConsentCheckCompleted()
                    }
                }
            },
            { requestError ->
                Log.e("ConsentManager", "Consent request error: [${requestError.errorCode}] ${requestError.message}")
                onConsentCheckListener.onConsentFormError(requestError.message)
                
                // Even on error, if we can request ads (e.g. from previous session), we should
                if (canRequestAds) {
                    onConsentCheckListener.onConsentCheckCompleted()
                }
            }
        )
    }

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus == 
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptionsForm(onDismiss: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onDismiss(error?.message)
        }
    }

    /**
     * Checks if the user has provided explicit consent for IAB TCF Purpose 1.
     */
    fun hasPurposeOneConsent(): Boolean {
        // Try default shared preferences first
        val defaultPrefs = activity.getSharedPreferences("${activity.packageName}_preferences", Context.MODE_PRIVATE)
        var purposeConsents = defaultPrefs.getString("IABTCF_PurposeConsents", "") ?: ""
        
        // If not found, try common alternative locations
        if (purposeConsents.isEmpty()) {
            val altPrefs = activity.getSharedPreferences(activity.packageName, Context.MODE_PRIVATE)
            purposeConsents = altPrefs.getString("IABTCF_PurposeConsents", "") ?: ""
        }

        Log.d("ConsentManager", "IABTCF_PurposeConsents found: '$purposeConsents'")
        
        // If the string starts with '1', it means Purpose 1 (Device Access) is granted.
        return purposeConsents.startsWith("1")
    }
}
