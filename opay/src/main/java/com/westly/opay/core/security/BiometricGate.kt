package com.westly.opay.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The only place that talks to the system fingerprint prompt. The app never receives fingerprint data: Android
 * keeps it and only reports success, cancel or an error. Class 3 (strong) biometrics are required so the prompt
 * can unlock a Keystore key through a [BiometricPrompt.CryptoObject].
 */
object BiometricGate {

    /**
     * True from the moment the system prompt is requested until it reports its result. The app lock reads it so the
     * prompt itself never counts as the app leaving the screen.
     */
    @Volatile
    var isPromptShowing: Boolean = false
        private set

    /** True when the phone has strong biometric hardware and at least one enrolled fingerprint. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. [onSuccess] receives the crypto object the prompt unlocked (null when none was
     * passed). [onCancel] runs when the user backs out; [onError] runs for any other failure with a message that
     * can be shown to the user. A caller can relabel the negative button with [negativeText] and receive its tap
     * separately in [onNegative]; without [onNegative] that tap is reported as [onCancel].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cryptoObject: BiometricPrompt.CryptoObject?,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit,
        negativeText: String = CANCEL_TEXT,
        onNegative: (() -> Unit)? = null
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isPromptShowing = false
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isPromptShowing = false
                when {
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && onNegative != null -> onNegative()
                    errorCode in CANCEL_CODES -> onCancel()
                    else -> onError(errString.toString())
                }
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeText)
            .build()
        isPromptShowing = true
        if (cryptoObject == null) prompt.authenticate(info) else prompt.authenticate(info, cryptoObject)
    }

    private const val CANCEL_TEXT = "Cancel"

    private val CANCEL_CODES = setOf(
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED
    )
}
