package com.westly.opay.feature.login

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.backend.BackendCodes
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.feature.register.OTP_LENGTH

private const val SHAKE_STEP_MILLIS = 50
private val SHAKE_STEPS = listOf(-12f, 12f, -8f, 8f, 0f)

private const val PHONE_NOT_FOUND_TEXT = "This phone number is not registered"
private const val SUSPENDED_TEXT = "Your account is suspended. Please contact support."
private const val DELIVERY_FAILED_TEXT = "We could not send the code. Please try again."

/** The login view model is shared by the three login screens and the start-up routing, so it is Activity-scoped. */
@Composable
internal fun rememberLoginViewModel(): LoginViewModel {
    val activity = LocalContext.current as ComponentActivity
    val factory = remember(activity) { LoginViewModel.factory(activity.application) }
    return viewModel<LoginViewModel>(viewModelStoreOwner = activity, factory = factory)
}

/** The error dialogs shared by the login screens. */
@Composable
internal fun LoginDialogHost(
    dialog: LoginDialog?,
    onDismiss: () -> Unit,
    onRegister: () -> Unit
) {
    when (dialog) {
        is LoginDialog.Locked -> OPayDialog(
            title = LoginFailures.lockedText(dialog.minutes),
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        LoginDialog.PhoneNotFound -> OPayDialog(
            title = PHONE_NOT_FOUND_TEXT,
            message = null,
            primaryText = "Register",
            onPrimary = {
                onDismiss()
                onRegister()
            },
            onDismiss = onDismiss,
            secondaryText = "OK",
            onSecondary = onDismiss
        )
        LoginDialog.Suspended -> OPayDialog(
            title = SUSPENDED_TEXT,
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        LoginDialog.RateLimited -> OPayDialog(
            title = BackendCodes.messageFor(BackendCodes.RATE_LIMITED),
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        LoginDialog.DeliveryFailed -> OPayDialog(
            title = DELIVERY_FAILED_TEXT,
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        null -> Unit
    }
}

@Composable
private fun rememberShake(shakeCount: Int): Animatable<Float, *> {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeCount) {
        if (shakeCount > 0) {
            for (target in SHAKE_STEPS) {
                shake.animateTo(target, tween(SHAKE_STEP_MILLIS))
            }
        }
    }
    return shake
}

/** Six boxes that fill with a dot as digits are typed. The digits are never shown. */
@Composable
internal fun LoginPinDots(
    filled: Int,
    hasError: Boolean,
    shakeCount: Int,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val shake = rememberShake(shakeCount)
    Row(
        modifier = modifier
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .semantics { contentDescription = "$filled of $LOGIN_PIN_LENGTH digits entered" },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (index in 0 until LOGIN_PIN_LENGTH) {
            val active = index == filled
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
                if (index < filled) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(c.text)
                    )
                }
            }
        }
    }
}

/** Six boxes for the device-verification code. A long press pastes a copied code. */
@Composable
internal fun LoginCodeBoxes(
    code: String,
    hasError: Boolean,
    shakeCount: Int,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val shake = rememberShake(shakeCount)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onPaste() }) },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (index in 0 until OTP_LENGTH) {
            val active = index == code.length
            val borderColor = when {
                hasError -> c.danger
                active -> c.green
                else -> c.divider
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(OPayShapes.tile)
                    .background(c.bg)
                    .border(if (active) 1.5.dp else 1.dp, borderColor, OPayShapes.tile),
                contentAlignment = Alignment.Center
            ) {
                val digit = code.getOrNull(index)
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
    }
}
