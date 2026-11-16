package com.westly.opay.feature.receipt

import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.feature.bills.detectNetwork
import com.westly.opay.feature.bills.localPhoneDigits

private const val MISSING = "-"
private const val PHONE_LENGTH = 11
private const val DATA_MARKER = "Data "
private val NetworkAliases = listOf(
    "t2 mobile" to "T2 Mobile",
    "9mobile" to "T2 Mobile",
    "mtn" to "MTN",
    "airtel" to "Airtel",
    "glo" to "Glo",
    "vitel" to "Vitel"
)

internal enum class ReceiptKind { Transfer, Airtime, Data, Other }

internal fun receiptKind(transaction: Transaction): ReceiptKind = when (transaction.type) {
    TxType.Airtime -> ReceiptKind.Airtime
    TxType.Data -> ReceiptKind.Data
    TxType.TransferOut, TxType.TransferIn -> ReceiptKind.Transfer
    else -> ReceiptKind.Other
}

internal fun formatReceiptPhone(digits: String): String = when {
    digits.isEmpty() -> MISSING
    digits.length == PHONE_LENGTH ->
        digits.substring(0, 3) + " " + digits.substring(3, 7) + " " + digits.substring(7)
    else -> digits
}

private fun matchNetwork(text: String?): Pair<String, String>? {
    val trimmed = text?.trim() ?: return null
    val lower = trimmed.lowercase()
    for ((alias, canonical) in NetworkAliases) {
        if (lower == alias) return canonical to ""
        if (lower.startsWith("$alias ")) return canonical to trimmed.substring(alias.length).trim()
    }
    return null
}

private fun networkOf(text: String?): String? = matchNetwork(text)?.first

private fun phoneDigitsOf(name: String?): String {
    if (name == null) return ""
    return localPhoneDigits(matchNetwork(name)?.second ?: name)
}

private fun planFromTitle(title: String): String? {
    val rest = matchNetwork(title)?.second ?: return null
    if (!rest.startsWith(DATA_MARKER, ignoreCase = true)) return null
    return rest.substring(DATA_MARKER.length).trim().takeIf { it.isNotEmpty() }
}

/**
 * The network, phone number and data plan of an airtime or data purchase.
 *
 * Convention written by the purchase flow:
 * - title = "<Network> Airtime" or "<Network> Data <Plan name>"
 * - counterpartyName = the phone number as digits
 * - remark = the plan name for data
 *
 * Older records are also read: title "Airtime" or "Mobile Data" with counterpartyName
 * "<Network> <phone>" (airtime) or the phone alone (data). Any value that cannot be found
 * is "-". [plan] is null for airtime.
 */
internal data class PurchaseDetails(val network: String, val phone: String, val plan: String?) {
    companion object {
        fun from(transaction: Transaction): PurchaseDetails {
            val digits = phoneDigitsOf(transaction.counterpartyName)
            val network = networkOf(transaction.title)
                ?: networkOf(transaction.counterpartyName)
                ?: detectNetwork(digits)
                ?: MISSING
            val plan = if (transaction.type == TxType.Data) {
                transaction.remark?.trim()?.takeIf { it.isNotEmpty() }
                    ?: planFromTitle(transaction.title)
                    ?: MISSING
            } else {
                null
            }
            return PurchaseDetails(network = network, phone = formatReceiptPhone(digits), plan = plan)
        }
    }
}

internal fun purchaseHeadline(transaction: Transaction): String {
    val details = PurchaseDetails.from(transaction)
    val label = if (transaction.type == TxType.Data) "Mobile Data" else "Airtime"
    return if (details.network == MISSING) label else details.network + " " + label
}
