package com.westly.neribogroup

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.westly.neribogroup.cloud.AuthResult
import com.westly.neribogroup.cloud.CloudConfig
import com.westly.neribogroup.cloud.CloudConflictInfo
import com.westly.neribogroup.cloud.CloudMode
import com.westly.neribogroup.cloud.CloudStatus
import com.westly.neribogroup.cloud.LinkOutcome
import com.westly.neribogroup.cloud.LedgerSplitter
import com.westly.neribogroup.cloud.SupabaseApi
import com.westly.neribogroup.cloud.SyncHost
import com.westly.neribogroup.cloud.SyncResult
import com.westly.neribogroup.cloud.SyncRules
import com.westly.neribogroup.cloud.SyncEngine
import com.westly.neribogroup.cloud.SyncStore
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.ProjectStage
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareRates
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.data.markCasePaid
import com.westly.neribogroup.data.withCaseNote
import com.westly.neribogroup.data.withClockAtLeast
import com.westly.neribogroup.data.withSoftwareApp
import com.westly.neribogroup.feature.cloud.CheckLine
import com.westly.neribogroup.feature.cloud.LinkDialog
import com.westly.neribogroup.feature.cloud.SelfCheck
import com.westly.neribogroup.ui.formatDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random

/** The pages that open from the home screen tiles. */
enum class Section { APPS, RENEWALS, STAFF, COSTS, PLAY, CASES, USD }

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var ledger by mutableStateOf(LedgerStorage.load(application))
        private set

    /** The page opened from a home tile (Apps, Renewals, Staff or Company costs), or null on the home screen. */
    var openSection by mutableStateOf<Section?>(null)

    /** The id of the app dashboard that is open, or null when it is not showing. */
    var openAppId by mutableStateOf<String?>(null)

    /** The id of the staff member that is open, or null when it is not showing. */
    var openStaffId by mutableStateOf<String?>(null)

    /** The case that is open, or null when it is not showing. */
    var openCaseId by mutableStateOf<String?>(null)

    /** The software proposal that is open, or null when it is not showing. */
    var openOfferId by mutableStateOf<String?>(null)

    /** The software project that is open, or null when it is not showing. */
    var openProjectId by mutableStateOf<String?>(null)

    /** True while the live exchange rates are being fetched. */
    var ratesLoading by mutableStateOf(false)
        private set

    /** A short line about the last rates refresh. Empty when there is nothing to say. */
    var ratesMessage by mutableStateOf("")
        private set

    /** Which tab the Cases screen opens on (0 Open, 1 Resolved, 2 Escalated, 3 Legal). */
    var casesStartTab by mutableStateOf(0)

    /** True while the Settings screen is showing (opened from the Home three-dot menu). */
    var showSettings by mutableStateOf(false)

    // Appearance: the app's own theme and font size. These are device preferences, not part of the backup.
    private val prefs = application.getSharedPreferences("neribo_settings", Context.MODE_PRIVATE)

    var darkTheme by mutableStateOf(prefs.getBoolean("theme_dark", true))
        private set

    var fontStep by mutableStateOf(prefs.getInt("font_step", 2).coerceIn(0, 5))
        private set

    fun updateDarkTheme(dark: Boolean) {
        darkTheme = dark
        prefs.edit().putBoolean("theme_dark", dark).apply()
    }

    fun updateFontStep(step: Int) {
        val fixed = step.coerceIn(0, 5)
        fontStep = fixed
        prefs.edit().putInt("font_step", fixed).apply()
    }

    // ---- Cloud (Phase 3A: login and the first upload only; nothing here runs unless the owner connects) ----

    private val cloudConfig = CloudConfig(application)
    private val cloudApi = SupabaseApi(cloudConfig)
    private val cloudEngine = SyncEngine(application, cloudConfig, cloudApi)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startSync = SyncStore.loadIfExists(application)

    var cloudMode by mutableStateOf(cloudConfig.cloudMode)
        private set

    var cloudSignedIn by mutableStateOf(cloudConfig.signedIn)
        private set

    var cloudEmail by mutableStateOf(cloudConfig.email)
        private set

    /** True once the first upload (or the download of the cloud data) was verified on this phone. */
    var cloudInitialized by mutableStateOf(startSync?.initialized ?: false)
        private set

    var cloudLastSyncMillis by mutableStateOf(startSync?.lastSyncMillis ?: 0L)
        private set

    /** The last plain-words problem of the link (also kept in cloud_sync.json). */
    var cloudProblem by mutableStateOf(startSync?.lastError ?: "")
        private set

    /** True while any cloud task runs (sign-in, link, download, replace). Ignores a second tap. */
    var cloudWorking by mutableStateOf(false)
        private set

    /** True while the Login / progress screen is showing (opened from the Cloud card in Settings). */
    var showLogin by mutableStateOf(false)
        private set

    /** The error line of the sign-in form. */
    var loginMessage by mutableStateOf("")
        private set

    /** -1 = nothing running; 0 safety copy, 1 uploading or downloading, 2 checking, 3 done. */
    var linkStep by mutableStateOf(-1)
        private set

    var linkIsDownload by mutableStateOf(false)
        private set

    /** The result line of the link: a problem in plain words, or the success line. */
    var linkMessage by mutableStateOf("")
        private set

    var linkOk by mutableStateOf(false)
        private set

    var linkDialog by mutableStateOf(LinkDialog.NONE)
        private set

    var selfCheckLines by mutableStateOf<List<CheckLine>?>(null)
        private set

    var selfCheckRunning by mutableStateOf(false)
        private set

    /** A warning line for the Cloud card (a very large document). Empty when there is nothing to say. */
    var cloudWarning by mutableStateOf("")
        private set

    // ---- Cloud sync state (Phase 3B-1a). 3B-1b and 3B-2 read these. ----

    /** One word for the whole cloud state: NOT_CONNECTED, SYNCING, UP_TO_DATE, WAITING_INTERNET, SIGN_IN_AGAIN, PROBLEM. */
    var cloudStatus by mutableStateOf(CloudStatus.NOT_CONNECTED)
        private set

    /** The last sync problem in plain words (also kept in cloud_sync.json). Empty when there is none. */
    var cloudLastError by mutableStateOf(startSync?.lastError ?: "")
        private set

    /** How many changed documents are waiting in the saved outbox batch. */
    var cloudPendingChanges by mutableStateOf(startSync?.pending?.changes?.size ?: 0)
        private set

    /** How many documents on the phone differ from the cloud (counted after each sync). */
    var cloudUnsyncedDocs by mutableStateOf(0)
        private set

    /** The conflicts waiting for the owner's choice, with friendly names. */
    var cloudConflicts by mutableStateOf<List<CloudConflictInfo>>(emptyList())
        private set

    /** A short calm line about the last "Sync now". Empty when there is nothing to say. */
    var cloudSyncMessage by mutableStateOf("")
        private set

    /** True while a sync (or a conflict choice) runs. Ignores a second tap. */
    var cloudSyncing by mutableStateOf(false)
        private set

    /** The result of the last finished sync (the status shown when nothing is running). */
    private var lastSyncStatus = if ((startSync?.lastError ?: "").isEmpty()) CloudStatus.UP_TO_DATE else CloudStatus.PROBLEM

    /**
     * True only while a sync puts cloud data on the phone. 3B-1b's change trigger must ignore changes made
     * while this is true, so the cloud data is never sent back as a new "local change".
     */
    @Volatile
    var applyingCloudData = false
        private set

    private val syncHost = object : SyncHost {
        override fun currentLedger(): Ledger = ledger

        override fun applyCloudLedger(base: Ledger, newLedger: Ledger): Boolean {
            val latch = CountDownLatch(1)
            val done = AtomicBoolean(false)
            mainHandler.post {
                try {
                    // Only if nothing was saved since the sync read the ledger.
                    if (ledger === base) {
                        val text = LedgerStorage.toJson(newLedger)
                        if (LedgerStorage.saveRawText(getApplication(), text)) {
                            applyingCloudData = true
                            try {
                                ledger = newLedger
                            } finally {
                                applyingCloudData = false
                            }
                            done.set(true)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
            val finished = try {
                latch.await(15, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false
            }
            return finished && done.get()
        }
    }


    init {
        cloudStatus = computeCloudStatus()
        cloudConflicts = buildConflictInfos(startSync)
        if (startSync != null && cloudConfig.cloudMode == CloudMode.ON) {
            val snapshot = ledger
            thread(start = true) {
                val count = runCatching { cloudEngine.countUnsynced(snapshot) }.getOrDefault(0)
                mainHandler.post { cloudUnsyncedDocs = count }
            }
        }
    }

    fun cloudSavedUrl(): String = cloudConfig.projectUrl

    fun cloudSavedAnonKey(): String = cloudConfig.anonKey

    /** The saved URL when this phone is already linked, else "". */
    fun cloudLinkedUrl(): String = if (cloudInitialized) cloudConfig.projectUrl else ""

    fun cloudDeviceId(): String = cloudConfig.deviceIdIfAny

    fun openLogin() {
        loginMessage = ""
        showLogin = true
    }

    /** Leaves the login screen. Ignored while a cloud task is running. */
    fun closeLogin() {
        if (cloudWorking) return
        showLogin = false
        loginMessage = ""
        if (linkOk) {
            linkOk = false
            linkStep = -1
            linkMessage = ""
        }
    }

    /** Signs in and, on a phone that is not linked yet, runs the first-link protocol. */
    fun startSignIn(url: String, anonKey: String, email: String, password: String, resetLink: Boolean) {
        if (cloudWorking) return
        val cleanUrl = CloudConfig.normalizeUrl(url)
        if (cleanUrl == null || !cleanUrl.contains('.')) {
            loginMessage = "That URL does not look right. It should start with https://"
            return
        }
        cloudWorking = true
        loginMessage = ""
        linkMessage = ""
        linkOk = false
        linkStep = -1
        linkIsDownload = false
        val snapshot = ledger
        val cleanEmail = email.trim()
        thread(start = true) {
            var outcome: LinkOutcome? = null
            var message = ""
            var restored = false
            try {
                if (resetLink) cloudEngine.resetLinkState()
                cloudConfig.projectUrl = cleanUrl
                cloudConfig.anonKey = anonKey
                cloudConfig.email = cleanEmail
                when (val auth = cloudApi.signIn(cleanEmail, password)) {
                    AuthResult.Ok -> {
                        if (cloudEngine.loadSync().initialized) {
                            // Already linked on this phone: only the session comes back. No second upload.
                            cloudConfig.cloudMode = CloudMode.ON
                            restored = true
                            outcome = LinkOutcome.Done
                        } else {
                            cloudConfig.cloudMode = CloudMode.LINKING
                            mainHandler.post { cloudMode = CloudMode.LINKING; cloudSignedIn = true }
                            outcome = cloudEngine.link(snapshot) { step -> postStep(step) }
                        }
                    }
                    AuthResult.InvalidCredentials -> message = "Email or password is not right."
                    AuthResult.Offline -> message = "No internet. Try again when you are online."
                    is AuthResult.ServerError -> message = "The cloud had a problem (code " + auth.code + "). Try again."
                }
            } catch (e: Throwable) {
                message = "Something went wrong. Your data on the phone is untouched."
            }
            val finalOutcome = outcome
            val finalMessage = message
            val wasRestored = restored
            mainHandler.post { finishCloudTask(finalOutcome, finalMessage, wasRestored) }
        }
    }

    /** "Try again" / resume: runs the first-link protocol again for a signed-in phone. */
    fun retryLink() {
        if (cloudWorking) return
        if (!cloudSignedIn) {
            openLogin()
            return
        }
        showLogin = true
        val snapshot = ledger
        runCloudTask { onStep -> cloudEngine.link(snapshot, onStep) }
    }

    /** The owner chose "Use cloud data" (case B or C). */
    fun useCloudData() {
        if (cloudWorking) return
        runCloudTask { onStep -> cloudEngine.useCloudData(onStep) }
    }

    /** Case C: the owner typed REPLACE. */
    fun confirmReplaceCloud() {
        if (cloudWorking) return
        val snapshot = ledger
        runCloudTask { onStep -> cloudEngine.replaceCloudWithPhone(snapshot, onStep) }
    }

    fun askReplaceCloud() {
        linkDialog = LinkDialog.REPLACE_CONFIRM
    }

    fun backToCaseCChoice() {
        linkDialog = LinkDialog.CASE_C
    }

    /** "Keep phone data" / "Cancel": nothing is linked, the session is closed, the phone data is untouched. */
    fun keepPhoneData() {
        if (cloudWorking) return
        cloudWorking = true
        linkDialog = LinkDialog.NONE
        thread(start = true) {
            runCatching { cloudEngine.signOutLocal() }
            mainHandler.post {
                cloudWorking = false
                linkStep = -1
                linkMessage = ""
                refreshCloudState()
            }
        }
    }

    /** Signs out. Local data stays; the cloud copy is kept. */
    fun signOutCloud() {
        if (cloudWorking || cloudSyncing) return
        cloudWorking = true
        thread(start = true) {
            runCatching { cloudEngine.signOutLocal() }
            mainHandler.post {
                cloudWorking = false
                linkOk = false
                linkStep = -1
                linkMessage = ""
                refreshCloudState()
            }
        }
    }

    /** Runs the self-check on the real ledger in the background and shows the PASS/FAIL lines. */
    fun runSelfCheck() {
        if (selfCheckRunning || cloudWorking) return
        selfCheckRunning = true
        val snapshot = ledger
        val online = cloudSignedIn && cloudInitialized
        thread(start = true) {
            val result = try {
                val sync = if (online) cloudEngine.loadSync() else null
                SelfCheck.run(snapshot, cloudApi, sync, online)
            } catch (e: Throwable) {
                null
            }
            mainHandler.post {
                selfCheckRunning = false
                if (result == null) {
                    selfCheckLines = listOf(CheckLine("Self-check", false, "It could not run. Your data is untouched."))
                } else {
                    selfCheckLines = result.lines
                    setWarningFor(result.largestDocBytes)
                }
                refreshCloudState()
            }
        }
    }

    fun closeSelfCheck() {
        selfCheckLines = null
    }

    private fun runCloudTask(task: ((Int) -> Unit) -> LinkOutcome) {
        cloudWorking = true
        linkDialog = LinkDialog.NONE
        linkMessage = ""
        linkOk = false
        linkStep = 0
        linkIsDownload = false
        thread(start = true) {
            val outcome = try {
                task { step -> postStep(step) }
            } catch (e: Throwable) {
                LinkOutcome.Failed("Something went wrong. Your data on the phone is untouched.")
            }
            mainHandler.post { finishCloudTask(outcome, "", false) }
        }
    }

    private fun postStep(step: Int) {
        mainHandler.post {
            if (step == SyncEngine.STEP_DOWNLOAD) {
                linkIsDownload = true
                linkStep = SyncEngine.STEP_UPLOAD
            } else {
                linkStep = step
            }
        }
    }

    private fun finishCloudTask(outcome: LinkOutcome?, message: String, restored: Boolean) {
        cloudWorking = false
        if (message.isNotEmpty()) loginMessage = message
        when (outcome) {
            null -> {}
            LinkOutcome.Done -> {
                linkOk = true
                linkStep = SyncEngine.STEP_DONE
                linkMessage = if (restored) "Signed in again ✔" else "Your data is now safely in the cloud ✔"
            }
            is LinkOutcome.DoneWithCloudData -> {
                openAppId = null
                openStaffId = null
                openCaseId = null
                openOfferId = null
                openProjectId = null
                openSection = null
                ledger = outcome.ledger
                linkOk = true
                linkStep = SyncEngine.STEP_DONE
                linkMessage = "The cloud data is now on this phone ✔"
            }
            LinkOutcome.AskUseCloud -> linkDialog = LinkDialog.USE_CLOUD
            LinkOutcome.AskCaseC -> linkDialog = LinkDialog.CASE_C
            is LinkOutcome.Failed -> {
                linkOk = false
                linkMessage = outcome.message
            }
        }
        setWarningFor(cloudEngine.largestDocBytes)
        refreshCloudState()
    }

    private fun setWarningFor(largestBytes: Int) {
        cloudWarning = if (largestBytes > 10_000_000) {
            "One part of your data is very large (" + (largestBytes / 1_000_000) + " MB). It may not upload."
        } else {
            ""
        }
    }

    private fun refreshCloudState() {
        cloudMode = cloudConfig.cloudMode
        cloudSignedIn = cloudConfig.signedIn
        cloudEmail = cloudConfig.email
        val sync = SyncStore.loadIfExists(getApplication())
        cloudInitialized = sync?.initialized ?: false
        cloudLastSyncMillis = sync?.lastSyncMillis ?: 0L
        cloudProblem = sync?.lastError ?: ""
        cloudLastError = sync?.lastError ?: ""
        cloudPendingChanges = sync?.pending?.changes?.size ?: 0
        cloudConflicts = buildConflictInfos(sync)
        cloudStatus = computeCloudStatus()
    }

    private fun computeCloudStatus(): CloudStatus = when {
        cloudSyncing -> CloudStatus.SYNCING
        cloudConfig.cloudMode != CloudMode.ON -> CloudStatus.NOT_CONNECTED
        !cloudConfig.signedIn -> CloudStatus.SIGN_IN_AGAIN
        else -> lastSyncStatus
    }

    /** The open conflicts (not yet chosen), with the document names in plain words. */
    private fun buildConflictInfos(sync: com.westly.neribogroup.cloud.SyncData?): List<CloudConflictInfo> {
        if (sync == null) return emptyList()
        val names = HashMap<String, String>()
        for (a in ledger.apps) names[LedgerSplitter.keyForApp(a.id)] = a.name
        val result = ArrayList<CloudConflictInfo>()
        for (text in sync.conflicts) {
            val c = com.westly.neribogroup.cloud.SyncConflict.fromJsonText(text) ?: continue
            if (c.handled) continue
            result.add(CloudConflictInfo(c.key, SyncRules.friendlyDocName(c.key, names), c.cloudVersion, c.file, c.notified))
        }
        return result
    }

    /** "Sync now": resend, pull, apply, push. Ignores a second tap. With the cloud off nothing is sent. */
    fun syncNowAction() {
        runSyncTask { cloudEngine.syncNow(syncHost) }
    }

    /** The owner chose "Keep phone version" for one conflict: it is marked handled and a sync runs. */
    fun resolveConflictKeepPhone(key: String) {
        runSyncTask { cloudEngine.resolveConflictKeepPhone(key, syncHost) }
    }

    /** The owner chose "Use cloud version" for one conflict: it is marked handled and a sync runs. */
    fun resolveConflictUseCloud(key: String) {
        runSyncTask { cloudEngine.resolveConflictUseCloud(key, syncHost) }
    }

    private fun runSyncTask(task: () -> SyncResult) {
        if (cloudWorking || cloudSyncing) return
        if (cloudConfig.cloudMode != CloudMode.ON) {
            cloudSyncMessage = "Connect to the cloud first."
            return
        }
        cloudSyncing = true
        cloudSyncMessage = ""
        cloudStatus = CloudStatus.SYNCING
        thread(start = true) {
            val result = try {
                task()
            } catch (e: Throwable) {
                SyncResult(true, CloudStatus.PROBLEM, "Sync problem — your data on the phone is safe.")
            }
            mainHandler.post { finishSync(result) }
        }
    }

    private fun finishSync(result: SyncResult) {
        cloudSyncing = false
        if (result.ran) {
            lastSyncStatus = result.status
            cloudSyncMessage = result.message
            cloudUnsyncedDocs = result.unsyncedDocs
        }
        refreshCloudState()
    }

    fun saveLedger(newLedger: Ledger) {
        ledger = newLedger
        LedgerStorage.save(getApplication(), newLedger)
    }

    fun saveApp(updated: AppData) {
        if (updated.id == SOFTWARE_APP_ID) return
        val current = ledger.apps.firstOrNull { it.id == updated.id }
        val now = System.currentTimeMillis()
        val finalApp = if (current == null) {
            updated
        } else {
            // Google Play can only go on, never off: the saved mode always wins.
            val locked = updated.copy(
                googlePlayEnabled = current.googlePlayEnabled,
                googlePlaySinceMillis = current.googlePlaySinceMillis
            )
            if (current.googlePlayEnabled) {
                // Everything the dice own (their users, rolls, Play Balance, campaign) stays as it is saved.
                locked.keepingDiceData(current)
            } else {
                locked.finalizeSave(current, now)
            }
        }
        saveLedger(
            ledger.copy(apps = ledger.apps.map { if (it.id == finalApp.id) finalApp else it })
        )
    }

    /** Rolls every dice of one app for today. Allowed once per calendar day. */
    fun rollApp(id: String) {
        if (id == SOFTWARE_APP_ID) return
        saveLedger(DiceEngine.roll(ledger, id, System.currentTimeMillis()))
    }

    /** Moves one app's Google Play Balance into profit. */
    fun withdrawApp(id: String) {
        if (id == SOFTWARE_APP_ID) return
        saveLedger(DiceEngine.withdrawApp(ledger, id, System.currentTimeMillis()))
    }

    /** Moves every app's Google Play Balance into profit. */
    fun withdrawAll() {
        saveLedger(DiceEngine.withdrawAll(ledger, System.currentTimeMillis()))
    }

    fun startCampaign(id: String, size: CampaignSize, startInDays: Int, lastsDays: Int) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        val updated = DiceEngine.startCampaign(
            app, size, startInDays, lastsDays, System.currentTimeMillis()
        )
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    fun stopCampaign(id: String) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        val updated = DiceEngine.stopCampaign(app, System.currentTimeMillis())
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Turns Google Play on for an app. One way only: it can never be turned off. */
    fun enableGooglePlay(id: String) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (app.googlePlayEnabled) return
        val updated = app.withGooglePlayOn(System.currentTimeMillis())
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Adds one expense to Company costs for a Google Play app. It is not added to the app's own expenses. */
    fun addAppExpense(id: String, title: String, amount: Long) {
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled || amount <= 0L) return
        val entry = Entry(
            title = title,
            amount = amount,
            isIncome = false,
            dateMillis = System.currentTimeMillis(),
            auto = false,
            appId = app.id
        )
        saveLedger(ledger.copy(company = ledger.company.copy(expenses = ledger.company.expenses + entry)))
    }

    /** Records a case payment as one company expense and marks the case Paid (one ledger write). */
    fun markCasePaid(caseId: String) {
        val updated = ledger.markCasePaid(caseId, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    fun addCaseNote(caseId: String, text: String) {
        val updated = ledger.withCaseNote(caseId, text, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Keeps the highest "now" ever seen, so setting the phone clock back cannot hide case events. */
    fun updateCasesClock(now: Long) {
        if (ledger.cases.isEmpty()) return
        val updated = ledger.withClockAtLeast(now)
        if (updated !== ledger) saveLedger(updated)
    }

    // ---- Software Development Clients ----

    /** Rolls the 2 software dice. Allowed once per calendar day. */
    fun rollSoftware() {
        val updated = SoftwareEngine.roll(ledger, System.currentTimeMillis(), Random.Default)
        if (updated !== ledger) saveLedger(updated)
    }

    /** Accepts a proposal: creates the client and the project and records the 50% upfront payment. */
    fun acceptSoftwareOffer(id: String) {
        val updated = SoftwareEngine.acceptOffer(ledger, id, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    fun declineSoftwareOffer(id: String) {
        val updated = SoftwareEngine.declineOffer(ledger, id, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Changes the stage of a project. Choosing Delivered records the final 50% and cannot be undone. */
    fun changeProjectStage(id: String, stage: ProjectStage) {
        val updated = SoftwareEngine.setStage(ledger, id, stage, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Records the next unpaid maintenance month of a project. One call is one month. */
    fun recordMaintenancePayment(id: String) {
        val updated = SoftwareEngine.recordMaintenance(ledger, id, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    fun stopProjectMaintenance(id: String) {
        val updated = SoftwareEngine.stopMaintenance(ledger, id, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Adds one expense of a software project to Company costs (it is not added to the app's own entries). */
    fun addProjectExpense(id: String, title: String, amount: Long) {
        val updated = SoftwareEngine.addProjectExpense(ledger, id, title, amount, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Converts dollars from the $ account to naira income. [requestId] makes a repeated tap harmless. */
    fun convertUsdToNaira(usdCents: Long, ngnPerUsd: Double, requestId: String) {
        val updated = SoftwareEngine.convertUsd(
            ledger, usdCents, ngnPerUsd, requestId, System.currentTimeMillis()
        )
        if (updated !== ledger) saveLedger(updated)
    }

    /** Keeps the highest "now" ever seen, so setting the phone clock back cannot undo a roll or an expiry. */
    fun updateSoftwareClock(now: Long) {
        val updated = SoftwareEngine.withClockAtLeast(ledger, now)
        if (updated !== ledger) saveLedger(updated)
    }

    /** Fetches the live exchange rates in the background. Does nothing while a fetch is running. */
    fun refreshRates() {
        if (ratesLoading) return
        ratesLoading = true
        ratesMessage = ""
        thread(start = true) {
            val live = SoftwareRates.fetchLive()
            Handler(Looper.getMainLooper()).post { finishRatesRefresh(live) }
        }
    }

    private fun finishRatesRefresh(live: Map<String, Double>?) {
        ratesLoading = false
        if (live == null) {
            ratesMessage = ratesUnavailableText()
            return
        }
        val updated = SoftwareEngine.applyLiveRates(ledger, live, System.currentTimeMillis())
        if (updated === ledger) {
            ratesMessage = "No rates were changed."
            return
        }
        saveLedger(updated)
        ratesMessage = "Rates updated."
    }

    private fun ratesUnavailableText(): String {
        val fetched = ledger.software.ratesFetchedMillis
        return if (fetched > 0L) {
            "Rates unavailable. Using saved rates from " + formatDate(fetched) + "."
        } else {
            "Rates unavailable. Using default rates."
        }
    }

    /** The owner typed a rate for one currency. It is kept until "Use live rate" is chosen. */
    fun saveRate(code: String, value: Double) {
        val updated = SoftwareEngine.saveManualRate(ledger, code, value, System.currentTimeMillis())
        if (updated !== ledger) saveLedger(updated)
    }

    /** Puts one currency back to the live rate: resets it to the default, then refreshes. */
    fun useLiveRate(code: String) {
        val updated = SoftwareEngine.resetRateToDefault(ledger, code)
        if (updated !== ledger) saveLedger(updated)
        refreshRates()
    }

    fun deleteApp(id: String) {
        if (id == SOFTWARE_APP_ID) return
        openAppId = null
        saveLedger(ledger.copy(apps = ledger.apps.filter { it.id != id }))
    }

    fun saveStaff(updated: Staff) {
        saveLedger(
            ledger.copy(staff = ledger.staff.map { if (it.id == updated.id) updated else it })
        )
    }

    fun deleteStaff(id: String) {
        openStaffId = null
        saveLedger(ledger.copy(staff = ledger.staff.filter { it.id != id }))
    }

    fun payStaff(id: String) {
        saveLedger(ledger.payStaff(id, System.currentTimeMillis()))
    }

    fun renew(id: String) {
        saveLedger(ledger.renew(id, System.currentTimeMillis()))
    }

    fun payAllStaff() {
        saveLedger(ledger.payAllStaff(System.currentTimeMillis()))
    }

    fun exportJson(): String = LedgerStorage.toJson(ledger)

    /** Returns true if the backup was valid and has replaced the current data. */
    fun importJson(text: String): Boolean {
        return try {
            val imported = LedgerStorage.fromJson(text).withSoftwareApp()
            openAppId = null
            openStaffId = null
            openCaseId = null
            openOfferId = null
            openProjectId = null
            openSection = null
            showSettings = false
            saveLedger(imported)
            true
        } catch (e: Exception) {
            false
        }
    }
}
