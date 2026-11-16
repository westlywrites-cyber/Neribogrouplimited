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
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenUnavailableException
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.feature.login.LoginDialog
import com.westly.opay.feature.login.LoginFailure
import com.westly.opay.feature.login.LoginFailures
import com.westly.opay.feature.login.PROOF_TTL_MILLIS
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinBuffer
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

const val FINGERPRINT_PAY_ENABLED_TOAST = "Fingerprint payment enabled"
const val FINGERPRINT_PAY_DISABLED_TOAST = "Fingerprint payment disabled"

/** The two pages of the turn-on flow: D7 (code), then D8 (payment PIN). */
enum class FingerprintPayStage { Verify, Pin }

/** What the turn-on flow draws. The typed digits, the proof and the token are never part of it. */
data class FingerprintPayUiState(
    val stage: FingerprintPayStage = FingerprintPayStage.Verify,
    val filled: Int = 0,
    val message: String? = null,
    val shakeCount: Int = 0,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val dialog: LoginDialog? = null,
    val sessionExpired: Boolean = false
) {
    val locked: Boolean get() = busy || finished
    val canConfirm: Boolean get() = stage == FingerprintPayStage.Pin && filled == PAYMENT_PIN_LENGTH && !locked
}

sealed interface FingerprintPayEvent {
    data class ShowToast(val message: String) : FingerprintPayEvent

    /** The screen shows the system fingerprint prompt for [cipher] and reports back through `onPrompt…`. */
    data class LaunchPrompt(val cipher: Cipher) : FingerprintPayEvent

    /** The token is stored on this device and the server flag is on. */
    data object Enabled : FingerprintPayEvent
}

/** The two `biometric-*` calls for kind `pay`. A fake of this is used in unit tests. */
interface FingerprintPayGateway {
    /** `biometric-setup` with kind `pay`. Returns the device token, which exists only in memory afterwards. */
    suspend fun enable(pin: String): BackendResult<String>

    /** `biometric-disable` with kind `pay`. */
    suspend fun disable(): BackendResult<Unit>
}

class BackendFingerprintPayGateway(private val backend: Backend) : FingerprintPayGateway {

    override suspend fun enable(pin: String): BackendResult<String> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return when (val result = backend.edge.biometricSetup(BiometricSetupRequest(BiometricKind.PAY, deviceId, pin))) {
            is BackendResult.Success -> BackendResult.Success(result.value.token)
            is BackendResult.Failure -> result
        }
    }

    override suspend fun disable(): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return when (val result = backend.edge.biometricDisable(BiometricDisableRequest(BiometricKind.PAY, deviceId))) {
            is BackendResult.Success -> BackendResult.Success(Unit)
            is BackendResult.Failure -> result
        }
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/**
 * Turns fingerprint payment on after D7 has produced a proof: the payment PIN (D8), `biometric-setup`, a Keystore
 * key, the fingerprint prompt and the encrypted token. Scoped to the screen's back stack entry. The proof lives
 * only in a private field and is dropped after 10 minutes, when the flow ends and when the screen goes away; the
 * typed digits live only in a [PinBuffer] and the device token only in [pendingToken] until the prompt has
 * encrypted it. None of them is part of the UI state, saved for process death, written to storage or logged.
 */
