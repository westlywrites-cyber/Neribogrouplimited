package com.westly.opay.feature.settings

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
import com.westly.opay.backend.BiometricDisableRequest
import com.westly.opay.backend.BiometricKind
import com.westly.opay.backend.BiometricSetupRequest
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.backendCall
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenStatus
import com.westly.opay.core.security.TokenUnavailableException
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.feature.login.LoginFailure
import com.westly.opay.feature.login.LoginFailures
import com.westly.opay.feature.register.LOGIN_PIN_LENGTH
import com.westly.opay.feature.register.PinBuffer
import io.github.jan.supabase.postgrest.postgrest
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val FINGERPRINT_ENABLED_TOAST = "Fingerprint login enabled"
const val FINGERPRINT_DISABLED_TOAST = "Fingerprint login disabled"
const val FINGERPRINT_CHANGED_TOAST = "Fingerprint changed. Please enable it again."
const val NO_FINGERPRINT_TEXT = "No fingerprint found. Please add a fingerprint in your phone settings."

/** The text on the right of the "Auto-logout" row. */
object LoginModeLabels {
    fun label(mode: String?): String? = when (mode) {
        "password_free" -> "Password-free"
        "sixty_minutes" -> "60 minutes"
        "always" -> "Always"
        else -> null
    }
}

/** The backend calls the Login Settings list needs. A fake of this is used in unit tests. */
interface LoginSettingsGateway {
    suspend fun loginMode(): BackendResult<String?>

    /** `biometric-setup` with kind `login`. Returns the device token, which exists only in memory afterwards. */
    suspend fun enableFingerprint(pin: String): BackendResult<String>

    /** `biometric-disable` with kind `login`. */
    suspend fun disableFingerprint(): BackendResult<Unit>
}

class BackendLoginSettingsGateway(private val backend: Backend) : LoginSettingsGateway {

    override suspend fun loginMode(): BackendResult<String?> {
        if (!backend.isOnline()) return offline()
        return when (
            val result = backendCall {
                backend.provider.client.postgrest.from("profiles").select().decodeSingleOrNull<ProfileRow>()
            }
        ) {
            is BackendResult.Success -> BackendResult.Success(result.value?.loginMode)
            is BackendResult.Failure -> result
        }
    }

    override suspend fun enableFingerprint(pin: String): BackendResult<String> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return when (val result = backend.edge.biometricSetup(BiometricSetupRequest(BiometricKind.LOGIN, deviceId, pin))) {
            is BackendResult.Success -> BackendResult.Success(result.value.token)
            is BackendResult.Failure -> result
        }
    }

    override suspend fun disableFingerprint(): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return when (val result = backend.edge.biometricDisable(BiometricDisableRequest(BiometricKind.LOGIN, deviceId))) {
            is BackendResult.Success -> BackendResult.Success(Unit)
            is BackendResult.Failure -> result
        }
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/** What the Login Settings list draws. The typed digits and the token are never part of it. */
data class LoginSettingsUiState(
    val loginModeLabel: String? = null,
    val fingerprintOn: Boolean = false,
    val busy: Boolean = false,
    val sheetOpen: Boolean = false,
    val filled: Int = 0,
    val sheetBusy: Boolean = false,
    val sheetMessage: String? = null,
    val shakeCount: Int = 0,
    val noFingerprintDialog: Boolean = false
)

sealed interface LoginSettingsEvent {
    data class ShowToast(val message: String) : LoginSettingsEvent

    /** The screen shows the system fingerprint prompt for [cipher] and reports back through `onPrompt…`. */
    data class LaunchPrompt(val cipher: Cipher) : LoginSettingsEvent
}

/**
 * Runs the Login Settings list: the Auto-logout label and turning fingerprint login on and off. The toggle is
 * derived from the vault (a stored token whose key still works), never from a separate flag. The login password is
 * held only in a [PinBuffer] and wiped as soon as it has been sent; the device token only in [pendingToken] until
 * the fingerprint prompt has encrypted it.
 */
