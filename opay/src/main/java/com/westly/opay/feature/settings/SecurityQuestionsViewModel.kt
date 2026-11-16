package com.westly.opay.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.RpcApi
import com.westly.opay.backend.SecurityAnswer
import com.westly.opay.data.AppRepository
import com.westly.opay.data.SettingsKeys
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

const val SECURITY_QUESTIONS_SAVED_TOAST = "Security questions saved"

/** The questions to choose from and the rules for the answers. */
object SecurityQuestionOptions {
    const val COUNT = 3
    const val MIN_ANSWER_LENGTH = 2
    const val MAX_ANSWER_LENGTH = 50

    val all: List<String> = listOf(
        "What was the name of your first pet?",
        "What is your mother's maiden name?",
        "In which town were you born?",
        "What was the name of your primary school?",
        "What is the name of your best childhood friend?",
        "What was the make of your first car?",
        "What is your favourite meal?",
        "What was your first job?"
    )
}

/** The three pages of the Security Questions screen. */
enum class SecurityQuestionsStep { Overview, Pin, Form }

/**
 * What the Security Questions screen draws. The payment PIN digits are never part of it, only how many were typed.
 * [picks] holds, for each slot, the index in [SecurityQuestionOptions.all] or null; [answers] the typed texts.
 */
data class SecurityQuestionsUiState(
    val step: SecurityQuestionsStep = SecurityQuestionsStep.Overview,
    val filled: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val shakeCount: Int = 0,
    val picks: List<Int?> = List(SecurityQuestionOptions.COUNT) { null },
    val answers: List<String> = List(SecurityQuestionOptions.COUNT) { "" },
    val pickerSlot: Int? = null
) {
    /** True when every slot has its own question and an answer of a usable length. */
    val canSave: Boolean
        get() = picks.none { it == null } &&
            picks.toSet().size == SecurityQuestionOptions.COUNT &&
            answers.all { it.trim().length >= SecurityQuestionOptions.MIN_ANSWER_LENGTH }

    /** The questions slot [slot] may still choose: none that another slot already holds. */
    fun availableFor(slot: Int): List<Int> {
        val taken = picks.filterIndexed { index, _ -> index != slot }.filterNotNull().toSet()
        return SecurityQuestionOptions.all.indices.filter { it !in taken }
    }
}

sealed interface SecurityQuestionsEvent {
    data class ShowToast(val message: String) : SecurityQuestionsEvent
}

/** The backend calls this screen needs. A fake of this is used in unit tests. */
interface SecurityQuestionsGateway {
    /** False while the phone has no connection. */
    fun isOnline(): Boolean

    /** `verify_payment_pin`. */
    suspend fun verifyPaymentPin(pin: String): BackendResult<Boolean>

    /** `set_security_answers`, then the setting `security_questions_set = true` in the profile. */
    suspend fun save(answers: List<SecurityAnswer>): BackendResult<Unit>
}

class BackendSecurityQuestionsGateway(
    private val rpc: RpcApi,
    private val repository: AppRepository,
    private val online: () -> Boolean
) : SecurityQuestionsGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun verifyPaymentPin(pin: String): BackendResult<Boolean> = rpc.verifyPaymentPin(pin)

    override suspend fun save(answers: List<SecurityAnswer>): BackendResult<Unit> {
        val saved = rpc.setSecurityAnswers(answers)
        if (saved is BackendResult.Failure) return saved
        return repository.saveSettings(mapOf(SettingsKeys.SECURITY_QUESTIONS_SET to true))
    }
}

/**
 * Runs the Security Questions screen. Setting the questions for the first time opens the form at once; setting them
 * again asks for the payment PIN first. The answers live only in [SecurityQuestionsUiState] while they are typed and
 * are cleared when they are saved, when the user leaves the form and when the screen goes away. They are never
 * written to the phone and never shown again.
 */