class FingerprintPayViewModel(
    private val gateway: FingerprintPayGateway,
    private val vault: TokenVault,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(FingerprintPayUiState())
    val state: StateFlow<FingerprintPayUiState> = _state.asStateFlow()

    private val _events = Channel<FingerprintPayEvent>(Channel.BUFFERED)
    val events: Flow<FingerprintPayEvent> = _events.receiveAsFlow()

    private var proof: String? = null
    private var proofReceivedAt = 0L
    private var entry = PinBuffer(PAYMENT_PIN_LENGTH)
    private var pendingToken: String? = null

    /** D7 accepted the code. The flow moves to the payment PIN page. */
    fun onProof(received: String) {
        if (received.isEmpty() || _state.value.finished) return
        entry.clear()
        proof = received
        proofReceivedAt = clock()
        _state.update {
            it.copy(stage = FingerprintPayStage.Pin, filled = 0, message = null, sessionExpired = false)
        }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.locked || current.stage != FingerprintPayStage.Pin || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, message = null)
    }

    fun onBackspace() {
        val current = _state.value
        if (current.locked || current.stage != FingerprintPayStage.Pin) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, message = null)
    }

    /** The Confirm button. Sends the payment PIN once all four digits are typed and the proof is still fresh. */
    fun confirm() {
        val current = _state.value
        if (!current.canConfirm || !entry.isFull) return
        val held = proof
        if (held == null || clock() - proofReceivedAt >= PROOF_TTL_MILLIS) {
            expire()
            return
        }
        val pin = entry.toPinString()
        entry.clear()
        _state.update { it.copy(busy = true, filled = 0) }
        scope.launch {
            when (val result = gateway.enable(pin)) {
                is BackendResult.Success -> onTokenIssued(result.value)
                is BackendResult.Failure -> onSetupFailed(result.error)
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    /** Called when the person confirms the "Session expired" box. The screen then leaves. */
    fun acknowledgeExpired() {
        entry.clear()
        proof = null
        _state.value = FingerprintPayUiState()
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
            val stored = try {
                withContext(io) { vault.encryptToken(VaultPurpose.Pay, token, cipher) }
                true
            } catch (e: TokenUnavailableException) {
                false
            }
            if (stored) {
                proof = null
                _state.update { it.copy(busy = false, finished = true) }
                emit(FingerprintPayEvent.Enabled)
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
            _state.update { it.copy(busy = false) }
            if (error != null) emit(FingerprintPayEvent.ShowToast(error.message))
        }
    }

    fun onPromptError(message: String) {
        scope.launch {
            pendingToken = null
            val error = rollback()
            _state.update { it.copy(busy = false) }
            emit(FingerprintPayEvent.ShowToast(error?.message ?: message))
        }
    }

    private suspend fun onTokenIssued(token: String) {
        val cipher = try {
            withContext(io) { vault.newEncryptCipher(VaultPurpose.Pay) }
        } catch (e: TokenUnavailableException) {
            failEnable(GENERIC_ERROR)
            return
        }
        pendingToken = token
        emit(FingerprintPayEvent.LaunchPrompt(cipher))
    }

    private fun onSetupFailed(error: BackendError) {
        when (val failure = LoginFailures.classify(error)) {
            is LoginFailure.WrongPin ->
                rejectPin(wrongPaymentPinText(failure.attemptsLeft))
            is LoginFailure.Locked -> rejectPin(LoginFailures.lockedText(failure.minutes))
            LoginFailure.RateLimited ->
                _state.update { it.copy(busy = false, filled = 0, dialog = LoginDialog.RateLimited) }
            else -> {
                _state.update { it.copy(busy = false, filled = 0) }
                emit(FingerprintPayEvent.ShowToast(error.message))
            }
        }
    }

    /** Back to an empty PIN page with a red line and a shake. The proof stays. */
    private fun rejectPin(message: String) {
        entry.clear()
        _state.update {
            it.copy(busy = false, filled = 0, message = message, shakeCount = it.shakeCount + 1)
        }
    }

    private fun expire() {
        entry.clear()
        proof = null
        _state.update { it.copy(busy = false, filled = 0, sessionExpired = true) }
    }

    /** Deletes the device key and token and asks the server to switch the feature off. Returns a real failure. */
    private suspend fun rollback(): BackendError? {
        withContext(io) { vault.delete(VaultPurpose.Pay) }
        val error = (gateway.disable() as? BackendResult.Failure)?.error
        return error?.takeIf { it.code != BackendCodes.BIOMETRIC_NOT_ENABLED }
    }

    private suspend fun failEnable(fallback: String) {
        val error = rollback()
        _state.update { it.copy(busy = false) }
        emit(FingerprintPayEvent.ShowToast(error?.message ?: fallback))
    }

    private fun emit(event: FingerprintPayEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        entry.clear()
        proof = null
        pendingToken = null
        scope.cancel()
    }

    companion object {
        private val GENERIC_ERROR = BackendCodes.messageFor(BackendCodes.UNKNOWN)

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val backend = (application as OPayApplication).backend
                FingerprintPayViewModel(
                    gateway = BackendFingerprintPayGateway(backend),
                    vault = KeystoreVault(backend.secureStore)
                )
            }
        }
    }
}
