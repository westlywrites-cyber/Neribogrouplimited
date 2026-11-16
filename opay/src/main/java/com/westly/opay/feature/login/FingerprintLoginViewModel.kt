package com.westly.opay.feature.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.BiometricLoginRequest
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SessionPayload
import com.westly.opay.backend.SignInResponse
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenUnavailableException
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.feature.settings.FINGERPRINT_CHANGED_TOAST
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val FINGERPRINT_NOT_RECOGNISED_TOAST = "Fingerprint not recognised"
const val FINGERPRINT_OFF_NOTICE = "Fingerprint login was turned off. Please log in with your password"
const val FINGERPRINT_UNAVAILABLE_NOTICE = "Fingerprint is not available. Please log in with your password"
const val MAX_FINGERPRINT_FAILURES = 3

/** Where the fingerprint login screen (C1) is in its life. */
enum class FingerprintPhase { Idle, Prompting, Verifying, Success, FallbackToPassword, Failed }

data class FingerprintLoginState(
    val phase: FingerprintPhase = FingerprintPhase.Idle,
    val name: String? = null,
    val failures: Int = 0
)

sealed interface FingerprintLoginEvent {
    /** The screen shows the system fingerprint prompt for [cipher] and reports back through the `onPrompt…` calls. */
    data class LaunchPrompt(val cipher: Cipher) : FingerprintLoginEvent

    data class ShowToast(val message: String) : FingerprintLoginEvent

    /** The session is established. The caller routes from [response] or dismisses the overlay it is shown in. */
    data class SignedIn(val response: SignInResponse) : FingerprintLoginEvent

    /** Leave for the login password screen, showing [notice] there when it is not null. */
    data class UsePassword(val notice: String?) : FingerprintLoginEvent
}

/** The backend calls the fingerprint login needs. A fake of this is used in unit tests. */
interface FingerprintLoginGateway {
    suspend fun rememberedName(): String?
    suspend fun deviceId(): String
    suspend fun biometricLogin(deviceId: String, token: String): BackendResult<SignInResponse>
    suspend fun signIn(session: SessionPayload): BackendResult<Unit>
}

class BackendFingerprintLoginGateway(private val backend: Backend) : FingerprintLoginGateway {

    override suspend fun rememberedName(): String? = withContext(Dispatchers.IO) {
        backend.secureStore.getString(SecureKeys.REMEMBERED_NAME_MASKED)?.takeIf { it.isNotBlank() }
    }

    override suspend fun deviceId(): String = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }

    override suspend fun biometricLogin(deviceId: String, token: String): BackendResult<SignInResponse> {
        if (!backend.isOnline()) {
            return BackendResult.Failure(
                BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK))
            )
        }
        return backend.edge.biometricLogin(BiometricLoginRequest(deviceId, token))
    }

    override suspend fun signIn(session: SessionPayload): BackendResult<Unit> =
        backend.session.signInWithTokens(session)
}

/**
 * Runs the fingerprint login screen (reference C1). The device token is decrypted only by the system fingerprint
 * prompt and lives in memory only until `biometric-login` has answered. A token that can no longer be used is
 * deleted and the user is sent to the login password screen with a notice.
 */
