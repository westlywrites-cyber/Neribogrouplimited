package com.westly.opay.feature.login

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.PhoneInput
import kotlinx.coroutines.delay

private val TOAST_ABOVE_KEYPAD = 340.dp
private const val RESET_TOAST_MILLIS = 1300L

private const val LEAVE_TITLE = "Reminder"
private const val LEAVE_MESSAGE = "Do you want to leave? Your progress on this screen will be lost."

/**
 * C5: forgot login password. Phone, email for the code, code, new login password, confirm. The password pages
 * cannot be captured and never show the digits. [onResetDone] opens the login password page.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onRegister: () -> Unit,
    onResetDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberForgotPasswordViewModel()
    val login = rememberLoginViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val clipboard = LocalClipboardManager.current
    val currentOnResetDone by rememberUpdatedState(onResetDone)
    var keypadOpen by rememberSaveable { mutableStateOf(true) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val passwordStage = state.stage == ForgotStage.NewPin || state.stage == ForgotStage.ConfirmPin
    val keypadVisible = when (state.stage) {
        ForgotStage.Phone -> keypadOpen
        ForgotStage.Email -> false
        else -> true
    }

    if (passwordStage) SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ForgotEvent.ShowToast -> toast.show(event.message)
                ForgotEvent.ResetDone -> {
                    // Empties the login password boxes and clears any "Incorrect password" line from before the reset.
                    repeat(LOGIN_PIN_LENGTH) { login.onPinBackspace() }
                    delay(RESET_TOAST_MILLIS)
                    currentOnResetDone()
                }
            }
        }
    }

    fun requestBack() {
        when (state.stage) {
            ForgotStage.Phone -> onBack()
            ForgotStage.NewPin -> confirmLeave = true
            else -> viewModel.stepBack()
        }
    }

    BackHandler(enabled = !state.locked) { requestBack() }
    val backAction: (() -> Unit)? = if (state.locked) null else ({ requestBack() })

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
                onBack = backAction,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                when (state.stage) {
                    ForgotStage.Phone -> PhoneStage(
                        state = state,
                        onFieldClick = { keypadOpen = true },
                        onClear = viewModel::clearPhone,
                        onNext = viewModel::submitPhone
                    )
                    ForgotStage.Email -> EmailStage(
                        state = state,
                        onEmailChange = viewModel::onEmailChange,
                        onSend = viewModel::sendCode
                    )
                    ForgotStage.Code -> CodeStage(
                        state = state,
                        onPaste = { viewModel.onCodePaste(clipboard.getText()?.text.orEmpty()) },
                        onResend = viewModel::sendCode
                    )
                    ForgotStage.NewPin, ForgotStage.ConfirmPin -> PasswordStage(state = state)
                }
            }
            if (keypadVisible) {
                SecureKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    onDone = { keypadOpen = false },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
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
            OPayDialog(
                title = SESSION_RESTART_MESSAGE,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::acknowledgeExpired,
                onDismiss = viewModel::acknowledgeExpired
            )
        }
        if (state.locked) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = onRegister
        )
        OPayToastHost(state = toast, bottomPadding = if (keypadVisible) TOAST_ABOVE_KEYPAD else 90.dp)
    }
}

@Composable
private fun rememberForgotPasswordViewModel(): ForgotPasswordViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ForgotPasswordViewModel.factory(application) }
    return viewModel<ForgotPasswordViewModel>(factory = factory)
}

@Composable
private fun PhoneStage(
    state: ForgotUiState,
    onFieldClick: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit
) {
    val c = OPay.colors
    Text(
        text = "Forgot login password",
        style = OPayType.headline,
        color = c.text
    )
    Text(
        text = "Enter your phone number to receive a verification code",
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    Spacer(Modifier.height(24.dp))
    LoginPhoneField(digits = state.phoneDigits, onClick = onFieldClick, onClear = onClear)
    if (state.phoneError) {
        Text(
            text = "Please enter a valid phone number",
            style = OPayType.caption,
            color = c.danger,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp)
        )
    }
    Spacer(Modifier.height(24.dp))
    OPayButton(text = "Next", onClick = onNext, enabled = state.canSubmitPhone)
}

@Composable
private fun EmailStage(
    state: ForgotUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val c = OPay.colors
    Text(
        text = "Enter verification code",
        style = OPayType.headline,
        color = c.text,
        modifier = Modifier.padding(bottom = 14.dp)
    )
    OPayCard {
        Text(
            text = "Enter your email address",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "We will email a 6-digit code for ${PhoneInput.international(state.phoneDigits)}",
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
    state: ForgotUiState,
    onPaste: () -> Unit,
    onResend: () -> Unit
) {
    val c = OPay.colors
    Text(
        text = "Enter verification code",
        style = OPayType.headline,
        color = c.text,
        modifier = Modifier.padding(bottom = 14.dp)
    )
    OPayCard {
        Text(
            text = "Check the code you received",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "We emailed a 6-digit code for ${PhoneInput.international(state.phoneDigits)} to " +
                EmailInput.normalize(state.email),
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
private fun PasswordStage(state: ForgotUiState) {
    val c = OPay.colors
    val confirming = state.stage == ForgotStage.ConfirmPin
    Text(
        text = if (confirming) "Confirm login password" else "Set new login password",
        style = OPayType.headline,
        color = c.text
    )
    Text(
        text = if (confirming) {
            "Enter the same 6-digit password again"
        } else {
            "This 6-digit password is used to log in to your OPay account"
        },
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    LoginPinDots(
        filled = state.filled,
        hasError = state.pinMessage != null,
        shakeCount = state.pinShake,
        modifier = Modifier.padding(top = 24.dp)
    )
    val message = state.pinMessage
    if (message != null) {
        ErrorLine(
            when (message) {
                ForgotPinMessage.Weak -> WEAK_PASSWORD_MESSAGE
                ForgotPinMessage.Mismatch -> PASSWORD_MISMATCH_MESSAGE
            }
        )
    } else if (!confirming) {
        Text(
            text = "Do not use repeated or consecutive numbers",
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(top = 12.dp)
        )
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
