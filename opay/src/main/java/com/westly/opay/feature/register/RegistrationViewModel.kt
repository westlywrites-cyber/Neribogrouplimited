package com.westly.opay.feature.register

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.OtpVerifyResponse
import com.westly.opay.navigation.Routes
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
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

const val OTP_LENGTH = 6
const val DEFAULT_RESEND_SECONDS = 58
const val CODE_SENT_MESSAGE = "Verification code has been sent to your mobile phone"

enum class CodeError { Incorrect, Expired }

enum class RegistrationDialog { PhoneTaken, RateLimited, DeliveryFailed, Verified }

sealed interface RegistrationEvent {
    data class ShowToast(val message: String) : RegistrationEvent
    data object OpenCode : RegistrationEvent
    data class GoTo(val route: String) : RegistrationEvent
}

data class RegistrationUiState(
    val phoneDigits: String = "",
    val termsAccepted: Boolean = false,
    val email: String = "",
    val sending: Boolean = false,
    val code: String = "",
    val verifying: Boolean = false,
    val secondsLeft: Int = 0,
    val codeError: CodeError? = null,
    val shakeCount: Int = 0,
    val dialog: RegistrationDialog? = null
) {
    val busy: Boolean get() = sending || verifying
    val phoneValid: Boolean get() = PhoneInput.isValid(phoneDigits)
    val phoneError: Boolean get() = PhoneInput.showError(phoneDigits)
    val canSubmitPhone: Boolean get() = phoneValid && termsAccepted && !busy
}

/** The send state of the questionnaire (reference B8 to B14). */
data class QuestionnaireUiState(
    val submitting: Boolean = false,
    val invalidAnswers: Boolean = false
)

