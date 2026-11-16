package com.westly.opay.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.login.LoginCodeBoxes
import com.westly.opay.feature.login.LoginDialogHost
import com.westly.opay.feature.register.BusyOverlay
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput

private val TOAST_ABOVE_KEYPAD = 340.dp
private const val PHONE_FALLBACK = "your phone number"

/**
 * D7, Verify Phone Number: the reusable code step for a signed-in user. [purpose] is `OtpPurpose.SETTINGS` or
 * `OtpPurpose.RESET_PAYMENT_PIN`. The first page takes the address the code is emailed to, the second takes the
 * code. [onVerified] receives the single-use proof; the caller keeps it in memory only and for 10 minutes at most.
 * [onBack] is called when the person leaves from the first page.
 */
@Composable
fun VerifyPhoneScreen(
    purpose: String,
    onBack: () -> Unit,
    onVerified: (proof: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberVerifyPhoneViewModel(purpose)
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val clipboard = LocalClipboardManager.current
    val currentOnVerified by rememberUpdatedState(onVerified)
    val codeStage = state.stage == VerifyPhoneStage.Code

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VerifyPhoneEvent.ShowToast -> toast.show(event.message)
                is VerifyPhoneEvent.Verified -> currentOnVerified(event.proof)
            }
        }
    }

    fun requestBack() {
        if (!viewModel.stepBack()) onBack()
    }

    BackHandler(enabled = !state.busy) { requestBack() }

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
                onBack = if (state.busy) null else ({ requestBack() }),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = "Verify Phone Number", style = OPayType.headline, color = c.text)
                Spacer(Modifier.height(14.dp))
                if (codeStage) {
                    CodeStage(
                        state = state,
                        onPaste = { viewModel.onCodePaste(clipboard.getText()?.text.orEmpty()) },
                        onResend = viewModel::sendCode
                    )
                } else {
                    EmailStage(
                        state = state,
                        onEmailChange = viewModel::onEmailChange,
                        onSend = viewModel::sendCode
                    )
                }
            }
            if (codeStage) {
                SecureKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
        if (state.busy) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = {}
        )
        OPayToastHost(state = toast, bottomPadding = if (codeStage) TOAST_ABOVE_KEYPAD else 90.dp)
    }
}

@Composable
private fun rememberVerifyPhoneViewModel(purpose: String): VerifyPhoneViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application, purpose) { VerifyPhoneViewModel.factory(application, purpose) }
    return viewModel<VerifyPhoneViewModel>(key = "verify_phone_$purpose", factory = factory)
}

@Composable
private fun EmailStage(
    state: VerifyPhoneUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = "Enter your email address",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "We will email a 6-digit code for ${state.maskedPhone.ifEmpty { PHONE_FALLBACK }}",
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp)
        )
        OPayUnderlineField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "Email address",
            keyboardType = KeyboardType.Email,
            maxLength = EmailInput.MAX_LENGTH,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (state.emailError) {
            Text(
                text = "Please enter a valid email address",
                style = OPayType.caption,
                color = c.danger,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OPayButton(
        text = if (state.secondsLeft > 0) "Send code in ${state.secondsLeft}s" else "Send code",
        onClick = onSend,
        enabled = state.canSendCode
    )
}

@Composable
private fun CodeStage(
    state: VerifyPhoneUiState,
    onPaste: () -> Unit,
    onResend: () -> Unit
) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = "Enter the code sent to ${state.maskedPhone.ifEmpty { PHONE_FALLBACK }}",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "We emailed the 6-digit code to ${EmailInput.normalize(state.email)}",
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    OPayCard {
        Text(
            text = "Fill in the box below with the code",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LoginCodeBoxes(
            code = state.code,
            hasError = state.codeError != null,
            shakeCount = state.codeShake,
            onPaste = onPaste
        )
        when (state.codeError) {
            CodeError.Incorrect -> ErrorLine("Incorrect code")
            CodeError.Expired -> ErrorLine("Code expired, tap Resend")
            null -> Unit
        }
    }
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Didn't receive the OTP? ",
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (state.secondsLeft > 0) {
            Text(
                text = "Resend (${state.secondsLeft}s)",
                style = OPayType.caption,
                color = c.green,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Text(
                text = "Resend",
                style = OPayType.caption,
                fontWeight = FontWeight.Bold,
                color = c.green,
                modifier = Modifier
                    .clickable(onClick = onResend)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = OPayType.caption,
        color = OPay.colors.danger,
        modifier = Modifier.padding(top = 8.dp)
    )
}
