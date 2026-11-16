package com.westly.opay.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.backend.OtpPurpose
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayButton
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
private const val PROMPT_TITLE = "Enable fingerprint payment"
private const val PROMPT_SUBTITLE = "Confirm your fingerprint to turn on fingerprint payment"

/**
 * Turning fingerprint payment on: D7 (code for purpose `settings`), D8 (payment PIN), then the system fingerprint
 * prompt. The PIN page cannot be captured and never shows the digits. [onEnabled] returns to Payment Settings,
 * which shows the toast and the switch in its on position (reference D9).
 */
@Composable
fun FingerprintPayScreen(
    onBack: () -> Unit,
    onEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val activity = LocalContext.current as FragmentActivity
    val viewModel = rememberFingerprintPayViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val currentOnEnabled by rememberUpdatedState(onEnabled)
    val pinStage = state.stage == FingerprintPayStage.Pin

    if (pinStage) SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FingerprintPayEvent.ShowToast -> toast.show(event.message)
                is FingerprintPayEvent.LaunchPrompt -> BiometricGate.authenticate(
                    activity = activity,
                    title = PROMPT_TITLE,
                    subtitle = PROMPT_SUBTITLE,
                    cryptoObject = BiometricPrompt.CryptoObject(event.cipher),
                    onSuccess = { unlocked -> viewModel.onPromptSuccess(unlocked?.cipher) },
                    onError = viewModel::onPromptError,
                    onCancel = viewModel::onPromptCancelled
                )
                FingerprintPayEvent.Enabled -> currentOnEnabled()
            }
        }
    }

    BackHandler(enabled = pinStage && state.locked) { }

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
                    onBack = if (state.locked) null else onBack,
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
                        title = "Verify Payment PIN",
                        subtitle = "Please enter payment PIN",
                        filled = state.filled,
                        message = state.message,
                        shakeCount = state.shakeCount,
                        showHint = false
                    )
                    OPayButton(
                        text = "Confirm",
                        onClick = viewModel::confirm,
                        enabled = state.canConfirm,
                        modifier = Modifier.padding(top = 24.dp)
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
                purpose = OtpPurpose.SETTINGS,
                onBack = onBack,
                onVerified = viewModel::onProof
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
private fun rememberFingerprintPayViewModel(): FingerprintPayViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { FingerprintPayViewModel.factory(application) }
    return viewModel<FingerprintPayViewModel>(factory = factory)
}
