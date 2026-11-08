package com.westly.neribogroup

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.neribogroup.feature.appdetail.AppDashboardScreen
import com.westly.neribogroup.feature.cloud.CloudCard
import com.westly.neribogroup.feature.cloud.CloudCardState
import com.westly.neribogroup.feature.cloud.LoginScreen
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.feature.group.AppsScreen
import com.westly.neribogroup.feature.group.CaseDetailScreen
import com.westly.neribogroup.feature.group.CasesScreen
import com.westly.neribogroup.feature.group.CompanyCostsScreen
import com.westly.neribogroup.feature.group.GroupDashboardScreen
import com.westly.neribogroup.feature.group.PlayBalanceScreen
import com.westly.neribogroup.feature.group.RenewalsScreen
import com.westly.neribogroup.feature.group.SettingsScreen
import com.westly.neribogroup.feature.group.StaffListScreen
import com.westly.neribogroup.feature.software.SoftwareClientsScreen
import com.westly.neribogroup.feature.software.SoftwareOfferScreen
import com.westly.neribogroup.feature.software.SoftwareProjectScreen
import com.westly.neribogroup.feature.staff.StaffDetailScreen
import com.westly.neribogroup.ui.theme.NeriboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The app uses its own theme, so the window behind the first frame follows the saved choice.
        val dark = getSharedPreferences("neribo_settings", MODE_PRIVATE).getBoolean("theme_dark", true)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(if (dark) 0xFF101414.toInt() else 0xFFF6F8F7.toInt())
        )
        setContent {
            val vm: AppViewModel = viewModel()
            NeriboTheme(darkTheme = vm.darkTheme, fontStep = vm.fontStep) {
                NeriboRoot(vm)
            }
        }
    }
}

