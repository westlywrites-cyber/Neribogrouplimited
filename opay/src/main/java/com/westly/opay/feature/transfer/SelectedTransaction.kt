package com.westly.opay.feature.transfer

import com.westly.opay.data.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SelectedTransaction {
    private val state = MutableStateFlow<Transaction?>(null)

    val current: StateFlow<Transaction?> = state.asStateFlow()

    fun select(transaction: Transaction?) {
        state.value = transaction
    }
}
