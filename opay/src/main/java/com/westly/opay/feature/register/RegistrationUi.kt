package com.westly.opay.feature.register

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.backend.BackendCodes
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import kotlinx.coroutines.delay

private const val VERIFIED_DIALOG_MILLIS = 1200L
private const val PHONE_TAKEN_TEXT = "This phone number is already registered. Please log in."
private const val DELIVERY_FAILED_TEXT = "We could not send the code. Please try again."

@Composable
internal fun rememberRegistrationViewModel(): RegistrationViewModel {
    val activity = LocalContext.current as ComponentActivity
    val factory = remember(activity) { RegistrationViewModel.factory(activity.application) }
    return viewModel<RegistrationViewModel>(viewModelStoreOwner = activity, factory = factory)
}

/** Dims the screen and blocks touches while a backend call runs. */
@Composable
internal fun BusyOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        OPayLoader()
    }
}

/** The error dialogs shared by both registration screens. The verified dialog is drawn by the code screen. */
@Composable
internal fun RegistrationDialogHost(
    dialog: RegistrationDialog?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit
) {
    when (dialog) {
        RegistrationDialog.PhoneTaken -> OPayDialog(
            title = PHONE_TAKEN_TEXT,
            message = null,
            primaryText = "Log in",
            onPrimary = {
                onDismiss()
                onLogin()
            },
            onDismiss = onDismiss,
            secondaryText = "OK",
            onSecondary = onDismiss
        )
        RegistrationDialog.RateLimited -> OPayDialog(
            title = BackendCodes.messageFor(BackendCodes.RATE_LIMITED),
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        RegistrationDialog.DeliveryFailed -> OPayDialog(
            title = DELIVERY_FAILED_TEXT,
            message = null,
            primaryText = "OK",
            onPrimary = onDismiss,
            onDismiss = onDismiss
        )
        RegistrationDialog.Verified, null -> Unit
    }
}

@Composable
internal fun VerifiedDialog(onDone: () -> Unit) {
    val c = OPay.colors
    LaunchedEffect(Unit) {
        delay(VERIFIED_DIALOG_MILLIS)
        onDone()
    }
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(c.green.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Check, size = 26.dp, tint = c.green)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Phone number verified",
                    style = OPayType.title,
                    color = c.text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OPayButton(text = "OK", onClick = onDone)
            }
        }
    }
}
