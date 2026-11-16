package com.westly.opay.feature.settings

import android.app.Application
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.login.LoginPinDots
import com.westly.opay.feature.register.BusyOverlay

private const val PROMPT_TITLE = "Enable fingerprint login"
private const val PROMPT_SUBTITLE = "Confirm your fingerprint to turn on fingerprint login"
private const val SHEET_TITLE = "Enter your login password to enable fingerprint login"

/**
 * Login Settings (reference D3): the Auto-logout row, the fingerprint toggle and the change-password row. Turning
 * fingerprint login on asks for the login password in a sheet, then for a fingerprint (reference D5).
 */
@Composable
fun LoginSettingsScreen(
    onBack: () -> Unit,
    onAutoLogout: () -> Unit,
    onChangePassword: () -> Unit,
    passwordChanged: Boolean,
    onPasswordChangedShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val activity = LocalContext.current as FragmentActivity
    val viewModel = rememberLoginSettingsViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginSettingsEvent.ShowToast -> toast.show(event.message)
                is LoginSettingsEvent.LaunchPrompt -> BiometricGate.authenticate(
                    activity = activity,
                    title = PROMPT_TITLE,
                    subtitle = PROMPT_SUBTITLE,
                    cryptoObject = BiometricPrompt.CryptoObject(event.cipher),
                    onSuccess = { unlocked -> viewModel.onPromptSuccess(unlocked?.cipher) },
                    onError = viewModel::onPromptError,
                    onCancel = viewModel::onPromptCancelled
                )
            }
        }
    }
    LaunchedEffect(passwordChanged) {
        if (passwordChanged) {
            toast.show(PASSWORD_CHANGED_TOAST)
            onPasswordChangedShown()
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
                .navigationBarsPadding()
        ) {
            OPayTopBar(
                title = "Login Settings",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                OPayCard {
                    OPayRow(
                        title = "Auto-logout",
                        value = state.loginModeLabel,
                        onClick = onAutoLogout
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Fingerprint login",
                        showChevron = false,
                        trailing = {
                            OPayToggle(
                                checked = state.fingerprintOn,
                                onCheckedChange = viewModel::onToggle
                            )
                        }
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Change login password",
                        onClick = onChangePassword
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (state.busy) BusyOverlay()
        if (state.noFingerprintDialog) {
            OPayDialog(
                title = NO_FINGERPRINT_TEXT,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::dismissNoFingerprintDialog,
                onDismiss = viewModel::dismissNoFingerprintDialog
            )
        }
        FingerprintPinSheet(
            state = state,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onDismiss = viewModel::dismissSheet
        )
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun rememberLoginSettingsViewModel(): LoginSettingsViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { LoginSettingsViewModel.factory(application) }
    return viewModel<LoginSettingsViewModel>(factory = factory)
}

/** The 6-digit login password sheet shown before the fingerprint prompt. Errors are drawn inside the sheet. */
@Composable
private fun FingerprintPinSheet(
    state: LoginSettingsUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = OPay.colors
    OPayBottomSheet(visible = state.sheetOpen, onDismiss = onDismiss) {
        SecureDialogWindow()
        Text(
            text = SHEET_TITLE,
            style = OPayType.title,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            LoginPinDots(
                filled = state.filled,
                hasError = state.sheetMessage != null,
                shakeCount = state.shakeCount
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            val message = state.sheetMessage
            if (state.sheetBusy) {
                OPayLoader(size = 28.dp)
            } else if (message != null) {
                Text(
                    text = message,
                    style = OPayType.caption,
                    fontWeight = FontWeight.Medium,
                    color = c.danger,
                    textAlign = TextAlign.Center
                )
            }
        }
        SecureKeypad(onDigit = onDigit, onBackspace = onBackspace)
    }
}

/** Blocks screenshots of the sheet's own window; the page behind it is covered by the screen's flag. */
@Composable
private fun SecureDialogWindow() {
    SecureWindow()
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
}
