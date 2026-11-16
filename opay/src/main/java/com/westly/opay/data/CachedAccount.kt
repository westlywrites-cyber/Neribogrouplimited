package com.westly.opay.data

import com.westly.opay.backend.SavingsPositionRow
import com.westly.opay.data.model.AppNotification
import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * The last known account values, saved with the local settings so the app has something to show offline.
 * It belongs to one signed-in user and is removed on sign-out.
 */
@Serializable
data class CachedAccount(
    val userId: String,
    val profile: UserProfile,
    val balanceKobo: Long,
    val freeTransfersLeft: Int,
    val transactions: List<Transaction>,
    val notifications: List<AppNotification>,
    val recents: List<Beneficiary>,
    val favourites: List<Beneficiary>,
    val savings: List<SavingsPositionRow>,
    val banks: List<Bank> = emptyList(),
    val beneficiaries: List<Beneficiary> = emptyList(),
    val businessNumber: String? = null
)
