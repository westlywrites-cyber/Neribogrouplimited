package com.westly.opay.feature.register

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.RegistrationState
import com.westly.opay.navigation.Routes
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

/** The four PIN screens of registration (reference B15 to B18). */
enum class PinStep(val isLogin: Boolean, val isConfirm: Boolean) {
    CreateLogin(isLogin = true, isConfirm = false),
    ConfirmLogin(isLogin = true, isConfirm = true),
    CreatePayment(isLogin = false, isConfirm = false),
    ConfirmPayment(isLogin = false, isConfirm = true);

    val length: Int get() = if (isLogin) LOGIN_PIN_LENGTH else PAYMENT_PIN_LENGTH

    val createStep: PinStep get() = if (isLogin) CreateLogin else CreatePayment

    val confirmStep: PinStep get() = if (isLogin) ConfirmLogin else ConfirmPayment
}

enum class PinMessage { Trivial, Mismatch }

sealed interface PinSetupEvent {
    data class ShowToast(val message: String) : PinSetupEvent
    data class GoTo(val route: String) : PinSetupEvent
}

/** What the PIN screens draw. The typed digits are never part of it, only how many were typed. */
data class PinSetupUiState(
    val step: PinStep = PinStep.CreateLogin,
    val filled: Int = 0,
    val checking: Boolean = true,
    val submitting: Boolean = false,
    val message: PinMessage? = null,
    val shakeCount: Int = 0
) {
    val busy: Boolean get() = checking || submitting
}

/** The backend calls the PIN steps need. A fake of this is used in unit tests. */
interface PinSetupGateway {
    suspend fun setLoginPin(pin: String): BackendResult<Unit>
    suspend fun setPaymentPin(pin: String): BackendResult<Unit>
    suspend fun registrationState(): BackendResult<RegistrationState>
}

class BackendPinSetupGateway(private val backend: Backend) : PinSetupGateway {

    override suspend fun setLoginPin(pin: String): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        return backend.rpc.setLoginPin(pin)
    }

    override suspend fun setPaymentPin(pin: String): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        return backend.rpc.setPaymentPin(pin)
    }

    override suspend fun registrationState(): BackendResult<RegistrationState> {
        if (!backend.isOnline()) return offline()
        return backend.rpc.registrationState()
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/**
 * Runs the login PIN and payment PIN steps. Scoped to the screen's back stack entry. Only the step name is saved
 * for process death; the digits live in [PinBuffer]s and are wiped when the screen goes away. A confirm step
 * restores as its create step because the first entry is gone.
 */
class PinSetupViewModel(
    private val gateway: PinSetupGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(PinSetupUiState(step = restoreStep()))
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    private val _events = Channel<PinSetupEvent>(Channel.BUFFERED)
    val events: Flow<PinSetupEvent> = _events.receiveAsFlow()

    private var entry = PinBuffer(_state.value.step.length)
    private var first: PinBuffer? = null

    init {
        scope.launch { checkProgress() }
    }

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

    /** Back from a confirm screen returns to its create screen and forgets both entries. */
    fun stepBack() {
        val current = _state.value
        if (current.busy || !current.step.isConfirm) return
        showStep(current.step.createStep)
    }

    private suspend fun checkProgress() {
        when (val result = gateway.registrationState()) {
            is BackendResult.Success -> applyProgress(result.value)
            is BackendResult.Failure -> {
                _state.update { it.copy(checking = false) }
                emit(PinSetupEvent.ShowToast(result.error.message))
            }
        }
    }

    /** Sends the user where the server says they are: the right PIN screen, or on to another screen. */
    private fun applyProgress(registration: RegistrationState) {
        val route = RegistrationRouting.nextRoute(registration)
        if (route != Routes.RegisterPin) {
            emit(PinSetupEvent.GoTo(route))
            return
        }
        if (registration.hasLoginPin && registration.hasPaymentPin) {
            emit(PinSetupEvent.GoTo(Routes.Waiting))
            return
        }
        showStep(if (registration.hasLoginPin) PinStep.CreatePayment else PinStep.CreateLogin)
    }

    private fun onEntryComplete() {
        val step = _state.value.step
        if (!step.isConfirm) {
            if (entry.isTrivial()) {
                reject(step, PinMessage.Trivial)
            } else {
                first = entry
                entry = PinBuffer(step.length)
                setStep(step.confirmStep)
            }
            return
        }
        val original = first
        if (original == null || !original.matches(entry)) {
            reject(step.createStep, PinMessage.Mismatch)
        } else {
            submit(step)
        }
    }

    private fun submit(step: PinStep) {
        val pin = entry.toPinString()
        _state.update { it.copy(submitting = true) }
        scope.launch {
            val result = if (step.isLogin) gateway.setLoginPin(pin) else gateway.setPaymentPin(pin)
            when (result) {
                is BackendResult.Success -> onSaved(step)
                is BackendResult.Failure -> onSaveFailed(step, result.error)
            }
        }
    }

    private suspend fun onSaved(step: PinStep) {
        wipe()
        if (step.isLogin) {
            showStep(PinStep.CreatePayment)
            return
        }
        when (val result = gateway.registrationState()) {
            is BackendResult.Success -> applyProgress(result.value)
            is BackendResult.Failure -> emit(PinSetupEvent.GoTo(Routes.Waiting))
        }
    }

    private suspend fun onSaveFailed(step: PinStep, error: BackendError) {
        when (error.code) {
            BackendCodes.PIN_ALREADY_SET -> onSaved(step)
            BackendCodes.INVALID_INPUT -> reject(step.createStep, PinMessage.Trivial)
            BackendCodes.STATE_INVALID -> {
                wipe()
                when (val result = gateway.registrationState()) {
                    is BackendResult.Success -> applyProgress(result.value)
                    is BackendResult.Failure -> {
                        showStep(step.createStep)
                        emit(PinSetupEvent.ShowToast(result.error.message))
                    }
                }
            }
            else -> {
                entry.clear()
                _state.update { it.copy(submitting = false, filled = 0) }
                emit(PinSetupEvent.ShowToast(error.message))
            }
        }
    }

    /** Back to a create screen with an error line and a shake. Both entries are wiped. */
    private fun reject(createStep: PinStep, message: PinMessage) {
        wipe(createStep.length)
        savedState[KEY_STEP] = createStep.name
        _state.update {
            it.copy(
                step = createStep,
                filled = 0,
                submitting = false,
                message = message,
                shakeCount = it.shakeCount + 1
            )
        }
    }

    private fun showStep(step: PinStep) {
        wipe(step.length)
        setStep(step)
    }

    private fun setStep(step: PinStep) {
        savedState[KEY_STEP] = step.name
        _state.update { it.copy(step = step, filled = 0, checking = false, submitting = false, message = null) }
    }

    private fun wipe(length: Int = entry.capacity) {
        entry.clear()
        first?.clear()
        first = null
        entry = PinBuffer(length)
    }

    private fun restoreStep(): PinStep {
        val saved = savedState.get<String>(KEY_STEP)
        return PinStep.entries.firstOrNull { it.name == saved }?.createStep ?: PinStep.CreateLogin
    }

    private fun emit(event: PinSetupEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        entry.clear()
        first?.clear()
        scope.cancel()
    }

    companion object {
        private const val KEY_STEP = "pin_step"

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PinSetupViewModel(
                    gateway = BackendPinSetupGateway((application as OPayApplication).backend),
                    savedState = createSavedStateHandle()
                )
            }
        }
    }
}
