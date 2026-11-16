package com.westly.opay.feature.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

const val ALL_CATEGORIES = "All Categories"
const val ALL_STATUS = "All Status"

val TransactionCategories = listOf(
    ALL_CATEGORIES, "Transfer", "Airtime", "Data", "Bills", "Interest", "Withdraw"
)
val TransactionStatuses = listOf(ALL_STATUS, "Successful", "Pending", "Failed")

internal val MonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private val ListZone: ZoneId = ZoneId.of("Africa/Lagos")
private const val SKELETON_MILLIS = 400L

sealed interface PeriodSelection {
    data object None : PeriodSelection
    data class Month(val month: YearMonth) : PeriodSelection
    data class Range(val start: LocalDate, val end: LocalDate) : PeriodSelection
}

data class MonthGroup(
    val month: YearMonth,
    val items: List<Transaction>,
    val inKobo: Long,
    val outKobo: Long
) {
    val key: String = month.toString()
    val label: String = MonthNames[month.monthValue - 1] + " " + month.year
    val inText: String = formatNaira(inKobo)
    val outText: String = formatNaira(outKobo)
}

data class TransactionsUiState(
    val loading: Boolean = true,
    val groups: List<MonthGroup> = emptyList(),
    val category: String = ALL_CATEGORIES,
    val status: String = ALL_STATUS,
    val selection: PeriodSelection = PeriodSelection.None
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as OPayApplication).repository

    private val category = MutableStateFlow(ALL_CATEGORIES)
    private val status = MutableStateFlow(ALL_STATUS)
    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.None)
    private val loading = MutableStateFlow(true)

    val state: StateFlow<TransactionsUiState> = combine(
        repository.transactions, category, status, selection, loading
    ) { all, cat, st, sel, busy ->
        TransactionsUiState(
            loading = busy,
            groups = buildGroups(all, cat, st, sel),
            category = cat,
            status = st,
            selection = sel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    init {
        viewModelScope.launch {
            delay(SKELETON_MILLIS)
            loading.value = false
        }
    }

    fun setCategory(value: String) {
        category.value = value
    }

    fun setStatus(value: String) {
        status.value = value
    }

    fun selectMonth(month: YearMonth) {
        selection.value = PeriodSelection.Month(month)
    }

    fun selectRange(start: LocalDate, end: LocalDate) {
        selection.value = PeriodSelection.Range(start, end)
    }

    fun clearSelection() {
        selection.value = PeriodSelection.None
    }

    private fun dateOf(tx: Transaction): LocalDate =
        Instant.ofEpochMilli(tx.timestampMillis).atZone(ListZone).toLocalDate()

    private fun matchesCategory(tx: Transaction, category: String): Boolean = when (category) {
        "Transfer" -> tx.type == TxType.TransferOut || tx.type == TxType.TransferIn
        "Airtime" -> tx.type == TxType.Airtime
        "Data" -> tx.type == TxType.Data
        "Bills" -> tx.type == TxType.Bill
        "Interest" -> tx.type == TxType.Interest
        "Withdraw" -> tx.type == TxType.Withdraw
        else -> true
    }

    private fun inSelection(date: LocalDate, selection: PeriodSelection): Boolean = when (selection) {
        PeriodSelection.None -> true
        is PeriodSelection.Month -> YearMonth.from(date) == selection.month
        is PeriodSelection.Range -> !date.isBefore(selection.start) && !date.isAfter(selection.end)
    }

    private fun buildGroups(
        all: List<Transaction>,
        category: String,
        status: String,
        selection: PeriodSelection
    ): List<MonthGroup> {
        val oldest = LocalDate.now(ListZone).minusYears(1)
        return all
            .filter { !dateOf(it).isBefore(oldest) }
            .filter { matchesCategory(it, category) }
            .filter { status == ALL_STATUS || it.status == status }
            .filter { inSelection(dateOf(it), selection) }
            .sortedByDescending { it.timestampMillis }
            .groupBy { YearMonth.from(dateOf(it)) }
            .map { (month, items) ->
                MonthGroup(
                    month = month,
                    items = items,
                    inKobo = items.filter { it.amountKobo > 0 }.sumOf { it.amountKobo },
                    outKobo = items.filter { it.amountKobo < 0 }.sumOf { abs(it.amountKobo) }
                )
            }
            .sortedByDescending { it.month }
    }
}
