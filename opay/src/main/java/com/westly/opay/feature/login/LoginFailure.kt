package com.westly.opay.feature.login

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** What a failed `login` call means for the screen. */
sealed interface LoginFailure {
    data class WrongPin(val attemptsLeft: Int?) : LoginFailure
    data class Locked(val minutes: Int) : LoginFailure
    data object PhoneNotFound : LoginFailure
    data object PinNotSet : LoginFailure
    data object Suspended : LoginFailure
    data class DeviceVerification(val resendAfter: Int) : LoginFailure
    data object RateLimited : LoginFailure
    data object Offline : LoginFailure
    data class Other(val message: String) : LoginFailure
}

object LoginFailures {
    const val DEFAULT_RESEND_SECONDS = 58

    fun classify(error: BackendError, now: Instant = Instant.now()): LoginFailure = when (error.code) {
        BackendCodes.INVALID_PIN -> LoginFailure.WrongPin(error.data["attempts_left"]?.trim()?.toIntOrNull())
        BackendCodes.PIN_LOCKED -> LoginFailure.Locked(lockedMinutes(error.data["locked_until"], now))
        BackendCodes.PHONE_NOT_FOUND -> LoginFailure.PhoneNotFound
        BackendCodes.PIN_NOT_SET -> LoginFailure.PinNotSet
        BackendCodes.ACCOUNT_SUSPENDED -> LoginFailure.Suspended
        BackendCodes.DEVICE_VERIFICATION_REQUIRED -> LoginFailure.DeviceVerification(
            error.data["resend_after"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_RESEND_SECONDS
        )
        BackendCodes.RATE_LIMITED -> LoginFailure.RateLimited
        BackendCodes.NETWORK -> LoginFailure.Offline
        else -> LoginFailure.Other(error.message)
    }

    /** Whole minutes left until [lockedUntil] (an ISO 8601 UTC time), rounded up and never below 1. */
    fun lockedMinutes(lockedUntil: String?, now: Instant = Instant.now()): Int {
        val until = try {
            Instant.parse(lockedUntil?.trim().orEmpty())
        } catch (e: DateTimeParseException) {
            return 1
        }
        val seconds = Duration.between(now, until).seconds
        if (seconds <= 0) return 1
        return ((seconds + 59) / 60).toInt()
    }

    /** "Incorrect password, 3 attempts left"; the count is left out when the server did not send it. */
    fun wrongPinText(attemptsLeft: Int?): String = when {
        attemptsLeft == null -> "Incorrect password"
        attemptsLeft == 1 -> "Incorrect password, 1 attempt left"
        else -> "Incorrect password, $attemptsLeft attempts left"
    }

    fun lockedText(minutes: Int): String =
        if (minutes == 1) "Too many wrong attempts. Try again in 1 minute."
        else "Too many wrong attempts. Try again in $minutes minutes."
}
