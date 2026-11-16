package com.westly.opay.feature.statement

import com.westly.opay.data.model.Transaction
import java.time.LocalDate
import kotlinx.serialization.Serializable

const val FORMAT_PDF = "PDF"

enum class TimeFrame { Custom, LastMonth, LastTwoYears }

enum class StatementFileType { Pdf, Excel }

@Serializable
enum class StatementStatus { Processing, Sent, Failed }

data class StatementPeriod(val start: LocalDate, val end: LocalDate)

/** One successful transaction together with the running balance the server stored for it. */
data class StatementTx(val transaction: Transaction, val balanceAfterKobo: Long)

/**
 * What the builder reads. [before] and [after] are the nearest successful rows outside the period; they are only
 * used when nothing lies in the period. [currentBalanceKobo] is used when the account has no transaction at all.
 */
data class StatementSource(
    val inPeriod: List<StatementTx>,
    val before: StatementTx?,
    val after: StatementTx?,
    val currentBalanceKobo: Long
)

data class StatementRequest(
    val accountNumber: String,
    val holderName: String,
    val email: String,
    val period: StatementPeriod,
    val showAddress: Boolean,
    val requestedAtMillis: Long
)

data class StatementRow(
    val transTime: String,
    val valueDate: String,
    val description: String,
    val debit: String,
    val credit: String,
    val balanceAfter: String,
    val channel: String,
    val reference: String
)

data class StatementData(
    val accountName: String,
    val accountNumber: String,
    val period: StatementPeriod,
    val generatedAtMillis: Long,
    val openingKobo: Long,
    val closingKobo: Long,
    val totalDebitKobo: Long,
    val totalCreditKobo: Long,
    val debitCount: Int,
    val creditCount: Int,
    val rows: List<StatementRow>
)

/**
 * One finished or running request, as kept on the phone. Dates are ISO text (yyyy-MM-dd). [sendTo] and [sendToSteps]
 * hold only masked forms of the address (never the address itself). A status this version does not know, such as the
 * "Ready" of Phase 1, is read as [StatementStatus.Failed]: that statement was never emailed, and its PDF is kept.
 */
@Serializable
data class StatementRecord(
    val id: String,
    val accountNumber: String,
    val startDate: String,
    val endDate: String,
    val requestedAtMillis: Long,
    val format: String = FORMAT_PDF,
    val status: StatementStatus = StatementStatus.Failed,
    val fileName: String,
    val filePath: String = "",
    val sendTo: String = "",
    val sendToSteps: String = ""
) {
    val period: StatementPeriod
        get() = StatementPeriod(LocalDate.parse(startDate), LocalDate.parse(endDate))
}