@Composable
private fun NeriboRoot(vm: AppViewModel) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the file")
                stream.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Backup saved" else "Backup failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            val ok = text != null && vm.importJson(text)
            Toast.makeText(
                context,
                if (ok) "Backup restored" else "That file is not a valid backup",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val openApp = vm.openAppId?.let { id -> vm.ledger.apps.firstOrNull { it.id == id } }
    val openStaff = vm.openStaffId?.let { id -> vm.ledger.staff.firstOrNull { it.id == id } }
    val openCase = vm.openCaseId?.let { id -> vm.ledger.cases.firstOrNull { it.id == id } }
    val openOffer = vm.openOfferId?.let { id -> vm.ledger.software.offers.firstOrNull { it.id == id } }
    val openProject = vm.openProjectId?.let { id -> vm.ledger.software.projects.firstOrNull { it.id == id } }

    if (openCase != null) {
        CaseDetailScreen(
            ledger = vm.ledger,
            case = openCase,
            onBack = { vm.openCaseId = null },
            onClock = { vm.updateCasesClock(it) },
            onMarkPaid = { vm.markCasePaid(openCase.id) },
            onAddNote = { vm.addCaseNote(openCase.id, it) },
            onOpenApp = { id ->
                vm.openCaseId = null
                vm.openAppId = id
            }
        )
    } else if (vm.showLogin) {
        LoginScreen(
            initialUrl = vm.cloudSavedUrl(),
            initialAnonKey = vm.cloudSavedAnonKey(),
            initialEmail = vm.cloudEmail,
            linkedUrl = vm.cloudLinkedUrl(),
            signedIn = vm.cloudSignedIn,
            working = vm.cloudWorking,
            errorMessage = vm.loginMessage,
            linkStep = vm.linkStep,
            linkIsDownload = vm.linkIsDownload,
            linkMessage = vm.linkMessage,
            linkOk = vm.linkOk,
            dialog = vm.linkDialog,
            onSignIn = { url, key, email, password, reset -> vm.startSignIn(url, key, email, password, reset) },
            onTryAgain = { vm.retryLink() },
            onBack = { vm.closeLogin() },
            onUseCloud = { vm.useCloudData() },
            onKeepPhone = { vm.keepPhoneData() },
            onAskReplace = { vm.askReplaceCloud() },
            onBackToChoice = { vm.backToCaseCChoice() },
            onConfirmReplace = { vm.confirmReplaceCloud() }
        )
    } else if (vm.showSettings && vm.openSection == null && openApp == null && openStaff == null) {
        SettingsScreen(
            darkTheme = vm.darkTheme,
            fontStep = vm.fontStep,
            onBack = { vm.showSettings = false },
            onDarkTheme = { vm.updateDarkTheme(it) },
            onFontStep = { vm.updateFontStep(it) },
            cloud = {
                CloudCard(
                    state = CloudCardState(
                        mode = vm.cloudMode,
                        signedIn = vm.cloudSignedIn,
                        initialized = vm.cloudInitialized,
                        working = vm.cloudWorking,
                        email = vm.cloudEmail,
                        lastSyncMillis = vm.cloudLastSyncMillis,
                        problem = vm.cloudProblem,
                        deviceId = vm.cloudDeviceId(),
                        warning = vm.cloudWarning,
                        selfCheckRunning = vm.selfCheckRunning
                    ),
                    selfCheckLines = vm.selfCheckLines,
                    onConnect = { vm.openLogin() },
                    onTryAgain = { vm.retryLink() },
                    onSignOut = { vm.signOutCloud() },
                    onSelfCheck = { vm.runSelfCheck() },
                    onCloseSelfCheck = { vm.closeSelfCheck() }
                )
            }
        )
    } else if (openOffer != null) {
        SoftwareOfferScreen(
            ledger = vm.ledger,
            offer = openOffer,
            onBack = { vm.openOfferId = null },
            onClock = { vm.updateSoftwareClock(it) },
            onAccept = { vm.acceptSoftwareOffer(openOffer.id) },
            onDecline = { vm.declineSoftwareOffer(openOffer.id) },
            onOpenProject = { projectId ->
                vm.openOfferId = null
                vm.openProjectId = projectId
            }
        )
    } else if (openProject != null) {
        SoftwareProjectScreen(
            ledger = vm.ledger,
            project = openProject,
            onBack = { vm.openProjectId = null },
            onClock = { vm.updateSoftwareClock(it) },
            onStage = { vm.changeProjectStage(openProject.id, it) },
            onRecordMaintenance = { vm.recordMaintenancePayment(openProject.id) },
            onStopMaintenance = { vm.stopProjectMaintenance(openProject.id) },
            onAddExpense = { title, amount -> vm.addProjectExpense(openProject.id, title, amount) }
        )
    } else if (openApp != null && openApp.id == SOFTWARE_APP_ID) {
        SoftwareClientsScreen(
            ledger = vm.ledger,
            ratesLoading = vm.ratesLoading,
            ratesMessage = vm.ratesMessage,
            onBack = { vm.openAppId = null },
            onClock = { vm.updateSoftwareClock(it) },
            onRoll = { vm.rollSoftware() },
            onOpenOffer = { vm.openOfferId = it },
            onOpenProject = { vm.openProjectId = it },
            onConvert = { cents, rate, requestId -> vm.convertUsdToNaira(cents, rate, requestId) },
            onRefreshRates = { vm.refreshRates() },
            onSaveRate = { code, value -> vm.saveRate(code, value) },
            onUseLiveRate = { vm.useLiveRate(it) }
        )
    } else if (openApp != null) {
        AppDashboardScreen(
            app = openApp,
            onBack = { vm.openAppId = null },
            onSave = { vm.saveApp(it) },
            onDelete = { vm.deleteApp(openApp.id) },
            onRoll = { vm.rollApp(openApp.id) },
            onWithdraw = { vm.withdrawApp(openApp.id) },
            onStartCampaign = { size, startIn, lasts ->
                vm.startCampaign(openApp.id, size, startIn, lasts)
            },
            onStopCampaign = { vm.stopCampaign(openApp.id) },
            chargedToCompany = vm.ledger.company.expenses.filter { it.appId == openApp.id },
            onEnableGooglePlay = { vm.enableGooglePlay(openApp.id) },
            onAddCompanyExpense = { title, amount -> vm.addAppExpense(openApp.id, title, amount) }
        )
    } else if (openStaff != null) {
        StaffDetailScreen(
            staff = openStaff,
            onBack = { vm.openStaffId = null },
            onSave = { vm.saveStaff(it) },
            onPay = { vm.payStaff(openStaff.id) },
            onDelete = { vm.deleteStaff(openStaff.id) }
        )
    } else {
        when (vm.openSection) {
            Section.APPS -> AppsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onOpenApp = { vm.openAppId = it },
                onSave = { vm.saveLedger(it) }
            )
            Section.RENEWALS -> RenewalsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onRenew = { vm.renew(it) },
                onSave = { vm.saveLedger(it) }
            )
            Section.STAFF -> StaffListScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onOpenStaff = { vm.openStaffId = it },
                onPayAllStaff = { vm.payAllStaff() },
                onSave = { vm.saveLedger(it) }
            )
            Section.COSTS -> CompanyCostsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onSave = { vm.saveLedger(it) }
            )
            Section.PLAY -> PlayBalanceScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                onWithdrawApp = { vm.withdrawApp(it) },
                onWithdrawAll = { vm.withdrawAll() }
            )
            Section.CASES -> CasesScreen(
                ledger = vm.ledger,
                initialTab = vm.casesStartTab,
                onBack = { vm.openSection = null },
                onClock = { vm.updateCasesClock(it) },
                onOpenCase = { vm.openCaseId = it }
            )
            Section.USD -> SoftwareClientsScreen(
                ledger = vm.ledger,
                ratesLoading = vm.ratesLoading,
                ratesMessage = vm.ratesMessage,
                onBack = { vm.openSection = null },
                onClock = { vm.updateSoftwareClock(it) },
                onRoll = { vm.rollSoftware() },
                onOpenOffer = { vm.openOfferId = it },
                onOpenProject = { vm.openProjectId = it },
                onConvert = { cents, rate, requestId -> vm.convertUsdToNaira(cents, rate, requestId) },
                onRefreshRates = { vm.refreshRates() },
                onSaveRate = { code, value -> vm.saveRate(code, value) },
                onUseLiveRate = { vm.useLiveRate(it) },
                usdOnly = true
            )
            null -> GroupDashboardScreen(
                ledger = vm.ledger,
                onOpenSection = {
                    vm.casesStartTab = 0
                    vm.openSection = it
                },
                onOpenLegal = {
                    vm.casesStartTab = 3
                    vm.openSection = Section.CASES
                },
                onOpenCase = { vm.openCaseId = it },
                onClock = { vm.updateCasesClock(it) },
                onOpenSettings = { vm.showSettings = true },
                onOpenApp = { vm.openAppId = it },
                onOpenStaff = { vm.openStaffId = it },
                onSave = { vm.saveLedger(it) },
                onExport = { exportLauncher.launch("neribo-group-backup.json") },
                onImport = { importLauncher.launch(arrayOf("*/*")) }
            )
        }
    }
}
