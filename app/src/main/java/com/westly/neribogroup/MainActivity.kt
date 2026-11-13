package com.westly.neribogroup

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.neribogroup.feature.common.BiometricGate
import com.westly.neribogroup.feature.common.BiometricGateNotice
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.appdetail.AppDashboardScreen
import com.westly.neribogroup.cloud.CloudMode
import com.westly.neribogroup.feature.cloud.AiCard
import com.westly.neribogroup.feature.cloud.AiCardState
import com.westly.neribogroup.feature.cloud.CloudCard
import com.westly.neribogroup.feature.cloud.CloudCardState
import com.westly.neribogroup.feature.cloud.ConflictDialog
import com.westly.neribogroup.feature.cloud.cloudDotFor
import com.westly.neribogroup.feature.cloud.LoginScreen
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.feature.group.AppsScreen
import com.westly.neribogroup.feature.group.CaseDetailScreen
import com.westly.neribogroup.feature.group.CasesScreen
import com.westly.neribogroup.feature.group.CompanyCostsScreen
import com.westly.neribogroup.feature.group.GroupDashboardScreen
import com.westly.neribogroup.data.Channel
import com.westly.neribogroup.feature.group.ChannelBalanceScreen
import com.westly.neribogroup.feature.group.DeploySitesScreen
import com.westly.neribogroup.feature.group.DocumentsScreen
import com.westly.neribogroup.feature.group.CompanyStorageScreen
import com.westly.neribogroup.feature.group.EquipmentScreen
import com.westly.neribogroup.feature.group.PlayBalanceScreen
import com.westly.neribogroup.feature.group.RenewalsScreen
import com.westly.neribogroup.feature.group.SettingsScreen
import com.westly.neribogroup.feature.group.SocialsScreen
import com.westly.neribogroup.feature.group.StaffListScreen
import com.westly.neribogroup.feature.storageroom.RoomActions
import com.westly.neribogroup.feature.storageroom.RoomCloudInfo
import com.westly.neribogroup.feature.storageroom.StorageRoomScreen
import com.westly.neribogroup.feature.software.SoftwareClientsScreen
import com.westly.neribogroup.feature.software.SoftwareOfferScreen
import com.westly.neribogroup.feature.software.SoftwareProjectScreen
import com.westly.neribogroup.feature.staff.StaffDetailScreen
import com.westly.neribogroup.ui.theme.NeriboTheme

// FragmentActivity (a ComponentActivity underneath) is what Android's BiometricPrompt needs.
class MainActivity : FragmentActivity() {
    // The same ViewModel that setContent gets below (both belong to this activity).
    private val cloudVm: AppViewModel by viewModels()

    // The one gate for sensitive actions: fingerprint or screen lock, open for 5 minutes after a success.
    private val gate: BiometricGate by lazy { BiometricGate(this) { cloudVm.logVerified(it) } }

    override fun onResume() {
        super.onResume()
        cloudVm.onAppResumed()
    }

    override fun onPause() {
        cloudVm.onAppPaused()
        super.onPause()
    }

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
                CompositionLocalProvider(LocalBiometricGate provides gate) {
                    NeriboRoot(vm)
                    BiometricGateNotice(gate)
                }
            }
        }
    }
}

