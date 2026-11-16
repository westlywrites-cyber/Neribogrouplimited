package com.westly.opay.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.westly.opay.OPayApplication
import com.westly.opay.feature.login.LoginStartup
import com.westly.opay.feature.login.rememberLoginViewModel
import com.westly.opay.feature.splash.SplashScreen
import com.westly.opay.feature.welcome.WelcomeFlag
import com.westly.opay.feature.transfer.TransferViewModel
import com.westly.opay.feature.welcome.WelcomeScreen
import kotlinx.coroutines.launch

private const val FADE_MILLIS = 150
private const val SLIDE_MILLIS = 250
private const val PASSWORD_CHANGED_KEY = "login_password_changed"

// BEGIN PHASE B10B2 (app lock constants)
/** Start-up screens of a signed-out user. The lock screen is never shown on top of these. */
private val SIGNED_OUT_START_ROUTES = setOf(
    Routes.Welcome,
    Routes.LoginPhone,
    Routes.LoginPin,
    Routes.LoginFingerprint
)
// END PHASE B10B2 (app lock constants)
// BEGIN PHASE B11A (notice key)
/** Carries the toast text from a finished payment PIN flow back to Payment Settings. Empty means no notice. */
private const val PAYMENT_PIN_NOTICE_KEY = "payment_pin_notice"
// END PHASE B11A (notice key)