class LoginSettingsViewModel(
    private val gateway: LoginSettingsGateway,
    private val vault: TokenVault,
    private val canAuthenticate: () -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(LoginSettingsUiState())
    val state: StateFlow<LoginSettingsUiState> = _state.asStateFlow()

    private val _events = Channel<LoginSettingsEvent>(Channel.BUFFERED)
    val events: Flow<LoginSettingsEvent> = _events.receiveAsFlow()

    private var entry = PinBuffer(LOGIN_PIN_LENGTH)
    private var pendingToken: String? = null

    /** Keeps a refresh from reading the vault between the token being encrypted and the toggle being turned on. */
    private val vaultLock = Mutex()

    /** Re-reads the vault and the profile. Called whenever the screen comes to the front. */
    fun refresh() {
        scope.launch {
            refreshFingerprint()
            refreshLoginMode()
        }
    }

    fun onToggle(on: Boolean) {
        val current = _state.value
        if (current.busy || current.sheetOpen || on == current.fingerprintOn) return
        if (!on) {
            disable()
        } else if (canAuthenticate()) {
            wipeEntry()
            _state.update { it.copy(sheetOpen = true, filled = 0, sheetMessage = null) }
        } else {
            _state.update { it.copy(noFingerprintDialog = true) }
        }
    }

    fun dismissNoFingerprintDialog() {
        _state.update { it.copy(noFingerprintDialog = false) }
    }

    fun dismissSheet() {
        if (_state.value.sheetBusy) return
        wipeEntry()
        _state.update { it.copy(sheetOpen = false, filled = 0, sheetMessage = null) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.sheetOpen || current.sheetBusy || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, sheetMessage = null)
        if (entry.isFull) submitPin()
    }

    fun onBackspace() {
        val current = _state.value
        if (!current.sheetOpen || current.sheetBusy) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, sheetMessage = null)
    }

    /** The fingerprint prompt accepted the fingerprint and unlocked [cipher]. */
    fun onPromptSuccess(cipher: Cipher?) {
        scope.launch {
            val token = pendingToken
            pendingToken = null
            if (cipher == null || token == null) {
                failEnable(GENERIC_ERROR)
                return@launch
            }
            val stored = vaultLock.withLock {
                try {
                    withContext(io) { vault.encryptToken(VaultPurpose.Login, token, cipher) }
                    _state.update { it.copy(fingerprintOn = true) }
                    true
                } catch (e: TokenUnavailableException) {
                    false
                }
            }
            if (stored) {
                emit(LoginSettingsEvent.ShowToast(FINGERPRINT_ENABLED_TOAST))
            } else {
                failEnable(GENERIC_ERROR)
            }
        }
    }

    /** The user closed the prompt after the server had issued a token. Server and device are put back to off. */
    fun onPromptCancelled() {
        scope.launch {
            pendingToken = null
            val error = rollback()
            if (error != null) emit(LoginSettingsEvent.ShowToast(error.message))
        }
    }

    fun onPromptError(message: String) {
        scope.launch {
            pendingToken = null
            val error = rollback()
            emit(LoginSettingsEvent.ShowToast(error?.message ?: message))
        }
    }

    private suspend fun refreshFingerprint() {
        val status = vaultLock.withLock {
            val found = withContext(io) { vault.status(VaultPurpose.Login) }
            _state.update { it.copy(fingerprintOn = found == TokenStatus.Valid) }
            found
        }
        if (status == TokenStatus.Invalidated) emit(LoginSettingsEvent.ShowToast(FINGERPRINT_CHANGED_TOAST))
    }

    private suspend fun refreshLoginMode() {
        val result = gateway.loginMode()
        if (result is BackendResult.Success) {
            val label = LoginModeLabels.label(result.value)
            _state.update { it.copy(loginModeLabel = label) }
        }
    }

    private fun submitPin() {
        val pin = entry.toPinString()
        entry.clear()
        _state.update { it.copy(sheetBusy = true, filled = 0) }
        scope.launch {
            when (val result = gateway.enableFingerprint(pin)) {
                is BackendResult.Success -> onTokenIssued(result.value)
                is BackendResult.Failure -> onSetupFailed(result.error)
            }
        }
    }

    private suspend fun onTokenIssued(token: String) {
        val cipher = try {
            withContext(io) { vault.newEncryptCipher(VaultPurpose.Login) }
        } catch (e: TokenUnavailableException) {
            _state.update { it.copy(sheetOpen = false, sheetBusy = false, filled = 0) }
            failEnable(GENERIC_ERROR)
            return
        }
        pendingToken = token
        _state.update { it.copy(sheetOpen = false, sheetBusy = false, filled = 0, sheetMessage = null) }
        emit(LoginSettingsEvent.LaunchPrompt(cipher))
    }

    private fun onSetupFailed(error: BackendError) {
        val message = when (val failure = LoginFailures.classify(error)) {
            is LoginFailure.WrongPin -> LoginFailures.wrongPinText(failure.attemptsLeft)
            is LoginFailure.Locked -> LoginFailures.lockedText(failure.minutes)
            else -> error.message
        }
        _state.update {
            it.copy(sheetBusy = false, filled = 0, sheetMessage = message, shakeCount = it.shakeCount + 1)
        }
    }

    private fun disable() {
        _state.update { it.copy(busy = true) }
        scope.launch {
            val result = gateway.disableFingerprint()
            val error = (result as? BackendResult.Failure)?.error
            if (error == null || error.code == BackendCodes.BIOMETRIC_NOT_ENABLED) {
                withContext(io) { vault.delete(VaultPurpose.Login) }
                _state.update { it.copy(busy = false, fingerprintOn = false) }
                emit(LoginSettingsEvent.ShowToast(FINGERPRINT_DISABLED_TOAST))
            } else {
                _state.update { it.copy(busy = false) }
                emit(LoginSettingsEvent.ShowToast(error.message))
            }
        }
    }

    /** Deletes the device key and token and asks the server to switch the feature off. Returns a real failure. */
    private suspend fun rollback(): BackendError? {
        withContext(io) { vault.delete(VaultPurpose.Login) }
        _state.update { it.copy(fingerprintOn = false) }
        val error = (gateway.disableFingerprint() as? BackendResult.Failure)?.error
        return error?.takeIf { it.code != BackendCodes.BIOMETRIC_NOT_ENABLED }
    }

    private suspend fun failEnable(fallback: String) {
        val error = rollback()
        emit(LoginSettingsEvent.ShowToast(error?.message ?: fallback))
    }

    private fun wipeEntry() {
        entry.clear()
    }

    private fun emit(event: LoginSettingsEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        entry.clear()
        pendingToken = null
        scope.cancel()
    }

    companion object {
        private val GENERIC_ERROR = BackendCodes.messageFor(BackendCodes.UNKNOWN)

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val backend = (application as OPayApplication).backend
                LoginSettingsViewModel(
                    gateway = BackendLoginSettingsGateway(backend),
                    vault = KeystoreVault(backend.secureStore),
                    canAuthenticate = { BiometricGate.canAuthenticate(application) }
                )
            }
        }
    }
}