class FingerprintLoginViewModel(
    private val gateway: FingerprintLoginGateway,
    private val vault: TokenVault,
    private val canAuthenticate: () -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(FingerprintLoginState())
    val state: StateFlow<FingerprintLoginState> = _state.asStateFlow()

    private val _events = Channel<FingerprintLoginEvent>(Channel.BUFFERED)
    val events: Flow<FingerprintLoginEvent> = _events.receiveAsFlow()

    private var entered = false

    /** Called when the screen opens: loads the name and opens the prompt once, even if the screen is recomposed. */
    fun onEntered() {
        if (entered) return
        entered = true
        scope.launch {
            val name = gateway.rememberedName()
            _state.update { it.copy(name = name) }
        }
        start()
    }

    /** Opens the system prompt. Used on entry and when the user taps the fingerprint icon to try again. */
    fun start() {
        val phase = _state.value.phase
        if (phase != FingerprintPhase.Idle && phase != FingerprintPhase.Failed) return
        _state.update { it.copy(phase = FingerprintPhase.Prompting) }
        scope.launch {
            val cipher = try {
                withContext(io) { vault.newDecryptCipher(VaultPurpose.Login) }
            } catch (e: TokenUnavailableException) {
                dropToken(e.invalidated)
                return@launch
            }
            if (!canAuthenticate()) {
                fallback(FINGERPRINT_UNAVAILABLE_NOTICE)
                return@launch
            }
            emit(FingerprintLoginEvent.LaunchPrompt(cipher))
        }
    }

    /** The prompt accepted the fingerprint and unlocked [cipher]. */
    fun onPromptSuccess(cipher: Cipher?) {
        if (_state.value.phase != FingerprintPhase.Prompting) return
        if (cipher == null) {
            onPromptFailed()
            return
        }
        _state.update { it.copy(phase = FingerprintPhase.Verifying) }
        scope.launch {
            val token = try {
                withContext(io) { vault.decryptToken(VaultPurpose.Login, cipher) }
            } catch (e: TokenUnavailableException) {
                dropToken(e.invalidated)
                return@launch
            }
            verify(token)
        }
    }

    /** The user closed the prompt (back gesture or tap outside). The screen stays so the icon can try again. */
    fun onPromptCancelled() {
        _state.update {
            if (it.phase == FingerprintPhase.Prompting) it.copy(phase = FingerprintPhase.Idle) else it
        }
    }

    /** The prompt ended with an error. Every third one sends the user to the login password screen. */
    fun onPromptFailed() {
        val current = _state.value
        if (current.phase != FingerprintPhase.Prompting) return
        val failures = current.failures + 1
        if (failures >= MAX_FINGERPRINT_FAILURES) {
            _state.update { it.copy(failures = failures) }
            fallback(FINGERPRINT_NOT_RECOGNISED_TOAST)
        } else {
            _state.update { it.copy(phase = FingerprintPhase.Failed, failures = failures) }
            emit(FingerprintLoginEvent.ShowToast(FINGERPRINT_NOT_RECOGNISED_TOAST))
        }
    }

    /** "Use password", on the screen or on the prompt's negative button. */
    fun onUsePassword() {
        val phase = _state.value.phase
        if (phase == FingerprintPhase.Verifying || phase == FingerprintPhase.Success) return
        fallback(null)
    }

    private suspend fun verify(token: String) {
        val deviceId = gateway.deviceId()
        when (val result = gateway.biometricLogin(deviceId, token)) {
            is BackendResult.Success -> finishSignIn(result.value)
            is BackendResult.Failure -> onLoginFailed(result.error)
        }
    }

    private suspend fun finishSignIn(response: SignInResponse) {
        when (val signedIn = gateway.signIn(response.session)) {
            is BackendResult.Failure -> failed(signedIn.error.message)
            is BackendResult.Success -> {
                _state.update { it.copy(phase = FingerprintPhase.Success) }
                emit(FingerprintLoginEvent.SignedIn(response))
            }
        }
    }

    private suspend fun onLoginFailed(error: BackendError) {
        when (error.code) {
            BackendCodes.INVALID_TOKEN, BackendCodes.BIOMETRIC_NOT_ENABLED -> {
                withContext(io) { vault.delete(VaultPurpose.Login) }
                fallback(FINGERPRINT_OFF_NOTICE)
            }
            else -> failed(error.message)
        }
    }

    /** The key cannot be used any more: delete the token and go to the password screen with the right notice. */
    private suspend fun dropToken(invalidated: Boolean) {
        withContext(io) { vault.delete(VaultPurpose.Login) }
        fallback(if (invalidated) FINGERPRINT_CHANGED_TOAST else FINGERPRINT_OFF_NOTICE)
    }

    private fun failed(message: String) {
        _state.update { it.copy(phase = FingerprintPhase.Failed) }
        emit(FingerprintLoginEvent.ShowToast(message))
    }

    private fun fallback(notice: String?) {
        _state.update { it.copy(phase = FingerprintPhase.FallbackToPassword) }
        emit(FingerprintLoginEvent.UsePassword(notice))
    }

    private fun emit(event: FingerprintLoginEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val backend = (application as OPayApplication).backend
                FingerprintLoginViewModel(
                    gateway = BackendFingerprintLoginGateway(backend),
                    vault = KeystoreVault(backend.secureStore),
                    canAuthenticate = { BiometricGate.canAuthenticate(application) }
                )
            }
        }
    }
}
