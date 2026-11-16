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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.login.LoginDialogHost
import com.westly.opay.feature.login.SESSION_RESTART_MESSAGE
import com.westly.opay.feature.register.BusyOverlay

private val TOAST_ABOVE_KEYPAD = 340.dp
private const val LEAVE_TITLE = "Reminder"
private const val LEAVE_MESSAGE = "Do you want to leave? Your progress on this screen will be lost."

/**
 * Forgot payment PIN: D7 (code for purpose `reset_payment_pin`), new PIN, confirm. The PIN pages cannot be
 * captured and never show the digits. [onResetDone] returns to Payment Settings, which shows the toast.
 */
@Composable
fun ForgotPaymentPinScreen(
    onBack: () -> Unit,
    onResetDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberForgotPaymentPinViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val currentOnResetDone by rememberUpdatedState(onResetDone)
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val pinStage = state.stage != ForgotPinStage.Verify

    if (pinStage) SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ForgotPaymentPinEvent.ShowToast -> toast.show(event.message)
                ForgotPaymentPinEvent.ResetDone -> currentOnResetDone()
            }
        }
    }

    fun requestBack() {
        if (state.stage == ForgotPinStage.NewPin) confirmLeave = true else viewModel.stepBack()
    }

    BackHandler(enabled = pinStage && !state.locked) { requestBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        if (pinStage) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                OPayTopBar(
                    title = "",
                    onBack = if (state.locked) null else ({ requestBack() }),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    val confirming = state.stage == ForgotPinStage.ConfirmPin
                    PaymentPinPage(
                        title = if (confirming) "Confirm new payment PIN" else "Set new payment PIN",
                        subtitle = if (confirming) {
                            "Enter the same 4-digit PIN again"
                        } else {
                            "This 4-digit PIN is used to confirm payments"
                        },
                        filled = state.filled,
                        message = state.message,
                        shakeCount = state.shakeCount,
                        showHint = !confirming
                    )
                }
                SecureKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        } else {
            VerifyPhoneScreen(
                purpose = OtpPurpose.RESET_PAYMENT_PIN,
                onBack = onBack,
                onVerified = viewModel::onProof
            )
        }
        if (confirmLeave) {
            OPayDialog(
                title = LEAVE_TITLE,
                message = LEAVE_MESSAGE,
                primaryText = "Leave",
                onPrimary = {
                    confirmLeave = false
                    onBack()
                },
                onDismiss = { confirmLeave = false },
                secondaryText = "Stay",
                onSecondary = { confirmLeave = false }
            )
        }
        if (state.sessionExpired) {
            val acknowledge = {
                viewModel.acknowledgeExpired()
                onBack()
            }
            OPayDialog(
                title = SESSION_RESTART_MESSAGE,
                message = null,
                primaryText = "OK",
                onPrimary = acknowledge,
                onDismiss = acknowledge
            )
        }
        if (state.locked) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = {}
        )
        OPayToastHost(state = toast, bottomPadding = if (pinStage) TOAST_ABOVE_KEYPAD else 90.dp)
    }
}

@Composable
private fun rememberForgotPaymentPinViewModel(): ForgotPaymentPinViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ForgotPaymentPinViewModel.factory(application) }
    return viewModel<ForgotPaymentPinViewModel>(factory = factory)
}
