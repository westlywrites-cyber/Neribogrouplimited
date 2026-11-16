package com.westly.opay.feature.statement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.PostgrestStatementGateway
import com.westly.opay.backend.RowMapping
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinBuffer
import com.westly.opay.feature.settings.wrongPaymentPinText
import com.westly.opay.feature.transfer.PayErrors
import com.westly.opay.feature.transfer.PayFailure
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DateTarget { Start, End }

data class DownloadStatementUiState(
    val frame: TimeFrame? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val account: String? = null,
    val fileType: StatementFileType? = null,
    val email: String = "",
    val emailSheet: Boolean = false,
    val showAddress: Boolean = true,
    val picker: DateTarget? = null,
    val accountDialog: Boolean = false,
    val fileSheet: Boolean = false,
    val confirm: Boolean = false,
    val pin: Boolean = false,
    val filled: Int = 0,
    val busy: Boolean = false,
    val pinMessage: String? = null,
    val shakeCount: Int = 0,
    val notice: String? = null
) {
    val ready: Boolean
        get() = start != null && end != null && account != null && fileType != null && email.isNotEmpty()
}

sealed interface DownloadStatementEvent {
    data class Toast(val text: String) : DownloadStatementEvent
    data object Started : DownloadStatementEvent
}

/** Runs the Download Statement form, the email address, the confirm dialog and the payment PIN check. */
class DownloadStatementViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OPayApplication
    private val engine = StatementEngine.get(app)
    private val gateway = PostgrestStatementGateway(app.backend.provider, app.backend::isOnline)
    private val today: LocalDate get() = LocalDate.now(StatementFormat.ZONE)
    private val entry = PinBuffer(PAYMENT_PIN_LENGTH)

    private val _state = MutableStateFlow(DownloadStatementUiState())
    val state: StateFlow<DownloadStatementUiState> = _state.asStateFlow()

    private val _events = Channel<DownloadStatementEvent>(Channel.BUFFERED)
    val events: Flow<DownloadStatementEvent> = _events.receiveAsFlow()

    private var created: LocalDate? = null

    init {
        engine.purge()
        loadCreated()
        loadEmail()
    }

    private fun toast(text: String) {
        _events.trySend(DownloadStatementEvent.Toast(text))
    }

    private fun loadCreated() {
        viewModelScope.launch {
            val result = gateway.accountCreatedAt()
            if (result is BackendResult.Success) {
                created = Instant.ofEpochMilli(RowMapping.epochMillis(result.value))
                    .atZone(StatementFormat.ZONE).toLocalDate()
            }
        }
    }

    private fun loadEmail() {
        val id = app.repository.profile.value.id
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { engine.emails.get(id) }
            _state.update { it.copy(email = if (StatementEmail.isValid(saved)) saved else "") }
        }
    }

    private fun withCreated(action: (LocalDate) -> Unit) {
        val date = created
        if (date == null) {
            toast("Loading account details. Try again in a moment.")
            loadCreated()
        } else {
            action(date)
        }
    }

    fun selectFrame(frame: TimeFrame) {
        if (frame == TimeFrame.Custom) {
            _state.update { it.copy(frame = TimeFrame.Custom) }
            return
        }
        withCreated { date ->
            val period = StatementPeriods.preset(frame, today, date)
            if (period == null) {
                toast(StatementPeriods.NO_PERIOD)
            } else {
                _state.update { it.copy(frame = frame, start = period.start, end = period.end) }
            }
        }
    }

    fun openPicker(target: DateTarget) {
        withCreated { date ->
            if (boundsFor(target, date) == null) {
                toast(StatementPeriods.NO_PERIOD)
            } else {
                _state.update { it.copy(frame = TimeFrame.Custom, picker = target) }
            }
        }
    }

    /** The dates the open picker may take; null when no date is allowed. */
    fun pickerBounds(target: DateTarget): DateBounds? = created?.let { boundsFor(target, it) }

    private fun boundsFor(target: DateTarget, date: LocalDate): DateBounds? {
        val current = _state.value
        return if (target == DateTarget.Start) {
            StatementPeriods.startBounds(today, date, current.end)
        } else {
            StatementPeriods.endBounds(today, date, current.start)
        }
    }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
    }

    fun onDatePicked(target: DateTarget, date: LocalDate) {
        val accountDate = created ?: return
        val current = _state.value
        val problem = if (target == DateTarget.Start) {
            StatementPeriods.checkStart(date, today, accountDate, current.end)
        } else {
            StatementPeriods.checkEnd(date, today, current.start)
        }
        if (problem != null) {
            toast(problem)
            return
        }
        _state.update {
            if (target == DateTarget.Start) it.copy(start = date, picker = null)
            else it.copy(end = date, picker = null)
        }
    }

    fun showAccountDialog(show: Boolean) {
        _state.update { it.copy(accountDialog = show) }
    }

    fun chooseAccount() {
        val number = app.repository.profile.value.accountNumber
        _state.update { it.copy(account = number, accountDialog = false) }
    }

    fun accountNumber(): String = app.repository.profile.value.accountNumber

    fun showFileSheet(show: Boolean) {
        _state.update { it.copy(fileSheet = show) }
    }

    fun setFileType(type: StatementFileType) {
        _state.update { it.copy(fileType = type, fileSheet = false) }
    }

    fun showEmailSheet(show: Boolean) {
        _state.update { it.copy(emailSheet = show) }
    }

    /** Keeps the address on this phone and closes the sheet. An address that is not valid is ignored. */
    fun saveEmail(raw: String) {
        val email = StatementEmail.clean(raw)
        if (!StatementEmail.isValid(email)) return
        if (app.repository.profile.value.id.isEmpty()) {
            toast("Loading account details. Try again in a moment.")
            return
        }
        engine.saveEmail(email)
        _state.update { it.copy(email = email, emailSheet = false) }
    }

    fun setShowAddress(value: Boolean) {
        _state.update { it.copy(showAddress = value) }
    }

    fun onContinue() {
        if (_state.value.ready) _state.update { it.copy(confirm = true) }
    }

    fun closeConfirm() {
        _state.update { it.copy(confirm = false) }
    }

    fun onGenerate() {
        entry.clear()
        _state.update { it.copy(confirm = false, pin = true, filled = 0, pinMessage = null, busy = false) }
    }

    fun cancelPin() {
        if (_state.value.busy) return
        entry.clear()
        _state.update { it.copy(pin = false, filled = 0, pinMessage = null) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.pin || current.busy || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, pinMessage = null)
        if (entry.isFull) verify()
    }

    fun onBackspace() {
        val current = _state.value
        if (!current.pin || current.busy) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, pinMessage = null)
    }

    private fun verify() {
        val pin = entry.toPinString()
        entry.clear()
        if (!app.backend.isOnline()) {
            reject(BackendCodes.messageFor(BackendCodes.NETWORK), shake = false)
            return
        }
        _state.update { it.copy(busy = true, filled = 0) }
        viewModelScope.launch {
            when (val result = app.backend.rpc.verifyPaymentPin(pin)) {
                is BackendResult.Success ->
                    if (result.value) begin() else reject(wrongPaymentPinText(null), shake = true)
                is BackendResult.Failure -> failed(result.error)
            }
        }
    }

    private fun failed(error: BackendError) {
        when (val failure = PayErrors.classify(error, isOpayTransfer = false, viaFingerprint = false)) {
            is PayFailure.WrongPin -> reject(wrongPaymentPinText(failure.attemptsLeft), shake = true)
            PayFailure.Offline -> reject(BackendCodes.messageFor(BackendCodes.NETWORK), shake = false)
            PayFailure.FingerprintOff -> reject(wrongPaymentPinText(null), shake = true)
            is PayFailure.Notice -> _state.update {
                it.copy(busy = false, pin = false, filled = 0, pinMessage = null, notice = failure.text)
            }
        }
    }

    private fun reject(message: String, shake: Boolean) {
        _state.update {
            it.copy(
                busy = false,
                filled = 0,
                pinMessage = message,
                shakeCount = if (shake) it.shakeCount + 1 else it.shakeCount
            )
        }
    }

    private fun begin() {
        val current = _state.value
        val start = current.start
        val end = current.end
        val account = current.account
        if (start == null || end == null || account == null) {
            reject("Check the details and try again", shake = false)
            return
        }
        if (current.email.isEmpty()) {
            reject(StatementErrors.INVALID_EMAIL, shake = false)
            return
        }
        val request = StatementRequest(
            accountNumber = account,
            holderName = app.repository.profile.value.fullName,
            email = current.email,
            period = StatementPeriod(start, end),
            showAddress = current.showAddress,
            requestedAtMillis = System.currentTimeMillis()
        )
        engine.start(request)
        _state.update { it.copy(busy = false, pin = false, filled = 0) }
        _events.trySend(DownloadStatementEvent.Started)
    }

    override fun onCleared() {
        entry.clear()
    }
}
