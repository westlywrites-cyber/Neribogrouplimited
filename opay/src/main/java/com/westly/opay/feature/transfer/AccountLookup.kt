package com.westly.opay.feature.transfer

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.ResolvedAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val ACCOUNT_LENGTH = 10

/** What the recipient name area shows. */
sealed interface LookupState {
    object Idle : LookupState
    object Loading : LookupState
    data class Found(val account: ResolvedAccount) : LookupState

    /** The bank has no such account. */
    object NotFound : LookupState

    /** The lookup could not finish: no connection or the bank service failed. */
    object Unverified : LookupState

    /** Too many lookups; the screen shows the "Operation Too Frequent" box. */
    object RateLimited : LookupState

    /** The number is the sender's own OPay account. */
    object OwnAccount : LookupState
}

/**
 * Runs one recipient lookup at a time. A lookup starts only when the number has exactly 10 digits and a bank is
 * chosen. Starting another one, or calling [cancel], cancels the one in flight, so an old answer never replaces a
 * newer state.
 */
class AccountLookup(
    private val scope: CoroutineScope,
    private val resolve: suspend (accountNumber: String, bankCode: String) -> ResolvedAccount,
    private val ownAccountNumber: () -> String
) {
    private val _state = MutableStateFlow<LookupState>(LookupState.Idle)
    val state: StateFlow<LookupState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(accountNumber: String, bank: Bank?) {
        cancel()
        if (accountNumber.length != ACCOUNT_LENGTH || bank == null) return
        _state.value = LookupState.Loading
        job = scope.launch {
            val outcome = try {
                val account = resolve(accountNumber, bank.code)
                ensureActive()
                if (account.bank.isInternal && account.accountNumber == ownAccountNumber()) {
                    LookupState.OwnAccount
                } else {
                    LookupState.Found(account)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendError) {
                stateFor(e)
            }
            _state.value = outcome
        }
    }

    /** Stops any lookup in flight and clears the state. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = LookupState.Idle
    }

    private fun stateFor(error: BackendError): LookupState = when (error.code) {
        BackendCodes.ACCOUNT_NOT_FOUND -> LookupState.NotFound
        BackendCodes.RATE_LIMITED -> LookupState.RateLimited
        else -> LookupState.Unverified
    }
}