private fun NavBackStackEntry.isTab(): Boolean = destination.route in Routes.tabRoutes

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backend = remember { (context.applicationContext as OPayApplication).backend }
    val welcomeFlag = remember { WelcomeFlag(backend.secureStore) }
    // BEGIN PHASE B8 (session end)
    val transferViewModel = viewModel<TransferViewModel>(viewModelStoreOwner = context as ComponentActivity)
    val loginViewModel = rememberLoginViewModel()
    val openLogin: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    val logout: () -> Unit = {
        loginViewModel.leaveSession(expired = false) { route ->
            transferViewModel.reset()
            openLogin(route)
        }
    }
    LaunchedEffect(backend) {
        backend.session.sessionExpired.collect {
            if (navController.currentDestination?.route != Routes.Splash) {
                loginViewModel.leaveSession(expired = true) { route ->
                    transferViewModel.reset()
                    openLogin(route)
                }
            }
        }
    }
    // END PHASE B8 (session end)
    // BEGIN PHASE B10B2 (app lock)
    val appLock = remember { (context.applicationContext as OPayApplication).appLock }
    LaunchedEffect(appLock) {
        appLock.lockRequests.collect {
            val route = navController.currentDestination?.route
            val alreadyLocked = runCatching { navController.getBackStackEntry(Routes.AppLocked) }.isSuccess
            if (route != null && route != Routes.Splash && !alreadyLocked) {
                navController.navigate(Routes.AppLocked) { launchSingleTop = true }
            }
        }
    }
    // END PHASE B10B2 (app lock)
    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        modifier = modifier,
        enterTransition = {
            if (targetState.isTab()) fadeIn(tween(FADE_MILLIS))
            else slideInHorizontally(tween(SLIDE_MILLIS)) { it }
        },
        exitTransition = {
            if (targetState.isTab()) fadeOut(tween(FADE_MILLIS))
            else slideOutHorizontally(tween(SLIDE_MILLIS)) { -it / 4 }
        },
        popEnterTransition = {
            if (targetState.isTab()) fadeIn(tween(FADE_MILLIS))
            else slideInHorizontally(tween(SLIDE_MILLIS)) { -it / 4 }
        },
        popExitTransition = {
            if (initialState.isTab()) fadeOut(tween(FADE_MILLIS))
            else slideOutHorizontally(tween(SLIDE_MILLIS)) { it }
        }
    ) {
        // BEGIN PHASE B3
        composable(
            route = Routes.Welcome,
            enterTransition = { fadeIn(tween(FADE_MILLIS)) }
        ) {
            WelcomeScreen(
                onRegister = {
                    welcomeFlag.markSeen()
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onLogin = {
                    welcomeFlag.markSeen()
                    navController.navigate(Routes.LoginPhone) { launchSingleTop = true }
                }
            )
        }
        // END PHASE B3
        // BEGIN PHASE B4
        composable(Routes.RegisterPhone) {
            com.westly.opay.feature.register.RegisterPhoneScreen(
                onBack = { navController.popBackStack() },
                onLogin = {
                    navController.navigate(Routes.LoginPhone) { launchSingleTop = true }
                },
                onOpenCode = {
                    navController.navigate(Routes.RegisterCode) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.RegisterCode) {
            com.westly.opay.feature.register.RegisterCodeScreen(
                onBack = { navController.popBackStack() },
                onLogin = {
                    navController.navigate(Routes.LoginPhone) { launchSingleTop = true }
                },
                onContinue = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.RegisterPhone) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        // END PHASE B4
        // BEGIN PHASE B5
        composable(Routes.RegisterAddress) {
            com.westly.opay.feature.register.RegisterAddressScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onLeave = {
                    if (!navController.popBackStack(Routes.Welcome, false)) {
                        navController.navigate(Routes.Welcome) {
                            popUpTo(Routes.RegisterAddress) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onNext = {
                    navController.navigate(Routes.RegisterNationality) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.RegisterNationality) {
            com.westly.opay.feature.register.RegisterNationalityScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onNext = {
                    navController.navigate(Routes.RegisterPep) { launchSingleTop = true }
                }
            )
        }
        // END PHASE B5
        // BEGIN PHASE B6
        composable(Routes.RegisterPep) {
            com.westly.opay.feature.register.RegisterPepScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onNotPep = {
                    navController.navigate(Routes.RegisterIncome) { launchSingleTop = true }
                },
                onPep = {
                    navController.navigate(Routes.RegisterPepDetails) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.RegisterPepDetails) {
            com.westly.opay.feature.register.RegisterPepDetailsScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onNext = {
                    navController.navigate(Routes.RegisterIncome) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.RegisterIncome) {
            com.westly.opay.feature.register.RegisterIncomeScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onContinue = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.RegisterAddress) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        // END PHASE B6
        // BEGIN PHASE B7
        composable(Routes.RegisterPin) {
            com.westly.opay.feature.register.RegisterPinScreen(
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onLeave = {
                    navController.navigate(Routes.Welcome) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onContinue = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.Waiting) {
            com.westly.opay.feature.register.WaitingScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        // END PHASE B7
        // BEGIN PHASE B8
        composable(Routes.Splash) {
            val scope = rememberCoroutineScope()
            SplashScreen(
                readWelcomeSeen = { welcomeFlag.isSeen() },
                onFinished = { welcomeSeen ->
                    scope.launch {
                        val target = LoginStartup.resolve(backend, welcomeSeen)
                        navController.navigate(target) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                        // BEGIN PHASE B10B2 (cold start lock)
                        if (target !in SIGNED_OUT_START_ROUTES && appLock.shouldLockOnColdStart()) {
                            navController.navigate(Routes.AppLocked) { launchSingleTop = true }
                        }
                        // END PHASE B10B2 (cold start lock)
                    }
                }
            )
        }
        composable(Routes.LoginPhone) {
            com.westly.opay.feature.login.LoginPhoneScreen(
                showBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onOpenPin = {
                    navController.navigate(Routes.LoginPin) { launchSingleTop = true }
                },
                onLoginIssues = {
                    navController.navigate(Routes.LoginIssues) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.LoginPin) {
            com.westly.opay.feature.login.LoginPinScreen(
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onForgot = {
                    navController.navigate(Routes.LoginIssues) { launchSingleTop = true }
                },
                onOpenPhone = {
                    navController.navigate(Routes.LoginPhone) {
                        popUpTo(Routes.LoginPin) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenDeviceCode = {
                    navController.navigate(Routes.LoginCode) { launchSingleTop = true }
                },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onResumeRegistration = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onSignedIn = openLogin,
                onOpenFingerprint = {
                    navController.navigate(Routes.LoginFingerprint) {
                        popUpTo(Routes.LoginPin) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.LoginCode) {
            com.westly.opay.feature.login.LoginCodeScreen(
                onBack = { navController.popBackStack() },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onSignedIn = openLogin
            )
        }
        // END PHASE B8
        // BEGIN PHASE B9A
        composable(Routes.LoginIssues) {
            com.westly.opay.feature.login.LoginIssuesScreen(
                onBack = { navController.popBackStack() },
                onForgotPassword = {
                    navController.navigate(Routes.ForgotPassword) { launchSingleTop = true }
                },
                onChangePhone = {
                    navController.navigate(Routes.ChangeLoginPhone) { launchSingleTop = true }
                },
                onContactSupport = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.ForgotPassword) {
            com.westly.opay.feature.login.ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onResetDone = {
                    if (!navController.popBackStack(Routes.LoginPin, inclusive = false)) {
                        navController.navigate(Routes.LoginPin) {
                            popUpTo(Routes.LoginIssues) { inclusive = true }
                        }
                    }
                }
            )
        }
        // END PHASE B9A
        // BEGIN PHASE B9B
        composable(Routes.ChangeLoginPhone) {
            com.westly.opay.feature.login.ChangePhoneScreen(
                onBack = { navController.popBackStack() },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onChanged = {
                    if (!navController.popBackStack(Routes.LoginPhone, inclusive = false)) {
                        navController.navigate(Routes.LoginPhone) {
                            popUpTo(Routes.LoginIssues) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        // END PHASE B9B
        // BEGIN PHASE B10A
        composable(Routes.LoginSettings) { entry ->
            val passwordChanged by entry.savedStateHandle
                .getStateFlow(PASSWORD_CHANGED_KEY, false)
                .collectAsState()
            com.westly.opay.feature.settings.LoginSettingsScreen(
                onBack = { navController.popBackStack() },
                onAutoLogout = {
                    navController.navigate(Routes.AutoLogout) { launchSingleTop = true }
                },
                onChangePassword = {
                    navController.navigate(Routes.ChangeLoginPassword) { launchSingleTop = true }
                },
                passwordChanged = passwordChanged,
                onPasswordChangedShown = { entry.savedStateHandle[PASSWORD_CHANGED_KEY] = false }
            )
        }
        composable(Routes.ChangeLoginPassword) {
            com.westly.opay.feature.settings.ChangeLoginPasswordScreen(
                onBack = { navController.popBackStack() },
                onChanged = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(PASSWORD_CHANGED_KEY, true)
                    navController.popBackStack()
                }
            )
        }
        // END PHASE B10A
        // BEGIN PHASE B10B1
        composable(Routes.LoginFingerprint) {
            val loginState by loginViewModel.state.collectAsState()
            com.westly.opay.feature.login.FingerprintLoginScreen(
                onSuccess = { response -> loginViewModel.completeBiometricSignIn(response, openLogin) },
                onUsePassword = { notice ->
                    loginViewModel.usePassword(notice)
                    navController.navigate(Routes.LoginPin) {
                        popUpTo(Routes.LoginFingerprint) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                initialNotice = if (loginState.expiredNotice) {
                    com.westly.opay.feature.login.SESSION_EXPIRED_MESSAGE
                } else {
                    null
                },
                onInitialNoticeShown = loginViewModel::consumeExpiredNotice
            )
        }
        // END PHASE B10B1
        // BEGIN PHASE B10B2
        composable(Routes.AutoLogout) {
            com.westly.opay.feature.settings.AutoLogoutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.AppLocked,
            enterTransition = { fadeIn(tween(FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(FADE_MILLIS)) },
            popExitTransition = { fadeOut(tween(FADE_MILLIS)) }
        ) {
            com.westly.opay.feature.login.AppLockedScreen(
                onUnlocked = {
                    appLock.onUnlocked()
                    navController.popBackStack(Routes.AppLocked, inclusive = true)
                },
                onHelp = {
                    navController.navigate(Routes.Help) { launchSingleTop = true }
                },
                onForgot = {
                    navController.navigate(Routes.LoginIssues) { launchSingleTop = true }
                },
                onSwitchAccount = logout,
                onOpenDeviceCode = {
                    navController.navigate(Routes.LoginCode) { launchSingleTop = true }
                },
                onRegister = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                },
                onResumeRegistration = {
                    navController.navigate(Routes.RegisterPhone) { launchSingleTop = true }
                }
            )
        }
        // END PHASE B10B2
        // BEGIN PHASE B11A
        composable(Routes.PaymentSettings) { entry ->
            val notice by entry.savedStateHandle
                .getStateFlow(PAYMENT_PIN_NOTICE_KEY, "")
                .collectAsState()
            com.westly.opay.feature.settings.PaymentSettingsScreen(
                onBack = { navController.popBackStack() },
                onChangePin = {
                    navController.navigate(Routes.ChangePaymentPin) { launchSingleTop = true }
                },
                onForgotPin = {
                    navController.navigate(Routes.ForgotPaymentPin) { launchSingleTop = true }
                },
                onFingerprintPayChange = {
                    navController.navigate(Routes.FingerprintPay) { launchSingleTop = true }
                },
                notice = notice,
                onNoticeShown = { entry.savedStateHandle[PAYMENT_PIN_NOTICE_KEY] = "" }
            )
        }
        composable(Routes.ChangePaymentPin) {
            com.westly.opay.feature.settings.ChangePaymentPinScreen(
                onBack = { navController.popBackStack() },
                onChanged = {
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(PAYMENT_PIN_NOTICE_KEY, com.westly.opay.feature.settings.PAYMENT_PIN_CHANGED_TOAST)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.ForgotPaymentPin) {
            com.westly.opay.feature.settings.ForgotPaymentPinScreen(
                onBack = { navController.popBackStack() },
                onResetDone = {
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(PAYMENT_PIN_NOTICE_KEY, com.westly.opay.feature.settings.PAYMENT_PIN_RESET_TOAST)
                    navController.popBackStack()
                }
            )
        }
        // END PHASE B11A
        // BEGIN PHASE B11B
        composable(Routes.FingerprintPay) {
            com.westly.opay.feature.settings.FingerprintPayScreen(
                onBack = { navController.popBackStack() },
                onEnabled = {
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(PAYMENT_PIN_NOTICE_KEY, com.westly.opay.feature.settings.FINGERPRINT_PAY_ENABLED_TOAST)
                    navController.popBackStack()
                }
            )
        }
        // END PHASE B11B
        // BEGIN PHASE 7
        composable(Routes.Home) {
            com.westly.opay.feature.home.HomeScreen(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 7
        // BEGIN PHASE 8
        composable(Routes.TransferBank) {
            com.westly.opay.feature.transfer.TransferBankScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 8
        // BEGIN PHASE 9
        composable(Routes.SelectBank) {
            com.westly.opay.feature.transfer.SelectBankScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SuccessRate) {
            com.westly.opay.feature.transfer.SuccessRateScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 9
        // BEGIN PHASE 10
        composable(Routes.TransferAmount) {
            com.westly.opay.feature.transfer.TransferAmountScreen(
                onBack = { navController.popBackStack() },
                onRecords = {
                    navController.navigate(Routes.Transactions) { launchSingleTop = true }
                },
                onPaid = {
                    navController.navigate(Routes.TransferSuccess) {
                        popUpTo(Routes.Home)
                    }
                }
            )
        }
        // END PHASE 10
        // BEGIN PHASE 11
        composable(
            route = Routes.TransferSuccess,
            enterTransition = {
                if (com.westly.opay.feature.transfer.SelectedTransaction.current.value == null) {
                    androidx.compose.animation.EnterTransition.None
                } else {
                    slideInHorizontally(tween(SLIDE_MILLIS)) { it }
                }
            }
        ) {
            com.westly.opay.feature.transfer.TransferSuccessScreen(
                onDone = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Home) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                        if (com.westly.opay.feature.transfer.SelectedTransaction.current.value == null) {
                            popUpTo(Routes.TransferSuccess) { inclusive = true }
                        }
                    }
                }
            )
        }
        // END PHASE 11
        // BEGIN PHASE 12
        composable(Routes.Receipt) {
            com.westly.opay.feature.receipt.ReceiptScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TxDetails) {
            com.westly.opay.feature.receipt.TransactionDetailsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 12
        // BEGIN PHASE 13
        // END PHASE 13
        // BEGIN PHASE 14
        composable(Routes.TransferOpay) {
            com.westly.opay.feature.transfer.TransferOpayScreen(
                onBack = { navController.popBackStack() },
                onOpenAmount = {
                    navController.navigate(Routes.TransferAmount) { launchSingleTop = true }
                },
                onHistory = {
                    navController.navigate(Routes.Transactions) { launchSingleTop = true }
                },
                onBeneficiaries = {
                    navController.navigate(Routes.Beneficiaries) { launchSingleTop = true }
                },
                onScan = {
                    navController.navigate(Routes.QrScan) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 14
        // BEGIN PHASE B15A
        composable(Routes.Beneficiaries) {
            com.westly.opay.feature.transfer.BeneficiariesScreen(
                onBack = { navController.popBackStack() },
                onOpenAmount = {
                    navController.navigate(Routes.TransferAmount) { launchSingleTop = true }
                },
                onOpenBank = {
                    if (!navController.popBackStack(Routes.TransferBank, false)) {
                        navController.navigate(Routes.TransferBank) { launchSingleTop = true }
                    }
                }
            )
        }
        // END PHASE B15A
        // BEGIN PHASE 15
        composable(Routes.Transactions) {
            com.westly.opay.feature.transactions.TransactionsScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = {
                    navController.navigate(Routes.TxDetails) { launchSingleTop = true }
                },
                onDownload = {
                    navController.navigate(Routes.DownloadStatement) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 15
        // BEGIN PHASE 16
        composable(Routes.Notifications) {
            com.westly.opay.feature.notifications.NotificationsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.AddMoney) {
            com.westly.opay.feature.addmoney.AddMoneyScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 16
        // BEGIN PHASE 17
        composable(Routes.QrScan) {
            com.westly.opay.feature.scan.QrScanScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Cards) {
            com.westly.opay.feature.cards.CardsScreen()
        }
        // END PHASE 17
        // BEGIN PHASE 18
        composable(Routes.Rewards) {
            com.westly.opay.feature.rewards.RewardsScreen(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Invite) {
            com.westly.opay.feature.invite.InviteScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 18
        // BEGIN PHASE 19
        composable(Routes.Wealth) {
            com.westly.opay.feature.wealth.WealthScreen(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Owealth) {
            com.westly.opay.feature.wealth.OWealthScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 19
        // BEGIN PHASE 20
        composable(Routes.SafeBox) {
            com.westly.opay.feature.savings.SafeBoxScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Targets) {
            com.westly.opay.feature.savings.TargetsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Fixed) {
            com.westly.opay.feature.savings.FixedScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SpendSave) {
            com.westly.opay.feature.savings.SpendSaveScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 20
        // BEGIN PHASE 21
        composable(Routes.Airtime) {
            com.westly.opay.feature.bills.AirtimeScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Data) {
            com.westly.opay.feature.bills.DataScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Ussd) {
            com.westly.opay.feature.bills.UssdScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.AllService) {
            com.westly.opay.feature.services.AllServiceScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 21
        // BEGIN PHASE 22
        composable(Routes.Betting) {
            com.westly.opay.feature.bills.BettingScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Tv) {
            com.westly.opay.feature.bills.TvScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Loan) {
            com.westly.opay.feature.loan.LoanScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 22
        // BEGIN PHASE 23
        composable(Routes.Me) {
            com.westly.opay.feature.me.MeScreen(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onSignedOut = logout
            )
        }
        composable(Routes.Profile) {
            com.westly.opay.feature.me.ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.AccountLimits) {
            com.westly.opay.feature.me.AccountLimitsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 23
        // BEGIN PHASE 24
        composable(Routes.BankCards) {
            com.westly.opay.feature.me.BankCardsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BizPayment) {
            com.westly.opay.feature.biz.BizPaymentScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.BizReceived) {
            com.westly.opay.feature.biz.BizReceivedScreen(
                initialTodayOnly = false,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BizReceivedToday) {
            com.westly.opay.feature.biz.BizReceivedScreen(
                initialTodayOnly = true,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Ojunior) {
            com.westly.opay.feature.me.OJuniorScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 24
        // BEGIN PHASE 25
        composable(Routes.Settings) {
            com.westly.opay.feature.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onSignedOut = logout
            )
        }
        composable(Routes.SavingsSettings) {
            com.westly.opay.feature.settings.SavingsSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Themes) {
            com.westly.opay.feature.settings.ThemesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 25
        // BEGIN PHASE 26
        composable(Routes.SmsAlerts) {
            com.westly.opay.feature.settings.SmsAlertsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SecurityQuestions) {
            com.westly.opay.feature.settings.SecurityQuestionsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SecurityPlus) {
            com.westly.opay.feature.settings.SecurityPlusScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CloseAccount) {
            com.westly.opay.feature.settings.CloseAccountScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.About) {
            com.westly.opay.feature.settings.AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END PHASE 26
        // BEGIN PHASE 27
        composable(Routes.SecurityCenter) {
            com.westly.opay.feature.security.SecurityCenterScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Help) {
            com.westly.opay.feature.help.HelpScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            )
        }
        // END PHASE 27
        // BEGIN DOWNLOAD STATEMENT PHASE 1
        composable(Routes.DownloadStatement) {
            com.westly.opay.feature.statement.DownloadStatementScreen(
                onBack = { navController.popBackStack() },
                onRecords = {
                    navController.navigate(Routes.DownloadRecords) { launchSingleTop = true }
                },
                onForgotPin = {
                    navController.navigate(Routes.ForgotPaymentPin) { launchSingleTop = true }
                },
                onStarted = {
                    navController.navigate(Routes.StatementGenerating) {
                        popUpTo(Routes.DownloadStatement) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.StatementGenerating) {
            com.westly.opay.feature.statement.StatementGeneratingScreen(
                onBack = { navController.popBackStack() },
                onOkay = {
                    if (!navController.popBackStack(Routes.Transactions, false)) navController.popBackStack()
                },
                onRecords = {
                    navController.navigate(Routes.DownloadRecords) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.DownloadRecords) {
            com.westly.opay.feature.statement.StatementRecordsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        // END DOWNLOAD STATEMENT PHASE 1
    }
}
