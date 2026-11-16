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
import com.westly.opay.backend.OkResponse
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.backend.OtpSendRequest
import com.westly.opay.backend.OtpSendResponse
import com.westly.opay.backend.OtpVerifyRequest
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.backend.ResetLoginPinRequest
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

const val PASSWORD_RESET_MESSAGE = "Password reset successfully"
const val WEAK_PASSWORD_MESSAGE = "Choose a stronger password"
const val PASSWORD_MISMATCH_MESSAGE = "The two passwords do not match"
const val SESSION_RESTART_MESSAGE = "Session expired, please start again"

/** The server keeps a proof for 10 minutes; the app stops using it at the same moment. */
const val PROOF_TTL_MILLIS = 600_000L

/** The five pages of the forgot-login-password flow. */
enum class ForgotStage { Phone, Email, Code, NewPin, ConfirmPin }

enum class ForgotPinMessage { Weak, Mismatch }

sealed interface ForgotEvent {
    data class ShowToast(val message: String) : ForgotEvent
    data object ResetDone : ForgotEvent
}

/** What the flow draws. The typed password digits and the proof are never part of it. */
data class ForgotUiState(
    val stage: ForgotStage = ForgotStage.Phone,
    val phoneDigits: String = "",
    val email: String = "",
    val emailError: Boolean = false,
    val code: String = "",
    val codeError: CodeError? = null,
    val codeShake: Int = 0,
    val secondsLeft: Int = 0,
    val filled: Int = 0,
    val pinMessage: ForgotPinMessage? = null,
    val pinShake: Int = 0,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val dialog: LoginDialog? = null,
    val sessionExpired: Boolean = false
) {
    val phoneValid: Boolean get() = PhoneInput.isValid(phoneDigits)
    val phoneError: Boolean get() = PhoneInput.showError(phoneDigits)
    val locked: Boolean get() = busy || finished
    val canSubmitPhone: Boolean get() = phoneValid && !locked
    val canSendCode: Boolean get() = EmailInput.isValid(email) && secondsLeft == 0 && !locked
}

/** The backend and storage calls the forgot-password flow needs. A fake of this is used in unit tests. */
interface ForgotPasswordGateway {
    suspend fun rememberedPhone(): String?
    suspend fun otpSend(phone10: String, email: String): BackendResult<OtpSendResponse>
    suspend fun otpVerify(phone10: String, code: String, email: String): BackendResult<OtpVerifyResponse>
    suspend fun resetLoginPin(phone10: String, proof: String, newPin: String): BackendResult<OkResponse>

    /** Removes the fingerprint token of this device: the server invalidated it with the reset. */
    suspend fun clearLoginBiometricToken()

    /** Remembers the number so the login password page opens for it. */
    suspend fun rememberPhone(phone10: String)
}

class BackendForgotPasswordGateway(private val backend: Backend) : ForgotPasswordGateway {

    override suspend fun rememberedPhone(): String? = withContext(Dispatchers.IO) {
        backend.secureStore.getString(SecureKeys.REMEMBERED_PHONE)?.takeIf { it.isNotBlank() }
    }

