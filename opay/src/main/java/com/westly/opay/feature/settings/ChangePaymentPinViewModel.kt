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
import com.westly.opay.feature.login.LoginFailure
import com.westly.opay.feature.login.LoginFailures
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

const val STRONGER_PIN_TEXT = "Choose a stronger PIN"
const val PIN_MISMATCH_TEXT = "The two PINs do not match"
const val PAYMENT_PIN_CHANGED_TOAST = "Payment PIN changed"
private const val STALE_TEXT = "Please enter your old PIN again"

/** "Incorrect PIN, 3 attempts left"; the count is left out when the server did not send it. */
fun wrongPaymentPinText(attemptsLeft: Int?): String = when {
    attemptsLeft == null -> "Incorrect PIN"
    attemptsLeft == 1 -> "Incorrect PIN, 1 attempt left"
    else -> "Incorrect PIN, $attemptsLeft attempts left"
}

/** The three pages of the signed-in change-payment-PIN flow. */
enum class ChangePinStep { Old, New, Confirm }

/** What the change-PIN screen draws. The typed digits are never part of it, only how many were typed. */
data class ChangePaymentPinUiState(
    val step: ChangePinStep = ChangePinStep.Old,
    val filled: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val shakeCount: Int = 0
)

sealed interface ChangePaymentPinEvent {
    data class ShowToast(val message: String) : ChangePaymentPinEvent
    data object Changed : ChangePaymentPinEvent
}

/** The one backend call this flow needs. A fake of this is used in unit tests. */
interface ChangePaymentPinGateway {
    suspend fun changePaymentPin(oldPin: String, newPin: String): BackendResult<Unit>
}

class BackendChangePaymentPinGateway(private val backend: Backend) : ChangePaymentPinGateway {

    override suspend fun changePaymentPin(oldPin: String, newPin: String): BackendResult<Unit> {
        if (!backend.isOnline()) {
            return BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
        }
        return backend.rpc.changePaymentPin(oldPin, newPin)
    }
}

/**
 * Old PIN, new PIN, confirm, then `change_payment_pin`. The three entries live only in [PinBuffer]s and are wiped
 * when the call succeeds, when the old PIN is rejected and when the screen goes away. Changing the PIN never
 * touches the fingerprint payment token.
 */
class ChangePaymentPinViewModel(
    private val gateway: ChangePaymentPinGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ChangePaymentPinUiState())
    val state: StateFlow<ChangePaymentPinUiState> = _state.asStateFlow()

    private val _events = Channel<ChangePaymentPinEvent>(Channel.BUFFERED)
    val events: Flow<ChangePaymentPinEvent> = _events.receiveAsFlow()

    private var old: PinBuffer? = null
    private var first: PinBuffer? = null
    private var entry = PinBuffer(PAYMENT_PIN_LENGTH)

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.busy || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, message = null)
        if (entry.isFull) onEntryComplete()
    }

    fun onBackspace() {
        val current = _state.value
        if (current.busy) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, message = null)
    }

    /**
     * Back from the new-PIN or confirm page returns to the old-PIN page and forgets every entry.
     * Returns false when the screen should leave instead.
     */
    fun stepBack(): Boolean {
        val current = _state.value
        if (current.busy) return true
        if (current.step == ChangePinStep.Old) return false
        wipeAll()
        _state.value = ChangePaymentPinUiState()
        return true
    }

    private fun onEntryComplete() {
        when (_state.value.step) {
            ChangePinStep.Old -> {
                old = entry
                entry = PinBuffer(PAYMENT_PIN_LENGTH)
                _state.update { it.copy(step = ChangePinStep.New, filled = 0) }
            }
            ChangePinStep.New -> {
                if (entry.isTrivial()) {
                    rejectNew(STRONGER_PIN_TEXT)
                } else {
                    first = entry
                    entry = PinBuffer(PAYMENT_PIN_LENGTH)
                    _state.update { it.copy(step = ChangePinStep.Confirm, filled = 0) }
                }
            }
            ChangePinStep.Confirm -> {
                val typedFirst = first
                if (typedFirst != null && typedFirst.matches(entry)) submit() else rejectNew(PIN_MISMATCH_TEXT)
            }
        }
    }

    private fun submit() {
        val oldPin = old?.toPinString()
        val newPin = first?.toPinString()
        entry.clear()
        if (oldPin == null || newPin == null) {
            backToOld(STALE_TEXT)
            return
        }
        _state.update { it.copy(busy = true, filled = 0) }
        scope.launch {
            when (val result = gateway.changePaymentPin(oldPin, newPin)) {
                is BackendResult.Success -> {
                    wipeAll()
                    _state.update { it.copy(busy = false) }
                    emit(ChangePaymentPinEvent.Changed)
                }
                is BackendResult.Failure -> onFailed(result.error)
            }
        }
    }

    private fun onFailed(error: BackendError) {
        when (val failure = LoginFailures.classify(error)) {
            is LoginFailure.WrongPin -> backToOld(wrongPaymentPinText(failure.attemptsLeft))
            is LoginFailure.Locked -> backToOld(LoginFailures.lockedText(failure.minutes))
            else -> if (error.code == BackendCodes.INVALID_INPUT) {
                rejectNew(STRONGER_PIN_TEXT)
            } else {
                _state.update { it.copy(busy = false, filled = 0) }
                emit(ChangePaymentPinEvent.ShowToast(error.message))
            }
        }
    }

    /** The old PIN was refused: every entry is wiped and the first page shows the reason. */
    private fun backToOld(message: String) {
        wipeAll()
        _state.update {
            ChangePaymentPinUiState(step = ChangePinStep.Old, message = message, shakeCount = it.shakeCount + 1)
        }
    }

    /** Back to the new-PIN page with an error line and a shake. The old PIN is kept. */
    private fun rejectNew(message: String) {
        first?.clear()
        first = null
        entry.clear()
        _state.update {
            it.copy(step = ChangePinStep.New, filled = 0, busy = false, message = message, shakeCount = it.shakeCount + 1)
        }
    }

    private fun wipeAll() {
        old?.clear()
        old = null
        first?.clear()
        first = null
        entry.clear()
    }

    private fun emit(event: ChangePaymentPinEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        wipeAll()
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChangePaymentPinViewModel(
                    BackendChangePaymentPinGateway((application as OPayApplication).backend)
                )
            }
        }
    }
}
