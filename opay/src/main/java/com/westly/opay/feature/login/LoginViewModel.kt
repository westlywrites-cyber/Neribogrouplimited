package com.westly.opay.feature.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.RegistrationState
import com.westly.opay.backend.SessionPayload
import com.westly.opay.backend.SignInResponse
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.OTP_LENGTH
import com.westly.opay.feature.register.PhoneInput
import com.westly.opay.feature.register.RegistrationRouting
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

const val LOGIN_PIN_LENGTH = 6
const val SESSION_EXPIRED_MESSAGE = "Your session has expired, please log in again"
const val DEVICE_CODE_SENT_MESSAGE = "Verification code has been sent to your email"

/** Device verification first asks for an address to send the code to, then for the code. */
enum class DeviceStage { Email, Code }

sealed interface LoginDialog {
    data class Locked(val minutes: Int) : LoginDialog
    data object PhoneNotFound : LoginDialog
    data object Suspended : LoginDialog
    data object RateLimited : LoginDialog
    data object DeliveryFailed : LoginDialog
}

sealed interface LoginEvent {
    data class ShowToast(val message: String) : LoginEvent
    data class SignedIn(val route: String) : LoginEvent
    data object OpenPin : LoginEvent
    data object OpenPhone : LoginEvent
    data object OpenDeviceCode : LoginEvent
    data class ResumeRegistration(val phone: String) : LoginEvent
}

data class LoginUiState(
    val phoneDigits: String = "",
    val phone: String = "",
    val name: String? = null,
    val pin: String = "",
    val incorrect: Boolean = false,
    val attemptsLeft: Int? = null,
    val shakeCount: Int = 0,
    val busy: Boolean = false,
    val dialog: LoginDialog? = null,
    val expiredNotice: Boolean = false,
    val stage: DeviceStage = DeviceStage.Email,
    val email: String = "",
    val emailError: Boolean = false,
    val code: String = "",
    val codeError: CodeError? = null,
    val codeShake: Int = 0,
    val secondsLeft: Int = 0,
    val fingerprintAvailable: Boolean = false,
    val notice: String? = null
) {
    val phoneValid: Boolean get() = PhoneInput.isValid(phoneDigits)
    val phoneError: Boolean get() = PhoneInput.showError(phoneDigits)
    val canSubmitPhone: Boolean get() = phoneValid && !busy
    val canSendCode: Boolean get() = EmailInput.isValid(email) && secondsLeft == 0 && !busy
}

/**
 * Drives the login screens (phone, PIN, device code). Activity-scoped so the number typed on the phone screen
 * reaches the PIN screen and the device-code screen.
 */
