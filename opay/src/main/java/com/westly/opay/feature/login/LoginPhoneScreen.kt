package com.westly.opay.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.PhoneInput

private val TOAST_ABOVE_KEYPAD = 340.dp

/** C2: the number to log in with. [showBack] is false when this is the first screen of the app. */
@Composable
fun LoginPhoneScreen(
    showBack: Boolean,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onRegister: () -> Unit,
    onOpenPin: () -> Unit,
    onLoginIssues: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberLoginViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    var keypadOpen by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowToast -> toast.show(event.message)
                LoginEvent.OpenPin -> onOpenPin()
                else -> Unit
            }
        }
    }

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
                onBack = if (showBack) onBack else null,
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
                    text = "Log in to your account",
                    style = OPayType.headline,
                    color = c.text
                )
                Spacer(Modifier.height(24.dp))
                LoginPhoneField(
                    digits = state.phoneDigits,
                    onClick = { keypadOpen = true },
                    onClear = viewModel::clearPhone
                )
                if (state.phoneError) {
                    Text(
                        text = "Please enter a valid phone number",
                        style = OPayType.caption,
                        color = c.danger,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                OPayButton(
                    text = "Next",
                    onClick = viewModel::submitPhone,
                    enabled = state.canSubmitPhone
                )
                Text(
                    text = "Login issues",
                    style = OPayType.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = c.green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLoginIssues)
                        .padding(top = 12.dp, bottom = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Don't have an OPay Account yet?",
                    style = OPayType.bodySmall,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Click here to get one",
                    style = OPayType.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = c.green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRegister)
                        .padding(vertical = 8.dp)
                )
            }
            if (keypadOpen) {
                SecureKeypad(
                    onDigit = viewModel::onPhoneDigit,
                    onBackspace = viewModel::onPhoneBackspace,
                    onDone = { keypadOpen = false },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
        LoginDialogHost(
            dialog = state.dialog,
            onDismiss = viewModel::dismissDialog,
            onRegister = onRegister
        )
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
    }
}

@Composable
internal fun LoginPhoneField(digits: String, onClick: () -> Unit, onClear: () -> Unit) {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.green, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+234",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier
                .clip(OPayShapes.chip)
                .background(c.cardAlt)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (digits.isEmpty()) {
                Text(
                    text = "Enter your Mobile No.",
                    style = OPayType.titleSmall,
                    color = c.muted
                )
            } else {
                Text(
                    text = PhoneInput.formatGroups(digits),
                    style = OPayType.titleSmall,
                    color = c.text
                )
            }
        }
        if (digits.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics {
                        contentDescription = "Clear"
                        role = Role.Button
                    }
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
            }
        }
    }
}
