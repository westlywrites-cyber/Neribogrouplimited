package com.westly.opay.backend

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One typed wrapper per database function a signed-in user may call. Parameters that are null are left out of the
 * request so the database default applies. The owner tools, accrue_interest and cleanup_expired_otps are not here
 * because the app can never call them.
 */
class RpcApi(private val provider: SupabaseProvider) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun registrationState(): BackendResult<RegistrationState> =
        call("registration_state", params(), RegistrationState.serializer())

    suspend fun submitKycForm(form: KycForm): BackendResult<Unit> =
        callUnit("submit_kyc_form", params("p_form" to encode(KycForm.serializer(), form)))

    suspend fun setLoginPin(pin: String): BackendResult<Unit> =
        callUnit("set_login_pin", params("p_pin" to p(pin)))

    suspend fun setPaymentPin(pin: String): BackendResult<Unit> =
        callUnit("set_payment_pin", params("p_pin" to p(pin)))

    suspend fun verifyLoginPin(pin: String): BackendResult<Boolean> =
        call("verify_login_pin", params("p_pin" to p(pin)), Boolean.serializer())

    suspend fun verifyPaymentPin(pin: String): BackendResult<Boolean> =
        call("verify_payment_pin", params("p_pin" to p(pin)), Boolean.serializer())

    suspend fun changeLoginPin(oldPin: String, newPin: String): BackendResult<Unit> =
        callUnit("change_login_pin", params("p_old" to p(oldPin), "p_new" to p(newPin)))

    suspend fun changePaymentPin(oldPin: String, newPin: String): BackendResult<Unit> =
        callUnit("change_payment_pin", params("p_old" to p(oldPin), "p_new" to p(newPin)))

    suspend fun updateProfile(
        nickname: String? = null,
        themeMode: String? = null,
        hideBalance: Boolean? = null,
        loginMode: String? = null,
        settings: JsonObject? = null
    ): BackendResult<ProfileRow> = call(
        "update_profile",
        params(
            "p_nickname" to p(nickname),
            "p_theme_mode" to p(themeMode),
            "p_hide_balance" to p(hideBalance),
            "p_login_mode" to p(loginMode),
            "p_settings" to settings
        ),
        ProfileRow.serializer()
    )

    suspend fun setSecurityAnswers(answers: List<SecurityAnswer>): BackendResult<Unit> =
        callUnit(
            "set_security_answers",
            params("p_answers" to encode(ListSerializer(SecurityAnswer.serializer()), answers))
        )

    suspend fun submitTransfer(
        accountNumber: String,
        bankCode: String,
        amountKobo: Long,
        remark: String? = null,
        category: String? = null,
        pin: String? = null,
        payToken: String? = null,
        deviceId: String? = null,
        counterpartyName: String? = null,
        bankName: String? = null
    ): BackendResult<TransactionRow> = call(
        "submit_transfer",
        params(
            "p_account_number" to p(accountNumber),
            "p_bank_code" to p(bankCode),
            "p_amount_kobo" to p(amountKobo),
            "p_remark" to p(remark),
            "p_category" to p(category),
            "p_pin" to p(pin),
            "p_pay_token" to p(payToken),
            "p_device_id" to p(deviceId),
            "p_counterparty_name" to p(counterpartyName),
            "p_bank_name" to p(bankName)
        ),
        TransactionRow.serializer()
    )

    suspend fun purchase(
        type: String,
        title: String,
        amountKobo: Long,
        counterparty: String? = null
    ): BackendResult<TransactionRow> = call(
        "purchase",
        params(
            "p_type" to p(type),
            "p_title" to p(title),
            "p_amount_kobo" to p(amountKobo),
            "p_counterparty" to p(counterparty)
        ),
        TransactionRow.serializer()
    )

    suspend fun toggleFavourite(accountNumber: String, bankCode: String): BackendResult<BeneficiaryRow> = call(
        "toggle_favourite",
        params("p_account_number" to p(accountNumber), "p_bank_code" to p(bankCode)),
        BeneficiaryRow.serializer()
    )

    suspend fun markNotificationsRead(): BackendResult<Unit> =
        callUnit("mark_notifications_read", params())

    suspend fun savingsCreate(
        kind: String,
        name: String,
        amountKobo: Long,
        targetKobo: Long? = null,
        maturesAt: String? = null,
        meta: JsonObject? = null
    ): BackendResult<SavingsPositionRow> = call(
        "savings_create",
        params(
            "p_kind" to p(kind),
            "p_name" to p(name),
            "p_amount_kobo" to p(amountKobo),
            "p_target_kobo" to p(targetKobo),
            "p_matures_at" to p(maturesAt),
            "p_meta" to meta
        ),
        SavingsPositionRow.serializer()
    )

    suspend fun savingsDeposit(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow> = call(
        "savings_deposit",
        params("p_position_id" to p(positionId), "p_amount_kobo" to p(amountKobo)),
        SavingsPositionRow.serializer()
    )

    suspend fun savingsWithdraw(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow> = call(
        "savings_withdraw",
        params("p_position_id" to p(positionId), "p_amount_kobo" to p(amountKobo)),
        SavingsPositionRow.serializer()
    )

    private suspend fun <T> call(
        name: String,
        parameters: JsonObject,
        serializer: KSerializer<T>
    ): BackendResult<T> = backendCall {
        val result = provider.client.postgrest.rpc(name, parameters)
        json.decodeFromString(serializer, result.data)
    }

    private suspend fun callUnit(name: String, parameters: JsonObject): BackendResult<Unit> = backendCall {
        provider.client.postgrest.rpc(name, parameters)
        Unit
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonElement {
        val element = json.encodeToJsonElement(serializer, value)
        return if (element is JsonObject) JsonObject(element.filterValues { it !is JsonNull }) else element
    }

    private fun params(vararg pairs: Pair<String, JsonElement?>): JsonObject =
        JsonObject(pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

    private fun p(value: String?): JsonElement? = value?.let { JsonPrimitive(it) }

    private fun p(value: Long?): JsonElement? = value?.let { JsonPrimitive(it) }

    private fun p(value: Boolean?): JsonElement? = value?.let { JsonPrimitive(it) }
}
