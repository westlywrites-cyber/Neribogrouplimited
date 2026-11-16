package com.westly.opay.feature.transfer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.PayAuth
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenStatus
import com.westly.opay.core.security.TokenUnavailableException
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TransferDraft
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinBuffer
import com.westly.opay.feature.settings.FINGERPRINT_CHANGED_TOAST
import com.westly.opay.feature.settings.wrongPaymentPinText
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
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

private const val MIN_PROCESSING_MILLIS = 1_600L
private const val TOAST_MILLIS = 1_500L

/** E1 is the details view (with the fingerprint prompt on top when it applies); E2 is the PIN keypad. */
enum class PayStage { Details, Pin }

/** A box that must be confirmed. [leavesFlow] is true when the outcome of the payment is not known. */
data class PayNotice(val text: String, val leavesFlow: Boolean)

/** What the pay sheet draws. The typed digits and the device token are never part of it. */
data class PayUiState(
    val stage: PayStage = PayStage.Details,
    val filled: Int = 0,
    val message: String? = null,
    val shakeCount: Int = 0,
    val processing: Boolean = false,
    val fingerprintAvailable: Boolean = false,
    val toast: String? = null,
    val notice: PayNotice? = null
) {
    /** While true the sheet ignores every input, so a payment can never be sent twice. */
    val locked: Boolean get() = processing || notice != null
}

sealed interface PayEvent {
    /** The screen shows the system fingerprint prompt for [cipher] and reports back through `onPrompt…`. */
    data class LaunchPrompt(val cipher: Cipher) : PayEvent

    /** The server accepted the transfer. [transaction] is the row it returned. */
    data class Paid(val transaction: Transaction) : PayEvent
}

/**
 * Runs the pay sheet: the fingerprint prompt first when this device has a pay token, the 4-digit payment PIN
 * otherwise, then exactly one server call per attempt. While a payment is processing, or a box is open, every
 * input is ignored. The typed digits live only in a [PinBuffer] and the device token only in a local variable
 * until it is sent; neither is part of the UI state, saved or logged.
 */
