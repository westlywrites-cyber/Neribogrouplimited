package com.westly.opay.feature.login

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay
import com.westly.opay.feature.register.CodeError
import com.westly.opay.feature.register.EmailInput
import com.westly.opay.feature.register.PhoneInput

private val CHANGE_TOAST_ABOVE_KEYPAD = 340.dp

/**
 * C6: change login phone number. Information page, old number, login password, new number, email for the code, code.
 * The password page cannot be captured and never shows the digits. [onChanged] opens the login phone page after the
 * person confirms the success box.
 */
@Composable
fun ChangePhoneScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onRegister: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberChangePhoneViewModel()
    val login = rememberLoginViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val clipboard = LocalClipboardManager.current
    val currentOnChanged by rememberUpdatedState(onChanged)
    var keypadOpen by rememberSaveable { mutableStateOf(true) }
    val keypadVisible = when (state.stage) {
        ChangeStage.OldPhone, ChangeStage.NewPhone -> keypadOpen
        ChangeStage.OldPin, ChangeStage.Code -> true
        ChangeStage.Intro, ChangeStage.Email -> false
    }

    if (state.stage == ChangeStage.OldPin) SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChangeEvent.ShowToast -> toast.show(event.message)
                is ChangeEvent.Finished -> {
                    // Opens the login phone page with the new number typed and no password or error left over.
                    login.clearPhone()
                    repeat(LOGIN_PIN_LENGTH) { login.onPinBackspace() }
                    event.newPhone.forEach { login.onPhoneDigit(it) }
                    currentOnChanged()
                }
            }
        }
    }

    fun requestBack() {
        if (state.stage == ChangeStage.Intro) onBack() else viewModel.stepBack()
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
                    ChangeStage.Intro -> ChangeIntroStage()
                    ChangeStage.OldPhone -> ChangeOldPhoneStage(
                        state = state,
                        onFieldClick = { keypadOpen = true },
                        onClear = viewModel::clearPhone,
                        onNext = viewModel::submitOldPhone
                    )
                    ChangeStage.OldPin -> ChangeOldPinStage(state = state)
                    ChangeStage.NewPhone -> ChangeNewPhoneStage(
                        state = state,
                        onFieldClick = { keypadOpen = true },
                        onClear = viewModel::clearPhone,
                        onNext = viewModel::submitNewPhone
                    )
                    ChangeStage.Email -> ChangeEmailStage(
                        state = state,
                        onEmailChange = viewModel::onEmailChange,
                        onSend = viewModel::sendCode
                    )
                    ChangeStage.Code -> ChangeCodeStage(
                        state = state,
                        onPaste = { viewModel.onCodePaste(clipboard.getText()?.text.orEmpty()) },
                        onResend = viewModel::sendCode
                    )
                }
            }
            if (state.stage == ChangeStage.Intro) {
                OPayButton(
                    text = "Proceed",
                    onClick = viewModel::proceed,
                    enabled = !state.locked,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                )
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
        if (state.sessionExpired) {
            OPayDialog(
                title = SESSION_RESTART_MESSAGE,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::acknowledgeExpired,
                onDismiss = viewModel::acknowledgeExpired
            )
        }
        if (state.phoneTaken) {
            OPayDialog(
                title = PHONE_TAKEN_MESSAGE,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::dismissPhoneTaken,
                onDismiss = viewModel::dismissPhoneTaken
            )
        }
        if (state.busy) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = onRegister
        )
        if (state.finished) {
            OPayDialog(
                title = PHONE_CHANGED_MESSAGE,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::confirmChanged,
                onDismiss = viewModel::confirmChanged
            )
        }
        OPayToastHost(
            state = toast,
            bottomPadding = if (keypadVisible) CHANGE_TOAST_ABOVE_KEYPAD else 90.dp
        )
    }
}

@Composable
private fun rememberChangePhoneViewModel(): ChangePhoneViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ChangePhoneViewModel.factory(application) }
    return viewModel<ChangePhoneViewModel>(factory = factory)
}

