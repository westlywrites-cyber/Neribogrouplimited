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
import com.westly.opay.backend.OkResponse
import com.westly.opay.backend.ResetPaymentPinRequest
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.feature.login.LoginDialog
import com.westly.opay.feature.login.PROOF_TTL_MILLIS
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinBuffer
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

const val PAYMENT_PIN_RESET_TOAST = "Payment PIN reset. Please enable fingerprint payment again if you want it."

/** The three pages of the forgot-payment-PIN flow: D7, new PIN, confirm. */
enum class ForgotPinStage { Verify, NewPin, ConfirmPin }

/** What the flow draws. The typed digits and the proof are never part of it. */
data class ForgotPaymentPinUiState(
    val stage: ForgotPinStage = ForgotPinStage.Verify,
    val filled: Int = 0,
    val message: String? = null,
    val shakeCount: Int = 0,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val dialog: LoginDialog? = null,
    val sessionExpired: Boolean = false
) {
    val locked: Boolean get() = busy || finished
}

sealed interface ForgotPaymentPinEvent {
    data class ShowToast(val message: String) : ForgotPaymentPinEvent

    /** The PIN was reset and the local fingerprint payment token is gone. */
    data object ResetDone : ForgotPaymentPinEvent
}

/** The calls the flow needs after D7. A fake of this is used in unit tests. */
interface ForgotPaymentPinGateway {
    suspend fun resetPaymentPin(proof: String, newPin: String): BackendResult<OkResponse>

    /** Removes the fingerprint payment token and its key from this device. Nothing happens when none exists. */
    suspend fun clearPayToken()
}

class BackendForgotPaymentPinGateway(
    private val backend: Backend,
    private val vault: TokenVault
) : ForgotPaymentPinGateway {

    override suspend fun resetPaymentPin(proof: String, newPin: String): BackendResult<OkResponse> {
        if (!backend.isOnline()) {
            return BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
        }
        return backend.edge.resetPaymentPin(ResetPaymentPinRequest(proof, newPin))
    }

    override suspend fun clearPayToken() {
        withContext(Dispatchers.IO) { vault.delete(VaultPurpose.Pay) }
    }
}

/**
 * Runs the forgot-payment-PIN flow after D7 has produced a proof: new PIN, confirm, `reset-payment-pin`. Scoped to
 * the screen's back stack entry. The proof lives only in a private field and is dropped after 10 minutes, when the
 * flow ends and when the screen goes away; the typed digits live only in [PinBuffer]s. Neither is part of the UI
 * state, saved for process death, written to storage or logged.
 */
class ForgotPaymentPinViewModel(
    private val gateway: ForgotPaymentPinGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ForgotPaymentPinUiState())
    val state: StateFlow<ForgotPaymentPinUiState> = _state.asStateFlow()

    private val _events = Channel<ForgotPaymentPinEvent>(Channel.BUFFERED)
    val events: Flow<ForgotPaymentPinEvent> = _events.receiveAsFlow()

    private var proof: String? = null
    private var proofReceivedAt = 0L
    private var entry = PinBuffer(PAYMENT_PIN_LENGTH)
    private var first: PinBuffer? = null

    /** D7 accepted the code. The flow moves to the new-PIN page. */
    fun onProof(received: String) {
        if (received.isEmpty() || _state.value.finished) return
        wipePins()
        proof = received
        proofReceivedAt = clock()
        _state.update { it.copy(stage = ForgotPinStage.NewPin, filled = 0, message = null, sessionExpired = false) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.locked || current.stage == ForgotPinStage.Verify) return
        if (!entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, message = null)
        if (entry.isFull) onEntryComplete()
    }

    fun onBackspace() {
        val current = _state.value
        if (current.locked || current.stage == ForgotPinStage.Verify) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, message = null)
    }

    /** Back from the confirm page returns to the new-PIN page and forgets both entries. */
    fun stepBack() {
        val current = _state.value
        if (current.locked || current.stage != ForgotPinStage.ConfirmPin) return
        wipePins()
        _state.update { it.copy(stage = ForgotPinStage.NewPin, filled = 0, message = null) }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    /** Called when the person confirms the "Session expired" box. The screen then leaves. */
    fun acknowledgeExpired() {
        wipePins()
        proof = null
        _state.value = ForgotPaymentPinUiState()
    }

    private fun onEntryComplete() {
        if (_state.value.stage == ForgotPinStage.NewPin) {
            if (entry.isTrivial()) {
                rejectNewPin(STRONGER_PIN_TEXT)
            } else {
                first = entry
                entry = PinBuffer(PAYMENT_PIN_LENGTH)
                _state.update { it.copy(stage = ForgotPinStage.ConfirmPin, filled = 0, message = null) }
            }
            return
        }
        val original = first
        if (original == null || !original.matches(entry)) rejectNewPin(PIN_MISMATCH_TEXT) else submit()
    }

    private fun submit() {
        val held = proof
        if (held == null || clock() - proofReceivedAt >= PROOF_TTL_MILLIS) {
            expire()
            return
        }
        val pin = entry.toPinString()
        entry.clear()
        _state.update { it.copy(busy = true, filled = 0) }
        scope.launch {
            when (val result = gateway.resetPaymentPin(held, pin)) {
                is BackendResult.Success -> onReset()
                is BackendResult.Failure -> onSubmitFailed(result.error)
            }
        }
    }

    private suspend fun onReset() {
        wipePins()
        proof = null
        gateway.clearPayToken()
        _state.update { it.copy(busy = false, finished = true, filled = 0) }
        _events.trySend(ForgotPaymentPinEvent.ResetDone)
    }

    private fun onSubmitFailed(error: BackendError) {
        when (error.code) {
            BackendCodes.PROOF_INVALID -> expire()
            BackendCodes.INVALID_INPUT -> rejectNewPin(STRONGER_PIN_TEXT)
            BackendCodes.RATE_LIMITED -> _state.update { it.copy(busy = false, filled = 0, dialog = LoginDialog.RateLimited) }
            else -> {
                _state.update { it.copy(busy = false, filled = 0) }
                _events.trySend(ForgotPaymentPinEvent.ShowToast(error.message))
            }
        }
    }

    /** Back to the new-PIN page with an error line and a shake. Both entries are wiped; the proof stays. */
    private fun rejectNewPin(message: String) {
        wipePins()
        _state.update {
            it.copy(
                stage = ForgotPinStage.NewPin,
                filled = 0,
                busy = false,
                message = message,
                shakeCount = it.shakeCount + 1
            )
        }
    }

    private fun expire() {
        wipePins()
        proof = null
        _state.update { it.copy(busy = false, filled = 0, sessionExpired = true) }
    }

    private fun wipePins() {
        entry.clear()
        first?.clear()
        first = null
        entry = PinBuffer(PAYMENT_PIN_LENGTH)
    }

    override fun onCleared() {
        entry.clear()
        first?.clear()
        proof = null
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val backend = (application as OPayApplication).backend
                ForgotPaymentPinViewModel(
                    BackendForgotPaymentPinGateway(backend, KeystoreVault(backend.secureStore))
                )
            }
        }
    }
}
