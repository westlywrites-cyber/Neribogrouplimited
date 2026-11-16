package com.westly.opay.feature.login

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.backend.SignInResponse
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.BusyOverlay

private const val PROMPT_TITLE = "OPay"
private const val PROMPT_SUBTITLE = "Log in with your fingerprint"
private const val USE_PASSWORD_TEXT = "Use password"
private val TOAST_BOTTOM_PADDING = 120.dp

/** The fingerprint view model is owned by the screen that shows it, so it is dropped when the screen is left. */
@Composable
internal fun rememberFingerprintLoginViewModel(): FingerprintLoginViewModel {
    val context = LocalContext.current
    val factory = remember(context) { FingerprintLoginViewModel.factory(context.applicationContext as Application) }
    return viewModel<FingerprintLoginViewModel>(factory = factory)
}

/**
 * C1: fingerprint login. The system prompt opens by itself; the big icon opens it again. It knows nothing about
 * navigation: [onSuccess] receives the session result so the caller can route or dismiss an overlay, and
 * [onUsePassword] receives the notice (if any) to show on the password screen. [initialNotice] is shown once as a
 * toast and then reported through [onInitialNoticeShown].
 */
@Composable
fun FingerprintLoginScreen(
    onSuccess: (SignInResponse) -> Unit,
    onUsePassword: (String?) -> Unit,
    modifier: Modifier = Modifier,
    initialNotice: String? = null,
    onInitialNoticeShown: () -> Unit = {},
    viewModel: FingerprintLoginViewModel = rememberFingerprintLoginViewModel()
) {
    val c = OPay.colors
    val activity = LocalContext.current as FragmentActivity
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnUsePassword by rememberUpdatedState(onUsePassword)
    val currentOnNoticeShown by rememberUpdatedState(onInitialNoticeShown)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FingerprintLoginEvent.LaunchPrompt -> BiometricGate.authenticate(
                    activity = activity,
                    title = PROMPT_TITLE,
                    subtitle = PROMPT_SUBTITLE,
                    cryptoObject = BiometricPrompt.CryptoObject(event.cipher),
                    onSuccess = { unlocked -> viewModel.onPromptSuccess(unlocked?.cipher) },
                    onError = { viewModel.onPromptFailed() },
                    onCancel = viewModel::onPromptCancelled,
                    negativeText = USE_PASSWORD_TEXT,
                    onNegative = viewModel::onUsePassword
                )
                is FingerprintLoginEvent.ShowToast -> toast.show(event.message)
                is FingerprintLoginEvent.SignedIn -> currentOnSuccess(event.response)
                is FingerprintLoginEvent.UsePassword -> currentOnUsePassword(event.notice)
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.onEntered() }
    LaunchedEffect(initialNotice) {
        if (initialNotice != null) {
            toast.show(initialNotice)
            currentOnNoticeShown()
        }
    }
    BackHandler(enabled = state.phase == FingerprintPhase.Verifying) {}

    val canRetry = state.phase == FingerprintPhase.Idle || state.phase == FingerprintPhase.Failed
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            LoginAvatar(initial = LoginFormat.initial(state.name))
            Text(
                text = state.name?.let { "Hi, $it" } ?: "Hi",
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(c.greenSoft)
                    .clickable(enabled = canRetry, role = Role.Button, onClick = viewModel::start),
                contentAlignment = Alignment.Center
            ) {
                FingerprintGlyph(size = 56.dp, tint = c.green)
            }
            Text(
                text = "Touch the fingerprint sensor to log in",
                style = OPayType.bodySmall,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = USE_PASSWORD_TEXT,
                style = OPayType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = c.green,
                modifier = Modifier
                    .clickable(
                        enabled = state.phase != FingerprintPhase.Verifying,
                        role = Role.Button,
                        onClick = viewModel::onUsePassword
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.phase == FingerprintPhase.Verifying) BusyOverlay()
        OPayToastHost(state = toast, bottomPadding = TOAST_BOTTOM_PADDING)
    }
}
