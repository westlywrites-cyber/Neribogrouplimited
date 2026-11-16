package com.westly.opay.feature.login

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.OPayApplication
import com.westly.opay.core.security.KeystoreVault
import com.westly.opay.core.security.TokenStatus
import com.westly.opay.core.security.VaultPurpose
import com.westly.opay.core.theme.OPay
import com.westly.opay.feature.settings.FINGERPRINT_CHANGED_TOAST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which login screen the lock screen is showing. [Checking] lasts only until the stored token has been looked at. */
internal enum class LockPage { Checking, Fingerprint, Password }

/**
 * The lock screen shown on top of the current screen when the app is locked. It shows the fingerprint screen (C1)
 * when a usable device token exists and the password screen (C3) otherwise, and "Use password" switches to C3.
 * The back button does nothing. [onUnlocked] runs after either screen has signed in; the caller removes this screen
 * so the user is back where they were.
 */
@Composable
fun AppLockedScreen(
    onUnlocked: () -> Unit,
    onHelp: () -> Unit,
    onForgot: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenDeviceCode: () -> Unit,
    onRegister: () -> Unit,
    onResumeRegistration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val loginViewModel = rememberLoginViewModel()
    var page by rememberSaveable { mutableStateOf(LockPage.Checking) }
    var fingerprintRun by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = true) {}
    LaunchedEffect(Unit) {
        if (page != LockPage.Checking) return@LaunchedEffect
        val backend = (application as OPayApplication).backend
        val status = withContext(Dispatchers.IO) { KeystoreVault(backend.secureStore).status(VaultPurpose.Login) }
        when (status) {
            TokenStatus.Valid -> {
                fingerprintRun += 1
                page = LockPage.Fingerprint
            }
            TokenStatus.Invalidated -> {
                loginViewModel.usePassword(FINGERPRINT_CHANGED_TOAST)
                page = LockPage.Password
            }
            TokenStatus.Absent -> {
                loginViewModel.usePassword(null)
                page = LockPage.Password
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OPay.colors.bg)
    ) {
        when (page) {
            LockPage.Checking -> Unit
            LockPage.Fingerprint -> {
                // A new view model for every visit, so the prompt opens again when the user comes back from C3.
                val factory = remember(application) { FingerprintLoginViewModel.factory(application) }
                val fingerprintViewModel = viewModel<FingerprintLoginViewModel>(
                    key = "app_lock_fingerprint_$fingerprintRun",
                    factory = factory
                )
                FingerprintLoginScreen(
                    onSuccess = { response -> loginViewModel.completeBiometricSignIn(response) { onUnlocked() } },
                    onUsePassword = { notice ->
                        loginViewModel.usePassword(notice)
                        page = LockPage.Password
                    },
                    viewModel = fingerprintViewModel
                )
            }
            LockPage.Password -> LoginPinScreen(
                onHelp = onHelp,
                onForgot = onForgot,
                onOpenPhone = onSwitchAccount,
                onOpenDeviceCode = onOpenDeviceCode,
                onRegister = onRegister,
                onResumeRegistration = onResumeRegistration,
                onSignedIn = { onUnlocked() },
                onOpenFingerprint = {
                    fingerprintRun += 1
                    page = LockPage.Fingerprint
                }
            )
        }
    }
}
