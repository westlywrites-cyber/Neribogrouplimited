package com.westly.opay.feature.receipt

import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType

sealed interface DetailsBadge {
    data object None : DetailsBadge
    data object Opay : DetailsBadge
    data class Bank(val name: String) : DetailsBadge
}

fun detailsBadgeFor(transaction: Transaction): DetailsBadge {
    if (transaction.type != TxType.TransferOut && transaction.type != TxType.TransferIn) {
        return DetailsBadge.None
    }
    val bank = transaction.counterpartyBank
    if (isOpayBank(bank) || transaction.counterpartyName.equals("OPay Support", ignoreCase = true)) {
        return DetailsBadge.Opay
    }
    if (!bank.isNullOrBlank()) return DetailsBadge.Bank(bank)
    return DetailsBadge.None
}