class LoginViewModel(
    private val gateway: LoginGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tickMillis: Long = 1000L
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private var timerJob: Job? = null

    /** True once the user has tapped "Use password" in this launch, so the login entry stays on the PIN screen. */
    private var passwordChosen = false

    fun onPhoneDigit(digit: Char) {
        _state.update { if (it.busy) it else it.copy(phoneDigits = PhoneInput.append(it.phoneDigits, digit)) }
    }

    fun onPhoneBackspace() {
        _state.update { if (it.busy) it else it.copy(phoneDigits = PhoneInput.backspace(it.phoneDigits)) }
    }

    fun clearPhone() {
        _state.update { if (it.busy) it else it.copy(phoneDigits = "") }
    }

    /** "Next" on the phone screen: the number is only checked by the server together with the PIN. */
    fun submitPhone() {
        val current = _state.value
        if (!current.canSubmitPhone) return
        val digits = current.phoneDigits
        scope.launch {
            val remembered = gateway.rememberedPhone()
            val name = if (remembered == digits) gateway.rememberedName() else null
            val fingerprint = remembered == digits && gateway.hasFingerprintToken()
            _state.update {
                it.copy(
                    phone = digits,
                    name = name,
                    pin = "",
                    incorrect = false,
                    attemptsLeft = null,
                    fingerprintAvailable = fingerprint
                )
            }
            emit(LoginEvent.OpenPin)
        }
    }

    /** Called when the PIN screen opens. Loads the remembered number unless one was just typed. */
    fun preparePin() {
        if (_state.value.phone.isNotEmpty()) return
        scope.launch {
            val phone = gateway.rememberedPhone()
            if (phone == null) {
                emit(LoginEvent.OpenPhone)
                return@launch
            }
            val name = gateway.rememberedName()
            val fingerprint = gateway.hasFingerprintToken()
            _state.update {
                if (it.phone.isEmpty()) it.copy(phone = phone, name = name, fingerprintAvailable = fingerprint) else it
            }
        }
    }

    /** "Switch account": forgets the remembered number and goes back to the phone screen. */
    fun switchAccount() {
        if (_state.value.busy) return
        scope.launch {
            gateway.forgetRemembered()
            timerJob?.cancel()
            _state.value = LoginUiState()
            emit(LoginEvent.OpenPhone)
        }
    }

    fun onPinDigit(digit: Char) {
        val current = _state.value
        if (current.busy || current.phone.isEmpty() || current.pin.length >= LOGIN_PIN_LENGTH) return
        val pin = current.pin + digit
        _state.value = current.copy(pin = pin, incorrect = false)
        if (pin.length == LOGIN_PIN_LENGTH) signIn(pin)
    }

    fun onPinBackspace() {
        _state.update { if (it.busy) it else it.copy(pin = it.pin.dropLast(1), incorrect = false) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.take(EmailInput.MAX_LENGTH), emailError = false) }
    }

    /** Sends (or resends) the device-verification code to the typed address. */
    fun sendDeviceCode() {
        val current = _state.value
        if (current.busy || current.secondsLeft > 0) return
        if (!EmailInput.isValid(current.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        val phone = current.phone
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true, code = "", codeError = null) }
        scope.launch {
            when (val result = gateway.otpSend(phone, email)) {
                is BackendResult.Success -> {
                    _state.update { it.copy(busy = false, stage = DeviceStage.Code) }
                    startTimer(result.value.resendAfter)
                    emit(LoginEvent.ShowToast(DEVICE_CODE_SENT_MESSAGE))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    handleDeviceFailure(result.error)
                }
            }
        }
    }

    fun onCodeDigit(digit: Char) {
        val current = _state.value
        if (current.busy || current.code.length >= OTP_LENGTH) return
        val code = current.code + digit
        _state.value = current.copy(code = code, codeError = null)
        if (code.length == OTP_LENGTH) verifyCode(code)
    }

    fun onCodeBackspace() {
        _state.update { if (it.busy) it else it.copy(code = it.code.dropLast(1), codeError = null) }
    }

    /** Accepts a pasted string only if it holds exactly six digits once spaces and punctuation are removed. */
    fun onCodePaste(raw: String) {
        val digits = raw.filter { it in '0'..'9' }
        if (_state.value.busy || digits.length != OTP_LENGTH) return
        _state.update { it.copy(code = digits, codeError = null) }
        verifyCode(digits)
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun consumeExpiredNotice() {
        _state.update { it.copy(expiredNotice = false) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * The fingerprint screen hands over to the PIN screen. The login entry stays on the PIN screen for the rest of
     * this launch, [notice] (when given) is shown there as a toast, and the fingerprint button is re-checked
     * because the screen may have deleted the token.
     */
    fun usePassword(notice: String?) {
        passwordChosen = true
        _state.update { it.copy(notice = notice ?: it.notice) }
        val phone = _state.value.phone
        if (phone.isEmpty()) return
        scope.launch {
            val available = gateway.rememberedPhone() == phone && gateway.hasFingerprintToken()
            _state.update { it.copy(fingerprintAvailable = available) }
        }
    }

    /**
     * The fingerprint screen signed in. Remembers the number and name for the next start, drops typed data and
     * hands [onRoute] the screen the registration state points to.
     */
    fun completeBiometricSignIn(response: SignInResponse, onRoute: (String) -> Unit) {
        scope.launch {
            gateway.rememberedPhone()?.let { gateway.rememberAfterSignIn(it) }
            timerJob?.cancel()
            _state.update {
                it.copy(
                    busy = false,
                    pin = "",
                    code = "",
                    incorrect = false,
                    attemptsLeft = null,
                    secondsLeft = 0,
                    expiredNotice = false,
                    notice = null
                )
            }
            onRoute(RegistrationRouting.nextRoute(response.registrationState))
        }
    }

    /**
     * Ends the session. A normal log out signs out first; an [expired] session is already gone. In both cases the
     * remembered number, device id and biometric tokens stay, all typed data is dropped, and [onRoute] receives the
     * login screen to open (the fingerprint screen when a token exists and the user has not chosen the password in
     * this launch).
     */
    fun leaveSession(expired: Boolean, onRoute: (String) -> Unit) {
        scope.launch {
            if (!expired) gateway.signOut()
            timerJob?.cancel()
            val phone = gateway.rememberedPhone()
            val fingerprintReady = !passwordChosen && gateway.hasFingerprintToken()
            _state.value = LoginUiState(expiredNotice = expired)
            onRoute(LoginRouting.signedOutRoute(phone, fingerprintReady))
        }
    }

    private fun signIn(pin: String) {
        val phone = _state.value.phone
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.login(phone, pin)) {
                is BackendResult.Success ->
                    completeSignIn(phone, result.value.session, result.value.registrationState)
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false, pin = "") }
                    handleLoginFailure(phone, LoginFailures.classify(result.error))
                }
            }
        }
    }

    private fun verifyCode(code: String) {
        val current = _state.value
        val phone = current.phone
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true) }
        scope.launch {
            when (val result = gateway.otpVerify(phone, code, email)) {
                is BackendResult.Success -> {
                    val session = result.value.session
                    val registration = result.value.registrationState
                    if (session == null || registration == null) {
                        _state.update { it.copy(busy = false, code = "") }
                        emit(LoginEvent.ShowToast(BackendCodes.messageFor(BackendCodes.UNKNOWN)))
                    } else {
                        completeSignIn(phone, session, registration)
                    }
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false, code = "") }
                    handleCodeFailure(result.error)
                }
            }
        }
    }

    private suspend fun completeSignIn(phone: String, session: SessionPayload, registration: RegistrationState) {
        when (val signedIn = gateway.signIn(session)) {
            is BackendResult.Failure -> {
                _state.update { it.copy(busy = false, pin = "", code = "") }
                handleLoginFailure(phone, LoginFailures.classify(signedIn.error))
                return
            }
            is BackendResult.Success -> Unit
        }
        gateway.rememberAfterSignIn(phone)
        timerJob?.cancel()
        _state.update {
            it.copy(busy = false, pin = "", code = "", incorrect = false, attemptsLeft = null, secondsLeft = 0)
        }
        emit(LoginEvent.SignedIn(RegistrationRouting.nextRoute(registration)))
    }

    private fun handleLoginFailure(phone: String, failure: LoginFailure) {
        when (failure) {
            is LoginFailure.WrongPin -> _state.update {
                it.copy(incorrect = true, attemptsLeft = failure.attemptsLeft, shakeCount = it.shakeCount + 1)
            }
            is LoginFailure.Locked -> showDialog(LoginDialog.Locked(failure.minutes))
            LoginFailure.PhoneNotFound -> showDialog(LoginDialog.PhoneNotFound)
            LoginFailure.PinNotSet -> emit(LoginEvent.ResumeRegistration(phone))
            LoginFailure.Suspended -> showDialog(LoginDialog.Suspended)
            is LoginFailure.DeviceVerification -> startDeviceVerification(failure.resendAfter)
            LoginFailure.RateLimited -> showDialog(LoginDialog.RateLimited)
            LoginFailure.Offline -> emit(LoginEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            is LoginFailure.Other -> emit(LoginEvent.ShowToast(failure.message))
        }
    }

    private fun handleCodeFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.INVALID_CODE ->
                _state.update { it.copy(codeError = CodeError.Incorrect, codeShake = it.codeShake + 1) }
            BackendCodes.CODE_EXPIRED -> _state.update { it.copy(codeError = CodeError.Expired) }
            else -> handleDeviceFailure(error)
        }
    }

    private fun handleDeviceFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.RATE_LIMITED -> showDialog(LoginDialog.RateLimited)
            BackendCodes.OTP_DELIVERY_FAILED -> showDialog(LoginDialog.DeliveryFailed)
            BackendCodes.PHONE_NOT_FOUND -> showDialog(LoginDialog.PhoneNotFound)
            BackendCodes.ACCOUNT_SUSPENDED -> showDialog(LoginDialog.Suspended)
            BackendCodes.NETWORK -> emit(LoginEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            else -> emit(LoginEvent.ShowToast(error.message))
        }
    }

    private fun startDeviceVerification(resendAfter: Int) {
        _state.update {
            it.copy(stage = DeviceStage.Email, emailError = false, code = "", codeError = null)
        }
        startTimer(resendAfter)
        emit(LoginEvent.OpenDeviceCode)
    }

    private fun showDialog(dialog: LoginDialog) {
        _state.update { it.copy(dialog = dialog) }
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

    private fun emit(event: LoginEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(BackendLoginGateway((application as OPayApplication).backend))
            }
        }
    }
}
