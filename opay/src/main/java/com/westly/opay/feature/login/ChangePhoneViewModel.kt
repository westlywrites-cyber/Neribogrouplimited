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
import com.westly.opay.backend.ChangePhoneRequest
import com.westly.opay.backend.OkResponse
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.backend.OtpSendRequest
import com.westly.opay.backend.OtpSendResponse
import com.westly.opay.backend.OtpVerifyRequest
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.backend.SecureKeys
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.OTP_LENGTH
import com.westly.opay.feature.register.PhoneInput
import com.westly.opay.feature.register.PinBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PHONE_CHANGED_MESSAGE = "Phone number changed. Please log in with your new number."
const val SAME_NUMBER_MESSAGE = "Enter a different phone number"
const val PHONE_TAKEN_MESSAGE = "This phone number is already in use"

/** The pages of the change-login-phone flow. [Intro] is the C6 information page. */
enum class ChangeStage { Intro, OldPhone, OldPin, NewPhone, Email, Code }

sealed interface ChangeEvent {
    data class ShowToast(val message: String) : ChangeEvent
    data class Finished(val newPhone: String) : ChangeEvent
}

/** What the flow draws. The typed login password and the proof are never part of it. */
data class ChangePhoneUiState(
    val stage: ChangeStage = ChangeStage.Intro,
    val oldDigits: String = "",
    val newDigits: String = "",
    val email: String = "",
    val emailError: Boolean = false,
    val code: String = "",
    val codeError: CodeError? = null,
    val codeShake: Int = 0,
    val secondsLeft: Int = 0,
    val filled: Int = 0,
    val pinError: String? = null,
    val pinShake: Int = 0,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val dialog: LoginDialog? = null,
    val phoneTaken: Boolean = false,
    val sessionExpired: Boolean = false
) {
    val locked: Boolean get() = busy || finished
    val oldValid: Boolean get() = PhoneInput.isValid(oldDigits)
    val oldError: Boolean get() = PhoneInput.showError(oldDigits)
    val newValid: Boolean get() = PhoneInput.isValid(newDigits)
    val newError: Boolean get() = PhoneInput.showError(newDigits)
    val sameNumber: Boolean get() = newDigits.length == PhoneInput.MAX_DIGITS && newDigits == oldDigits
    val canSubmitOld: Boolean get() = oldValid && !locked
    val canSubmitNew: Boolean get() = newValid && !sameNumber && !locked
    val canSendCode: Boolean get() = EmailInput.isValid(email) && secondsLeft == 0 && !locked
}

/** The backend and storage calls the change-phone flow needs. A fake of this is used in unit tests. */
interface ChangePhoneGateway {
    /** Sends the code for the NEW number. */
    suspend fun otpSend(newPhone10: String, email: String): BackendResult<OtpSendResponse>

    suspend fun otpVerify(newPhone10: String, code: String, email: String): BackendResult<OtpVerifyResponse>

    suspend fun changePhone(
        oldPhone10: String,
        pin: String,
        newPhone10: String,
        proof: String
    ): BackendResult<OkResponse>

    /** Removes the remembered number and name, the fingerprint token and any cached session of this device. */
    suspend fun clearLocalAccess()
}

class BackendChangePhoneGateway(private val backend: Backend) : ChangePhoneGateway {