@Composable
private fun NeriboRoot(vm: AppViewModel) {
    val context = LocalContext.current
    val gate = LocalBiometricGate.current

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
                if (ok) "Backup saved. Photos are not inside the backup file; they sync through the cloud." else "Backup failed",
                if (ok) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
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
            // Replacing everything on the phone: the owner is verified right before it happens.
            gate.verify(
                title = "Import backup file?",
                subtitle = "This replaces everything on this phone with the backup file."
            ) {
                val toCloud = vm.importCopiesToCloud()
                val ok = text != null && vm.importJson(text)
                if (ok) vm.logVerified("Backup file imported")
                Toast.makeText(
                    context,
                    if (ok && toCloud) {
                        "Backup restored. Imported data will be copied to the cloud"
                    } else if (ok) {
                        "Backup restored"
                    } else {
                        "That file is not a valid backup"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Phase 4B: one toast when Claude rolled an app.
    val aiNotice = vm.aiNotice
    if (aiNotice.isNotEmpty()) {
        LaunchedEffect(aiNotice) {
            Toast.makeText(context, aiNotice, Toast.LENGTH_LONG).show()
            vm.consumeAiNotice()
        }
    }

    // Staff photos: one toast when adding photos did not go fully as asked.
    val photoNotice = vm.photoNotice
    if (photoNotice.isNotEmpty()) {
        LaunchedEffect(photoNotice) {
            Toast.makeText(context, photoNotice, Toast.LENGTH_LONG).show()
            vm.consumePhotoNotice()
        }
    }

    // The conflict question: one at a time, never closes without a choice, never chooses by itself.
    val waitingConflict = vm.cloudConflicts.firstOrNull()
    if (waitingConflict != null && !vm.cloudSyncing && !vm.cloudWorking) {
        ConflictDialog(
            name = waitingConflict.friendlyName,
            onKeepPhone = { vm.resolveConflictKeepPhone(waitingConflict.key) },
            onUseCloud = { vm.resolveConflictUseCloud(waitingConflict.key) }
        )
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
                        selfCheckRunning = vm.selfCheckRunning,
                        syncing = vm.cloudSyncing,
                        syncMessage = vm.cloudSyncMessage,
                        status = vm.cloudStatus,
                        pendingChanges = vm.cloudPendingChanges,
                        unsyncedDocs = vm.cloudUnsyncedDocs,
                        backupBusy = vm.cloudBackupBusy,
                        backupMessage = vm.cloudBackupMessage,
                        mediaMessage = vm.mediaMessage
                    ),
                    selfCheckLines = vm.selfCheckLines,
                    onConnect = { vm.openLogin() },
                    onTryAgain = { vm.retryLink() },
                    onSignOut = { vm.signOutCloud() },
                    onSelfCheck = { vm.runSelfCheck() },
                    onCloseSelfCheck = { vm.closeSelfCheck() },
                    onSyncNow = { vm.syncNowAction() },
                    onBackUp = { vm.backUpNowAction() },
                    history = vm.cloudHistory,
                    onOpenHistory = { vm.openCloudHistory() },
                    onPickHistoryDoc = { vm.pickHistoryDoc(it) },
                    onHistoryBack = { vm.backToHistoryList() },
                    onHistoryClose = { vm.closeCloudHistory() },
                    onRestoreVersion = { vm.restoreHistoryVersion(it) }
                )
            },
            ai = {
                AiCard(
                    state = AiCardState(
                        cloudReady = vm.cloudMode == CloudMode.ON && vm.cloudSignedIn && vm.cloudInitialized,
                        aiMode = vm.aiMode,
                        running = vm.aiRunning,
                        message = vm.aiMessage,
                        waiting = vm.aiWaiting,
                        answered = vm.aiAnswered,
                        appliedToday = vm.aiAppliedToday(),
                        settings = vm.aiSettings,
                        settingsBusy = vm.aiSettingsBusy,
                        settingsMessage = vm.aiSettingsMessage,
                        casesWaiting = vm.aiCasesWaiting
                    ),
                    onToggle = { vm.updateAiMode(it) },
                    onCheck = { vm.runAiJobs() },
                    onReloadSettings = { vm.loadAiSettings() },
                    onSaveSettings = { vm.saveAiSettings(it) },
                    onWriteCasesBuiltIn = { vm.writeCasesBuiltIn() }
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
            onUseLiveRate = { vm.useLiveRate(it) },
            aiOn = vm.aiActive,
            aiStateFor = { n -> vm.aiRollStateForSoftware(n) },
            aiChecking = vm.aiRunning,
            onAiCheck = { vm.runAiJobs() },
            softwareAttempts = vm.softwareAttemptsToday(System.currentTimeMillis())
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
            onStartAdvert = { name, type, channel, platforms, startIn, lasts ->
                vm.startAdvert(openApp.id, name, type, channel, platforms, startIn, lasts)
            },
            onStopAdvert = { advertId -> vm.stopAdvert(openApp.id, advertId) },
            chargedToCompany = vm.ledger.company.expenses.filter { it.appId == openApp.id },
            onEnableLive = { play, apple, website -> vm.enableLive(openApp.id, play, apple, website) },
            onSetChannels = { play, apple, website -> vm.setChannels(openApp.id, play, apple, website) },
            onSetDeploy = { vm.setDeployTracking(openApp.id, it) },
            onSetStorage = { free, pro, premium, site, verified ->
                vm.setStorageOffer(openApp.id, free, pro, premium, site, verified)
            },
            onAddCompanyExpense = { title, amount -> vm.addAppExpense(openApp.id, title, amount) },
            onAddIncome = { title, amount -> vm.addAppIncome(openApp.id, title, amount) },
            onRenewalIncome = { userId, amount -> vm.recordRenewalIncome(openApp.id, userId, amount) },
            onWithdrawChannel = { channel -> vm.withdrawChannelApp(openApp.id, channel) },
            aiOn = vm.aiActive,
            aiStateFor = { n -> vm.aiRollStateFor(openApp, n) },
            aiChecking = vm.aiRunning,
            onAiCheck = { vm.runAiJobs() },
            onAiRollBuiltIn = { vm.aiRollBuiltIn(openApp.id) },
            extraAdvertPlatforms = vm.extraAdvertPlatformNames()
        )
    } else if (openStaff != null) {
        StaffDetailScreen(
            staff = openStaff,
            onBack = { vm.openStaffId = null },
            onSave = { vm.saveStaff(it) },
            onPay = { vm.payStaff(openStaff.id) },
            onDelete = { vm.deleteStaff(openStaff.id) },
            photoFile = { vm.staffImageFile(openStaff.id, it.id) },
            mediaTick = vm.mediaTick,
            photoBusy = vm.photoBusy,
            waitingPhotoIds = vm.pendingPhotoIds,
            onAddPhotos = { vm.addStaffPhotos(openStaff.id, it) },
            onDeletePhoto = { vm.removeStaffPhoto(openStaff.id, it) }
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
            Section.APPLE -> ChannelBalanceScreen(
                ledger = vm.ledger,
                channel = Channel.APPLE,
                onBack = { vm.openSection = null },
                onWithdrawApp = { vm.withdrawChannelApp(it, Channel.APPLE) },
                onWithdrawAll = { vm.withdrawChannelAll(Channel.APPLE) }
            )
            Section.PAYSTACK -> ChannelBalanceScreen(
                ledger = vm.ledger,
                channel = Channel.WEBSITE,
                onBack = { vm.openSection = null },
                onWithdrawApp = { vm.withdrawChannelApp(it, Channel.WEBSITE) },
                onWithdrawAll = { vm.withdrawChannelAll(Channel.WEBSITE) }
            )
            Section.DOCS -> DocumentsScreen(
                ledger = vm.ledger,
                photoFile = { vm.assetPhotoFile("doc", it) },
                photoTick = vm.assetPhotoTick,
                onBack = { vm.openSection = null },
                onSave = { vm.saveAssets(it) },
                onPickPhoto = { id, uri, done -> vm.saveAssetPhoto("doc", id, uri, done) },
                onDeletePhoto = { vm.deleteAssetPhoto("doc", it) }
            )
            Section.TOOLS -> EquipmentScreen(
                ledger = vm.ledger,
                photoFile = { id, index -> vm.toolPhotoFile(id, index) },
                photoTick = vm.assetPhotoTick,
                onBack = { vm.openSection = null },
                onSave = { vm.saveAssets(it) },
                onPickPhoto = { id, index, uri, done -> vm.saveToolPhoto(id, index, uri, done) },
                onRemovePhoto = { id, index, count -> vm.removeToolPhoto(id, index, count) },
                onDeletePhotos = { vm.deleteToolPhotos(it) }
            )
            Section.DEPLOY -> DeploySitesScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null }
            )
            Section.STORAGE -> CompanyStorageScreen(
                ledger = vm.ledger,
                message = vm.storageMessage,
                onBack = { vm.openSection = null },
                onSetTotal = { mb, confirmed -> vm.setCompanyStorageTotal(mb, confirmed) },
                onSetShare = { id, mb, confirmed -> vm.setAppStorageShare(id, mb, confirmed) },
                onBackUpOffsite = { vm.backUpOffsiteNow() },
                onClearMessage = { vm.clearStorageMessage() }
            )
            Section.STORAGE_ROOM -> StorageRoomScreen(
                ledger = vm.ledger,
                cloud = RoomCloudInfo(
                    on = vm.cloudMode == CloudMode.ON && vm.cloudSignedIn,
                    syncing = vm.cloudSyncing,
                    problem = vm.cloudLastError
                ),
                cloudFeed = vm.storageRoomCloud,
                actions = RoomActions(
                    setTotal = { mb, confirmed -> vm.setCompanyStorageTotal(mb, confirmed) },
                    setShare = { id, mb, confirmed -> vm.setAppStorageShare(id, mb, confirmed) },
                    setOffer = { id, free, pro, premium, verified ->
                        val site = vm.ledger.apps.firstOrNull { it.id == id }?.storageSiteMb ?: 0L
                        vm.setStorageOffer(id, free, pro, premium, site, verified)
                    },
                    setUsers = { id, free, pro, premium -> vm.setManualPlanUsers(id, free, pro, premium) },
                    backUpOffsite = { vm.backUpOffsiteNow() }
                ),
                onCreatePin = { vm.setStoragePin(it) },
                onPullFromCloud = { vm.syncNowAction() },
                onBack = { vm.openSection = null }
            )
            Section.SOCIALS -> SocialsScreen(
                ledger = vm.ledger,
                onBack = { vm.openSection = null },
                rollInfoFor = { n -> vm.socialsRollInfo(n) },
                todayKeyFor = { n -> dayKey(vm.socialsNow(n)) },
                aiOn = vm.aiActive,
                aiChecking = vm.aiRunning,
                onAiCheck = { vm.runAiJobs() },
                onAddAccount = { name, handle, display -> vm.addSocialAccount(name, handle, display) },
                deleteBlockFor = { id, n -> vm.socialDeleteBlock(id, n) },
                onDeleteAccount = { id -> vm.deleteSocialAccount(id) },
                onSaveEdits = { vm.saveSocialEdits(it) },
                aiStateFor = { n -> vm.aiRollStateForSocials(n) },
                claudeMessage = vm.aiSocialsMessage,
                fileFor = { id -> vm.socialPhotoFile(id) },
                mediaTick = vm.mediaTick,
                photoBusy = vm.photoBusy,
                waitingPhotoIds = vm.pendingSocialPhotoIds,
                onSetPhoto = { id, uri -> vm.setSocialPhoto(id, uri) },
                onRemovePhoto = { id -> vm.removeSocialPhoto(id) }
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
                cloudDot = cloudDotFor(
                    vm.cloudMode,
                    vm.cloudSignedIn,
                    vm.cloudInitialized,
                    vm.cloudStatus,
                    maxOf(vm.cloudPendingChanges, vm.cloudUnsyncedDocs)
                ),
                onOpenApp = { vm.openAppId = it },
                onOpenStaff = { vm.openStaffId = it },
                onSave = { vm.saveLedger(it) },
                onExport = { exportLauncher.launch("neribo-group-backup.json") },
                onImport = { importLauncher.launch(arrayOf("*/*")) }
            )
        }
    }
}
