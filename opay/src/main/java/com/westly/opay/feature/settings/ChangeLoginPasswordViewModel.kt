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
import com.westly.opay.feature.register.LOGIN_PIN_LENGTH
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

const val STRONGER_PASSWORD_TEXT = "Choose a stronger password"
const val PASSWORD_MISMATCH_TEXT = "The two passwords do not match"
const val PASSWORD_CHANGED_TOAST = "Login password changed"
private const val STALE_TEXT = "Please enter your old password again"

/** The three pages of the signed-in change-password flow. */
enum class ChangeStep { Old, New, Confirm }

/** What the change-password screen draws. The typed digits are never part of it, only how many were typed. */
data class ChangePasswordUiState(
    val step: ChangeStep = ChangeStep.Old,
    val filled: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val shakeCount: Int = 0
)

sealed interface ChangePasswordEvent {
    data class ShowToast(val message: String) : ChangePasswordEvent
    data object Changed : ChangePasswordEvent
}

/** The one backend call this flow needs. A fake of this is used in unit tests. */
interface ChangeLoginPasswordGateway {
    suspend fun changeLoginPin(oldPin: String, newPin: String): BackendResult<Unit>
}

class BackendChangeLoginPasswordGateway(private val backend: Backend) : ChangeLoginPasswordGateway {

    override suspend fun changeLoginPin(oldPin: String, newPin: String): BackendResult<Unit> {
        if (!backend.isOnline()) {
            return BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
        }
        return backend.rpc.changeLoginPin(oldPin, newPin)
    }
}

/**
 * Old password, new password, confirm, then `change_login_pin`. The three entries live only in [PinBuffer]s and are
 * wiped when the call succeeds, when the old password is rejected and when the screen goes away. Changing the
 * password never touches the fingerprint token.
 */
class ChangeLoginPasswordViewModel(
    private val gateway: ChangeLoginPasswordGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    private var old: PinBuffer? = null
    private var first: PinBuffer? = null
    private var entry = PinBuffer(LOGIN_PIN_LENGTH)

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
     * Back from the new-password or confirm page returns to the old-password page and forgets every entry.
     * Returns false when the screen should leave instead.
     */
    fun stepBack(): Boolean {
        val current = _state.value
        if (current.busy) return true
        if (current.step == ChangeStep.Old) return false
        wipeAll()
        _state.value = ChangePasswordUiState()
        return true
    }

    private fun onEntryComplete() {
        when (_state.value.step) {
            ChangeStep.Old -> {
                old = entry
                entry = PinBuffer(LOGIN_PIN_LENGTH)
                _state.update { it.copy(step = ChangeStep.New, filled = 0) }
            }
            ChangeStep.New -> {
                if (entry.isTrivial()) {
                    rejectNew(STRONGER_PASSWORD_TEXT)
                } else {
                    first = entry
                    entry = PinBuffer(LOGIN_PIN_LENGTH)
                    _state.update { it.copy(step = ChangeStep.Confirm, filled = 0) }
                }
            }
            ChangeStep.Confirm -> {
                val typedFirst = first
                if (typedFirst != null && typedFirst.matches(entry)) submit() else rejectNew(PASSWORD_MISMATCH_TEXT)
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
            when (val result = gateway.changeLoginPin(oldPin, newPin)) {
                is BackendResult.Success -> {
                    wipeAll()
                    _state.update { it.copy(busy = false) }
                    emit(ChangePasswordEvent.Changed)
                }
                is BackendResult.Failure -> onFailed(result.error)
            }
        }
    }

    private fun onFailed(error: BackendError) {
        when (val failure = LoginFailures.classify(error)) {
            is LoginFailure.WrongPin -> backToOld(LoginFailures.wrongPinText(failure.attemptsLeft))
            is LoginFailure.Locked -> backToOld(LoginFailures.lockedText(failure.minutes))
            else -> if (error.code == BackendCodes.INVALID_INPUT) {
                rejectNew(STRONGER_PASSWORD_TEXT)
            } else {
                _state.update { it.copy(busy = false, filled = 0) }
                emit(ChangePasswordEvent.ShowToast(error.message))
            }
        }
    }

    /** The old password was refused: every entry is wiped and the first page shows the reason. */
    private fun backToOld(message: String) {
        wipeAll()
        _state.update {
            ChangePasswordUiState(step = ChangeStep.Old, message = message, shakeCount = it.shakeCount + 1)
        }
    }

    /** Back to the new-password page with an error line and a shake. The old password is kept. */
    private fun rejectNew(message: String) {
        first?.clear()
        first = null
        entry.clear()
        _state.update {
            it.copy(step = ChangeStep.New, filled = 0, busy = false, message = message, shakeCount = it.shakeCount + 1)
        }
    }

    private fun wipeAll() {
        old?.clear()
        old = null
        first?.clear()
        first = null
        entry.clear()
    }

    private fun emit(event: ChangePasswordEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        wipeAll()
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChangeLoginPasswordViewModel(
                    BackendChangeLoginPasswordGateway((application as OPayApplication).backend)
                )
            }
        }
    }
}
