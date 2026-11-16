package com.westly.opay.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransactionRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    val title: String,
    @SerialName("counterparty_name") val counterpartyName: String? = null,
    @SerialName("counterparty_bank") val counterpartyBank: String? = null,
    @SerialName("counterparty_account") val counterpartyAccount: String? = null,
    @SerialName("amount_kobo") val amountKobo: Long,
    @SerialName("balance_after_kobo") val balanceAfterKobo: Long,
    val status: String,
    @SerialName("tx_no") val txNo: String,
    @SerialName("session_id") val sessionId: String,
    val category: String? = null,
    val remark: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ProfileRow(
    val id: String,
    val phone: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("middle_name") val middleName: String? = null,
    val surname: String? = null,
    val nickname: String? = null,
    val status: String,
    val tier: Int,
    val gender: String? = null,
    val dob: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("login_mode") val loginMode: String,
    @SerialName("hide_balance") val hideBalance: Boolean,
    @SerialName("theme_mode") val themeMode: String,
    val settings: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String,
    @SerialName("approved_at") val approvedAt: String? = null
)

@Serializable
data class BeneficiaryRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("account_number") val accountNumber: String,
    @SerialName("bank_code") val bankCode: String,
    @SerialName("bank_name") val bankName: String,
    @SerialName("is_favourite") val isFavourite: Boolean,
    @SerialName("last_used_at") val lastUsedAt: String
)

@Serializable
data class SavingsPositionRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val kind: String,
    val name: String,
    @SerialName("principal_kobo") val principalKobo: Long,
    @SerialName("accrued_kobo") val accruedKobo: Long,
    @SerialName("target_kobo") val targetKobo: Long? = null,
    @SerialName("rate_bps") val rateBps: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("matures_at") val maturesAt: String? = null,
    val status: String,
    val meta: JsonObject = JsonObject(emptyMap())
)

/** Keys of `submit_kyc_form(p_form)`. Null fields are left out of the request. */
@Serializable
data class KycForm(
    val state: String,
    val lga: String,
    val area: String,
    @SerialName("full_address") val fullAddress: String,
    val landmark: String? = null,
    @SerialName("permanent_same") val permanentSame: Boolean = true,
    @SerialName("permanent_state") val permanentState: String? = null,
    @SerialName("permanent_lga") val permanentLga: String? = null,
    @SerialName("permanent_area") val permanentArea: String? = null,
    @SerialName("permanent_full_address") val permanentFullAddress: String? = null,
    val nationality: String = "Nigeria",
    @SerialName("is_pep") val isPep: Boolean,
    @SerialName("pep_office") val pepOffice: String? = null,
    @SerialName("pep_start_date") val pepStartDate: String? = null,
    @SerialName("pep_holds_position") val pepHoldsPosition: Boolean? = null,
    @SerialName("pep_related") val pepRelated: Boolean = false,
    val occupation: String,
    @SerialName("income_range") val incomeRange: String,
    @SerialName("other_income") val otherIncome: Boolean = false,
    @SerialName("other_income_source") val otherIncomeSource: String? = null,
    @SerialName("expected_annual_income") val expectedAnnualIncome: String? = null
)

@Serializable
data class SecurityAnswer(val question: String, val answer: String)