class PayViewModel(
    private val submit: suspend (TransferDraft, PayAuth) -> Transaction,
    private val vault: TokenVault,
    private val draft: () -> TransferDraft,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val minProcessingMillis: Long = MIN_PROCESSING_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    private val _events = Channel<PayEvent>(Channel.BUFFERED)
    val events: Flow<PayEvent> = _events.receiveAsFlow()

    private val entry = PinBuffer(PAYMENT_PIN_LENGTH)
    private var toastJob: Job? = null
    private var opened = 0

    /** The sheet came up. A valid pay token starts the fingerprint prompt; otherwise the details stay. */
    fun onSheetOpened() {
        entry.clear()
        toastJob?.cancel()
        _state.value = PayUiState()
        opened += 1
        val mine = opened
        scope.launch {
            when (withContext(io) { vault.status(VaultPurpose.Pay) }) {
                TokenStatus.Valid -> promptFingerprint(mine)
                TokenStatus.Invalidated -> showToast(FINGERPRINT_CHANGED_TOAST)
                TokenStatus.Absent -> Unit
            }
        }
    }

    /** The sheet went away. A payment that was accepted keeps its processing screen until the screen leaves. */
    fun onSheetClosed() {
        opened += 1
        entry.clear()
        if (!_state.value.processing) {
            toastJob?.cancel()
            _state.value = PayUiState()
        }
    }

    /** The "Pay" button on the details view. */
    fun onPayClicked() {
        if (_state.value.locked) return
        if (_state.value.fingerprintAvailable) requestFingerprint() else onUsePin()
    }

    /** "Use Payment PIN", the "Use PIN" button of the prompt, or a prompt that could not be used. */
    fun onUsePin() {
        if (_state.value.locked) return
        showPinStage()
    }

    /** "Use Fingerprint" on the PIN keypad. */
    fun onUseFingerprint() {
        if (_state.value.locked) return
        entry.clear()
        _state.update { it.copy(stage = PayStage.Details, filled = 0, message = null) }
        requestFingerprint()
    }

    fun onBackFromPin() {
        if (_state.value.locked) return
        entry.clear()
        _state.update { it.copy(stage = PayStage.Details, filled = 0, message = null) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.locked || current.stage != PayStage.Pin || !entry.append(digit)) return
        _state.value = current.copy(filled = entry.length, message = null)
        if (entry.isFull) {
            val pin = entry.toPinString()
            entry.clear()
            pay(PayAuth.Pin(pin))
        }
    }

    fun onBackspace() {
        val current = _state.value
        if (current.locked || current.stage != PayStage.Pin) return
        entry.backspace()
        _state.value = current.copy(filled = entry.length, message = null)
    }

    /** The fingerprint was accepted and unlocked [cipher], which decrypts the stored pay token. */
    fun onPromptSuccess(cipher: Cipher?) {
        if (_state.value.locked) return
        scope.launch {
            val token = if (cipher == null) {
                null
            } else {
                try {
                    withContext(io) { vault.decryptToken(VaultPurpose.Pay, cipher) }
                } catch (e: TokenUnavailableException) {
                    null
                }
            }
            if (token == null) {
                turnFingerprintOff()
            } else {
                pay(PayAuth.Biometric(token))
            }
        }
    }

    /** E3: the person closed the prompt. The toast shows and the PIN keypad opens. */
    fun onPromptCancelled() {
        if (_state.value.locked) return
        showToast(PAY_FINGERPRINT_CANCELED_TOAST)
        showPinStage()
    }

    /** A sensor error. The PIN keypad opens without a message. */
    fun onPromptError() {
        if (_state.value.locked) return
        showPinStage()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun requestFingerprint() {
        val mine = opened
        scope.launch { promptFingerprint(mine) }
    }

    private suspend fun promptFingerprint(mine: Int) {
        val cipher = try {
            withContext(io) { vault.newDecryptCipher(VaultPurpose.Pay) }
        } catch (e: TokenUnavailableException) {
            _state.update { it.copy(stage = PayStage.Pin, filled = 0, fingerprintAvailable = false) }
            if (e.invalidated) showToast(FINGERPRINT_CHANGED_TOAST)
            return
        }
        if (mine != opened) return
        _state.update { it.copy(stage = PayStage.Details, fingerprintAvailable = true) }
        _events.trySend(PayEvent.LaunchPrompt(cipher))
    }

    private fun showPinStage() {
        entry.clear()
        _state.update { it.copy(stage = PayStage.Pin, filled = 0, message = null) }
    }

    private fun pay(auth: PayAuth) {
        val before = _state.value
        if (before.processing || before.notice != null) return
        _state.value = before.copy(processing = true, message = null)
        val pending = draft()
        scope.launch {
            val started = clock()
            val outcome: Result<Transaction> = try {
                Result.success(submit(pending, auth))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val remaining = minProcessingMillis - (clock() - started)
            if (remaining > 0) delay(remaining)
            val paid = outcome.getOrNull()
            if (paid != null) {
                onPaid(paid)
            } else {
                onFailed(outcome.exceptionOrNull(), pending, auth is PayAuth.Biometric)
            }
        }
    }

    private fun onPaid(transaction: Transaction) {
        entry.clear()
        SelectedTransaction.select(transaction)
        _events.trySend(PayEvent.Paid(transaction))
    }

    private suspend fun onFailed(error: Throwable?, pending: TransferDraft, viaFingerprint: Boolean) {
        val failure = if (error is BackendError) {
            PayErrors.classify(error, pending.isOpayTransfer, viaFingerprint)
        } else {
            PayFailure.Notice(PAY_UNKNOWN_TEXT, leavesFlow = true)
        }
        when (failure) {
            is PayFailure.WrongPin -> _state.update {
                it.copy(
                    processing = false,
                    stage = PayStage.Pin,
                    filled = 0,
                    message = wrongPaymentPinText(failure.attemptsLeft),
                    shakeCount = it.shakeCount + 1
                )
            }
            PayFailure.Offline -> {
                _state.update { it.copy(processing = false, filled = 0) }
                showToast(BackendCodes.messageFor(BackendCodes.NETWORK))
            }
            PayFailure.FingerprintOff -> turnFingerprintOff()
            is PayFailure.Notice -> _state.update {
                it.copy(processing = false, filled = 0, notice = PayNotice(failure.text, failure.leavesFlow))
            }
        }
    }

    /** The server or the phone no longer accepts the pay token: it is deleted here and the PIN keypad opens. */
    private suspend fun turnFingerprintOff() {
        withContext(io) { vault.delete(VaultPurpose.Pay) }
        entry.clear()
        _state.update {
            it.copy(processing = false, stage = PayStage.Pin, filled = 0, fingerprintAvailable = false)
        }
        showToast(PAY_FINGERPRINT_OFF_TOAST)
    }

    private fun showToast(text: String) {
        toastJob?.cancel()
        _state.update { it.copy(toast = text) }
        toastJob = scope.launch {
            delay(TOAST_MILLIS)
            _state.update { it.copy(toast = null) }
        }
    }

    override fun onCleared() {
        entry.clear()
        scope.cancel()
    }

    companion object {
        fun factory(application: Application, draft: () -> TransferDraft): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = application as OPayApplication
                    PayViewModel(
                        submit = { transfer, auth -> app.repository.submitTransfer(transfer, auth) },
                        vault = KeystoreVault(app.backend.secureStore),
                        draft = draft
                    )
                }
            }
    }
}
