package com.westly.neribogroup.feature.common

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The one gate for every sensitive action. It asks for the phone's fingerprint, face or screen lock
 * (Android's own prompt), and runs the action only after that succeeded.
 *
 * - After a success the gate stays open for [UNLOCK_MILLIS] (5 minutes), then asks again. The open time is
 *   kept only in memory and uses the phone's elapsed-time clock, so changing the date does not extend it.
 * - If the phone has no fingerprint and no screen lock, the action is blocked with a clear message.
 *   Verification is never skipped.
 * - Every verified action can be written to the storage log with "(verified)" at the end.
 */
class BiometricGate(
    private val activity: FragmentActivity,
    /** Writes one line to the storage log. The gate adds "(verified)" itself. */
    private val writeLog: (String) -> Unit
) {

    /** A message that blocks the action (no screen lock, locked out...). Empty when there is none. */
    var notice by mutableStateOf("")
        private set

    private var prompting = false
    private var promptStartedAt = 0L

    fun clearNotice() {
        notice = ""
    }

    /** True while the last successful verification is less than 5 minutes old. */
    fun isUnlocked(): Boolean = SystemClock.elapsedRealtime() < unlockedUntil

    /**
     * Verifies the owner, then runs [onSuccess]. Nothing runs when the owner cancels, fails, or the phone cannot
     * verify. [title] and [subtitle] are shown on the Android prompt. [logText], when not empty, is written to
     * the storage log after [onSuccess] ran, as "[logText] (verified)".
     */
    fun verify(title: String, subtitle: String, logText: String = "", onSuccess: () -> Unit) {
        // A prompt that never answered (for example the app was killed under it) must not block the gate for ever.
        if (prompting && SystemClock.elapsedRealtime() - promptStartedAt < STALE_PROMPT_MILLIS) return
        if (isUnlocked()) {
            complete(logText, onSuccess)
            return
        }

        val authenticators = authenticators()
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> Unit
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                notice = "The fingerprint sensor is not available right now. Try again in a moment. Nothing was changed."
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                notice = NO_LOCK_MESSAGE
                return
            }
            else -> {
                notice = "This phone cannot verify you right now. Update your phone's software and try again. " +
                    "Nothing was changed."
                return
            }
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlockedUntil = SystemClock.elapsedRealtime() + UNLOCK_MILLIS
                complete(logText, onSuccess)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                when (errorCode) {
                    // The owner backed out: nothing to say.
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> Unit
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        notice = "Too many wrong tries. Wait a little, or unlock the phone with your screen lock, " +
                            "then try again. Nothing was changed."
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> notice = NO_LOCK_MESSAGE
                    else -> notice = "Verification did not finish. Nothing was changed."
                }
            }
            // A wrong finger is not an error: the Android prompt stays open and the owner can try again.
        }

        try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.ifBlank { null })
                .setAllowedAuthenticators(authenticators)
                .build()
            prompting = true
            promptStartedAt = SystemClock.elapsedRealtime()
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        } catch (e: Exception) {
            prompting = false
            notice = "Verification could not start. Nothing was changed."
        }
    }

    private fun complete(logText: String, onSuccess: () -> Unit) {
        onSuccess()
        if (logText.isNotBlank()) writeLog(logText.trim() + " (verified)")
    }

    companion object {
        /** After a success the gate stays open this long. */
        const val UNLOCK_MILLIS = 5L * 60L * 1000L

        private const val STALE_PROMPT_MILLIS = 2L * 60L * 1000L

        // Kept in the companion, so turning the phone (a new activity) does not close or extend the open time.
        private var unlockedUntil = 0L

        private const val NO_LOCK_MESSAGE =
            "This action needs your fingerprint or your phone's screen lock. Set a screen lock (PIN, pattern or " +
                "password) in your phone's Settings, then try again. Nothing was changed."

        /**
         * Library limits: strong biometric + screen lock is refused on API 28 and 29 (build() would throw), and
         * the screen lock alone needs API 30. So Android 11 and newer use strong + screen lock, and API 26 to 29
         * use weak + screen lock, which every one of those versions accepts.
         */
        private fun authenticators(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            } else {
                BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            }
    }
}

/** Every screen reads the gate from here, so no screen needs an extra parameter. */
val LocalBiometricGate = staticCompositionLocalOf<BiometricGate> {
    error("BiometricGate is not provided. MainActivity provides it around the whole app.")
}

/** Shows the message when the gate blocked an action. Placed once, at the top of the app. */
@Composable
fun BiometricGateNotice(gate: BiometricGate) {
    val message = gate.notice
    if (message.isNotEmpty()) {
        NeriboDialog(onDismiss = { gate.clearNotice() }, title = "Cannot verify") {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DialogActions {
                TintedButton(text = "OK", onClick = { gate.clearNotice() })
            }
        }
    }
}
