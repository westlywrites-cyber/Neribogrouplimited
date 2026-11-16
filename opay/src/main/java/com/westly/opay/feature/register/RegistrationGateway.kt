package com.westly.opay.feature.register

import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.KycForm
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.backend.OtpSendRequest
import com.westly.opay.backend.OtpSendResponse
import com.westly.opay.backend.OtpVerifyRequest
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.backend.RegistrationState
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SessionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The backend calls the registration steps need. A fake of this is used in unit tests. */
interface RegistrationGateway {
    suspend fun otpSend(phone10: String): BackendResult<OtpSendResponse>
    suspend fun otpVerify(phone10: String, code: String): BackendResult<OtpVerifyResponse>
    suspend fun signIn(session: SessionPayload): BackendResult<Unit>
    suspend fun rememberPhone(phone10: String)
    suspend fun signOut()
    suspend fun submitKyc(form: KycForm): BackendResult<Unit>
    suspend fun registrationState(): BackendResult<RegistrationState>

    /** Sets the address the next code is sent to. Null means none. Fakes may ignore it. */
    fun setEmail(email: String?) {}
}

class BackendRegistrationGateway(private val backend: Backend) : RegistrationGateway {

    @Volatile
    private var email: String? = null

    override fun setEmail(email: String?) {
        this.email = email
    }

    override suspend fun otpSend(phone10: String): BackendResult<OtpSendResponse> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return backend.edge.otpSend(OtpSendRequest(phone10, OtpPurpose.SIGNUP, deviceId, email))
    }

    override suspend fun otpVerify(phone10: String, code: String): BackendResult<OtpVerifyResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            OtpVerifyRequest(phone10, OtpPurpose.SIGNUP, code, identity.deviceId(), identity.deviceName(), email)
        }
        return backend.edge.otpVerify(request)
    }

    override suspend fun signIn(session: SessionPayload): BackendResult<Unit> =
        backend.session.signInWithTokens(session)

    override suspend fun rememberPhone(phone10: String) {
        withContext(Dispatchers.IO) { backend.secureStore.putString(SecureKeys.REMEMBERED_PHONE, phone10) }
    }

    override suspend fun signOut() {
        backend.session.signOut()
    }

    override suspend fun submitKyc(form: KycForm): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        return backend.rpc.submitKycForm(form)
    }

    override suspend fun registrationState(): BackendResult<RegistrationState> {
        if (!backend.isOnline()) return offline()
        return backend.rpc.registrationState()
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}
