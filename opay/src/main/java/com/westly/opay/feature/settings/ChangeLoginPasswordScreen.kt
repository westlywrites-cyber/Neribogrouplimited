package com.westly.opay.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.login.LoginPinDots
import com.westly.opay.feature.register.BusyOverlay

private val TOAST_ABOVE_KEYPAD = 340.dp
private const val PASSWORD_HINT_TEXT = "Do not use repeated or consecutive numbers"

/**
 * Signed-in change of the 6-digit login password: old password, new password, confirm. The digits are never shown
 * and the screen cannot be captured.
 */
@Composable
fun ChangeLoginPasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberChangeLoginPasswordViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()

    SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChangePasswordEvent.ShowToast -> toast.show(event.message)
                ChangePasswordEvent.Changed -> onChanged()
            }
        }
    }

    fun requestBack() {
        if (!viewModel.stepBack()) onBack()
    }

    BackHandler { requestBack() }

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
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = titleFor(state.step), style = OPayType.headline, color = c.text)
                Text(
                    text = subTextFor(state.step),
                    style = OPayType.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
                LoginPinDots(
                    filled = state.filled,
                    hasError = state.message != null,
                    shakeCount = state.shakeCount,
                    modifier = Modifier.padding(top = 24.dp)
                )
                val message = state.message
                if (message != null) {
                    Text(
                        text = message,
                        style = OPayType.caption,
                        color = c.danger,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else if (state.step == ChangeStep.New) {
                    Text(
                        text = PASSWORD_HINT_TEXT,
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
        if (state.busy) BusyOverlay()
        OPayToastHost(state = toast, bottomPadding = TOAST_ABOVE_KEYPAD)
    }
}

@Composable
private fun rememberChangeLoginPasswordViewModel(): ChangeLoginPasswordViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ChangeLoginPasswordViewModel.factory(application) }
    return viewModel<ChangeLoginPasswordViewModel>(factory = factory)
}

private fun titleFor(step: ChangeStep): String = when (step) {
    ChangeStep.Old -> "Enter old password"
    ChangeStep.New -> "Set new login password"
    ChangeStep.Confirm -> "Confirm new login password"
}

private fun subTextFor(step: ChangeStep): String = when (step) {
    ChangeStep.Old -> "Enter your current 6-digit login password"
    ChangeStep.New -> "This 6-digit password is used to log in to your OPay account"
    ChangeStep.Confirm -> "Enter the same 6-digit password again"
}
