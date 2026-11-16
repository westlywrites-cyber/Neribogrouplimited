package com.westly.opay.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class BackendError(
    val code: String,
    override val message: String,
    val data: Map<String, String> = emptyMap()
) : Exception(message)

sealed interface BackendResult<out T> {
    data class Success<out T>(val value: T) : BackendResult<T>
    data class Failure(val error: BackendError) : BackendResult<Nothing>
}

object BackendCodes {
    const val INVALID_INPUT = "INVALID_INPUT"
    const val NOT_AUTHENTICATED = "NOT_AUTHENTICATED"
    const val STATE_INVALID = "STATE_INVALID"
    const val ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE"
    const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
    const val PHONE_TAKEN = "PHONE_TAKEN"
    const val PHONE_NOT_FOUND = "PHONE_NOT_FOUND"
    const val ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
    const val INVALID_CODE = "INVALID_CODE"
    const val CODE_EXPIRED = "CODE_EXPIRED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val OTP_DELIVERY_FAILED = "OTP_DELIVERY_FAILED"
    const val DEVICE_VERIFICATION_REQUIRED = "DEVICE_VERIFICATION_REQUIRED"
    const val PROOF_INVALID = "PROOF_INVALID"
    const val INVALID_PIN = "INVALID_PIN"
    const val PIN_LOCKED = "PIN_LOCKED"
    const val PIN_NOT_SET = "PIN_NOT_SET"
    const val PIN_ALREADY_SET = "PIN_ALREADY_SET"
    const val BIOMETRIC_NOT_ENABLED = "BIOMETRIC_NOT_ENABLED"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE"
    const val AMOUNT_TOO_LOW = "AMOUNT_TOO_LOW"
    const val AMOUNT_TOO_HIGH = "AMOUNT_TOO_HIGH"
    const val SELF_TRANSFER = "SELF_TRANSFER"
    const val NOT_FOUND = "NOT_FOUND"
    const val NOT_MATURED = "NOT_MATURED"
    const val LOOKUP_FAILED = "LOOKUP_FAILED"

    /** Client-only codes. */
    const val NETWORK = "NETWORK"
    const val UNKNOWN = "UNKNOWN"

    val ALL: Set<String> = setOf(
        INVALID_INPUT, NOT_AUTHENTICATED, STATE_INVALID, ACCOUNT_NOT_ACTIVE, ACCOUNT_SUSPENDED,
        PHONE_TAKEN, PHONE_NOT_FOUND, ACCOUNT_NOT_FOUND, INVALID_CODE, CODE_EXPIRED, RATE_LIMITED,
        OTP_DELIVERY_FAILED, DEVICE_VERIFICATION_REQUIRED, PROOF_INVALID, INVALID_PIN, PIN_LOCKED,
        PIN_NOT_SET, PIN_ALREADY_SET, BIOMETRIC_NOT_ENABLED, INVALID_TOKEN, INSUFFICIENT_BALANCE,
        AMOUNT_TOO_LOW, AMOUNT_TOO_HIGH, SELF_TRANSFER, NOT_FOUND, NOT_MATURED, LOOKUP_FAILED,
        NETWORK, UNKNOWN
    )