class SecurityQuestionsViewModel(
    private val gateway: SecurityQuestionsGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(SecurityQuestionsUiState())
    val state: StateFlow<SecurityQuestionsUiState> = _state.asStateFlow()

    private val _events = Channel<SecurityQuestionsEvent>(Channel.BUFFERED)
    val events: Flow<SecurityQuestionsEvent> = _events.receiveAsFlow()

    private var entry = PinBuffer(PAYMENT_PIN_LENGTH)

    /** The button on the first page. [alreadySet] is true when questions exist, which means the PIN is asked first. */
    fun onStart(alreadySet: Boolean) {
        if (_state.value.step != SecurityQuestionsStep.Overview) return
        if (!gateway.isOnline()) {
            emit(SecurityQuestionsEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            return
        }
        entry.clear()
        _state.value = SecurityQuestionsUiState(
            step = if (alreadySet) SecurityQuestionsStep.Pin else SecurityQuestionsStep.Form
        )
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.step != SecurityQuestionsStep.Pin || current.busy || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, message = null)
        if (entry.isFull) verifyPin()
    }

    fun onBackspace() {
        val current = _state.value
        if (current.step != SecurityQuestionsStep.Pin || current.busy) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, message = null)
    }

    fun openPicker(slot: Int) {
        val current = _state.value
        if (current.step == SecurityQuestionsStep.Form && !current.busy) _state.value = current.copy(pickerSlot = slot)
    }

    fun closePicker() {
        _state.update { it.copy(pickerSlot = null) }
    }

    /** Puts question [questionIndex] in the open slot and closes the picker. */
    fun onPick(questionIndex: Int) {
        val current = _state.value
        val slot = current.pickerSlot ?: return
        if (questionIndex !in current.availableFor(slot)) return
        _state.value = current.copy(
            picks = current.picks.mapIndexed { index, pick -> if (index == slot) questionIndex else pick },
            pickerSlot = null
        )
    }

    fun onAnswerChanged(slot: Int, text: String) {
        val current = _state.value
        if (current.step != SecurityQuestionsStep.Form || current.busy || slot !in current.answers.indices) return
        val clean = text.take(SecurityQuestionOptions.MAX_ANSWER_LENGTH)
        _state.value = current.copy(
            answers = current.answers.mapIndexed { index, answer -> if (index == slot) clean else answer }
        )
    }

    fun onSave() {
        val current = _state.value
        if (current.step != SecurityQuestionsStep.Form || current.busy || !current.canSave) return
        if (!gateway.isOnline()) {
            emit(SecurityQuestionsEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            return
        }
        val answers = current.picks.mapIndexed { index, pick ->
            SecurityAnswer(SecurityQuestionOptions.all[pick ?: return], current.answers[index].trim())
        }
        _state.value = current.copy(busy = true)
        scope.launch {
            when (val result = gateway.save(answers)) {
                is BackendResult.Success -> {
                    _state.value = SecurityQuestionsUiState()
                    emit(SecurityQuestionsEvent.ShowToast(SECURITY_QUESTIONS_SAVED_TOAST))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    emit(SecurityQuestionsEvent.ShowToast(result.error.message))
                }
            }
        }
    }

    /** Back from the PIN page or the form returns to the first page and forgets everything typed. Returns false to leave. */
    fun stepBack(): Boolean {
        val current = _state.value
        if (current.busy) return true
        if (current.step == SecurityQuestionsStep.Overview) return false
        if (current.pickerSlot != null) {
            closePicker()
            return true
        }
        entry.clear()
        _state.value = SecurityQuestionsUiState()
        return true
    }

    private fun verifyPin() {
        val pin = entry.toPinString()
        entry.clear()
        _state.update { it.copy(busy = true, filled = 0) }
        scope.launch {
            when (val result = gateway.verifyPaymentPin(pin)) {
                is BackendResult.Success -> if (result.value) {
                    _state.value = SecurityQuestionsUiState(step = SecurityQuestionsStep.Form)
                } else {
                    rejectPin(wrongPaymentPinText(null))
                }
                is BackendResult.Failure -> onPinFailed(result.error)
            }
        }
    }

    private fun onPinFailed(error: BackendError) {
        when (val failure = LoginFailures.classify(error)) {
            is LoginFailure.WrongPin -> rejectPin(wrongPaymentPinText(failure.attemptsLeft))
            is LoginFailure.Locked -> rejectPin(LoginFailures.lockedText(failure.minutes))
            else -> {
                _state.update { it.copy(busy = false, filled = 0) }
                emit(SecurityQuestionsEvent.ShowToast(error.message))
            }
        }
    }

    private fun rejectPin(message: String) {
        _state.update { it.copy(busy = false, filled = 0, message = message, shakeCount = it.shakeCount + 1) }
    }

    private fun emit(event: SecurityQuestionsEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        entry.clear()
        _state.value = SecurityQuestionsUiState()
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as OPayApplication
                SecurityQuestionsViewModel(
                    BackendSecurityQuestionsGateway(app.backend.rpc, app.repository, app.backend::isOnline)
                )
            }
        }
    }
}