@Composable
private fun ColumnScope.ChangeIntroStage() {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .padding(top = 10.dp)
            .align(Alignment.CenterHorizontally)
            .size(64.dp)
            .clip(CircleShape)
            .background(c.warn.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        OPayIconView(icon = OPayIcon.Phone, size = 28.dp, tint = c.warn)
    }
    Text(
        text = "Change Login Phone No.",
        style = OPayType.headline,
        color = c.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    )
    Text(
        text = "You may update your phone number if you no longer use it or have lost access to it",
        style = OPayType.bodySmall,
        color = c.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    )
    Spacer(Modifier.height(16.dp))
    OPayCard(background = c.cardAlt) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "After updating, your old phone number can't be used to access this account.",
                style = OPayType.caption,
                color = c.muted
            )
            Text(
                text = "Your 10-digit OPay account number will be changed to your new phone number, " +
                    "without the first zero",
                style = OPayType.caption,
                color = c.muted
            )
            Text(
                text = "You need a new phone number that has never been registered with OPay to use as " +
                    "your new login no.",
                style = OPayType.caption,
                color = c.muted
            )
        }
    }
    Text(
        text = "\u26A0 Once your account is transferred to a new phone number, it cannot be reversed",
        style = OPayType.caption,
        color = c.warn,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.warn.copy(alpha = 0.15f))
            .padding(10.dp)
    )
}

@Composable
private fun ChangeOldPhoneStage(
    state: ChangePhoneUiState,
    onFieldClick: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit
) {
    val c = OPay.colors
    Text(
        text = "Current phone number",
        style = OPayType.headline,
        color = c.text
    )
    Text(
        text = "Enter the phone number you use to log in now",
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    Spacer(Modifier.height(24.dp))
    LoginPhoneField(digits = state.oldDigits, onClick = onFieldClick, onClear = onClear)
    if (state.oldError) ChangeErrorLine("Please enter a valid phone number")
    Spacer(Modifier.height(24.dp))
    OPayButton(text = "Next", onClick = onNext, enabled = state.canSubmitOld)
}

@Composable
private fun ChangeOldPinStage(state: ChangePhoneUiState) {
    val c = OPay.colors
    Text(
        text = "Enter login password",
        style = OPayType.headline,
        color = c.text
    )
    Text(
        text = "Enter the 6-digit login password of this account",
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    LoginPinDots(
        filled = state.filled,
        hasError = state.pinError != null,
        shakeCount = state.pinShake,
        modifier = Modifier.padding(top = 24.dp)
    )
    val message = state.pinError
    if (message != null) ChangeErrorLine(message)
}

@Composable
private fun ChangeNewPhoneStage(
    state: ChangePhoneUiState,
    onFieldClick: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit
) {
    val c = OPay.colors
    Text(
        text = "New phone number",
        style = OPayType.headline,
        color = c.text
    )
    Text(
        text = "Enter the new phone number you want to log in with",
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    Spacer(Modifier.height(24.dp))
    LoginPhoneField(digits = state.newDigits, onClick = onFieldClick, onClear = onClear)
    when {
        state.sameNumber -> ChangeErrorLine(SAME_NUMBER_MESSAGE)
        state.newError -> ChangeErrorLine("Please enter a valid phone number")
    }
    Spacer(Modifier.height(24.dp))
    OPayButton(text = "Next", onClick = onNext, enabled = state.canSubmitNew)
}

@Composable
private fun ChangeEmailStage(
    state: ChangePhoneUiState,
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
            text = "We will email a 6-digit code for ${PhoneInput.international(state.newDigits)}",
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
        if (state.emailError) ChangeErrorLine("Please enter a valid email address")
    }
    Spacer(Modifier.height(12.dp))
    OPayButton(
        text = if (state.secondsLeft > 0) "Send code in ${state.secondsLeft}s" else "Send code",
        onClick = onSend,
        enabled = state.canSendCode
    )
}

@Composable
private fun ChangeCodeStage(
    state: ChangePhoneUiState,
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
            text = "We emailed a 6-digit code for ${PhoneInput.international(state.newDigits)} to " +
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
            CodeError.Incorrect -> ChangeErrorLine("Incorrect code")
            CodeError.Expired -> ChangeErrorLine("Code expired, tap Resend")
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
private fun ChangeErrorLine(text: String) {
    Text(
        text = text,
        style = OPayType.caption,
        color = OPay.colors.danger,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp)
    )
}
