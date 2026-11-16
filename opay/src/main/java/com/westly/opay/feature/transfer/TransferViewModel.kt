package com.westly.opay.feature.transfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.ResolvedAccount
import com.westly.opay.data.model.TransferDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as OPayApplication).repository

    private val _draft = MutableStateFlow(TransferDraft())
    val draft: StateFlow<TransferDraft> = _draft.asStateFlow()

    private val lookup = AccountLookup(
        scope = viewModelScope,
        resolve = repository::resolveAccount,
        ownAccountNumber = { repository.profile.value.accountNumber }
    )

    /** What the recipient name area shows. */
    val lookupState: StateFlow<LookupState> = lookup.state

    val matching: StateFlow<Boolean> = lookup.state
        .map { it is LookupState.Loading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val recents = repository.recents
    val favourites = repository.favourites

    /** The recipients saved at the OPay bank, for the Transfer to OPay Account screen. */
    val opayRecents = repository.opayRecents
    val opayFavourites = repository.opayFavourites
    val freeTransfersLeft = repository.freeTransfersLeft

    init {
        viewModelScope.launch {
            lookup.state.collect { state ->
                if (state is LookupState.Found) {
                    _draft.update { it.copy(resolved = state.account, bank = state.account.bank) }
                }
            }
        }
    }

    fun suggestions(prefix: String): List<ResolvedAccount> = repository.suggestAccounts(prefix)

    fun opaySuggestions(prefix: String): List<ResolvedAccount> = repository.suggestOpayAccounts(prefix)

    fun onAccountChanged(value: String) {
        val digits = digitsOf(value)
        if (digits == _draft.value.accountNumber) return
        _draft.update { it.copy(accountNumber = digits, resolved = null) }
        startResolve()
    }

    fun selectBank(bank: Bank) {
        _draft.update { it.copy(bank = bank, resolved = null, isOpayTransfer = bank.isInternal) }
        startResolve()
    }

    fun selectBeneficiary(beneficiary: Beneficiary) {
        val bank = repository.banks.firstOrNull { it.name == beneficiary.bankName }
        val digits = digitsOf(beneficiary.accountNumber)
        _draft.update {
            val chosen = bank ?: it.bank
            it.copy(
                accountNumber = digits,
                bank = chosen,
                resolved = null,
                isOpayTransfer = chosen?.isInternal == true
            )
        }
        startResolve()
    }

    fun setAmount(amountKobo: Long) {
        _draft.update { it.copy(amountKobo = amountKobo) }
    }

    fun setRemark(remark: String) {
        _draft.update { it.copy(remark = remark) }
    }

    fun setCategory(category: String?) {
        _draft.update { it.copy(category = category) }
    }

    fun reset() {
        lookup.cancel()
        _draft.value = TransferDraft()
    }

    /** Runs the lookup again for the number and bank on screen, after a failed one. */
    fun retryLookup() {
        startResolve()
    }

    /** Clears a finished lookup message, such as the rate limit box, without touching the number. */
    fun dismissLookup() {
        lookup.cancel()
    }

    fun startOpayTransfer(input: String) {
        val digits = input.filter { it in '0'..'9' }
        val number = if (digits.length == ACCOUNT_LENGTH + 1 && digits.startsWith("0")) {
            digits.drop(1)
        } else {
            digits.take(ACCOUNT_LENGTH)
        }
        val bank = repository.internalBank ?: return
        lookup.cancel()
        _draft.value = TransferDraft(accountNumber = number, bank = bank, isOpayTransfer = true)
        startResolve()
    }

    fun consumeOpayTransfer() {
        reset()
    }

    private fun digitsOf(value: String): String =
        value.filter { it in '0'..'9' }.take(ACCOUNT_LENGTH)

    private fun startResolve() {
        val snapshot = _draft.value
        lookup.start(snapshot.accountNumber, snapshot.bank)
    }
}
