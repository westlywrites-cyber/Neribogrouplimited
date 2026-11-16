package com.westly.opay.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay

private val TOAST_ABOVE_KEYPAD = 340.dp

/**
 * Signed-in change of the 4-digit payment PIN: old PIN, new PIN, confirm. The digits are never shown and the
 * screen cannot be captured. [onChanged] returns to Payment Settings, which shows the toast.
 */
@Composable
fun ChangePaymentPinScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberChangePaymentPinViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()

    SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChangePaymentPinEvent.ShowToast -> toast.show(event.message)
                ChangePaymentPinEvent.Changed -> onChanged()
            }
        }
    }

    fun requestBack() {
        if (!viewModel.stepBack()) onBack()
    }

    BackHandler { requestBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            OPayTopBar(
                title = "",
                onBack = { requestBack() },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                PaymentPinPage(
                    title = titleFor(state.step),
                    subtitle = subTextFor(state.step),
                    filled = state.filled,
                    message = state.message,
                    shakeCount = state.shakeCount,
                    showHint = state.step == ChangePinStep.New
                )
            }
            SecureKeypad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                modifier = Modifier.navigationBarsPadding()
            )
        }
        if (state.busy) BusyOverlay()
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
    }
}

@Composable
private fun rememberChangePaymentPinViewModel(): ChangePaymentPinViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ChangePaymentPinViewModel.factory(application) }
    return viewModel<ChangePaymentPinViewModel>(factory = factory)
}

private fun titleFor(step: ChangePinStep): String = when (step) {
    ChangePinStep.Old -> "Enter old payment PIN"
    ChangePinStep.New -> "Set new payment PIN"
    ChangePinStep.Confirm -> "Confirm new payment PIN"
}

private fun subTextFor(step: ChangePinStep): String = when (step) {
    ChangePinStep.Old -> "Enter your current 4-digit payment PIN"
    ChangePinStep.New -> "This 4-digit PIN is used to confirm payments"
    ChangePinStep.Confirm -> "Enter the same 4-digit PIN again"
}
