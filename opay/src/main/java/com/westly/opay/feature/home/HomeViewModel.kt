package com.westly.opay.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.data.LoadState
import com.westly.opay.data.model.AppNotification
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.data.model.UserProfile
import com.westly.opay.feature.notifications.unreadCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LatestTransaction(val title: String, val amountKobo: Long)

data class HomeUiState(
    val nickname: String,
    val tier: Int,
    val unread: Int,
    val balanceKobo: Long,
    val hideBalance: Boolean,
    val latest: LatestTransaction?,
    val earnedTodayKobo: Long,
    /** True until the first read of the account has finished. */
    val loading: Boolean,
    /** The reason the first read failed, or null. */
    val loadError: String?,
    /** True while there is no connection; the values shown are the last known ones. */
    val offline: Boolean
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as OPayApplication).repository
    private val zone: ZoneId = ZoneId.of("Africa/Lagos")

    val uiState: StateFlow<HomeUiState> = combine(
        combine(repository.profile, repository.balanceKobo, repository.hideBalance) { profile, balance, hide ->
            Triple(profile, balance, hide)
        },
        combine(repository.transactions, repository.notifications) { transactions, notifications ->
            transactions to notifications
        },
        combine(repository.loadState, repository.offline) { load, offline -> load to offline }
    ) { (profile, balance, hide), (transactions, notifications), (load, offline) ->
        build(profile, balance, hide, transactions, notifications, load, offline)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = build(
            repository.profile.value,
            repository.balanceKobo.value,
            repository.hideBalance.value,
            repository.transactions.value,
            repository.notifications.value,
            repository.loadState.value,
            repository.offline.value
        )
    )

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun toggleHideBalance() {
        repository.setHideBalance(!repository.hideBalance.value)
    }

    /** The "Tap to retry" card shown when the first read failed. */
    fun retry() {
        viewModelScope.launch { repository.refresh() }
    }

    private fun build(
        profile: UserProfile,
        balance: Long,
        hide: Boolean,
        transactions: List<Transaction>,
        notifications: List<AppNotification>,
        load: LoadState,
        offline: Boolean
    ) = HomeUiState(
        nickname = profile.nickname,
        tier = profile.tier,
        unread = unreadCount(notifications),
        balanceKobo = balance,
        hideBalance = hide,
        latest = latestOf(transactions),
        earnedTodayKobo = earnedToday(transactions),
        loading = load is LoadState.Idle || load is LoadState.Loading,
        loadError = (load as? LoadState.Failed)?.error?.message,
        offline = offline
    )

    private fun latestOf(transactions: List<Transaction>): LatestTransaction? =
        transactions.maxByOrNull { it.timestampMillis }
            ?.let { LatestTransaction(title = latestLabel(it), amountKobo = it.amountKobo) }

    /** Interest paid today, by the Africa/Lagos calendar day. */
    private fun earnedToday(transactions: List<Transaction>): Long {
        val today = LocalDate.now(zone)
        return transactions
            .filter { it.type == TxType.Interest && it.amountKobo > 0 }
            .filter { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == today }
            .sumOf { it.amountKobo }
    }

    private fun latestLabel(transaction: Transaction): String = when (transaction.type) {
        TxType.Airtime -> "Airtime"
        TxType.Data -> "Mobile Data"
        else -> transaction.title
    }
}