/** Shared by the phone screen, the code screen and the questionnaire, so it is scoped to the Activity. */
class RegistrationViewModel(
    private val gateway: RegistrationGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tickMillis: Long = 1000L,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(RegistrationUiState(email = savedState.get<String>(KEY_EMAIL).orEmpty()))
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()

    private val _address = MutableStateFlow(readAddress())
    /** The address half of the registration form. Survives process death through the saved state. */
    val address: StateFlow<AddressForm> = _address.asStateFlow()

    private val _pep = MutableStateFlow(readPep())
    /** The politically-exposed-person answers. Survives process death through the saved state. */
    val pep: StateFlow<PepForm> = _pep.asStateFlow()

    private val _income = MutableStateFlow(readIncome())
    /** The occupation and income answers. Survives process death through the saved state. */
    val income: StateFlow<IncomeForm> = _income.asStateFlow()

    private val _questionnaire = MutableStateFlow(QuestionnaireUiState())
    val questionnaire: StateFlow<QuestionnaireUiState> = _questionnaire.asStateFlow()

    /** Fixed to Nigeria; no other country is offered. */
    val nationality: String
        get() = savedState.get<String>(KEY_NATIONALITY) ?: NATIONALITY_NIGERIA

    private val _events = Channel<RegistrationEvent>(Channel.BUFFERED)
    val events: Flow<RegistrationEvent> = _events.receiveAsFlow()

    private var timerJob: Job? = null
    private var lastSentPhone: String? = null
    private var lastSentEmail: String? = null

    init {
        gateway.setEmail(_state.value.email.ifEmpty { null })
    }
    private var pendingRoute: String? = null

    fun onPhoneDigit(digit: Char) {
        _state.update { if (it.busy) it else it.copy(phoneDigits = PhoneInput.append(it.phoneDigits, digit)) }
    }

    fun onPhoneBackspace() {
        _state.update { if (it.busy) it else it.copy(phoneDigits = PhoneInput.backspace(it.phoneDigits)) }
    }

    fun clearPhone() {
        _state.update { if (it.busy) it else it.copy(phoneDigits = "") }
    }

    fun setTermsAccepted(accepted: Boolean) {
        _state.update { it.copy(termsAccepted = accepted) }
    }

    /**
     * Stores the address typed on the Privacy Policy page. An empty value clears it. Returns false, and changes
     * nothing, when the text is not a valid address.
     */
    fun saveEmail(raw: String): Boolean {
        val value = EmailInput.normalize(raw)
        if (value.isNotEmpty() && !EmailInput.isValid(value)) return false
        savedState[KEY_EMAIL] = value
        _state.update { it.copy(email = value) }
        gateway.setEmail(value.ifEmpty { null })
        return true
    }

    fun submitPhone() {
        val current = _state.value
        if (!current.canSubmitPhone) return
        val phone = current.phoneDigits
        if (lastSentPhone == phone && lastSentEmail == current.email && current.secondsLeft > 0) {
            _state.update { it.copy(code = "", codeError = null) }
            emit(RegistrationEvent.OpenCode)
            return
        }
        _state.update { it.copy(sending = true) }
        scope.launch {
            when (val result = gateway.otpSend(phone)) {
                is BackendResult.Success -> {
                    lastSentPhone = phone
                    lastSentEmail = current.email
                    _state.update { it.copy(sending = false, code = "", codeError = null) }
                    startTimer(result.value.resendAfter)
                    emit(RegistrationEvent.OpenCode)
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(sending = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    fun onCodeDigit(digit: Char) {
        val current = _state.value
        if (current.busy || current.code.length >= OTP_LENGTH) return
        val code = current.code + digit
        _state.value = current.copy(code = code, codeError = null)
        if (code.length == OTP_LENGTH) verify(code)
    }

    fun onCodeBackspace() {
        _state.update { if (it.busy) it else it.copy(code = it.code.dropLast(1), codeError = null) }
    }

    /** Accepts a pasted string only if it holds exactly six digits once spaces and punctuation are removed. */
    fun onCodePaste(raw: String) {
        val digits = raw.filter { it in '0'..'9' }
        if (_state.value.busy || digits.length != OTP_LENGTH) return
        _state.update { it.copy(code = digits, codeError = null) }
        verify(digits)
    }

    fun resend() {
        val current = _state.value
        if (current.busy || current.secondsLeft > 0) return
        val phone = current.phoneDigits
        _state.update { it.copy(sending = true, code = "", codeError = null) }
        scope.launch {
            when (val result = gateway.otpSend(phone)) {
                is BackendResult.Success -> {
                    lastSentPhone = phone
                    lastSentEmail = current.email
                    _state.update { it.copy(sending = false) }
                    startTimer(result.value.resendAfter)
                    emit(RegistrationEvent.ShowToast(CODE_SENT_MESSAGE))
                }
                is BackendResult.Failure -> {
                    _state.update { it.copy(sending = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    fun setState(value: String) = updateAddress { it.copy(state = value, lga = "") }

    fun setLga(value: String) = updateAddress { it.copy(lga = value) }

    fun setArea(value: String) = updateAddress { it.copy(area = value) }

    fun setFullAddress(value: String) =
        updateAddress { it.copy(fullAddress = value.take(ADDRESS_MAX_LENGTH)) }

    fun setLandmark(value: String) =
        updateAddress { it.copy(landmark = value.take(LANDMARK_MAX_LENGTH)) }

    fun setPermanentSame(value: Boolean) = updateAddress { it.copy(permanentSame = value) }

    fun setPermanentState(value: String) =
        updateAddress { it.copy(permanentState = value, permanentLga = "") }

    fun setPermanentLga(value: String) = updateAddress { it.copy(permanentLga = value) }

    fun setPermanentArea(value: String) = updateAddress { it.copy(permanentArea = value) }

    fun setPermanentFullAddress(value: String) =
        updateAddress { it.copy(permanentFullAddress = value.take(ADDRESS_MAX_LENGTH)) }

    /** "No, I'm not" on the first PEP question. Forgets any details typed earlier. */
    fun answerNotPep() = updatePep { PepForm(answered = false) }

    /** "Yes, I am" on the first PEP question, after the confirmation box. */
    fun answerPep() = updatePep { it.copy(answered = true) }

    fun setPepHolder(value: Boolean) = updatePep { it.copy(holder = value) }

    fun setPepOffice(value: String) = updatePep { it.copy(office = value) }

    /** [iso] is a date written as yyyy-MM-dd. */
    fun setPepStartDate(iso: String) = updatePep { it.copy(startDate = iso) }

    fun setPepHoldsPosition(value: Boolean) = updatePep { it.copy(holdsPosition = value) }

    fun setPepRelated(value: Boolean) = updatePep { it.copy(related = value) }

    fun setOccupation(value: String) = updateIncome { it.copy(occupation = value) }

    fun setIncomeRange(value: String) = updateIncome { it.copy(incomeRange = value) }

    fun setOtherIncome(value: Boolean) = updateIncome {
        if (value) it.copy(otherIncome = true) else it.copy(otherIncome = false, otherSource = "", expectedAnnual = "")
    }

    fun setOtherSource(value: String) = updateIncome { it.copy(otherSource = value) }

    fun setExpectedAnnual(value: String) = updateIncome { it.copy(expectedAnnual = value) }

    fun dismissInvalidAnswers() {
        _questionnaire.update { it.copy(invalidAnswers = false) }
    }

    /**
     * Sends the whole registration form with `submit_kyc_form`. Does nothing while a send is running or while
     * any answer is missing, so a double tap never sends twice.
     */
    fun submitQuestionnaire(today: LocalDate = LocalDate.now()) {
        if (_questionnaire.value.submitting) return
        val form = KycFormBuilder.build(_address.value, nationality, _pep.value, _income.value, today) ?: return
        _questionnaire.update { it.copy(submitting = true) }
        scope.launch {
            when (val result = gateway.submitKyc(form)) {
                is BackendResult.Success -> continueAfterSubmit()
                is BackendResult.Failure -> handleSubmitFailure(result.error)
            }
        }
    }

    /** The address and nationality fields under the names of the `submit_kyc_form` contract. */
    fun addressContractFields(): JsonObject = _address.value.toContractFields(nationality)

    /** Signs the user out, forgets the address typed so far and then calls [onDone]. */
    fun leaveRegistration(onDone: () -> Unit) {
        scope.launch {
            gateway.signOut()
            updateAddress { AddressForm() }
            updatePep { PepForm() }
            updateIncome { IncomeForm() }
            _questionnaire.value = QuestionnaireUiState()
            onDone()
        }
    }

    fun dismissDialog() {
        _state.update { if (it.dialog == RegistrationDialog.Verified) it else it.copy(dialog = null) }
    }

    fun onVerifiedDialogDone() {
        if (_state.value.dialog != RegistrationDialog.Verified) return
        val route = pendingRoute ?: return
        pendingRoute = null
        _state.update { it.copy(dialog = null) }
        emit(RegistrationEvent.GoTo(route))
    }

    private fun verify(code: String) {
        val phone = _state.value.phoneDigits
        _state.update { it.copy(verifying = true) }
        scope.launch {
            when (val result = gateway.otpVerify(phone, code)) {
                is BackendResult.Success -> completeSignIn(phone, result.value)
                is BackendResult.Failure -> {
                    _state.update { it.copy(verifying = false, code = "") }
                    handleVerifyFailure(result.error)
                }
            }
        }
    }

    private suspend fun completeSignIn(phone: String, response: OtpVerifyResponse) {
        val session = response.session
        val registration = response.registrationState
        if (session == null || registration == null) {
            _state.update { it.copy(verifying = false, code = "") }
            handleFailure(BackendError(BackendCodes.UNKNOWN, BackendCodes.messageFor(BackendCodes.UNKNOWN)))
            return
        }
        when (val signedIn = gateway.signIn(session)) {
            is BackendResult.Failure -> {
                _state.update { it.copy(verifying = false, code = "") }
                handleFailure(signedIn.error)
                return
            }
            is BackendResult.Success -> Unit
        }
        gateway.rememberPhone(phone)
        timerJob?.cancel()
        lastSentPhone = null
        lastSentEmail = null
        val route = RegistrationRouting.nextRoute(registration)
        if (RegistrationRouting.showsVerifiedDialog(registration)) {
            pendingRoute = route
            _state.update { it.copy(verifying = false, secondsLeft = 0, dialog = RegistrationDialog.Verified) }
        } else {
            _state.update { it.copy(verifying = false, secondsLeft = 0) }
            emit(RegistrationEvent.GoTo(route))
        }
    }

    private suspend fun continueAfterSubmit() {
        val route = when (val state = gateway.registrationState()) {
            is BackendResult.Success -> RegistrationRouting.nextRoute(state.value)
            is BackendResult.Failure -> Routes.RegisterPin
        }
        _questionnaire.update { it.copy(submitting = false) }
        emit(RegistrationEvent.GoTo(route))
    }

    private suspend fun handleSubmitFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.STATE_INVALID -> continueAfterSubmit()
            BackendCodes.INVALID_INPUT ->
                _questionnaire.update { it.copy(submitting = false, invalidAnswers = true) }
            BackendCodes.NETWORK -> {
                _questionnaire.update { it.copy(submitting = false) }
                emit(RegistrationEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            }
            else -> {
                _questionnaire.update { it.copy(submitting = false) }
                emit(RegistrationEvent.ShowToast(error.message))
            }
        }
    }

    private fun handleVerifyFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.INVALID_CODE ->
                _state.update { it.copy(codeError = CodeError.Incorrect, shakeCount = it.shakeCount + 1) }
            BackendCodes.CODE_EXPIRED ->
                _state.update { it.copy(codeError = CodeError.Expired) }
            else -> handleFailure(error)
        }
    }

    private fun handleFailure(error: BackendError) {
        when (error.code) {
            BackendCodes.PHONE_TAKEN -> showDialog(RegistrationDialog.PhoneTaken)
            BackendCodes.RATE_LIMITED -> showDialog(RegistrationDialog.RateLimited)
            BackendCodes.OTP_DELIVERY_FAILED -> showDialog(RegistrationDialog.DeliveryFailed)
            BackendCodes.NETWORK ->
                emit(RegistrationEvent.ShowToast(BackendCodes.messageFor(BackendCodes.NETWORK)))
            else -> emit(RegistrationEvent.ShowToast(error.message))
        }
    }

    private fun showDialog(dialog: RegistrationDialog) {
        _state.update { it.copy(dialog = dialog) }
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _state.update { it.copy(secondsLeft = if (seconds > 0) seconds else DEFAULT_RESEND_SECONDS) }
        timerJob = scope.launch {
            while (_state.value.secondsLeft > 0) {
                delay(tickMillis)
                _state.update { it.copy(secondsLeft = (it.secondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun emit(event: RegistrationEvent) {
        _events.trySend(event)
    }

    private fun updateAddress(transform: (AddressForm) -> AddressForm) {
        val next = transform(_address.value)
        _address.value = next
        writeAddress(next)
    }

    private fun updatePep(transform: (PepForm) -> PepForm) {
        val next = transform(_pep.value)
        _pep.value = next
        writePep(next)
    }

    private fun updateIncome(transform: (IncomeForm) -> IncomeForm) {
        val next = transform(_income.value)
        _income.value = next
        writeIncome(next)
    }

    private fun readPep(): PepForm = PepForm(
        answered = triStateToBoolean(savedState.get<String>(KEY_PEP_ANSWERED)),
        holder = savedState.get<Boolean>(KEY_PEP_HOLDER) ?: true,
        office = savedState.get<String>(KEY_PEP_OFFICE).orEmpty(),
        startDate = savedState.get<String>(KEY_PEP_START).orEmpty(),
        holdsPosition = savedState.get<Boolean>(KEY_PEP_HOLDS) ?: false,
        related = savedState.get<Boolean>(KEY_PEP_RELATED) ?: false
    )

    private fun writePep(form: PepForm) {
        savedState[KEY_PEP_ANSWERED] = booleanToTriState(form.answered)
        savedState[KEY_PEP_HOLDER] = form.holder
        savedState[KEY_PEP_OFFICE] = form.office
        savedState[KEY_PEP_START] = form.startDate
        savedState[KEY_PEP_HOLDS] = form.holdsPosition
        savedState[KEY_PEP_RELATED] = form.related
    }

    private fun readIncome(): IncomeForm = IncomeForm(
        occupation = savedState.get<String>(KEY_OCCUPATION).orEmpty(),
        incomeRange = savedState.get<String>(KEY_INCOME_RANGE).orEmpty(),
        otherIncome = triStateToBoolean(savedState.get<String>(KEY_OTHER_INCOME)),
        otherSource = savedState.get<String>(KEY_OTHER_SOURCE).orEmpty(),
        expectedAnnual = savedState.get<String>(KEY_EXPECTED_ANNUAL).orEmpty()
    )

    private fun writeIncome(form: IncomeForm) {
        savedState[KEY_OCCUPATION] = form.occupation
        savedState[KEY_INCOME_RANGE] = form.incomeRange
        savedState[KEY_OTHER_INCOME] = booleanToTriState(form.otherIncome)
        savedState[KEY_OTHER_SOURCE] = form.otherSource
        savedState[KEY_EXPECTED_ANNUAL] = form.expectedAnnual
    }

    private fun booleanToTriState(value: Boolean?): String = when (value) {
        true -> TRI_YES
        false -> TRI_NO
        null -> ""
    }

    private fun triStateToBoolean(value: String?): Boolean? = when (value) {
        TRI_YES -> true
        TRI_NO -> false
        else -> null
    }

    private fun readAddress(): AddressForm {
        fun text(key: String): String = savedState.get<String>(key).orEmpty()
        return AddressForm(
            state = text(KEY_STATE),
            lga = text(KEY_LGA),
            area = text(KEY_AREA),
            fullAddress = text(KEY_FULL_ADDRESS),
            landmark = text(KEY_LANDMARK),
            permanentSame = savedState.get<Boolean>(KEY_PERMANENT_SAME) ?: true,
            permanentState = text(KEY_PERMANENT_STATE),
            permanentLga = text(KEY_PERMANENT_LGA),
            permanentArea = text(KEY_PERMANENT_AREA),
            permanentFullAddress = text(KEY_PERMANENT_FULL_ADDRESS)
        )
    }

    private fun writeAddress(form: AddressForm) {
        savedState[KEY_STATE] = form.state
        savedState[KEY_LGA] = form.lga
        savedState[KEY_AREA] = form.area
        savedState[KEY_FULL_ADDRESS] = form.fullAddress
        savedState[KEY_LANDMARK] = form.landmark
        savedState[KEY_PERMANENT_SAME] = form.permanentSame
        savedState[KEY_PERMANENT_STATE] = form.permanentState
        savedState[KEY_PERMANENT_LGA] = form.permanentLga
        savedState[KEY_PERMANENT_AREA] = form.permanentArea
        savedState[KEY_PERMANENT_FULL_ADDRESS] = form.permanentFullAddress
        savedState[KEY_NATIONALITY] = NATIONALITY_NIGERIA
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        private const val KEY_EMAIL = "signup_email"
        private const val KEY_STATE = "address_state"
        private const val KEY_LGA = "address_lga"
        private const val KEY_AREA = "address_area"
        private const val KEY_FULL_ADDRESS = "address_full"
        private const val KEY_LANDMARK = "address_landmark"
        private const val KEY_PERMANENT_SAME = "address_permanent_same"
        private const val KEY_PERMANENT_STATE = "address_permanent_state"
        private const val KEY_PERMANENT_LGA = "address_permanent_lga"
        private const val KEY_PERMANENT_AREA = "address_permanent_area"
        private const val KEY_PERMANENT_FULL_ADDRESS = "address_permanent_full"
        private const val KEY_NATIONALITY = "nationality"
        private const val KEY_PEP_ANSWERED = "pep_answered"
        private const val KEY_PEP_HOLDER = "pep_holder"
        private const val KEY_PEP_OFFICE = "pep_office"
        private const val KEY_PEP_START = "pep_start_date"
        private const val KEY_PEP_HOLDS = "pep_holds_position"
        private const val KEY_PEP_RELATED = "pep_related"
        private const val KEY_OCCUPATION = "income_occupation"
        private const val KEY_INCOME_RANGE = "income_range"
        private const val KEY_OTHER_INCOME = "income_other"
        private const val KEY_OTHER_SOURCE = "income_other_source"
        private const val KEY_EXPECTED_ANNUAL = "income_expected_annual"
        private const val TRI_YES = "yes"
        private const val TRI_NO = "no"

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RegistrationViewModel(
                    gateway = BackendRegistrationGateway((application as OPayApplication).backend),
                    savedState = createSavedStateHandle()
                )
            }
        }
    }
}