    /** Default user-facing text. Edge Functions send their own text in the error envelope. */
    fun messageFor(code: String): String = when (code) {
        INVALID_INPUT -> "Check the details and try again"
        NOT_AUTHENTICATED -> "Please sign in again"
        STATE_INVALID -> "This action is not available right now"
        ACCOUNT_NOT_ACTIVE -> "Your account is not active yet"
        ACCOUNT_SUSPENDED -> "Your account has been suspended"
        PHONE_TAKEN -> "This phone number is already registered"
        PHONE_NOT_FOUND -> "No account found for this phone number"
        ACCOUNT_NOT_FOUND -> "Account not found"
        INVALID_CODE -> "Incorrect code"
        CODE_EXPIRED -> "This code has expired. Request a new one"
        RATE_LIMITED -> "Operation Too Frequent, Please try again later"
        OTP_DELIVERY_FAILED -> "We could not send the code. Try again"
        DEVICE_VERIFICATION_REQUIRED -> "Verify this device to continue"
        PROOF_INVALID -> "Verification expired. Start again"
        INVALID_PIN -> "Incorrect password"
        PIN_LOCKED -> "Too many wrong attempts. Try again later"
        PIN_NOT_SET -> "No password has been set for this account"
        PIN_ALREADY_SET -> "A password is already set"
        BIOMETRIC_NOT_ENABLED -> "Fingerprint is not set up on this device"
        INVALID_TOKEN -> "Fingerprint sign-in is no longer valid"
        INSUFFICIENT_BALANCE -> "Insufficient balance"
        AMOUNT_TOO_LOW -> "The amount is too low"
        AMOUNT_TOO_HIGH -> "The amount is too high"
        SELF_TRANSFER -> "You cannot transfer to your own account"
        NOT_FOUND -> "Not found"
        NOT_MATURED -> "This plan has not matured yet"
        LOOKUP_FAILED -> "Account lookup failed. Try again"
        NETWORK -> "No internet connection"
        else -> "Something went wrong. Try again"
    }
}

/**
 * Turns the text of a failed backend response into a [BackendError]. Pure Kotlin so it can be unit tested.
 *
 * Edge Functions answer `{"error":{"code","message","data"}}`. Database functions raise an exception whose
 * message is the code, and PostgREST wraps it as `{"code","details","hint","message"}`. supabase-kt 3.0.3 does
 * not document which field of its exception carries that text, so the caller passes every candidate.
 */
object BackendErrorParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromEnvelope(text: String?): BackendError? {
        val root = parseObject(text) ?: return null
        val error = root["error"] as? JsonObject ?: return null
        val rawCode = error.string("code") ?: return null
        val code = if (rawCode in BackendCodes.ALL) rawCode else BackendCodes.UNKNOWN
        val message = error.string("message")?.takeIf { it.isNotBlank() } ?: BackendCodes.messageFor(code)
        val data = (error["data"] as? JsonObject)
            ?.mapValues { (_, value) -> if (value is JsonPrimitive) value.content else value.toString() }
            .orEmpty()
        return BackendError(code, message, data)
    }

    fun fromResponseTexts(texts: List<String?>, statusCode: Int? = null): BackendError {
        val candidates = texts.filterNotNull().filter { it.isNotBlank() }
        for (text in candidates) {
            fromEnvelope(text)?.let { return it }
        }
        for (text in candidates) {
            fromDatabaseJson(text)?.let { return it }
        }
        fromPlainTokens(candidates)?.let { return it }
        val code = if (statusCode == 401) BackendCodes.NOT_AUTHENTICATED else BackendCodes.UNKNOWN
        return BackendError(code, BackendCodes.messageFor(code))
    }

    private fun fromDatabaseJson(text: String): BackendError? {
        val root = parseObject(text) ?: return null
        val message = root.string("message") ?: return null
        if (message !in BackendCodes.ALL) return null
        return withDetail(message, root.string("details"))
    }

    private fun fromPlainTokens(candidates: List<String>): BackendError? {
        val tokens = candidates.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        val code = tokens.firstOrNull { it in BackendCodes.ALL } ?: return null
        val detail = tokens.firstOrNull { it != code && it !in BackendCodes.ALL && it.first().isDigit() }
        return withDetail(code, detail)
    }

    private fun withDetail(code: String, detail: String?): BackendError {
        val value = detail?.trim().orEmpty()
        val data = when {
            value.isEmpty() -> emptyMap()
            code == BackendCodes.INVALID_PIN -> mapOf("attempts_left" to value)
            code == BackendCodes.PIN_LOCKED -> mapOf("locked_until" to value)
            else -> emptyMap()
        }
        return BackendError(code, BackendCodes.messageFor(code), data)
    }

    private fun parseObject(text: String?): JsonObject? {
        if (text == null) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
