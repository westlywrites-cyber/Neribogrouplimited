package com.westly.opay.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val fullName: String,
    val nickname: String,
    val phone: String,
    val accountNumber: String,
    val tier: Int,
    val emailMasked: String,
    val gender: String,
    val dobMasked: String
)

@Serializable
data class Bank(
    val code: String,
    val name: String,
    val successRate: Int,
    val isMfb: Boolean,
    val isInternal: Boolean = false
)

@Serializable
enum class TxType { TransferOut, TransferIn, Airtime, Data, Interest, Withdraw, SafeBox, Bill }

@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val title: String,
    val counterpartyName: String?,
    val counterpartyBank: String?,
    val counterpartyAccount: String?,
    val amountKobo: Long,
    val timestampMillis: Long,
    val status: String,
    val txNo: String,
    val sessionId: String,
    val category: String?,
    val remark: String?
)

@Serializable
data class Beneficiary(
    val name: String,
    val accountNumber: String,
    val bankName: String,
    val isFavourite: Boolean,
    val bankCode: String = ""
)

@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestampMillis: Long,
    val tab: String,
    val read: Boolean = false
)

@Serializable
data class ResolvedAccount(
    val accountNumber: String,
    val name: String,
    val bank: Bank,
    /** True for a real OPay account found only by the real-time lookup. Never shown; it only picks the send path. */
    val external: Boolean = false
)

@Serializable
data class TransferDraft(
    val accountNumber: String = "",
    val bank: Bank? = null,
    val resolved: ResolvedAccount? = null,
    val amountKobo: Long = 0,
    val remark: String = "",
    val category: String? = null,
    val isOpayTransfer: Boolean = false
)
