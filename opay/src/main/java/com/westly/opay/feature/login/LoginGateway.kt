package com.westly.opay.feature.login

import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.LoginRequest
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.backend.OtpSendRequest
import com.westly.opay.backend.OtpSendResponse
import com.westly.opay.backend.OtpVerifyRequest
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SessionPayload
import com.westly.opay.backend.SignInResponse
import com.westly.opay.backend.backendCall
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.VaultPurpose
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The backend calls the login screens need. A fake of this is used in unit tests. */
interface LoginGateway {
    suspend fun rememberedPhone(): String?
    suspend fun rememberedName(): String?
    suspend fun login(phone10: String, pin: String): BackendResult<SignInResponse>
    suspend fun otpSend(phone10: String, email: String): BackendResult<OtpSendResponse>
    suspend fun otpVerify(phone10: String, code: String, email: String): BackendResult<OtpVerifyResponse>
    suspend fun signIn(session: SessionPayload): BackendResult<Unit>

    /** Stores the phone and, when the profile has one, the first name for the next start. */
    suspend fun rememberAfterSignIn(phone10: String)

    /** Forgets the remembered phone and name and the fingerprint login token. The device id stays. */
    suspend fun forgetRemembered()

    suspend fun signOut()

    /** True when a fingerprint login token is stored on this phone. */
    suspend fun hasFingerprintToken(): Boolean = false
}

class BackendLoginGateway(private val backend: Backend) : LoginGateway {

    override suspend fun rememberedPhone(): String? = withContext(Dispatchers.IO) {
        backend.secureStore.getString(SecureKeys.REMEMBERED_PHONE)?.takeIf { it.isNotBlank() }
    }

    override suspend fun rememberedName(): String? = withContext(Dispatchers.IO) {
        backend.secureStore.getString(SecureKeys.REMEMBERED_NAME_MASKED)?.takeIf { it.isNotBlank() }
    }

    override suspend fun login(phone10: String, pin: String): BackendResult<SignInResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            LoginRequest(phone10, pin, identity.deviceId(), identity.deviceName())
        }
        return backend.edge.login(request)
    }

    override suspend fun otpSend(phone10: String, email: String): BackendResult<OtpSendResponse> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return backend.edge.otpSend(OtpSendRequest(phone10, OtpPurpose.LOGIN_DEVICE, deviceId, email))
    }

    override suspend fun otpVerify(phone10: String, code: String, email: String): BackendResult<OtpVerifyResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            OtpVerifyRequest(phone10, OtpPurpose.LOGIN_DEVICE, code, identity.deviceId(), identity.deviceName(), email)
        }
        return backend.edge.otpVerify(request)
    }

    override suspend fun signIn(session: SessionPayload): BackendResult<Unit> =
        backend.session.signInWithTokens(session)

    override suspend fun rememberAfterSignIn(phone10: String) {
        val first = firstName()
        withContext(Dispatchers.IO) {
            val store = backend.secureStore
            val previousPhone = store.getString(SecureKeys.REMEMBERED_PHONE)
            store.putString(SecureKeys.REMEMBERED_PHONE, phone10)
            val name = LoginFormat.rememberedName(first)
            when {
                name != null -> store.putString(SecureKeys.REMEMBERED_NAME_MASKED, name)
                previousPhone != phone10 -> store.remove(SecureKeys.REMEMBERED_NAME_MASKED)
            }
        }
    }

    override suspend fun forgetRemembered() {
        withContext(Dispatchers.IO) {
            backend.secureStore.remove(SecureKeys.REMEMBERED_PHONE)
            backend.secureStore.remove(SecureKeys.REMEMBERED_NAME_MASKED)
            // The fingerprint token belongs to the account that was just forgotten.
            KeystoreVault(backend.secureStore).delete(VaultPurpose.Login)
        }
    }

    override suspend fun hasFingerprintToken(): Boolean = withContext(Dispatchers.IO) {
        !backend.secureStore.getString(SecureKeys.DEVICE_TOKEN_LOGIN).isNullOrBlank()
    }

    override suspend fun signOut() {
        backend.session.signOut()
    }

    /** The signed-in user's own profile row (row-level security allows only that row). Null if it cannot be read. */
    private suspend fun firstName(): String? {
        val result = backendCall {
            backend.provider.client.postgrest.from("profiles").select().decodeSingleOrNull<ProfileRow>()
        }
        return (result as? BackendResult.Success)?.value?.firstName
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}
