package com.westly.opay.backend

import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val SEND_STATEMENT_TIMEOUT_MS = 60_000L

/** One typed call per Edge Function of the backend contract. Failures come back as [BackendResult.Failure]. */
class EdgeApi(private val provider: SupabaseProvider) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = Headers.build { append(HttpHeaders.ContentType, "application/json") }

    suspend fun otpSend(request: OtpSendRequest): BackendResult<OtpSendResponse> =
        call("otp-send", OtpSendRequest.serializer(), request, OtpSendResponse.serializer())

    suspend fun otpVerify(request: OtpVerifyRequest): BackendResult<OtpVerifyResponse> =
        call("otp-verify", OtpVerifyRequest.serializer(), request, OtpVerifyResponse.serializer())

    suspend fun login(request: LoginRequest): BackendResult<SignInResponse> =
        call("login", LoginRequest.serializer(), request, SignInResponse.serializer())

    suspend fun biometricSetup(request: BiometricSetupRequest): BackendResult<BiometricSetupResponse> =
        call("biometric-setup", BiometricSetupRequest.serializer(), request, BiometricSetupResponse.serializer())

    suspend fun biometricDisable(request: BiometricDisableRequest): BackendResult<OkResponse> =
        call("biometric-disable", BiometricDisableRequest.serializer(), request, OkResponse.serializer())

    suspend fun biometricLogin(request: BiometricLoginRequest): BackendResult<SignInResponse> =
        call("biometric-login", BiometricLoginRequest.serializer(), request, SignInResponse.serializer())

    suspend fun resetLoginPin(request: ResetLoginPinRequest): BackendResult<OkResponse> =
        call("reset-login-pin", ResetLoginPinRequest.serializer(), request, OkResponse.serializer())

    suspend fun resetPaymentPin(request: ResetPaymentPinRequest): BackendResult<OkResponse> =
        call("reset-payment-pin", ResetPaymentPinRequest.serializer(), request, OkResponse.serializer())

    suspend fun changePhone(request: ChangePhoneRequest): BackendResult<OkResponse> =
        call("change-phone", ChangePhoneRequest.serializer(), request, OkResponse.serializer())

    suspend fun resolveAccount(request: ResolveAccountRequest): BackendResult<ResolveAccountResponse> =
        call("resolve-account", ResolveAccountRequest.serializer(), request, ResolveAccountResponse.serializer())

    /** Sends a transfer to a real OPay account that is not registered in this app; the answer is the sender's row. */
    suspend fun transferOpayExternal(request: TransferOpayExternalRequest): BackendResult<TransactionRow> =
        call("transfer-opay-external", TransferOpayExternalRequest.serializer(), request, TransactionRow.serializer())

    /** Emails a statement PDF through `send-statement`. It waits up to 60 seconds; a timeout is an UNKNOWN failure. */
    suspend fun sendStatement(request: SendStatementRequest): BackendResult<Unit> {
        val result = try {
            withTimeout(SEND_STATEMENT_TIMEOUT_MS) {
                call("send-statement", SendStatementRequest.serializer(), request, OkResponse.serializer())
            }
        } catch (e: TimeoutCancellationException) {
            return BackendResult.Failure(BackendError(BackendCodes.UNKNOWN, BackendCodes.messageFor(BackendCodes.UNKNOWN)))
        }
        return when (result) {
            is BackendResult.Success -> BackendResult.Success(Unit)
            is BackendResult.Failure -> result
        }
    }

    private suspend fun <Req, Res> call(
        name: String,
        requestSerializer: KSerializer<Req>,
        request: Req,
        responseSerializer: KSerializer<Res>
    ): BackendResult<Res> = backendCall {
        val body = json.encodeToJsonElement(requestSerializer, request).jsonObject
        val response = provider.client.functions.invoke(function = name, body = body, headers = jsonHeaders)
        json.decodeFromString(responseSerializer, response.bodyAsText())
    }
}
