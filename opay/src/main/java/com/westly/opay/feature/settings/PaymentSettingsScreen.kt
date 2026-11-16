package com.westly.opay.feature.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay

/**
 * Payment Settings (reference D6): the payment PIN rows and the fingerprint payment switch. The switch shows the
 * state of the pay token on this device. Turning it off is done here; turning it on first checks that a fingerprint
 * is enrolled and then calls [onFingerprintPayChange] with true to open the turn-on flow. [notice] is a toast text
 * handed back by a finished flow (empty when there is none); [onNoticeShown] clears it.
 */
@Composable
fun PaymentSettingsScreen(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onForgotPin: () -> Unit,
    onFingerprintPayChange: (enabled: Boolean) -> Unit,
    notice: String,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberPaymentSettingsViewModel()
    val fingerprintOn by viewModel.fingerprintOn.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val noFingerprintDialog by viewModel.noFingerprintDialog.collectAsState()
    val toast = rememberOPayToast()
    val currentOnFingerprintPayChange by rememberUpdatedState(onFingerprintPayChange)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentSettingsEvent.ShowToast -> toast.show(event.message)
                PaymentSettingsEvent.StartEnable -> currentOnFingerprintPayChange(true)
            }
        }
    }
    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            toast.show(notice)
            onNoticeShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OPayTopBar(
                title = "Payment Settings",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                SettingsSectionTitle("Payment PIN")
                OPayCard {
                    OPayRow(
                        title = "Change Payment PIN",
                        onClick = onChangePin
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Forgot Payment PIN",
                        onClick = onForgotPin
                    )
                }
                SettingsSectionTitle("Biometrics")
                OPayCard {
                    OPayRow(
                        title = "Pay with Fingerprint",
                        showChevron = false,
                        trailing = {
                            OPayToggle(
                                checked = fingerprintOn,
                                onCheckedChange = viewModel::onToggle
                            )
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (busy) BusyOverlay()
        if (noFingerprintDialog) {
            OPayDialog(
                title = NO_FINGERPRINT_TEXT,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::dismissNoFingerprintDialog,
                onDismiss = viewModel::dismissNoFingerprintDialog
            )
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun rememberPaymentSettingsViewModel(): PaymentSettingsViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { PaymentSettingsViewModel.factory(application) }
    return viewModel<PaymentSettingsViewModel>(factory = factory)
}
