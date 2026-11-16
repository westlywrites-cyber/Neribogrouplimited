package com.westly.opay.feature.statement

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError

/** The short plain messages of Download Statement. Pure, so they can be unit tested. */
object StatementErrors {
    val NO_INTERNET: String = BackendCodes.messageFor(BackendCodes.NETWORK)
    const val RATE_LIMITED = "Too many requests. Try again later."
    const val TOO_LARGE = "This period is too large. Choose a shorter period."
    const val INVALID_EMAIL = "Enter a valid email address"
    const val SEND_FAILED = "We could not send the email. Try again."
    const val GENERATE_FAILED = "We could not generate your statement. Try again."
    const val FILE_MISSING = "The statement file is no longer available"
    const val RESENT = "Statement sent"

    /** The message for a failed `send-statement` call. The function puts its short outcome name in `data.reason`. */
    fun messageFor(error: BackendError): String = when {
        error.code == BackendCodes.NETWORK -> NO_INTERNET
        error.code == BackendCodes.RATE_LIMITED -> RATE_LIMITED
        error.data[REASON] == "invalid_email" -> INVALID_EMAIL
        error.data[REASON] == "too_large" -> TOO_LARGE
        else -> SEND_FAILED
    }

    private const val REASON = "reason"
}
