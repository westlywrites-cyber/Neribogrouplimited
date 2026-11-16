package com.westly.opay.feature.register

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast

private val PIN_TOAST_ABOVE_KEYPAD = 340.dp
private const val PIN_SHAKE_STEP_MILLIS = 50
private val PIN_SHAKE_STEPS = listOf(-12f, 12f, -8f, 8f, 0f)

private const val PIN_LEAVE_TITLE = "Reminder"
private const val PIN_LEAVE_MESSAGE = "Do you want to leave registration? Your progress on this screen will be lost."
private const val PIN_TRIVIAL_TEXT = "Choose a less guessable number"
private const val PIN_HINT_TEXT = "Do not use repeated or consecutive numbers"

/**
 * Registration step 2 of 2, last pages: create and confirm the 6-digit login password, then create and confirm the
 * 4-digit payment PIN (reference B15 to B18). The digits are never shown and the screen cannot be captured.
 */
@Composable
fun RegisterPinScreen(
    onHelp: () -> Unit,
    onLeave: () -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberPinSetupViewModel()
    val registration = rememberRegistrationViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PinSetupEvent.ShowToast -> toast.show(event.message)
                is PinSetupEvent.GoTo -> onContinue(event.route)
            }
        }
    }

    fun requestBack() {
        if (state.step.isConfirm) viewModel.stepBack() else confirmLeave = true
    }

    BackHandler { if (!state.busy && !leaving) requestBack() }

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
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = titleFor(state.step),
                    style = OPayType.headline,
                    color = c.text
                )
                Text(
                    text = subTextFor(state.step),
                    style = OPayType.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
                PinDots(
                    length = state.step.length,
                    filled = state.filled,
                    hasError = state.message != null,
                    shakeCount = state.shakeCount,
                    modifier = Modifier.padding(top = 24.dp)
                )
                val message = state.message
                if (message != null) {
                    Text(
                        text = messageText(message, state.step),
                        style = OPayType.caption,
                        color = c.danger,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else if (!state.step.isConfirm) {
                    Text(
                        text = PIN_HINT_TEXT,
                        style = OPayType.caption,
                        color = c.muted,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            SecureKeypad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                modifier = Modifier.navigationBarsPadding()
            )
        }
        if (confirmLeave) {
            OPayDialog(
                title = PIN_LEAVE_TITLE,
                message = PIN_LEAVE_MESSAGE,
                primaryText = "Leave",
                onPrimary = {
                    confirmLeave = false
                    leaving = true
                    registration.leaveRegistration(onLeave)
                },
                onDismiss = { confirmLeave = false },
                secondaryText = "Stay",
                onSecondary = { confirmLeave = false }
            )
        }
        if (state.busy || leaving) BusyOverlay()
        OPayToastHost(state = toast, bottomPadding = PIN_TOAST_ABOVE_KEYPAD)
    }
}

@Composable
private fun rememberPinSetupViewModel(): PinSetupViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { PinSetupViewModel.factory(application) }
    return viewModel<PinSetupViewModel>(factory = factory)
}

private fun titleFor(step: PinStep): String = when (step) {
    PinStep.CreateLogin -> "Set login password"
    PinStep.ConfirmLogin -> "Confirm login password"
    PinStep.CreatePayment -> "Set payment PIN"
    PinStep.ConfirmPayment -> "Confirm payment PIN"
}

private fun subTextFor(step: PinStep): String = when (step) {
    PinStep.CreateLogin -> "This 6-digit password is used to log in to your OPay account"
    PinStep.ConfirmLogin -> "Enter the same 6-digit password again"
    PinStep.CreatePayment -> "This 4-digit PIN is used to confirm payments"
    PinStep.ConfirmPayment -> "Enter the same 4-digit PIN again"
}

private fun messageText(message: PinMessage, step: PinStep): String = when (message) {
    PinMessage.Trivial -> PIN_TRIVIAL_TEXT
    PinMessage.Mismatch ->
        if (step.isLogin) "The two passwords do not match" else "The two PINs do not match"
}

@Composable
internal fun PinDots(
    length: Int,
    filled: Int,
    hasError: Boolean,
    shakeCount: Int,
    modifier: Modifier = Modifier
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeCount) {
        if (shakeCount > 0) {
            for (target in PIN_SHAKE_STEPS) {
                shake.animateTo(target, tween(PIN_SHAKE_STEP_MILLIS))
            }
        }
    }
    Row(
        modifier = modifier
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .semantics { contentDescription = "$filled of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (index in 0 until length) {
            PinDot(filled = index < filled, active = index == filled, hasError = hasError)
        }
    }
}

@Composable
private fun PinDot(filled: Boolean, active: Boolean, hasError: Boolean) {
    val c = OPay.colors
    val borderColor = when {
        hasError -> c.danger
        active -> c.green
        else -> c.divider
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(OPayShapes.tile)
            .background(c.card)
            .border(if (active) 1.5.dp else 1.dp, borderColor, OPayShapes.tile),
        contentAlignment = Alignment.Center
    ) {
        if (filled) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(c.text)
            )
        }
    }
}
