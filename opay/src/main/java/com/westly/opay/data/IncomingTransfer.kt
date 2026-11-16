package com.westly.opay.data

import com.westly.opay.core.util.formatNaira

/** A transfer received while the app was open. [counterpartyName] is the sender's name as the server wrote it. */
data class IncomingTransfer(val amountKobo: Long, val counterpartyName: String?)

fun incomingTransferMessage(transfer: IncomingTransfer): String {
    val amount = formatNaira(transfer.amountKobo)
    val name = transfer.counterpartyName?.trim().orEmpty()
    return if (name.isEmpty()) "You received $amount" else "You received $amount from $name"
}
