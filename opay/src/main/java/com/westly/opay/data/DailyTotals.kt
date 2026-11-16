package com.westly.opay.data

import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import java.time.Instant
import java.time.ZoneId

/** What the server reports for a finished transaction (`transactions.status`). */
private const val STATUS_SUCCESSFUL = "Successful"

/** Today's incoming transfers: the total in kobo and how many there are. */
data class ReceivedSummary(val totalKobo: Long, val count: Int)

/**
 * Pure "today" totals over the transaction list the repository already keeps live.
 * "Today" is the calendar day in Africa/Lagos, starting at 00:00 Lagos time, the same day the database uses.
 */
object DailyTotals {
    val zone: ZoneId = ZoneId.of("Africa/Lagos")

    fun isToday(millis: Long, nowMillis: Long): Boolean =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    /** Successful incoming transfers only, newest first. Interest and every other type are left out. */
    fun receivedCredits(transactions: List<Transaction>): List<Transaction> =
        transactions
            .filter { it.type == TxType.TransferIn && it.status.equals(STATUS_SUCCESSFUL, ignoreCase = true) }
            .sortedByDescending { it.timestampMillis }

    fun receivedToday(transactions: List<Transaction>, nowMillis: Long): ReceivedSummary {
        val today = receivedCredits(transactions).filter { isToday(it.timestampMillis, nowMillis) }
        return ReceivedSummary(totalKobo = today.sumOf { it.amountKobo }, count = today.size)
    }

    /** The sum of today's `Interest` rows, which is what `pay_balance_interest()` writes to `transactions`. */
    fun interestToday(transactions: List<Transaction>, nowMillis: Long): Long =
        transactions
            .filter {
                it.type == TxType.Interest &&
                    it.status.equals(STATUS_SUCCESSFUL, ignoreCase = true) &&
                    isToday(it.timestampMillis, nowMillis)
            }
            .sumOf { it.amountKobo }
}
