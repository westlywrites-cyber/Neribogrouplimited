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
import com.westly.opay.backend.OtpSendRequest
import com.westly.opay.backend.OtpSendResponse
import com.westly.opay.backend.OtpVerifyRequest
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.backendCall
import com.westly.opay.feature.login.DEVICE_CODE_SENT_MESSAGE
import com.westly.opay.feature.login.LoginDialog
import com.westly.opay.feature.login.LoginFailures
import com.westly.opay.feature.login.LoginFormat
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.OTP_LENGTH
import io.github.jan.supabase.postgrest.postgrest
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

/** The two pages of D7: the address the code is emailed to, then the code. */
enum class VerifyPhoneStage { Email, Code }

/** What D7 draws. The proof is never part of it. */
data class VerifyPhoneUiState(
    val stage: VerifyPhoneStage = VerifyPhoneStage.Email,
    val maskedPhone: String = "",
    val email: String = "",
    val emailError: Boolean = false,
    val code: String = "",
    val codeError: CodeError? = null,
    val codeShake: Int = 0,
    val secondsLeft: Int = 0,
    val busy: Boolean = false,
    val dialog: LoginDialog? = null
) {
    val canSendCode: Boolean get() = EmailInput.isValid(email) && secondsLeft == 0 && !busy
}

sealed interface VerifyPhoneEvent {
    data class ShowToast(val message: String) : VerifyPhoneEvent

    /** The code was accepted. [proof] is single use and valid for 10 minutes; the receiver keeps it in memory only. */
    data class Verified(val proof: String) : VerifyPhoneEvent
}

/** The backend calls D7 needs. A fake of this is used in unit tests. */
interface VerifyPhoneGateway {
    /** The signed-in user's own number as `phone10`. */
    suspend fun ownPhone(): BackendResult<String>

    suspend fun otpSend(phone10: String, purpose: String, email: String): BackendResult<OtpSendResponse>

    suspend fun otpVerify(
        phone10: String,
        purpose: String,
        code: String,
        email: String
    ): BackendResult<OtpVerifyResponse>
}

class BackendVerifyPhoneGateway(private val backend: Backend) : VerifyPhoneGateway {

    override suspend fun ownPhone(): BackendResult<String> {
        if (!backend.isOnline()) return offline()
        return when (
            val result = backendCall {
                backend.provider.client.postgrest.from("profiles").select().decodeSingleOrNull<ProfileRow>()
            }
        ) {
            is BackendResult.Success -> {
                val phone = result.value?.phone
                if (phone.isNullOrBlank()) {
                    BackendResult.Failure(
                        BackendError(BackendCodes.NOT_AUTHENTICATED, BackendCodes.messageFor(BackendCodes.NOT_AUTHENTICATED))
                    )
                } else {
                    BackendResult.Success(phone)
                }
            }
            is BackendResult.Failure -> result
        }
    }

    override suspend fun otpSend(phone10: String, purpose: String, email: String): BackendResult<OtpSendResponse> {
        if (!backend.isOnline()) return offline()
        val deviceId = withContext(Dispatchers.IO) { backend.deviceIdentity.deviceId() }
        return backend.edge.otpSend(OtpSendRequest(phone10, purpose, deviceId, email))
    }