    override suspend fun otpSend(newPhone10: String, email: String): BackendResult<OtpSendResponse> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return backend.edge.otpSend(OtpSendRequest(newPhone10, OtpPurpose.CHANGE_PHONE, deviceId, email))
    }

    override suspend fun otpVerify(
        newPhone10: String,
        code: String,
        email: String
    ): BackendResult<OtpVerifyResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            OtpVerifyRequest(
                newPhone10, OtpPurpose.CHANGE_PHONE, code, identity.deviceId(), identity.deviceName(), email
            )
        }
        return backend.edge.otpVerify(request)
    }

    override suspend fun changePhone(
        oldPhone10: String,
        pin: String,
        newPhone10: String,
        proof: String
    ): BackendResult<OkResponse> {
        if (!backend.isOnline()) return offline()
        return backend.edge.changePhone(ChangePhoneRequest(oldPhone10, pin, newPhone10, proof))
    }

    override suspend fun clearLocalAccess() {
        withContext(Dispatchers.IO) {
            val store = backend.secureStore
            store.remove(SecureKeys.REMEMBERED_PHONE)
            store.remove(SecureKeys.REMEMBERED_NAME_MASKED)
            store.remove(SecureKeys.DEVICE_TOKEN_LOGIN)
        }
        backend.session.signOut()
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/**
 * Runs C6: information page, old number, login password, new number, email for the code, code, then the
 * `change-phone` call. Scoped to the screen's back stack entry. The login password lives only in a [PinBuffer] and
 * the proof only in a private field; neither is part of the UI state, saved for process death, written to storage or
 * logged, and both are dropped when the flow ends, restarts or the screen goes away.
 */
class ChangePhoneViewModel(
    private val gateway: ChangePhoneGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tickMillis: Long = 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ChangePhoneUiState())
    val state: StateFlow<ChangePhoneUiState> = _state.asStateFlow()

    private val _events = Channel<ChangeEvent>(Channel.BUFFERED)
    val events: Flow<ChangeEvent> = _events.receiveAsFlow()

    private var timerJob: Job? = null
    private var proof: String? = null
    private var proofReceivedAt = 0L
    private var pin = PinBuffer(LOGIN_PIN_LENGTH)
    private var restartOnDismiss = false
    private var confirmed = false

    /** "Proceed" on the information page. */
    fun proceed() {
        _state.update { if (it.stage == ChangeStage.Intro && !it.locked) it.copy(stage = ChangeStage.OldPhone) else it }
    }

    /** One digit from the keypad, routed to the page that is open. */
    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ChangeStage.OldPhone -> _state.update { it.copy(oldDigits = PhoneInput.append(it.oldDigits, digit)) }
            ChangeStage.OldPin -> onPinDigit(digit, current)
            ChangeStage.NewPhone -> _state.update { it.copy(newDigits = PhoneInput.append(it.newDigits, digit)) }
            ChangeStage.Code -> onCodeDigit(digit, current)
            ChangeStage.Intro, ChangeStage.Email -> Unit
        }
    }

    fun onBackspace() {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ChangeStage.OldPhone -> _state.update { it.copy(oldDigits = PhoneInput.backspace(it.oldDigits)) }
            ChangeStage.OldPin -> {
                pin.backspace()
                _state.update { it.copy(filled = pin.length, pinError = null) }
            }
            ChangeStage.NewPhone -> _state.update { it.copy(newDigits = PhoneInput.backspace(it.newDigits)) }
            ChangeStage.Code -> _state.update { it.copy(code = it.code.dropLast(1), codeError = null) }
            ChangeStage.Intro, ChangeStage.Email -> Unit
        }
    }

    fun clearPhone() {
        _state.update {
            when {
                it.locked -> it
                it.stage == ChangeStage.OldPhone -> it.copy(oldDigits = "")
                it.stage == ChangeStage.NewPhone -> it.copy(newDigits = "")
                else -> it
            }
        }
    }

    /** "Next" on the old-number page. The number is checked by the server together with the password. */
    fun submitOldPhone() {
        if (!_state.value.canSubmitOld) return
        wipePin()
        _state.update { it.copy(stage = ChangeStage.OldPin, filled = 0, pinError = null) }
    }

    /** "Next" on the new-number page. The same number as the old one never reaches the server. */
    fun submitNewPhone() {
        if (!_state.value.canSubmitNew) return
        _state.update { it.copy(stage = ChangeStage.Email, emailError = false) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.take(EmailInput.MAX_LENGTH), emailError = false) }
    }

    /** Sends (or resends) the code for the new number to the typed address. */
    fun sendCode() {
        val current = _state.value
        if (current.locked || current.secondsLeft > 0) return
        if (current.stage != ChangeStage.Email && current.stage != ChangeStage.Code) return
        if (!EmailInput.isValid(current.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        if (!current.newValid || current.sameNumber) return
        val phone = current.newDigits
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true, code = "", codeError = null) }
        scope.launch {
            when (val result = gateway.otpSend(phone, email)) {
                is BackendResult.Success -> {
                    _state.update { it.copy(busy = false, stage = ChangeStage.Code) }
                    startTimer(result.value.resendAfter)
                    emit(ChangeEvent.ShowToast(DEVICE_CODE_SENT_MESSAGE))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    handleSendFailure(result.error)
                }
            }
        }
    }

    /** Accepts a pasted string only if it holds exactly six digits once spaces and punctuation are removed. */
    fun onCodePaste(raw: String) {
        val current = _state.value
        val digits = raw.filter { it in '0'..'9' }
        if (current.locked || current.stage != ChangeStage.Code || digits.length != OTP_LENGTH) return
        _state.update { it.copy(code = digits, codeError = null) }
        verifyCode(digits)
    }

    /** Back inside the flow. The screen handles the first page (leave). */
    fun stepBack() {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ChangeStage.OldPhone -> _state.update { it.copy(stage = ChangeStage.Intro) }
            ChangeStage.OldPin -> {
                wipePin()
                proof = null
                _state.update { it.copy(stage = ChangeStage.OldPhone, filled = 0, pinError = null) }
            }
            ChangeStage.NewPhone -> {
                wipePin()
                _state.update { it.copy(stage = ChangeStage.OldPin, filled = 0, pinError = null) }
            }
            ChangeStage.Email -> _state.update { it.copy(stage = ChangeStage.NewPhone, emailError = false) }
            ChangeStage.Code ->
                _state.update { it.copy(stage = ChangeStage.Email, code = "", codeError = null) }
            ChangeStage.Intro -> Unit
        }
    }

    fun dismissDialog() {
        val closing = _state.value.dialog
        _state.update { it.copy(dialog = null) }
        if (closing is LoginDialog.Locked || closing == LoginDialog.PhoneNotFound) restart()
    }

    fun dismissPhoneTaken() {
        _state.update { it.copy(phoneTaken = false) }
        if (restartOnDismiss) restart()
    }

    /** Called when the person confirms the "Session expired" box. */
    fun acknowledgeExpired() {
        restart()
    }

    /** Called when the person confirms the "Phone number changed" box. */
    fun confirmChanged() {
        val current = _state.value
        if (!current.finished || confirmed) return
        confirmed = true
        emit(ChangeEvent.Finished(current.newDigits))
    }

    private fun onCodeDigit(digit: Char, current: ChangePhoneUiState) {
        if (current.code.length >= OTP_LENGTH) return
        val code = current.code + digit
        _state.value = current.copy(code = code, codeError = null)
        if (code.length == OTP_LENGTH) verifyCode(code)
    }

    private fun verifyCode(code: String) {
        val current = _state.value
        val phone = current.newDigits
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.otpVerify(phone, code, email)) {
                is BackendResult.Success -> {
                    val received = result.value.proof
                    if (received.isNullOrEmpty()) {
                        _state.update { it.copy(busy = false, code = "") }
                        emit(ChangeEvent.ShowToast(BackendCodes.messageFor(BackendCodes.UNKNOWN)))
                    } else {
                        proof = received
                        proofReceivedAt = clock()
                        timerJob?.cancel()
                        _state.update { it.copy(code = "", codeError = null, secondsLeft = 0) }
                        submit()
                    }
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false, code = "") }
                    handleCodeFailure(result.error)
                }
            }
        }
    }

    private fun onPinDigit(digit: Char, current: ChangePhoneUiState) {
        if (!pin.append(digit)) return
        _state.value = current.copy(filled = pin.length, pinError = null)
        if (pin.isFull) onPinComplete()
    }

    /** The password is held until the end. Without a proof the flow goes on to the new number; with one, it submits. */
    private fun onPinComplete() {
        if (proof != null) {
            submit()
        } else {
            _state.update { it.copy(stage = ChangeStage.NewPhone, filled = 0, pinError = null) }
        }
    }

    private fun submit() {
        val held = proof
        if (held == null || clock() - proofReceivedAt >= PROOF_TTL_MILLIS) {
            expire()
            return
        }
        if (!pin.isFull) {
            returnToPin(null)
            return
        }
        val current = _state.value
        val oldPhone = current.oldDigits
        val newPhone = current.newDigits
        val password = pin.toPinString()
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.changePhone(oldPhone, password, newPhone, held)) {
                is BackendResult.Success -> onChanged()
                is BackendResult.Failure -> onSubmitFailed(result.error)
            }
        }
    }

    private suspend fun onChanged() {
        wipePin()
        proof = null
        timerJob?.cancel()
        gateway.clearLocalAccess()
        _state.update { it.copy(busy = false, finished = true, filled = 0, secondsLeft = 0, code = "") }
    }

    private fun onSubmitFailed(error: BackendError) {
        when (error.code) {
            BackendCodes.PROOF_INVALID -> expire()
            BackendCodes.PHONE_TAKEN -> {
                restartOnDismiss = true
                _state.update { it.copy(busy = false, phoneTaken = true) }
            }
            else -> when (val failure = LoginFailures.classify(error)) {
                is LoginFailure.WrongPin -> returnToPin(LoginFailures.wrongPinText(failure.attemptsLeft))
                is LoginFailure.Locked -> {
                    wipePin()
                    _state.update { it.copy(busy = false, filled = 0, dialog = LoginDialog.Locked(failure.minutes)) }
                }
                LoginFailure.PhoneNotFound -> {
                    wipePin()
                    _state.update { it.copy(busy = false, filled = 0, dialog = LoginDialog.PhoneNotFound) }
                }
                LoginFailure.Suspended -> {
                    returnToPin(null)
                    _state.update { it.copy(dialog = LoginDialog.Suspended) }
                }
                LoginFailure.RateLimited -> {
                    returnToPin(null)
                    _state.update { it.copy(dialog = LoginDialog.RateLimited) }
                }
                LoginFailure.Offline -> {
                    returnToPin(null)
                    emit(ChangeEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
                }
                else -> {
                    returnToPin(null)
                    emit(ChangeEvent.ShowToast(error.message))
                }
            }
        }
    }

    /** Back to the password page with the typed password wiped. The proof stays, so the next entry submits again. */
    private fun returnToPin(message: String?) {
        wipePin()
        timerJob?.cancel()
        _state.update {
            it.copy(
                stage = ChangeStage.OldPin,
                busy = false,
                filled = 0,
                secondsLeft = 0,
                code = "",
                pinError = message,
                pinShake = if (message != null) it.pinShake + 1 else it.pinShake
            )
        }
    }

    private fun expire() {
        wipePin()
        proof = null
        timerJob?.cancel()
        _state.update { it.copy(busy = false, filled = 0, secondsLeft = 0, code = "", sessionExpired = true) }
    }

    /** Back to the first page with the typed old number kept and everything else dropped. */
    private fun restart() {
        wipePin()
        proof = null
        timerJob?.cancel()
        restartOnDismiss = false
        _state.update { ChangePhoneUiState(oldDigits = it.oldDigits) }
    }

    private fun handleCodeFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.INVALID_CODE ->
                _state.update { it.copy(codeError = CodeError.Incorrect, codeShake = it.codeShake + 1) }
            BackendCodes.CODE_EXPIRED -> _state.update { it.copy(codeError = CodeError.Expired) }
            else -> handleFailure(error)
        }
    }

    private fun handleSendFailure(error: BackendError) {
        if (error.code == BackendCodes.PHONE_TAKEN) {
            restartOnDismiss = false
            _state.update { it.copy(stage = ChangeStage.NewPhone, phoneTaken = true) }
        } else {
            handleFailure(error)
        }
    }

    private fun handleFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.RATE_LIMITED -> showDialog(LoginDialog.RateLimited)
            BackendCodes.OTP_DELIVERY_FAILED -> showDialog(LoginDialog.DeliveryFailed)
            BackendCodes.PHONE_NOT_FOUND -> showDialog(LoginDialog.PhoneNotFound)
            BackendCodes.ACCOUNT_SUSPENDED -> showDialog(LoginDialog.Suspended)
            BackendCodes.PROOF_INVALID -> expire()
            BackendCodes.NETWORK -> emit(ChangeEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            else -> emit(ChangeEvent.ShowToast(error.message))
        }
    }

    private fun showDialog(dialog: LoginDialog) {
        _state.update { it.copy(dialog = dialog) }
    }

    private fun wipePin() {
        pin.clear()
        pin = PinBuffer(LOGIN_PIN_LENGTH)
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _state.update {
            it.copy(secondsLeft = if (seconds > 0) seconds else LoginFailures.DEFAULT_RESEND_SECONDS)
        }
        timerJob = scope.launch {
            while (_state.value.secondsLeft > 0) {
                delay(tickMillis)
                _state.update { it.copy(secondsLeft = (it.secondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun emit(event: ChangeEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        pin.clear()
        proof = null
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChangePhoneViewModel(BackendChangePhoneGateway((application as OPayApplication).backend))
            }
        }
    }
}