    override suspend fun otpSend(phone10: String, email: String): BackendResult<OtpSendResponse> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return backend.edge.otpSend(OtpSendRequest(phone10, OtpPurpose.RESET_LOGIN_PIN, deviceId, email))
    }

    override suspend fun otpVerify(
        phone10: String,
        code: String,
        email: String
    ): BackendResult<OtpVerifyResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            OtpVerifyRequest(
                phone10, OtpPurpose.RESET_LOGIN_PIN, code, identity.deviceId(), identity.deviceName(), email
            )
        }
        return backend.edge.otpVerify(request)
    }

    override suspend fun resetLoginPin(phone10: String, proof: String, newPin: String): BackendResult<OkResponse> {
        if (!backend.isOnline()) return offline()
        return backend.edge.resetLoginPin(ResetLoginPinRequest(phone10, proof, newPin))
    }

    override suspend fun clearLoginBiometricToken() {
        withContext(Dispatchers.IO) { backend.secureStore.remove(SecureKeys.DEVICE_TOKEN_LOGIN) }
    }

    override suspend fun rememberPhone(phone10: String) {
        withContext(Dispatchers.IO) {
            val store = backend.secureStore
            val previous = store.getString(SecureKeys.REMEMBERED_PHONE)
            store.putString(SecureKeys.REMEMBERED_PHONE, phone10)
            if (previous != phone10) store.remove(SecureKeys.REMEMBERED_NAME_MASKED)
        }
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/**
 * Runs C5: phone, email for the code, code, new login password, confirm. Scoped to the screen's back stack entry.
 * The proof from the code check lives only in a private field and the typed digits only in [PinBuffer]s; neither is
 * part of the UI state, saved for process death, written to storage or logged, and both are dropped when the flow
 * ends or the screen goes away.
 */
class ForgotPasswordViewModel(
    private val gateway: ForgotPasswordGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tickMillis: Long = 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    private val _events = Channel<ForgotEvent>(Channel.BUFFERED)
    val events: Flow<ForgotEvent> = _events.receiveAsFlow()

    private var timerJob: Job? = null
    private var proof: String? = null
    private var proofReceivedAt = 0L
    private var entry = PinBuffer(LOGIN_PIN_LENGTH)
    private var first: PinBuffer? = null

    init {
        scope.launch {
            val remembered = gateway.rememberedPhone()
            if (remembered != null && PhoneInput.isValid(remembered)) {
                _state.update { if (it.phoneDigits.isEmpty()) it.copy(phoneDigits = remembered) else it }
            }
        }
    }

    /** One digit from the keypad, routed to the page that is open. */
    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ForgotStage.Phone -> _state.update { it.copy(phoneDigits = PhoneInput.append(it.phoneDigits, digit)) }
            ForgotStage.Email -> Unit
            ForgotStage.Code -> onCodeDigit(digit, current)
            ForgotStage.NewPin, ForgotStage.ConfirmPin -> onPinDigit(digit, current)
        }
    }

    fun onBackspace() {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ForgotStage.Phone -> _state.update { it.copy(phoneDigits = PhoneInput.backspace(it.phoneDigits)) }
            ForgotStage.Email -> Unit
            ForgotStage.Code -> _state.update { it.copy(code = it.code.dropLast(1), codeError = null) }
            ForgotStage.NewPin, ForgotStage.ConfirmPin -> {
                entry.backspace()
                _state.update { it.copy(filled = entry.length, pinMessage = null) }
            }
        }
    }

    fun clearPhone() {
        _state.update { if (it.locked || it.stage != ForgotStage.Phone) it else it.copy(phoneDigits = "") }
    }

    /** "Next" on the phone page. The number is checked by the server when the code is requested. */
    fun submitPhone() {
        if (!_state.value.canSubmitPhone) return
        _state.update { it.copy(stage = ForgotStage.Email, emailError = false) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.take(EmailInput.MAX_LENGTH), emailError = false) }
    }

    /** Sends (or resends) the code to the typed address. */
    fun sendCode() {
        val current = _state.value
        if (current.locked || current.secondsLeft > 0) return
        if (!EmailInput.isValid(current.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        val phone = current.phoneDigits
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true, code = "", codeError = null) }
        scope.launch {
            when (val result = gateway.otpSend(phone, email)) {
                is BackendResult.Success -> {
                    _state.update { it.copy(busy = false, stage = ForgotStage.Code) }
                    startTimer(result.value.resendAfter)
                    emit(ForgotEvent.ShowToast(DEVICE_CODE_SENT_MESSAGE))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    /** Accepts a pasted string only if it holds exactly six digits once spaces and punctuation are removed. */
    fun onCodePaste(raw: String) {
        val current = _state.value
        val digits = raw.filter { it in '0'..'9' }
        if (current.locked || current.stage != ForgotStage.Code || digits.length != OTP_LENGTH) return
        _state.update { it.copy(code = digits, codeError = null) }
        verifyCode(digits)
    }

    /** Back inside the flow. The screen handles the first page (leave) and the new-password page (ask first). */
    fun stepBack() {
        val current = _state.value
        if (current.locked) return
        when (current.stage) {
            ForgotStage.Email -> _state.update { it.copy(stage = ForgotStage.Phone, emailError = false) }
            ForgotStage.Code ->
                _state.update { it.copy(stage = ForgotStage.Email, code = "", codeError = null) }
            ForgotStage.ConfirmPin -> {
                wipePins()
                _state.update { it.copy(stage = ForgotStage.NewPin, filled = 0, pinMessage = null) }
            }
            ForgotStage.Phone, ForgotStage.NewPin -> Unit
        }
    }

    fun dismissDialog() {
        val closing = _state.value.dialog
        _state.update { it.copy(dialog = null) }
        if (closing == LoginDialog.PhoneNotFound) restart()
    }

    /** Called when the person confirms the "Session expired" box. */
    fun acknowledgeExpired() {
        restart()
    }

    private fun onCodeDigit(digit: Char, current: ForgotUiState) {
        if (current.code.length >= OTP_LENGTH) return
        val code = current.code + digit
        _state.value = current.copy(code = code, codeError = null)
        if (code.length == OTP_LENGTH) verifyCode(code)
    }

    private fun verifyCode(code: String) {
        val current = _state.value
        val phone = current.phoneDigits
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.otpVerify(phone, code, email)) {
                is BackendResult.Success -> {
                    val received = result.value.proof
                    if (received.isNullOrEmpty()) {
                        _state.update { it.copy(busy = false, code = "") }
                        emit(ForgotEvent.ShowToast(BackendCodes.messageFor(BackendCodes.UNKNOWN)))
                    } else {
                        proof = received
                        proofReceivedAt = clock()
                        timerJob?.cancel()
                        wipePins()
                        _state.update {
                            it.copy(
                                busy = false,
                                stage = ForgotStage.NewPin,
                                code = "",
                                codeError = null,
                                secondsLeft = 0,
                                filled = 0,
                                pinMessage = null
                            )
                        }
                    }
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false, code = "") }
                    handleCodeFailure(result.error)
                }
            }
        }
    }

    private fun onPinDigit(digit: Char, current: ForgotUiState) {
        if (!entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, pinMessage = null)
        if (entry.isFull) onEntryComplete()
    }

    private fun onEntryComplete() {
        if (_state.value.stage == ForgotStage.NewPin) {
            if (entry.isTrivial()) {
                rejectNewPin(ForgotPinMessage.Weak)
            } else {
                first = entry
                entry = PinBuffer(LOGIN_PIN_LENGTH)
                _state.update { it.copy(stage = ForgotStage.ConfirmPin, filled = 0, pinMessage = null) }
            }
            return
        }
        val original = first
        if (original == null || !original.matches(entry)) rejectNewPin(ForgotPinMessage.Mismatch) else submit()
    }

    private fun submit() {
        val held = proof
        if (held == null || clock() - proofReceivedAt >= PROOF_TTL_MILLIS) {
            expire()
            return
        }
        val phone = _state.value.phoneDigits
        val pin = entry.toPinString()
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.resetLoginPin(phone, held, pin)) {
                is BackendResult.Success -> onReset(phone)
                is BackendResult.Failure -> onSubmitFailed(result.error)
            }
        }
    }

    private suspend fun onReset(phone: String) {
        wipePins()
        proof = null
        timerJob?.cancel()
        gateway.clearLoginBiometricToken()
        gateway.rememberPhone(phone)
        _state.update { it.copy(busy = false, finished = true, filled = 0, secondsLeft = 0) }
        emit(ForgotEvent.ShowToast(PASSWORD_RESET_MESSAGE))
        emit(ForgotEvent.ResetDone)
    }

    private fun onSubmitFailed(error: BackendError) {
        when (error.code) {
            BackendCodes.PROOF_INVALID -> expire()
            BackendCodes.INVALID_INPUT -> rejectNewPin(ForgotPinMessage.Weak)
            else -> {
                entry.clear()
                _state.update { it.copy(busy = false, filled = 0) }
                handleFailure(error)
            }
        }
    }

    /** Back to the new-password page with an error line and a shake. Both entries are wiped; the proof stays. */
    private fun rejectNewPin(message: ForgotPinMessage) {
        wipePins()
        _state.update {
            it.copy(
                stage = ForgotStage.NewPin,
                filled = 0,
                busy = false,
                pinMessage = message,
                pinShake = it.pinShake + 1
            )
        }
    }

    private fun expire() {
        wipePins()
        proof = null
        timerJob?.cancel()
        _state.update { it.copy(busy = false, filled = 0, secondsLeft = 0, sessionExpired = true) }
    }

    /** Back to the first page with the typed number kept and everything else dropped. */
    private fun restart() {
        wipePins()
        proof = null
        timerJob?.cancel()
        _state.update { ForgotUiState(phoneDigits = it.phoneDigits) }
    }

    private fun handleCodeFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.INVALID_CODE ->
                _state.update { it.copy(codeError = CodeError.Incorrect, codeShake = it.codeShake + 1) }
            BackendCodes.CODE_EXPIRED -> _state.update { it.copy(codeError = CodeError.Expired) }
            else -> handleFailure(error)
        }
    }

    private fun handleFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.RATE_LIMITED -> showDialog(LoginDialog.RateLimited)
            BackendCodes.OTP_DELIVERY_FAILED -> showDialog(LoginDialog.DeliveryFailed)
            BackendCodes.PHONE_NOT_FOUND -> showDialog(LoginDialog.PhoneNotFound)
            BackendCodes.ACCOUNT_SUSPENDED -> showDialog(LoginDialog.Suspended)
            BackendCodes.PROOF_INVALID -> expire()
            BackendCodes.NETWORK -> emit(ForgotEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            else -> emit(ForgotEvent.ShowToast(error.message))
        }
    }

    private fun showDialog(dialog: LoginDialog) {
        _state.update { it.copy(dialog = dialog) }
    }

    private fun wipePins() {
        entry.clear()
        first?.clear()
        first = null
        entry = PinBuffer(LOGIN_PIN_LENGTH)
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

    private fun emit(event: ForgotEvent) {
        _events.trySend(event)
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
                ForgotPasswordViewModel(BackendForgotPasswordGateway((application as OPayApplication).backend))
            }
        }
    }
}
