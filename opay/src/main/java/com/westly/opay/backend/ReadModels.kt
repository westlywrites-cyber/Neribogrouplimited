package com.westly.opay.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountRow(
    @SerialName("user_id") val userId: String,
    @SerialName("account_number") val accountNumber: String,
    @SerialName("balance_kobo") val balanceKobo: Long,
    @SerialName("free_transfers_left") val freeTransfersLeft: Int,
    @SerialName("free_transfers_day") val freeTransfersDay: String,
    @SerialName("business_number") val businessNumber: String? = null
)

@Serializable
data class NotificationRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val body: String,
    val tab: String,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class BankRow(
    val code: String,
    val name: String,
    @SerialName("success_rate") val successRate: Int,
    @SerialName("is_mfb") val isMfb: Boolean,
    @SerialName("is_internal") val isInternal: Boolean = false,
    val sort: Int = 1000
)
