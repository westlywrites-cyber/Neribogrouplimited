package com.westly.opay.feature.transfer

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.core.util.formatNaira
import com.westly.opay.feature.login.LoginFailures
import java.time.Instant

const val PAY_FINGERPRINT_CANCELED_TOAST = "Fingerprint operation canceled by user."
const val PAY_FINGERPRINT_OFF_TOAST = "Fingerprint payment was turned off"
const val PAY_UNKNOWN_TEXT = "Transaction could not be completed. Check your transaction list before trying again."
const val PAY_SUSPENDED_TEXT = "Your account is suspended. Please contact support."

/** The transfer limits of the contract (`submit_transfer`). The amount screen shows the same limits. */
private const val BANK_MIN_KOBO = 10_000L
private const val OPAY_MIN_KOBO = 1_000L
private const val MAX_KOBO = 500_000_000L

/** What a failed payment means for the pay sheet. */
sealed interface PayFailure {
    /** The payment PIN was wrong. The sheet shakes, clears the dots and shows the red line. */
    data class WrongPin(val attemptsLeft: Int?) : PayFailure

    /** Nothing was sent because there is no connection. */
    data object Offline : PayFailure

    /** The server no longer accepts the fingerprint token of this device. */
    data object FingerprintOff : PayFailure

    /** A box with [text]. [leavesFlow] is true when the outcome of the payment is not known. */
    data class Notice(val text: String, val leavesFlow: Boolean = false) : PayFailure
}

object PayErrors {

    /**
     * Maps the error of `submit_transfer` to what the sheet does. [BackendCodes.NETWORK] only reaches here when
     * nothing was sent; a request that was sent and lost arrives as [BackendCodes.UNKNOWN] (see
     * `AppRepository.submitTransfer`). A fingerprint payment that the server answers with `INVALID_PIN` means the
     * token was refused, so it is handled like a turned-off fingerprint.
     */
    fun classify(
        error: BackendError,
        isOpayTransfer: Boolean,
        viaFingerprint: Boolean,
        now: Instant = Instant.now()
    ): PayFailure = when (error.code) {
        BackendCodes.INVALID_PIN ->
            if (viaFingerprint) PayFailure.FingerprintOff
            else PayFailure.WrongPin(error.data["attempts_left"]?.trim()?.toIntOrNull())
        BackendCodes.BIOMETRIC_NOT_ENABLED, BackendCodes.INVALID_TOKEN -> PayFailure.FingerprintOff
        BackendCodes.PIN_LOCKED ->
            PayFailure.Notice(lockedText(LoginFailures.lockedMinutes(error.data["locked_until"], now)))
        BackendCodes.INSUFFICIENT_BALANCE -> PayFailure.Notice("Insufficient balance")
        BackendCodes.AMOUNT_TOO_LOW, BackendCodes.AMOUNT_TOO_HIGH ->
            PayFailure.Notice(limitText(isOpayTransfer))
        BackendCodes.ACCOUNT_NOT_FOUND -> PayFailure.Notice("Account not found")
        BackendCodes.SELF_TRANSFER -> PayFailure.Notice("You cannot transfer to your own account")
        BackendCodes.ACCOUNT_SUSPENDED -> PayFailure.Notice(PAY_SUSPENDED_TEXT)
        BackendCodes.NETWORK -> PayFailure.Offline
        BackendCodes.UNKNOWN -> PayFailure.Notice(PAY_UNKNOWN_TEXT, leavesFlow = true)
        else -> PayFailure.Notice(error.message)
    }

    fun lockedText(minutes: Int): String {
        val unit = if (minutes == 1) "minute" else "minutes"
        return "Payment PIN locked. Try again in $minutes $unit or reset it in Payment Settings."
    }

    fun limitText(isOpayTransfer: Boolean): String {
        val min = if (isOpayTransfer) OPAY_MIN_KOBO else BANK_MIN_KOBO
        return "Amount must be between ${formatNaira(min)} and ${formatNaira(MAX_KOBO)}"
    }
}
