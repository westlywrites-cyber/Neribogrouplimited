package com.westly.opay.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object OtpPurpose {
    const val SIGNUP = "signup"
    const val LOGIN_DEVICE = "login_device"
    const val RESET_LOGIN_PIN = "reset_login_pin"
    const val CHANGE_PHONE = "change_phone"
    const val SETTINGS = "settings"
    const val RESET_PAYMENT_PIN = "reset_payment_pin"
}

object BiometricKind {
    const val LOGIN = "login"
    const val PAY = "pay"
}

@Serializable
data class SessionPayload(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("user_id") val userId: String
)

@Serializable
data class RegistrationState(
    val status: String,
    @SerialName("has_kyc") val hasKyc: Boolean,
    @SerialName("has_login_pin") val hasLoginPin: Boolean,
    @SerialName("has_payment_pin") val hasPaymentPin: Boolean
)

@Serializable
data class OkResponse(val ok: Boolean)

@Serializable
data class SignInResponse(
    val session: SessionPayload,
    @SerialName("registration_state") val registrationState: RegistrationState
)

@Serializable
data class OtpSendRequest(
    val phone: String,
    val purpose: String,
    @SerialName("device_id") val deviceId: String,
    val email: String? = null
)

@Serializable
data class OtpSendResponse(
    val ok: Boolean,
    @SerialName("resend_after") val resendAfter: Int,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class OtpVerifyRequest(
    val phone: String,
    val purpose: String,
    val code: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val email: String? = null
)

/** Purposes signup and login_device return [session] and [registrationState]; all others return [proof]. */
@Serializable
data class OtpVerifyResponse(
    val session: SessionPayload? = null,
    @SerialName("registration_state") val registrationState: RegistrationState? = null,
    val proof: String? = null
)

@Serializable
data class LoginRequest(
    val phone: String,
    val pin: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class BiometricSetupRequest(
    val kind: String,
    @SerialName("device_id") val deviceId: String,
    val pin: String
)

@Serializable
data class BiometricSetupResponse(val token: String)

@Serializable
data class BiometricDisableRequest(
    val kind: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class BiometricLoginRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String
)

@Serializable
data class ResetLoginPinRequest(
    val phone: String,
    val proof: String,
    @SerialName("new_pin") val newPin: String
)

@Serializable
data class ResetPaymentPinRequest(
    val proof: String,
    @SerialName("new_pin") val newPin: String
)

@Serializable
data class ChangePhoneRequest(
    @SerialName("old_phone") val oldPhone: String,
    val pin: String,
    @SerialName("new_phone") val newPhone: String,
    val proof: String
)

@Serializable
data class ResolveAccountRequest(
    @SerialName("account_number") val accountNumber: String,
    @SerialName("bank_code") val bankCode: String
)

/** Body of `transfer-opay-external`. There is no name field: the server looks the recipient name up itself. */
@Serializable
data class TransferOpayExternalRequest(
    @SerialName("account_number") val accountNumber: String,
    @SerialName("amount_kobo") val amountKobo: Long,
    val remark: String? = null,
    val category: String? = null,
    val pin: String? = null,
    @SerialName("pay_token") val payToken: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

/** Body of `send-statement`. The three names are the function's own field names. */
@Serializable
data class SendStatementRequest(
    val email: String,
    val pdfBase64: String,
    val periodLabel: String
)

@Serializable
data class ResolveAccountResponse(
    @SerialName("account_number") val accountNumber: String,
    val name: String,
    @SerialName("bank_code") val bankCode: String,
    @SerialName("bank_name") val bankName: String,
    /** `opay_internal` or `paystack`. */
    val source: String
)
