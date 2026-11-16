package com.westly.opay.feature.statement

import com.westly.opay.backend.TransactionRow
import com.westly.opay.backend.RowMapping
import com.westly.opay.core.util.maskAccount
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.feature.receipt.PurchaseDetails

/** Turns the transactions of a period into the figures and rows of the statement. Pure. */
object StatementBuilder {
    const val CHANNEL = "Mobile"
    const val EMPTY = "--"
    const val SUCCESSFUL = "Successful"
    private const val SEPARATOR = " | "
    private const val MISSING = "-"

    /** Maps a server row; null when the row's type is not one the app knows. */
    fun toStatementTx(row: TransactionRow): StatementTx? {
        val transaction = RowMapping.transaction(row) ?: return null
        return StatementTx(transaction, row.balanceAfterKobo)
    }

    fun build(request: StatementRequest, source: StatementSource, generatedAtMillis: Long): StatementData {
        val rows = source.inPeriod
            .filter { it.transaction.status == SUCCESSFUL }
            .sortedBy { it.transaction.timestampMillis }
        val first = rows.firstOrNull()
        val last = rows.lastOrNull()
        val opening: Long
        val closing: Long
        if (first != null && last != null) {
            opening = first.balanceAfterKobo - first.transaction.amountKobo
            closing = last.balanceAfterKobo
        } else {
            val start = balanceAtStart(source)
            opening = start
            closing = start
        }
        val debits = rows.filter { it.transaction.amountKobo < 0 }
        val credits = rows.filter { it.transaction.amountKobo >= 0 }
        return StatementData(
            accountName = request.holderName.uppercase(),
            accountNumber = request.accountNumber,
            period = request.period,
            generatedAtMillis = generatedAtMillis,
            openingKobo = opening,
            closingKobo = closing,
            totalDebitKobo = debits.sumOf { -it.transaction.amountKobo },
            totalCreditKobo = credits.sumOf { it.transaction.amountKobo },
            debitCount = debits.size,
            creditCount = credits.size,
            rows = rows.map(::row)
        )
    }

    private fun balanceAtStart(source: StatementSource): Long {
        val before = source.before?.takeIf { it.transaction.status == SUCCESSFUL }
        if (before != null) return before.balanceAfterKobo
        val after = source.after?.takeIf { it.transaction.status == SUCCESSFUL }
        if (after != null) return after.balanceAfterKobo - after.transaction.amountKobo
        return source.currentBalanceKobo
    }

    private fun row(item: StatementTx): StatementRow {
        val tx = item.transaction
        val amount = tx.amountKobo
        return StatementRow(
            transTime = StatementFormat.tableTime(tx.timestampMillis),
            valueDate = StatementFormat.tableDate(tx.timestampMillis),
            description = describe(tx),
            debit = if (amount < 0) StatementFormat.plainAmount(amount) else EMPTY,
            credit = if (amount >= 0) StatementFormat.plainAmount(amount) else EMPTY,
            balanceAfter = StatementFormat.plainAmount(item.balanceAfterKobo),
            channel = CHANNEL,
            reference = tx.txNo
        )
    }

    fun describe(tx: Transaction): String = when (tx.type) {
        TxType.TransferOut -> transferText("Transfer to", tx, tx.counterpartyAccount)
        TxType.TransferIn -> transferText("Transfer from", tx, tx.counterpartyAccount?.let { maskAccount(it) })
        TxType.Airtime, TxType.Data -> purchaseText(tx)
        else -> tx.title
    }

    private fun transferText(label: String, tx: Transaction, account: String?): String {
        val name = tx.counterpartyName.clean()
        val head = if (name.isEmpty()) label else "$label $name"
        return listOf(head, tx.counterpartyBank.clean(), account.clean(), tx.remark.clean())
            .filter { it.isNotEmpty() }
            .joinToString(SEPARATOR)
    }

    private fun purchaseText(tx: Transaction): String {
        val details = PurchaseDetails.from(tx)
        val phone = details.phone.replace(" ", "")
        if (phone == MISSING || details.network == MISSING) return tx.title
        val label = if (tx.type == TxType.Data) "Mobile Data" else "Airtime"
        val parts = mutableListOf(label, phone, details.network)
        details.plan?.takeIf { it != MISSING }?.let { parts.add(it) }
        return parts.joinToString(SEPARATOR)
    }

    private fun String?.clean(): String = this?.trim().orEmpty()
}
