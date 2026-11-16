package com.westly.opay.data

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.core.util.formatNaira
import com.westly.opay.feature.transfer.PAY_SUSPENDED_TEXT
import com.westly.opay.feature.transfer.PAY_UNKNOWN_TEXT

/** The limits of the `purchase` function and the text a failed purchase shows on the confirm sheet. */
object PurchaseErrors {
    const val MIN_KOBO = 5_000L
    const val MAX_KOBO = 50_000_000L

    fun lowText(): String = "Minimum amount is ${formatNaira(MIN_KOBO)}"

    fun highText(): String = "Maximum amount is ${formatNaira(MAX_KOBO)}"

    /** The text for [error]. Codes without a purchase-specific text keep the text they already carry. */
    fun message(error: BackendError): String = when (error.code) {
        BackendCodes.INSUFFICIENT_BALANCE -> "Insufficient balance"
        BackendCodes.AMOUNT_TOO_LOW -> lowText()
        BackendCodes.AMOUNT_TOO_HIGH -> highText()
        BackendCodes.ACCOUNT_SUSPENDED -> PAY_SUSPENDED_TEXT
        BackendCodes.NETWORK -> BackendCodes.messageFor(BackendCodes.NETWORK)
        BackendCodes.UNKNOWN -> PAY_UNKNOWN_TEXT
        else -> error.message
    }
}
