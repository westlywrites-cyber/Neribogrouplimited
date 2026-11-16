package com.westly.opay.feature.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayRingAvatar
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay
import com.westly.opay.feature.register.rememberRegistrationViewModel

private val TOAST_ABOVE_KEYPAD = 340.dp
private val AVATAR_SIZE = 64.dp
private val FINGERPRINT_SLOT_HEIGHT = 48.dp

/**
 * C3: the 6-digit login password for the remembered (or just typed) number. The sixth digit signs in.
 * The digits are never shown and the screen cannot be captured.
 */
@Composable
fun LoginPinScreen(
    onHelp: () -> Unit,
    onForgot: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenDeviceCode: () -> Unit,
    onRegister: () -> Unit,
    onResumeRegistration: () -> Unit,
    onSignedIn: (String) -> Unit,
    onOpenFingerprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberLoginViewModel()
    val registration = rememberRegistrationViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()

    SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.preparePin()
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowToast -> toast.show(event.message)
                is LoginEvent.SignedIn -> onSignedIn(event.route)
                LoginEvent.OpenPhone -> onOpenPhone()
                LoginEvent.OpenDeviceCode -> onOpenDeviceCode()
                is LoginEvent.ResumeRegistration -> {
                    registration.clearPhone()
                    event.phone.forEach { registration.onPhoneDigit(it) }
                    onResumeRegistration()
                }
                LoginEvent.OpenPin -> Unit
            }
        }
    }
    LaunchedEffect(state.expiredNotice) {
        if (state.expiredNotice) {
            toast.show(SESSION_EXPIRED_MESSAGE)
            viewModel.consumeExpiredNotice()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            toast.show(it)
            viewModel.consumeNotice()
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
                onBack = null,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                LoginAvatar(initial = LoginFormat.initial(state.name))
                if (state.name != null) {
                    Text(
                        text = "Hi, ${state.name}",
                        style = OPayType.title,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (state.phone.isNotEmpty()) {
                    Text(
                        text = LoginFormat.maskedPhone(state.phone),
                        style = OPayType.bodySmall,
                        color = c.muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = "Enter your 6-digit login password",
                    style = OPayType.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 24.dp)
                )
                LoginPinDots(
                    filled = state.pin.length,
                    hasError = state.incorrect,
                    shakeCount = state.shakeCount,
                    modifier = Modifier.padding(top = 14.dp)
                )
                if (state.incorrect) {
                    Text(
                        text = LoginFailures.wrongPinText(state.attemptsLeft),
                        style = OPayType.caption,
                        color = c.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Forgot password?",
                        style = OPayType.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = c.green,
                        modifier = Modifier
                            .clickable(onClick = onForgot)
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Switch account",
                        style = OPayType.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = c.green,
                        modifier = Modifier
                            .clickable(onClick = viewModel::switchAccount)
                            .padding(vertical = 8.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FINGERPRINT_SLOT_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.fingerprintAvailable) FingerprintLoginButton(onClick = onOpenFingerprint)
                }
            }
            SecureKeypad(
                onDigit = viewModel::onPinDigit,
                onBackspace = viewModel::onPinBackspace,
                modifier = Modifier.navigationBarsPadding()
            )
        }
        if (state.busy) BusyOverlay()
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = onRegister
        )
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
    }
}

@Composable
internal fun LoginAvatar(initial: String) {
    val c = OPay.colors
    if (initial.isEmpty()) {
        OPayRingAvatar(size = AVATAR_SIZE)
        return
    }
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(c.green),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = c.onGreen
        )
    }
}
