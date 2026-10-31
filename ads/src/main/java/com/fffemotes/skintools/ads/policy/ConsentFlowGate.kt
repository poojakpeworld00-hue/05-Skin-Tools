package com.fffemotes.skintools.ads.policy

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

class ConsentFlowGate private constructor(context: Context) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun interface ConsentReadyListener {
        fun consentFlowDone(error: FormError?)
    }

    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            PrivacyOptionsRequirementStatus.REQUIRED

    fun askConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: ConsentReadyListener
    ) {
        val debugSettings = ConsentDebugSettings.Builder(activity).build()
        val params = ConsentRequestParameters.Builder()
            .setConsentDebugSettings(debugSettings)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    onConsentGatheringCompleteListener.consentFlowDone(formError)
                }
            },
            { requestConsentError ->
                onConsentGatheringCompleteListener.consentFlowDone(requestConsentError)
            }
        )
    }

    fun openPrivacyOptions(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    }

    companion object {
        @Volatile
        private var instance: ConsentFlowGate? = null

        fun getInstance(context: Context): ConsentFlowGate =
            instance ?: synchronized(this) {
                instance ?: ConsentFlowGate(context.applicationContext).also { instance = it }
            }
    }
}
