package com.westly.opay.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.rememberOPayToast

private val TOAST_ABOVE_KEYPAD = 340.dp
private const val SHAKE_STEP_MILLIS = 50
private val SHAKE_STEPS = listOf(-12f, 12f, -8f, 8f, 0f)

@Composable
fun RegisterCodeScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberRegistrationViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { toast.show(CODE_SENT_MESSAGE) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RegistrationEvent.ShowToast -> toast.show(event.message)
                is RegistrationEvent.GoTo -> onContinue(event.route)
                RegistrationEvent.OpenCode -> Unit
            }
        }
    }
    BackHandler(enabled = state.busy || state.dialog == RegistrationDialog.Verified) {}

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
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Enter verification code",
                    style = OPayType.headline,
                    color = c.text
                )
                Text(
                    text = "Step 1 of 2",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                OPayCard {
                    Text(
                        text = "Check the code you received",
                        style = OPayType.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Text(
                        text = "We sent a 6-digit code by SMS to ${PhoneInput.international(state.phoneDigits)}",
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
                    CodeBoxes(
                        code = state.code,
                        hasError = state.codeError != null,
                        shakeCount = state.shakeCount,
                        onPaste = { viewModel.onCodePaste(clipboard.getText()?.text.orEmpty()) }
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
                                .clickable(onClick = viewModel::resend)
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
            SecureKeypad(
                onDigit = viewModel::onCodeDigit,
                onBackspace = viewModel::onCodeBackspace,
                modifier = Modifier.navigationBarsPadding()
            )
        }
        if (state.busy) BusyOverlay()
        RegistrationDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onLogin = onLogin
        )
        if (state.dialog == RegistrationDialog.Verified) {
            VerifiedDialog(onDone = viewModel::onVerifiedDialogDone)
        }
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
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

@Composable
private fun CodeBoxes(
    code: String,
    hasError: Boolean,
    shakeCount: Int,
    onPaste: () -> Unit
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeCount) {
        if (shakeCount > 0) {
            for (target in SHAKE_STEPS) {
                shake.animateTo(target, tween(SHAKE_STEP_MILLIS))
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onPaste() }) },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (index in 0 until OTP_LENGTH) {
            CodeBox(
                digit = code.getOrNull(index),
                active = index == code.length,
                hasError = hasError,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CodeBox(
    digit: Char?,
    active: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val borderColor = when {
        hasError -> c.danger
        active -> c.green
        else -> c.divider
    }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(OPayShapes.tile)
            .background(c.bg)
            .border(if (active) 1.5.dp else 1.dp, borderColor, OPayShapes.tile),
        contentAlignment = Alignment.Center
    ) {
        if (digit != null) {
            Text(
                text = digit.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
        }
    }
}
