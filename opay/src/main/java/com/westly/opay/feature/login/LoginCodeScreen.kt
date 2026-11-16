package com.westly.opay.feature.login

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.PhoneInput

private val TOAST_ABOVE_KEYPAD = 340.dp

/**
 * Device verification after a correct password on a new device. The `login` call has already sent a code that has
 * no address to go to, so this screen first asks for an address, sends the code there and then checks it.
 */
@Composable
fun LoginCodeScreen(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onSignedIn: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberLoginViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val clipboard = LocalClipboardManager.current
    val inCodeStage = state.stage == DeviceStage.Code

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowToast -> toast.show(event.message)
                is LoginEvent.SignedIn -> onSignedIn(event.route)
                else -> Unit
            }
        }
    }
    BackHandler(enabled = state.busy) {}

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
                onBack = onBack,
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
                Text(
                    text = "Verify this device",
                    style = OPayType.headline,
                    color = c.text,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                if (inCodeStage) {
                    CodeStage(
                        state = state,
                        onPaste = { viewModel.onCodePaste(clipboard.getText()?.text.orEmpty()) },
                        onResend = viewModel::sendDeviceCode
                    )
                } else {
                    EmailStage(
                        state = state,
                        onEmailChange = viewModel::onEmailChange,
                        onSend = viewModel::sendDeviceCode
                    )
                }
            }
            if (inCodeStage) {
                SecureKeypad(
                    onDigit = viewModel::onCodeDigit,
                    onBackspace = viewModel::onCodeBackspace,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
        if (state.busy) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = onRegister
        )
        OPayToastHost(state = toast, bottomPadding = if (inCodeStage) TOAST_ABOVE_KEYPAD else 90.dp)
    }
}

@Composable
private fun EmailStage(
    state: LoginUiState,
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
            text = "For your security, we will email a 6-digit code for ${PhoneInput.international(state.phone)}",
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
    state: LoginUiState,
    onPaste: () -> Unit,
    onResend: () -> Unit
) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = "For your security, enter the code sent to ${PhoneInput.international(state.phone)}",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "We emailed the code to ${EmailInput.normalize(state.email)}",
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
        if (state.secondsLeft > 0) {
            Text(
                text = "Resend code in ${state.secondsLeft}s",
                style = OPayType.caption,
                color = c.muted,
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
