package com.westly.opay.feature.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendError
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsErrors
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsPosition
import com.westly.opay.data.SavingsSummary
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal const val NOT_AVAILABLE_MESSAGE = ""

private const val RECENT_COUNT = 3

data class ActivityItem(
    val title: String,
    val timestampMillis: Long,
    val amountKobo: Long
)

internal fun displayNaira(kobo: Long, hidden: Boolean): String =
    if (hidden) "\u20A6****" else formatNaira(kobo)

class WealthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as OPayApplication).repository

    val hideBalance: StateFlow<Boolean> = repository.hideBalance

    /** Every active savings position, newest first. Read from the server rows; nothing is computed on the device. */
    val positions: StateFlow<List<SavingsPosition>> = repository.savingsPositions
        .map { SavingsBook.positions(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SavingsBook.positions(repository.savingsPositions.value)
        )

    /** Totals of [positions]: `principal_kobo + accrued_kobo`, and the accrued interest alone. */
    val summary: StateFlow<SavingsSummary> = positions
        .map { SavingsBook.summary(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SavingsBook.summary(SavingsBook.positions(repository.savingsPositions.value))
        )

    /** The wallet balance (`accounts.balance_kobo`), shown as the first row of the assets breakdown. */
    val walletKobo: StateFlow<Long> = repository.balanceKobo

    /** Total Balance: the wallet balance plus every active savings position. [summary] itself stays savings-only. */
    val totalKobo: StateFlow<Long> = combine(repository.balanceKobo, summary) { wallet, savings ->
        wallet + savings.totalKobo
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.balanceKobo.value +
            SavingsBook.summary(SavingsBook.positions(repository.savingsPositions.value)).totalKobo
    )

    private val ledger: StateFlow<List<Transaction>> = repository.transactions

    fun toggleHideBalance() {
        repository.setHideBalance(!repository.hideBalance.value)
    }

    /** The newest ledger rows written by the server for [kind]: its deposits, withdrawals and interest. */
    fun activities(kind: SavingsKind): StateFlow<List<ActivityItem>> = ledger
        .map { buildActivities(it, kind) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildActivities(ledger.value, kind)
        )

    fun create(
        kind: SavingsKind,
        name: String,
        amountKobo: Long,
        targetKobo: Long? = null,
        maturesAt: String? = null,
        meta: JsonObject? = null,
        onDone: (String?) -> Unit
    ) = launchCall(onDone) { repository.savingsCreate(kind, name, amountKobo, targetKobo, maturesAt, meta) }

    fun deposit(positionId: String, amountKobo: Long, onDone: (String?) -> Unit) =
        launchCall(onDone) { repository.savingsDeposit(positionId, amountKobo) }

    fun withdraw(positionId: String, amountKobo: Long, onDone: (String?) -> Unit) =
        launchCall(onDone) { repository.savingsWithdraw(positionId, amountKobo) }

    /** Runs one savings call and reports null on success or the text to show on failure. */
    private fun launchCall(onDone: (String?) -> Unit, call: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                call()
                null
            } catch (e: BackendError) {
                SavingsErrors.message(e)
            }
            onDone(message)
        }
    }
}

/** The ledger rows of one kind, newest first. The server titles them "<Kind> deposit", "<Kind> withdrawal", "<Kind> interest". */
internal fun buildActivities(transactions: List<Transaction>, kind: SavingsKind): List<ActivityItem> =
    transactions
        .filter { (it.type == TxType.SafeBox || it.type == TxType.Interest) && it.title.startsWith(titlePrefix(kind)) }
        .sortedByDescending { it.timestampMillis }
        .take(RECENT_COUNT)
        .map { ActivityItem(it.title, it.timestampMillis, it.amountKobo) }

private fun titlePrefix(kind: SavingsKind): String = when (kind) {
    SavingsKind.OWealth -> "OWealth"
    SavingsKind.SafeBox -> "SafeBox"
    SavingsKind.Target -> "Target Savings"
    SavingsKind.Fixed -> "Fixed Savings"
    SavingsKind.SpendSave -> "Spend and Save"
}