    override suspend fun otpVerify(
        phone10: String,
        purpose: String,
        code: String,
        email: String
    ): BackendResult<OtpVerifyResponse> {
        if (!backend.isOnline()) return offline()
        val identity = backend.deviceIdentity
        val request = withContext(Dispatchers.IO) {
            OtpVerifyRequest(phone10, purpose, code, identity.deviceId(), identity.deviceName(), email)
        }
        return backend.edge.otpVerify(request)
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/**
 * D7, the reusable verify-phone step for a signed-in user. [purpose] is `settings` or `reset_payment_pin`. The code
 * is requested for the user's own number, then checked; the proof comes back through [VerifyPhoneEvent.Verified]
 * and is never kept here. Nothing about the flow is saved for process death, written to storage or logged.
 */
class VerifyPhoneViewModel(
    private val purpose: String,
    private val gateway: VerifyPhoneGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tickMillis: Long = 1000L
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(VerifyPhoneUiState())
    val state: StateFlow<VerifyPhoneUiState> = _state.asStateFlow()

    private val _events = Channel<VerifyPhoneEvent>(Channel.BUFFERED)
    val events: Flow<VerifyPhoneEvent> = _events.receiveAsFlow()

    private var timerJob: Job? = null
    private var phone10: String? = null

    init {
        scope.launch { loadPhone(report = false) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.take(EmailInput.MAX_LENGTH), emailError = false) }
    }

    /** Sends (or resends) the code to the typed address for the user's own number. */
    fun sendCode() {
        val current = _state.value
        if (current.busy || current.secondsLeft > 0) return
        if (!EmailInput.isValid(current.email)) {
            _state.update { it.copy(emailError = true) }
            return
        }
        val email = EmailInput.normalize(current.email)
        _state.update { it.copy(busy = true, code = "", codeError = null) }
        scope.launch {
            val phone = loadPhone(report = true)
            if (phone == null) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            when (val result = gateway.otpSend(phone, purpose, email)) {
                is BackendResult.Success -> {
                    _state.update { it.copy(busy = false, stage = VerifyPhoneStage.Code) }
                    startTimer(result.value.resendAfter)
                    emit(VerifyPhoneEvent.ShowToast(DEVICE_CODE_SENT_MESSAGE))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.busy || current.stage != VerifyPhoneStage.Code || digit !in '0'..'9') return
        if (current.code.length >= OTP_LENGTH) return
        val code = current.code + digit
        _state.value = current.copy(code = code, codeError = null)
        if (code.length == OTP_LENGTH) verifyCode(code)
    }

    fun onBackspace() {
        val current = _state.value
        if (current.busy || current.stage != VerifyPhoneStage.Code) return
        _state.update { it.copy(code = it.code.dropLast(1), codeError = null) }
    }

    /** Accepts a pasted string only if it holds exactly six digits once spaces and punctuation are removed. */
    fun onCodePaste(raw: String) {
        val current = _state.value
        val digits = raw.filter { it in '0'..'9' }
        if (current.busy || current.stage != VerifyPhoneStage.Code || digits.length != OTP_LENGTH) return
        _state.update { it.copy(code = digits, codeError = null) }
        verifyCode(digits)
    }

    /** Back from the code page returns to the address page. Returns false when the screen should leave instead. */
    fun stepBack(): Boolean {
        val current = _state.value
        if (current.busy) return true
        if (current.stage == VerifyPhoneStage.Email) return false
        timerJob?.cancel()
        _state.update { it.copy(stage = VerifyPhoneStage.Email, code = "", codeError = null, secondsLeft = 0) }
        return true
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    private fun verifyCode(code: String) {
        val email = EmailInput.normalize(_state.value.email)
        _state.update { it.copy(busy = true) }
        scope.launch {
            val phone = loadPhone(report = true)
            if (phone == null) {
                _state.update { it.copy(busy = false, code = "") }
                return@launch
            }
            when (val result = gateway.otpVerify(phone, purpose, code, email)) {
                is BackendResult.Success -> {
                    val proof = result.value.proof
                    if (proof.isNullOrEmpty()) {
                        _state.update { it.copy(busy = false, code = "") }
                        emit(VerifyPhoneEvent.ShowToast(BackendCodes.messageFor(BackendCodes.UNKNOWN)))
                    } else {
                        reset()
                        emit(VerifyPhoneEvent.Verified(proof))
                    }
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(busy = false, code = "") }
                    handleCodeFailure(result.error)
                }
            }
        }
    }

    /** Back to a clean first page. The user's number stays loaded. */
    private fun reset() {
        timerJob?.cancel()
        _state.value = VerifyPhoneUiState(maskedPhone = _state.value.maskedPhone)
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
            BackendCodes.RATE_LIMITED -> _state.update { it.copy(dialog = LoginDialog.RateLimited) }
            BackendCodes.OTP_DELIVERY_FAILED -> _state.update { it.copy(dialog = LoginDialog.DeliveryFailed) }
            BackendCodes.ACCOUNT_SUSPENDED -> _state.update { it.copy(dialog = LoginDialog.Suspended) }
            BackendCodes.NETWORK -> emit(VerifyPhoneEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            else -> emit(VerifyPhoneEvent.ShowToast(error.message))
        }
    }

    /** Reads the user's own number once. With [report] a failure is shown as a toast. */
    private suspend fun loadPhone(report: Boolean): String? {
        phone10?.let { return it }
        return when (val result = gateway.ownPhone()) {
            is BackendResult.Success -> {
                phone10 = result.value
                _state.update { it.copy(maskedPhone = LoginFormat.maskedPhone(result.value)) }
                result.value
            }
            is BackendResult.Failure -> {
                if (report) handleFailure(result.error)
                null
            }
        }
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

    private fun emit(event: VerifyPhoneEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application, purpose: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VerifyPhoneViewModel(purpose, BackendVerifyPhoneGateway((application as OPayApplication).backend))
            }
        }
    }
}
