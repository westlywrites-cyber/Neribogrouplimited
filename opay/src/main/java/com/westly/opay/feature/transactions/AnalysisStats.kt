package com.westly.opay.feature.transactions

import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private const val OPAY_BANK = "OPay"
private const val SUCCESSFUL = "Successful"
private const val TARGET_TICKS = 7.0

enum class AnalysisSide { Expenses, Income }

enum class LeaderKind { TransferBank, MoneySent, MoneyReceived, MobileData, Airtime, BankDeposit, Other }

data class DayBar(val label: String, val kobo: Long)

data class BillSlice(val name: String, val kobo: Long)

data class LeaderRow(val kind: LeaderKind, val name: String, val kobo: Long)

data class AnalysisStats(
    val count: Int,
    val totalKobo: Long,
    val days: List<DayBar>,
    val bills: List<BillSlice>,
    val leaders: List<LeaderRow>
)

data class AxisScale(val step: Double, val ticks: List<Double>) {
    val top: Double get() = ticks.last()
}

private fun leaderOf(tx: Transaction, side: AnalysisSide): LeaderRow {
    val kobo = abs(tx.amountKobo)
    val toOpay = tx.counterpartyBank == OPAY_BANK
    return if (side == AnalysisSide.Expenses) {
        when (tx.type) {
            TxType.TransferOut ->
                if (toOpay) LeaderRow(LeaderKind.MoneySent, "Money sent", kobo)
                else LeaderRow(LeaderKind.TransferBank, "Transfer to bank", kobo)
            TxType.Data -> LeaderRow(LeaderKind.MobileData, "Mobile Data", kobo)
            TxType.Airtime -> LeaderRow(LeaderKind.Airtime, "Airtime", kobo)
            else -> LeaderRow(LeaderKind.Other, "Other", kobo)
        }
    } else {
        when {
            tx.type == TxType.TransferIn && toOpay -> LeaderRow(LeaderKind.MoneyReceived, "Money received", kobo)
            tx.type == TxType.TransferIn -> LeaderRow(LeaderKind.BankDeposit, "Bank Deposit", kobo)
            else -> LeaderRow(LeaderKind.Other, "Other", kobo)
        }
    }
}

private fun billOf(tx: Transaction, side: AnalysisSide): String = if (side == AnalysisSide.Expenses) {
    when (tx.type) {
        TxType.TransferOut -> "Transfer"
        TxType.Airtime, TxType.Data, TxType.Bill -> "Utilities"
        else -> "Other"
    }
} else {
    when (tx.type) {
        TxType.TransferIn -> if (tx.counterpartyBank == OPAY_BANK) "Transfer" else "Deposit"
        TxType.Interest, TxType.SafeBox -> "Savings"
        else -> "Other"
    }
}

fun buildAnalysisStats(
    all: List<Transaction>,
    month: YearMonth,
    side: AnalysisSide,
    today: LocalDate,
    zone: ZoneId
): AnalysisStats {
    val items = all.filter { tx ->
        tx.status == SUCCESSFUL &&
            (if (side == AnalysisSide.Expenses) tx.amountKobo < 0 else tx.amountKobo > 0) &&
            YearMonth.from(Instant.ofEpochMilli(tx.timestampMillis).atZone(zone)) == month
    }
    val lastDay = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    val perDay = items.groupBy {
        Instant.ofEpochMilli(it.timestampMillis).atZone(zone).dayOfMonth
    }.mapValues { (_, list) -> list.sumOf { abs(it.amountKobo) } }
    val days = (1..lastDay).map { day ->
        DayBar(label = month.monthValue.toString() + "-" + day, kobo = perDay[day] ?: 0L)
    }
    val bills = items
        .groupBy { billOf(it, side) }
        .map { (name, list) -> BillSlice(name, list.sumOf { abs(it.amountKobo) }) }
        .sortedByDescending { it.kobo }
    val leaders = items
        .map { leaderOf(it, side) }
        .groupBy { it.kind }
        .map { (kind, rows) -> LeaderRow(kind, rows.first().name, rows.sumOf { it.kobo }) }
        .sortedByDescending { it.kobo }
    return AnalysisStats(
        count = items.size,
        totalKobo = items.sumOf { abs(it.amountKobo) },
        days = days,
        bills = bills,
        leaders = leaders
    )
}

fun niceAxis(maxValue: Double): AxisScale {
    if (maxValue <= 0.0) return AxisScale(1.0, listOf(0.0, 1.0))
    val raw = maxValue / TARGET_TICKS
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 3.0, 5.0, 10.0)
        .map { it * magnitude }
        .first { it >= raw - 1e-12 }
    val count = ceil(maxValue / step - 1e-9).toInt().coerceAtLeast(1)
    return AxisScale(step, (0..count).map { it * step })
}

fun formatAxisTick(value: Double, step: Double): String =
    if (step >= 1.0) String.format(java.util.Locale.US, "%,d", value.toLong())
    else String.format(java.util.Locale.US, "%.2f", value)
