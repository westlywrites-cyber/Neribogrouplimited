package com.westly.opay.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendResult
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenStatus
import com.westly.opay.core.security.TokenVault
import com.westly.opay.core.security.VaultPurpose
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PaymentSettingsEvent {
    data class ShowToast(val message: String) : PaymentSettingsEvent

    /** Fingerprint payment may be turned on: the screen opens the turn-on flow (D7, D8). */
    data object StartEnable : PaymentSettingsEvent
}

/**
 * The "Pay with Fingerprint" switch on Payment Settings. Its state is derived from the vault (a stored pay token
 * whose key still works), never from a separate flag, so it goes off when the token is turned off here, removed by
 * a PIN reset or invalidated by a new fingerprint. Turning it on is handed to the turn-on flow; turning it off is
 * done here.
 */
class PaymentSettingsViewModel(
    private val gateway: FingerprintPayGateway,
    private val vault: TokenVault,
    private val canAuthenticate: () -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _fingerprintOn = MutableStateFlow(false)
    val fingerprintOn: StateFlow<Boolean> = _fingerprintOn.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _noFingerprintDialog = MutableStateFlow(false)
    val noFingerprintDialog: StateFlow<Boolean> = _noFingerprintDialog.asStateFlow()

    private val _events = Channel<PaymentSettingsEvent>(Channel.BUFFERED)
    val events: Flow<PaymentSettingsEvent> = _events.receiveAsFlow()

    /** Re-reads the vault. Called whenever the screen comes to the front. */
    fun refresh() {
        scope.launch {
            val status = withContext(io) { vault.status(VaultPurpose.Pay) }
            _fingerprintOn.value = status == TokenStatus.Valid
            if (status == TokenStatus.Invalidated) emit(PaymentSettingsEvent.ShowToast(FINGERPRINT_CHANGED_TOAST))
        }
    }

    fun onToggle(on: Boolean) {
        if (_busy.value || on == _fingerprintOn.value) return
        when {
            !on -> disable()
            canAuthenticate() -> emit(PaymentSettingsEvent.StartEnable)
            else -> _noFingerprintDialog.value = true
        }
    }

    fun dismissNoFingerprintDialog() {
        _noFingerprintDialog.value = false
    }

    private fun disable() {
        _busy.value = true
        scope.launch {
            val error = (gateway.disable() as? BackendResult.Failure)?.error
            if (error == null || error.code == BackendCodes.BIOMETRIC_NOT_ENABLED) {
                withContext(io) { vault.delete(VaultPurpose.Pay) }
                _fingerprintOn.value = false
                emit(PaymentSettingsEvent.ShowToast(FINGERPRINT_PAY_DISABLED_TOAST))
            } else {
                emit(PaymentSettingsEvent.ShowToast(error.message))
            }
            _busy.value = false
        }
    }

    private fun emit(event: PaymentSettingsEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val backend = (application as OPayApplication).backend
                PaymentSettingsViewModel(
                    gateway = BackendFingerprintPayGateway(backend),
                    vault = KeystoreVault(backend.secureStore),
                    canAuthenticate = { BiometricGate.canAuthenticate(application) }
                )
            }
        }
    }
}
