package com.westly.neribogroup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.westly.neribogroup.cloud.AiCounts
import com.westly.neribogroup.cloud.AiDone
import com.westly.neribogroup.cloud.AiJobEngine
import com.westly.neribogroup.cloud.AiJobHost
import com.westly.neribogroup.cloud.AiJobRules
import com.westly.neribogroup.cloud.AiRollState
import com.westly.neribogroup.cloud.AiRunResult
import com.westly.neribogroup.cloud.AiSettings
import com.westly.neribogroup.cloud.AiSettingsOutcome
import com.westly.neribogroup.cloud.CaseApplyResult
import com.westly.neribogroup.cloud.JobRollOutcome
import com.westly.neribogroup.cloud.JobRollResult
import com.westly.neribogroup.cloud.AuthResult
import com.westly.neribogroup.cloud.BackupOutcome
import com.westly.neribogroup.cloud.CloudConfig
import com.westly.neribogroup.cloud.CloudConflictInfo
import com.westly.neribogroup.cloud.CloudMode
import com.westly.neribogroup.cloud.CloudStatus
import com.westly.neribogroup.cloud.HistoryDoc
import com.westly.neribogroup.cloud.HistoryOutcome
import com.westly.neribogroup.cloud.LinkOutcome
import com.westly.neribogroup.cloud.LedgerSplitter
import com.westly.neribogroup.cloud.MediaSync
import com.westly.neribogroup.cloud.sha256Hex
import com.westly.neribogroup.cloud.RestoreOutcome
import com.westly.neribogroup.cloud.SupabaseApi
import com.westly.neribogroup.cloud.SyncHost
import com.westly.neribogroup.cloud.SyncResult
import com.westly.neribogroup.cloud.SyncRules
import com.westly.neribogroup.cloud.SyncEngine
import com.westly.neribogroup.cloud.SyncStore
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.AdvertEngine
import com.westly.neribogroup.data.AdvertPlatform
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.CaseGenerator
import com.westly.neribogroup.data.CaseSkeleton
import com.westly.neribogroup.data.ComplaintCase
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.PresetRoll
import com.westly.neribogroup.data.RollBlock
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.ProjectStage
import com.westly.neribogroup.data.SOCIALS_APP_ID
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SOCIAL_PHOTO_MAX_SIDE
import com.westly.neribogroup.cloud.AdvertBackend
import com.westly.neribogroup.data.AdvertAnswer
import com.westly.neribogroup.data.AdvertType
import com.westly.neribogroup.data.CompanyAssets
import com.westly.neribogroup.data.CompanyPhotos
import com.westly.neribogroup.data.ChannelRules
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.data.StorageLogLine
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.data.Channel
import com.westly.neribogroup.data.DayRolls
import com.westly.neribogroup.data.SocialDice
import com.westly.neribogroup.data.SocialEdit
import com.westly.neribogroup.data.SocialImage
import com.westly.neribogroup.data.SocialMedia
import com.westly.neribogroup.data.SocialsEngine
import com.westly.neribogroup.data.SocialsRollInfo
import com.westly.neribogroup.data.SocialsRules
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwarePreset
import com.westly.neribogroup.data.SoftwareRates
import com.westly.neribogroup.data.ImageProcessor
import com.westly.neribogroup.data.MAX_STAFF_IMAGES
import com.westly.neribogroup.data.ProcessedImage
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.data.StaffImage
import com.westly.neribogroup.data.StaffMedia
import com.westly.neribogroup.data.newImageId
import com.westly.neribogroup.data.markCasePaid
import com.westly.neribogroup.data.withCaseAdded
import com.westly.neribogroup.data.withCaseNote
import com.westly.neribogroup.data.withClockAtLeast
import com.westly.neribogroup.data.withSoftwareApp
import com.westly.neribogroup.feature.cloud.CheckLine
import com.westly.neribogroup.feature.cloud.CloudHistoryUi
import com.westly.neribogroup.feature.cloud.LinkDialog
import com.westly.neribogroup.feature.cloud.SelfCheck
import com.westly.neribogroup.ui.formatDate
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random

/** The pages that open from the home screen tiles. */
enum class Section { APPS, RENEWALS, STAFF, COSTS, PLAY, CASES, USD, SOCIALS, APPLE, PAYSTACK, DOCS, TOOLS, DEPLOY, STORAGE }

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
    private val mediaSync = MediaSync(application, cloudConfig, cloudApi)
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

    // ---- Claude (AI) — Phase 4B. Nothing here runs unless AI mode is ON and the cloud is linked. ----

    private val aiEngine = AiJobEngine(application, cloudConfig, cloudApi, cloudEngine)

    /** The owner's AI mode switch (kept on the phone). */
    var aiMode by mutableStateOf(cloudConfig.aiMode)
        private set

    /** True while a pass over the Claude jobs runs. */
    var aiRunning by mutableStateOf(false)
        private set

    /** A short calm line about the last pass (shown in the Claude (AI) card). */
    var aiMessage by mutableStateOf("")
        private set

    /** A one-time text to show as a toast ("Claude rolled ..."). Empty when there is nothing to say. */
    var aiNotice by mutableStateOf("")
        private set

    /** True when the last pass could not reach the cloud. */
    var aiOffline by mutableStateOf(false)
        private set

    /** Per app: dice answered out of dice waiting (from the last pass). */
    var aiCounts by mutableStateOf<Map<String, AiCounts>>(emptyMap())
        private set

    /** Per app: how its last AI-mode roll ended (see AiDone). */
    var aiDoneMap by mutableStateOf<Map<String, String>>(startSync?.aiDone ?: emptyMap())
        private set

    var aiWaiting by mutableStateOf(0)
        private set

    /** Part C-2: a plain line for the Socials page from the last pass (for example "Claude's answer could not be used"). */
    var aiSocialsMessage by mutableStateOf("")
        private set

    /** Part C: how many SOFTWARE_ROLL requests were made per day (from the sync state). */
    var aiSoftwareAttempts by mutableStateOf<Map<String, Int>>(startSync?.softwareAttempts ?: emptyMap())
        private set

    /** Phase 5: customer cases still waiting for their story (from Claude or the built-in generator). */
    var aiCasesWaiting by mutableStateOf(startSync?.caseJobs?.size ?: 0)
        private set

    var aiAnswered by mutableStateOf(0)
        private set

    /** The AI settings kept in the cloud, once read. */
    var aiSettings by mutableStateOf<AiSettings?>(null)
        private set

    var aiSettingsMessage by mutableStateOf("")
        private set

    var aiSettingsBusy by mutableStateOf(false)
        private set

    /** AI mode counts only while the cloud is linked and signed in. Otherwise the app is as in Phase 3. */
    val aiActive: Boolean
        get() = aiMode && cloudMode == CloudMode.ON && cloudSignedIn && cloudInitialized

    /** Rolls an app on the main thread with the answers of Claude. Saves ledger.json before anything else. */
    private val jobHost = object : AiJobHost {
        override fun currentLedger(): Ledger = ledger

        override fun rollSoftwareForJobs(preset: SoftwarePreset?, wallNow: Long): JobRollResult {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(JobRollResult.FAILED)
            mainHandler.post {
                try {
                    val current = ledger
                    if (!SoftwareEngine.canRoll(current, wallNow)) {
                        outcome.set(JobRollResult.BLOCKED)
                    } else {
                        val rolled = SoftwareEngine.roll(current, wallNow, Random.Default, preset)
                        if (rolled === current) {
                            outcome.set(JobRollResult.BLOCKED)
                        } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(rolled))) {
                            ledger = rolled
                            onLocalChange()
                            outcome.set(JobRollResult.ROLLED)
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
            return if (finished) outcome.get() else JobRollResult.FAILED
        }

        override fun applySoftwareRoll(dayKey: String, wallNow: Long, build: (Ledger, Long) -> Ledger?): JobRollResult {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(JobRollResult.FAILED)
            mainHandler.post {
                try {
                    val current = ledger
                    if (!SoftwareEngine.canRoll(current, wallNow) || current.software.lastRollDayKey >= dayKey) {
                        outcome.set(JobRollResult.BLOCKED)
                    } else {
                        val updated = build(current, wallNow)
                        if (updated == null || updated === current) {
                            outcome.set(JobRollResult.BLOCKED)
                        } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                            ledger = updated
                            onLocalChange()
                            outcome.set(JobRollResult.ROLLED)
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
            return if (finished) outcome.get() else JobRollResult.FAILED
        }

        override fun rollSocialsForJobs(day: String, wallNow: Long, gains: Map<String, Long>, comment: String): JobRollResult {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(JobRollResult.FAILED)
            mainHandler.post {
                try {
                    val current = ledger
                    // A day after "today" (clock protection included) is never rolled.
                    if (day > dayKey(SocialDice.nowFor(current, wallNow))) {
                        outcome.set(JobRollResult.BLOCKED)
                    } else {
                        val updated = SocialDice.applyDay(current, day, gains, comment)
                        if (updated === current) {
                            outcome.set(JobRollResult.BLOCKED)
                        } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                            ledger = updated
                            onLocalChange()
                            outcome.set(JobRollResult.ROLLED)
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
            return if (finished) outcome.get() else JobRollResult.FAILED
        }

        override fun applyAdvertAnswers(answers: List<AdvertAnswer>): Boolean {
            if (answers.isEmpty()) return true
            val latch = CountDownLatch(1)
            val ok = AtomicReference(false)
            mainHandler.post {
                try {
                    val current = ledger
                    var changed = false
                    val apps = current.apps.map { app ->
                        if (app.adverts.isEmpty()) {
                            app
                        } else {
                            val merged = AdvertEngine.mergeAnswers(app.adverts, answers)
                            if (merged != app.adverts) {
                                changed = true
                                app.copy(adverts = merged)
                            } else {
                                app
                            }
                        }
                    }
                    if (!changed) {
                        ok.set(true)
                    } else {
                        val updated = current.copy(apps = apps)
                        if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                            ledger = updated
                            onLocalChange()
                            ok.set(true)
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
            return finished && ok.get()
        }

        override fun applyAppDay(appId: String, rolls: DayRolls): Boolean {
            val latch = CountDownLatch(1)
            val ok = AtomicReference(false)
            mainHandler.post {
                try {
                    val current = ledger
                    val updated = current.copy(
                        apps = current.apps.map { if (it.id == appId) it.copy(dayRolls = rolls) else it }
                    )
                    if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                        ledger = updated
                        onLocalChange()
                        ok.set(true)
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
            return finished && ok.get()
        }

        override fun applyRollNotes(advertComments: Map<String, String>, socialComments: Map<String, String>): Boolean {
            val latch = CountDownLatch(1)
            val ok = AtomicReference(false)
            mainHandler.post {
                try {
                    val current = ledger
                    var changed = false
                    val apps = current.apps.map { app ->
                        if (app.adverts.isEmpty()) {
                            app
                        } else {
                            val filled = AdvertEngine.applyComments(app.adverts, advertComments)
                            if (filled != app.adverts) {
                                changed = true
                                app.copy(adverts = filled)
                            } else {
                                app
                            }
                        }
                    }
                    val withApps = if (changed) current.copy(apps = apps) else current
                    val updated = SocialDice.applyComments(withApps, socialComments)
                    if (updated === current) {
                        ok.set(true)
                    } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                        ledger = updated
                        onLocalChange()
                        ok.set(true)
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
            return finished && ok.get()
        }

        override fun rollForJobs(appId: String, preset: PresetRoll?, wallNow: Long): JobRollResult {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(JobRollResult.FAILED)
            mainHandler.post {
                try {
                    val current = ledger
                    val target = current.apps.firstOrNull { it.id == appId }
                    val now = maxOf(wallNow, current.casesClockMillis)
                    if (target == null || !target.googlePlayEnabled ||
                        DiceEngine.rollBlock(target, now) != RollBlock.OK
                    ) {
                        outcome.set(JobRollResult.BLOCKED)
                    } else {
                        val rolled = DiceEngine.roll(current, appId, now, Random.Default, preset)
                        if (rolled === current) {
                            outcome.set(JobRollResult.BLOCKED)
                        } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(rolled))) {
                            ledger = rolled
                            onLocalChange()
                            outcome.set(JobRollResult.ROLLED)
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
            return if (finished) outcome.get() else JobRollResult.FAILED
        }

        override fun rollWithSkeleton(appId: String, preset: PresetRoll?, wallNow: Long): JobRollOutcome {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(JobRollResult.FAILED)
            val skeleton = AtomicReference<CaseSkeleton?>(null)
            mainHandler.post {
                try {
                    val current = ledger
                    val target = current.apps.firstOrNull { it.id == appId }
                    val now = maxOf(wallNow, current.casesClockMillis)
                    if (target == null || !target.googlePlayEnabled ||
                        DiceEngine.rollBlock(target, now) != RollBlock.OK
                    ) {
                        outcome.set(JobRollResult.BLOCKED)
                    } else {
                        val random = Random.Default
                        // The roll itself, without a case. The case is only decided here (its skeleton).
                        val (rolled, facts) = DiceEngine.rollWithFacts(current, appId, now, random, preset)
                        if (facts == null || rolled === current) {
                            outcome.set(JobRollResult.BLOCKED)
                        } else if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(rolled))) {
                            ledger = rolled
                            onLocalChange()
                            skeleton.set(CaseGenerator.decideSkeleton(rolled, appId, facts, now, random))
                            outcome.set(JobRollResult.ROLLED)
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
            return if (finished) JobRollOutcome(outcome.get(), skeleton.get())
            else JobRollOutcome(JobRollResult.FAILED, null)
        }

        override fun applyCase(caseId: String, wallNow: Long, build: (Ledger, Long) -> ComplaintCase?): CaseApplyResult {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference(CaseApplyResult.FAILED)
            mainHandler.post {
                try {
                    val current = ledger
                    if (current.cases.any { it.id == caseId }) {
                        outcome.set(CaseApplyResult.ALREADY_THERE)
                    } else {
                        val now = maxOf(wallNow, current.casesClockMillis)
                        val made = build(current, now)
                        if (made == null) {
                            outcome.set(CaseApplyResult.NOT_BUILT)
                        } else {
                            val updated = current.withCaseAdded(made, now)
                            if (LedgerStorage.saveRawText(getApplication(), LedgerStorage.toJson(updated))) {
                                ledger = updated
                                onLocalChange()
                                outcome.set(CaseApplyResult.ADDED)
                            }
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
            return if (finished) outcome.get() else CaseApplyResult.FAILED
        }
    }

    /** The AI mode switch. It can be turned on only when the cloud is linked and signed in. */
    fun updateAiMode(on: Boolean) {
        if (on) {
            if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn || !cloudInitialized) {
                aiMessage = "Connect to the cloud first (the Cloud card above)."
                return
            }
            cloudConfig.aiMode = true
            aiMode = true
            aiMessage = ""
            loadAiSettings()
            runAiJobs()
        } else {
            cloudConfig.aiMode = false
            aiMode = false
            aiMessage = ""
        }
    }

    /** Puts today's jobs in the cloud, reads Claude's answers and rolls the apps that are ready. */
    fun runAiJobs() {
        if (!aiActive || aiRunning || cloudWorking) return
        aiRunning = true
        thread(start = true) {
            val result = try {
                aiEngine.run(jobHost)
            } catch (e: Throwable) {
                AiRunResult(ran = true, message = "Claude's jobs had a problem. Your data is safe.")
            }
            mainHandler.post { finishAiRun(result) }
        }
    }

    /** "Write cases with the built-in generator": every waiting customer case is written now, without Claude. */
    fun writeCasesBuiltIn() {
        if (!aiActive || aiRunning || cloudWorking) return
        aiRunning = true
        thread(start = true) {
            val result = try {
                aiEngine.runCasesBuiltIn(jobHost)
            } catch (e: Throwable) {
                AiRunResult(ran = true, message = "The cases could not be written. Your data is safe.")
            }
            mainHandler.post { finishAiRun(result) }
        }
    }

    private fun finishAiRun(result: AiRunResult) {
        aiRunning = false
        if (result.ran) {
            aiOffline = result.offline
            if (result.complete) {
                aiCounts = result.counts
                aiWaiting = result.waiting
                aiAnswered = result.answered
                aiSocialsMessage = result.socialsMessage
            }
            aiMessage = result.message
            if (result.casesWaiting >= 0) aiCasesWaiting = result.casesWaiting
            aiDoneMap = SyncStore.loadIfExists(getApplication())?.aiDone ?: aiDoneMap
            aiSoftwareAttempts = SyncStore.loadIfExists(getApplication())?.softwareAttempts ?: aiSoftwareAttempts
            if (result.notices.isNotEmpty()) aiNotice = result.notices.joinToString("\n")
        }
        // A roll changed the ledger: send it to the cloud now that the job pass has let go of the sync lock.
        if (result.rolledAny) autoSync()
    }

    fun consumeAiNotice() {
        aiNotice = ""
    }

    /** The state of the "Today's roll" card of one app. */
    fun aiRollStateFor(app: AppData, nowMillis: Long): AiRollState {
        val protectedNow = maxOf(nowMillis, ledger.casesClockMillis)
        return AiJobRules.stateFor(app, dayKey(protectedNow), aiDoneMap[app.id], aiCounts[app.id], aiOffline)
    }

    /** Phase 6: the state of the "Today's roll" card of the software dice. */
    fun aiRollStateForSoftware(nowMillis: Long): AiRollState {
        val protectedNow = maxOf(nowMillis, ledger.software.clockMillis, ledger.casesClockMillis)
        return AiJobRules.stateForKey(
            ledger.software.lastRollDayKey, dayKey(protectedNow), aiDoneMap[SOFTWARE_APP_ID],
            aiCounts[SOFTWARE_APP_ID], aiOffline
        )
    }

    /** Part C-2: the state of the Socials roll (the same kinds as the app cards). */
    fun aiRollStateForSocials(nowMillis: Long): AiRollState = AiJobRules.stateForKey(
        ledger.socials.lastRollDayKey, dayKey(socialsNow(nowMillis)), aiDoneMap[SOCIALS_APP_ID],
        aiCounts[SOCIALS_APP_ID], aiOffline
    )

    /** Part C: how many times today's software roll was asked of Claude (0 to 3). */
    fun softwareAttemptsToday(nowMillis: Long): Int {
        val protectedNow = maxOf(nowMillis, ledger.software.clockMillis, ledger.casesClockMillis)
        return aiSoftwareAttempts[dayKey(protectedNow)] ?: 0
    }

    /** Phase 6: "Roll with built-in dice instead" for the software dice. */
    fun aiRollSoftwareBuiltIn() {
        val before = ledger.software.lastRollDayKey
        rollSoftware()
        if (ledger.software.lastRollDayKey == before) return
        val wall = System.currentTimeMillis()
        aiDoneMap = aiDoneMap + (SOFTWARE_APP_ID to AiDone.encode(dayKey(wall), wall, false, 0))
        if (!aiActive) return
        thread(start = true) {
            runCatching { aiEngine.afterBuiltInRoll(SOFTWARE_APP_ID, wall, wall) }
            mainHandler.post {
                aiDoneMap = SyncStore.loadIfExists(getApplication())?.aiDone ?: aiDoneMap
            }
        }
    }

    /** How many dice jobs were applied today (for the status line). */
    fun aiAppliedToday(): Int {
        val today = dayKey(maxOf(System.currentTimeMillis(), ledger.casesClockMillis))
        return aiDoneMap.values.mapNotNull { AiDone.parse(it) }.filter { it.dayKey == today }.sumOf { it.jobs }
    }

    /** "Roll with built-in dice instead": the old local roll, then the waiting jobs of that app are dropped. */
    fun aiRollBuiltIn(appId: String) {
        if (appId == SOFTWARE_APP_ID) return
        val before = ledger.apps.firstOrNull { it.id == appId }?.lastRollDayKey ?: return
        rollApp(appId)
        val after = ledger.apps.firstOrNull { it.id == appId }?.lastRollDayKey ?: return
        if (after == before) return
        val wall = System.currentTimeMillis()
        aiDoneMap = aiDoneMap + (appId to AiDone.encode(dayKey(wall), wall, false, 0))
        if (!aiActive) return
        thread(start = true) {
            runCatching { aiEngine.afterBuiltInRoll(appId, wall, wall) }
            mainHandler.post {
                aiDoneMap = SyncStore.loadIfExists(getApplication())?.aiDone ?: aiDoneMap
            }
        }
    }

    /** Reads the AI settings from the cloud. */
    fun loadAiSettings() {
        if (aiSettingsBusy) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) return
        aiSettingsBusy = true
        aiSettingsMessage = ""
        thread(start = true) {
            val out = try {
                aiEngine.fetchSettings()
            } catch (e: Throwable) {
                AiSettingsOutcome(null, "Could not read the AI settings. Try again later.")
            }
            mainHandler.post {
                aiSettingsBusy = false
                val got = out.settings
                if (got != null) aiSettings = got
                aiSettingsMessage = out.message
            }
        }
    }

    /** Saves the AI settings in the cloud. */
    fun saveAiSettings(wanted: AiSettings) {
        if (aiSettingsBusy) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            aiSettingsMessage = "Connect to the cloud first."
            return
        }
        aiSettingsBusy = true
        aiSettingsMessage = ""
        thread(start = true) {
            val out = try {
                aiEngine.saveSettings(wanted)
            } catch (e: Throwable) {
                AiSettingsOutcome(null, "Could not save the AI settings. Try again later.")
            }
            mainHandler.post {
                aiSettingsBusy = false
                val got = out.settings
                if (got != null) {
                    aiSettings = got
                    aiSettingsMessage = "Saved ✔"
                } else {
                    aiSettingsMessage = out.message
                }
            }
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

    /**
     * Runs the self-check on the real ledger in the background and shows the PASS/FAIL lines.
     * When signed in, one normal Sync now runs first, so the cloud check compares the latest state.
     */
    fun runSelfCheck() {
        if (selfCheckRunning || cloudWorking || cloudHistory.restoring) return
        selfCheckRunning = true
        val online = cloudSignedIn && cloudInitialized
        val canSync = online && cloudConfig.cloudMode == CloudMode.ON && !cloudSyncing
        if (canSync) {
            cloudSyncing = true
            cloudStatus = CloudStatus.SYNCING
            mainHandler.removeCallbacks(retryRunnable)
            mainHandler.removeCallbacks(debounceRunnable)
        }
        thread(start = true) {
            var syncResult: SyncResult? = null
            val result = try {
                if (canSync) syncResult = cloudEngine.syncNow(syncHost)
                // Read after the sync: it may have put cloud data on the phone.
                val snapshot = ledger
                val sync = if (online) cloudEngine.loadSync() else null
                val finishedSync = syncResult
                val status: CloudStatus? = if (finishedSync != null && finishedSync.ran) finishedSync.status else null
                SelfCheck.run(snapshot, cloudApi, sync, online, status)
            } catch (e: Throwable) {
                null
            }
            val syncDone = syncResult
            mainHandler.post {
                selfCheckRunning = false
                if (result == null) {
                    selfCheckLines = listOf(CheckLine("Self-check", false, "It could not run. Your data is untouched."))
                } else {
                    selfCheckLines = result.lines
                    setWarningFor(result.largestDocBytes)
                }
                if (canSync) {
                    if (syncDone != null) {
                        finishSync(syncDone)
                    } else {
                        cloudSyncing = false
                        refreshCloudState()
                    }
                } else {
                    refreshCloudState()
                }
            }
        }
    }

    fun closeSelfCheck() {
        selfCheckLines = null
    }

    // ---- Cloud: Back up now, cloud history and Restore this version (Phase 3B-2) ----

    /** True while "Back up now" runs. Ignores a second tap. */
    var cloudBackupBusy by mutableStateOf(false)
        private set

    /** The short line after the last "Back up now" (the count, or a calm problem). */
    var cloudBackupMessage by mutableStateOf("")
        private set

    /** The cloud history window: document list, version list, restore progress. */
    var cloudHistory by mutableStateOf(CloudHistoryUi())
        private set

    /** "Back up now": the cloud keeps a full copy. Never touches the phone's data. */
    fun backUpNowAction() {
        if (cloudBackupBusy || cloudWorking || cloudHistory.restoring) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            cloudBackupMessage = "Connect to the cloud first."
            return
        }
        if (cloudSyncing) {
            cloudBackupMessage = "Please wait, syncing…"
            return
        }
        cloudBackupBusy = true
        cloudBackupMessage = ""
        thread(start = true) {
            val outcome = try {
                cloudEngine.createBackup("manual")
            } catch (e: Throwable) {
                BackupOutcome(false, "The backup did not finish. Your data on the phone is safe.")
            }
            mainHandler.post {
                cloudBackupBusy = false
                cloudBackupMessage = outcome.message
                refreshCloudState()
            }
        }
    }

    /** Opens the history window with the list of documents. */
    fun openCloudHistory() {
        if (cloudHistory.open || cloudBackupBusy || cloudWorking) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            cloudBackupMessage = "Connect to the cloud first."
            return
        }
        cloudHistory = CloudHistoryUi(open = true, loadingDocs = true)
        val snapshot = ledger
        thread(start = true) {
            val docs = try {
                buildHistoryDocs(snapshot)
            } catch (e: Throwable) {
                emptyList<HistoryDoc>()
            }
            mainHandler.post {
                if (cloudHistory.open) cloudHistory = cloudHistory.copy(docs = docs, loadingDocs = false)
            }
        }
    }

    /** The owner tapped one document: its latest 20 versions are loaded. */
    fun pickHistoryDoc(doc: HistoryDoc) {
        val h = cloudHistory
        if (!h.open || h.restoring || h.loadingRows) return
        cloudHistory = h.copy(selected = doc, rows = emptyList(), loadingRows = true, message = "")
        loadHistoryRows(doc, "")
    }

    /** Back to the list of documents. Ignored while a restore runs. */
    fun backToHistoryList() {
        val h = cloudHistory
        if (!h.open || h.restoring) return
        cloudHistory = h.copy(selected = null, rows = emptyList(), loadingRows = false, message = "")
    }

    /** Closes the history window. Ignored while a restore runs. */
    fun closeCloudHistory() {
        if (cloudHistory.restoring) return
        cloudHistory = CloudHistoryUi()
    }

    /** [note] is shown under the list when it loaded (for example the result of a restore). */
    private fun loadHistoryRows(doc: HistoryDoc, note: String) {
        thread(start = true) {
            val result = try {
                cloudEngine.listDocHistory(doc.key)
            } catch (e: Throwable) {
                HistoryOutcome(emptyList(), "Could not load the history. Try again later.")
            }
            mainHandler.post {
                val now = cloudHistory
                if (now.open && now.selected?.key == doc.key) {
                    cloudHistory = now.copy(
                        rows = result.rows,
                        loadingRows = false,
                        message = if (result.message.isNotEmpty()) result.message else note
                    )
                }
                refreshCloudState()
            }
        }
    }

    /**
     * "Restore this version" (already confirmed by the owner): the cloud makes that version the newest one,
     * then one normal sync brings it to the phone through the safe apply path. Never runs beside a sync.
     */
    fun restoreHistoryVersion(version: Int) {
        val h = cloudHistory
        val doc = h.selected ?: return
        if (!h.open || h.restoring || h.loadingRows) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            cloudHistory = h.copy(message = "Connect to the cloud first.")
            return
        }
        if (cloudWorking || cloudSyncing || cloudBackupBusy) {
            cloudHistory = h.copy(message = "Please wait, syncing…")
            return
        }
        cloudHistory = h.copy(restoring = true, message = "")
        cloudSyncing = true
        cloudStatus = CloudStatus.SYNCING
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(debounceRunnable)
        thread(start = true) {
            val outcome = try {
                cloudEngine.restoreDocVersion(doc.key, version, syncHost)
            } catch (e: Throwable) {
                RestoreOutcome(false, "The restore did not finish. Your data on the phone is safe.", false, 0, null)
            }
            val text = restoreText(doc.name, version, outcome)
            mainHandler.post {
                val after = outcome.sync
                if (after != null) {
                    finishSync(after)
                } else {
                    cloudSyncing = false
                    refreshCloudState()
                }
                val now = cloudHistory
                if (now.open) {
                    cloudHistory = now.copy(restoring = false, loadingRows = outcome.ok, message = if (outcome.ok) "" else text)
                    if (outcome.ok) loadHistoryRows(doc, text)
                }
            }
        }
    }

    private fun restoreText(name: String, version: Int, o: RestoreOutcome): String {
        if (!o.ok) return o.message
        if (o.unchanged) return "That is already the current version of $name. Nothing was changed."
        val after = o.sync
        if (after != null && after.ran && after.newConflicts > 0) {
            return "The cloud went back to version $version of $name. This phone has its own changes to it, " +
                "so you will be asked which version to keep."
        }
        if (after != null && after.ran && after.status == CloudStatus.UP_TO_DATE) {
            return "Done. The cloud and this phone went back to version $version of $name. " +
                "The version before stays in history."
        }
        return "The cloud went back to version $version of $name. This phone will get it when the sync finishes."
    }

    /** Every document the owner can open the history of: the fixed ones, the apps, then any other known key. */
    private fun buildHistoryDocs(snapshot: Ledger): List<HistoryDoc> {
        val names = HashMap<String, String>()
        for (a in snapshot.apps) names[LedgerSplitter.keyForApp(a.id)] = a.name
        val fixed = listOf(
            LedgerSplitter.DOC_COMPANY,
            LedgerSplitter.DOC_STAFF,
            LedgerSplitter.DOC_CASES,
            LedgerSplitter.DOC_SOFTWARE,
            LedgerSplitter.DOC_SOCIALS
        )
        val keys = LinkedHashSet<String>()
        keys.addAll(fixed)
        runCatching {
            for (d in LedgerSplitter.split(LedgerStorage.toJson(snapshot))) keys.add(d.key)
        }
        val known = cloudEngine.loadSync().docs.keys.filter { !keys.contains(it) }.sorted()
        keys.addAll(known)
        return keys.map { key ->
            val name = if (fixed.contains(key)) SyncRules.friendlyDocName(key, names) else (names[key] ?: key)
            HistoryDoc(key, name)
        }
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
        // Right after login / linking (the first upload itself is already done): one normal sync.
        if (outcome == LinkOutcome.Done || outcome is LinkOutcome.DoneWithCloudData) autoSync()
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
        refreshMediaState()
        cloudStatus = computeCloudStatus()
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) cancelCloudTimers()
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
        // A manual Sync now also gives photos the server refused one more chance.
        manualMediaRetry = true
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
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(debounceRunnable)
        thread(start = true) {
            val result = try {
                task()
            } catch (e: Throwable) {
                SyncResult(true, CloudStatus.PROBLEM, "Sync problem — your data on the phone is safe.")
            }
            // Photos follow the documents: send waiting photos, fetch the ones other phones added.
            val manual = manualMediaRetry
            manualMediaRetry = false
            val photosChanged = if (result.ran && result.status != CloudStatus.WAITING_INTERNET &&
                result.status != CloudStatus.SIGN_IN_AGAIN && result.status != CloudStatus.NOT_CONNECTED
            ) {
                try {
                    mediaSync.process({ ledger }, manual)
                } catch (e: Throwable) {
                    false
                }
            } else {
                false
            }
            mainHandler.post {
                if (photosChanged) mediaTick += 1
                finishSync(result)
            }
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
        // Phase 4B: after a good sync, look at the Claude jobs (does nothing when AI mode is off).
        if (result.ran && result.status == CloudStatus.UP_TO_DATE) runAiJobs()
        if (!appInForeground || cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            syncAgainAfter = false
            return
        }
        if (result.ran && result.status == CloudStatus.WAITING_INTERNET) {
            // One retry after the engine's back-off delay, while the app is open.
            syncAgainAfter = false
            val delay = cloudEngine.nextRetryDelayMillis
            if (delay > 0L) {
                mainHandler.removeCallbacks(retryRunnable)
                mainHandler.postDelayed(retryRunnable, delay)
            }
        } else if (syncAgainAfter) {
            syncAgainAfter = false
            autoSync()
        }
    }

    // ---- Cloud triggers (Phase 3B-1b): when a sync starts by itself. Nothing here runs with the cloud OFF. ----

    /** True while the app is in the foreground (set by MainActivity onResume / onPause). */
    private var appInForeground = false

    /** The first resume after the app process started runs the start-up sync (resend, then sync). */
    private var startupSyncDone = false

    /** SystemClock.elapsedRealtime() of the last resume sync. Used only to ignore a double resume. */
    private var lastResumeSyncAt = 0L

    /** True when a change or trigger came in while a sync was running: one more sync follows. */
    private var syncAgainAfter = false

    private val debounceRunnable = Runnable { autoSync() }

    private val retryRunnable = Runnable { autoSync() }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!appInForeground) return
            autoSync()
            mainHandler.postDelayed(this, FOREGROUND_TICK_MILLIS)
        }
    }

    /**
     * Claude's answers are looked at often while the app is open and AI mode is ON, so "roll next" can follow
     * "roll next" with no long wait (the proposals of a software day, for example, are put in the cloud only
     * after the app has read the two dice). It only reads the jobs; the 5-minute sync timer is unchanged.
     */
    private val aiTickRunnable = object : Runnable {
        override fun run() {
            if (!appInForeground) return
            if (aiActive && !aiRunning && !cloudWorking) runAiJobs()
            mainHandler.postDelayed(this, AI_TICK_MILLIS)
        }
    }

    /** MainActivity.onResume. Starts the 5-minute timer and syncs (the first time: resend first). */
    fun onAppResumed() {
        appInForeground = true
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.postDelayed(tickRunnable, FOREGROUND_TICK_MILLIS)
        mainHandler.removeCallbacks(aiTickRunnable)
        mainHandler.postDelayed(aiTickRunnable, AI_TICK_MILLIS)
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) return
        val now = SystemClock.elapsedRealtime()
        if (!startupSyncDone) {
            startupSyncDone = true
            lastResumeSyncAt = now
            // The session is refreshed inside every call, so it is fresh when these two run.
            runSyncTask {
                val first = cloudEngine.resendPendingOnStart()
                if (first.ran && first.status == CloudStatus.SIGN_IN_AGAIN) first else cloudEngine.syncNow(syncHost)
            }
            return
        }
        // A rotation or a quick switch gives two resumes in a row: one sync is enough.
        if (lastResumeSyncAt != 0L && now - lastResumeSyncAt < RESUME_SYNC_GAP_MILLIS) return
        lastResumeSyncAt = now
        autoSync()
    }

    /** MainActivity.onPause. No timer survives in the background; a running sync may finish. */
    fun onAppPaused() {
        appInForeground = false
        syncAgainAfter = false
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.removeCallbacks(aiTickRunnable)
        mainHandler.removeCallbacks(debounceRunnable)
        mainHandler.removeCallbacks(retryRunnable)
    }

    /**
     * Called after a local save. Only schedules one sync, at least 2 seconds after the last change
     * (a burst of changes gives one sync). Never called for cloud-applied changes.
     */
    private fun onLocalChange() {
        if (applyingCloudData) return
        if (!appInForeground) return
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) return
        mainHandler.removeCallbacks(debounceRunnable)
        mainHandler.postDelayed(debounceRunnable, DEBOUNCE_MILLIS)
    }

    /** The one door for every automatic sync. Busy = one more sync is remembered for when this one ends. */
    private fun autoSync() {
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) return
        if (cloudWorking) return
        if (cloudSyncing) {
            syncAgainAfter = true
            return
        }
        runSyncTask { cloudEngine.syncNow(syncHost) }
    }

    private fun cancelCloudTimers() {
        syncAgainAfter = false
        mainHandler.removeCallbacks(debounceRunnable)
        mainHandler.removeCallbacks(retryRunnable)
    }

    /** True when an import should show the note "Imported data will be copied to the cloud". */
    fun importCopiesToCloud(): Boolean = cloudConfig.cloudMode == CloudMode.ON && cloudConfig.signedIn

    fun saveLedger(newLedger: Ledger) {
        ledger = newLedger
        LedgerStorage.save(getApplication(), newLedger)
        // After the local save: only schedules a sync (does nothing with the cloud off).
        onLocalChange()
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
        val target = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!AdvertBackend.needsBackend(target) || !cloudSignedIn) {
            saveLedger(DiceEngine.roll(ledger, id, System.currentTimeMillis()))
            return
        }
        // The backend decides what the adverts bring, so it is asked first (off the main thread); then the app rolls.
        // Without a connection the adverts simply wait: their days are delivered on a later roll.
        val today = dayKey(maxOf(System.currentTimeMillis(), ledger.casesClockMillis))
        Thread {
            val fetched = AdvertBackend.fetchAdvertDays(cloudApi, cloudConfig.deviceId, target, today)
            val dayFetch = AdvertBackend.fetchAppDay(cloudApi, cloudConfig.deviceId, target, today)
            mainHandler.post {
                if (fetched.answers.isNotEmpty() || dayFetch.rolls != null) {
                    val current = ledger
                    ledger = current.copy(
                        apps = current.apps.map { a ->
                            if (a.id == id) {
                                a.copy(
                                    adverts = AdvertEngine.mergeAnswers(a.adverts, fetched.answers),
                                    dayRolls = dayFetch.rolls ?: a.dayRolls
                                )
                            } else {
                                a
                            }
                        }
                    )
                }
                saveLedger(DiceEngine.roll(ledger, id, System.currentTimeMillis()))
            }
        }.start()
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

    /** Moves one app's Apple Store Balance or Paystack Balance into profit. */
    fun withdrawChannelApp(id: String, channel: Channel) {
        if (id == SOFTWARE_APP_ID) return
        saveLedger(DiceEngine.withdrawChannelApp(ledger, id, channel, System.currentTimeMillis()))
    }

    /** Moves every app's Apple Store Balance or Paystack Balance into profit. */
    fun withdrawChannelAll(channel: Channel) {
        saveLedger(DiceEngine.withdrawChannelAll(ledger, channel, System.currentTimeMillis()))
    }

    /** Saves the company's documents, tools and total storage. */
    fun saveAssets(assets: CompanyAssets) {
        saveLedger(ledger.copy(assets = assets))
    }

    /** Changes every time a document or tool photo changes, so the screens load it again. */
    var assetPhotoTick by mutableStateOf(0)
        private set

    fun assetPhotoFile(kind: String, id: String): java.io.File =
        CompanyPhotos.file(getApplication<Application>(), kind, id)

    /** Makes a small JPEG of the picked photo and saves it on this phone. [done] gets "" or a short problem text. */
    fun saveAssetPhoto(kind: String, id: String, uri: android.net.Uri, done: (String) -> Unit) {
        val context = getApplication<Application>()
        Thread {
            val processed = ImageProcessor.process(context, uri, 1600)
            val message = when (processed) {
                is ProcessedImage.Ok ->
                    if (CompanyPhotos.write(context, kind, id, processed.bytes)) "" else "The photo could not be saved."
                is ProcessedImage.Failed -> processed.message
            }
            mainHandler.post {
                if (message.isEmpty()) assetPhotoTick += 1
                done(message)
            }
        }.start()
    }

    fun deleteAssetPhoto(kind: String, id: String) {
        CompanyPhotos.delete(getApplication<Application>(), kind, id)
        assetPhotoTick += 1
    }

    // ---------------------------------------------------------------------------------------------
    // Equipment photos: up to 5 for each piece of equipment
    // ---------------------------------------------------------------------------------------------

    fun toolPhotoFile(id: String, index: Int): File =
        CompanyPhotos.fileAt(getApplication<Application>(), "tool", id, index)

    /** Makes a small JPEG of the picked photo and saves it as photo number [index] of the item. */
    fun saveToolPhoto(id: String, index: Int, uri: Uri, done: (String) -> Unit) {
        val context = getApplication<Application>()
        Thread {
            val processed = ImageProcessor.process(context, uri, 1600)
            val message = when (processed) {
                is ProcessedImage.Ok ->
                    if (CompanyPhotos.writeAt(context, "tool", id, index, processed.bytes)) "" else "The photo could not be saved."
                is ProcessedImage.Failed -> processed.message
            }
            mainHandler.post {
                if (message.isEmpty()) assetPhotoTick += 1
                done(message)
            }
        }.start()
    }

    /** Removes photo [index] of an item that has [count] photos; the later photos move up one place. */
    fun removeToolPhoto(id: String, index: Int, count: Int) {
        CompanyPhotos.removeAt(getApplication<Application>(), "tool", id, index, count)
        assetPhotoTick += 1
    }

    /** Removes every photo of an item (when the item is deleted). */
    fun deleteToolPhotos(id: String) {
        CompanyPhotos.deleteAll(getApplication<Application>(), "tool", id)
        assetPhotoTick += 1
    }

    // ---------------------------------------------------------------------------------------------
    // Company storage: the total, each app's share, the log and the off-site copy
    // ---------------------------------------------------------------------------------------------

    /** A short message about the last storage action (for example the off-site backup). Empty when none. */
    var storageMessage by mutableStateOf("")
        private set

    fun clearStorageMessage() {
        storageMessage = ""
    }

    /** The assets with one more line at the top of the storage log (the newest 200 are kept). */
    private fun CompanyAssets.withStorageLog(text: String): CompanyAssets = copy(
        storageLog = (listOf(StorageLogLine(System.currentTimeMillis(), text)) + storageLog)
            .take(ChannelRules.MAX_STORAGE_LOG)
    )

    /**
     * Writes one line to the storage log for an action the owner verified with the phone's fingerprint or screen
     * lock (the line ends with "(verified)"). Called by the verification gate after the action ran.
     */
    fun logVerified(text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        saveAssets(ledger.assets.withStorageLog(line + " (verified)"))
    }

    /** Sets the company's total storage, in MB. [confirmed] is true when the owner verified the change. */
    fun setCompanyStorageTotal(totalMb: Long, confirmed: Boolean) {
        val total = maxOf(0L, totalMb)
        val old = ledger.assets.storageTotalMb
        if (total == old) return
        val text = "Company storage changed from " + StorageRules.textMb(old) + " to " + StorageRules.textMb(total) +
            (if (confirmed) " (verified)" else "")
        saveAssets(ledger.assets.copy(storageTotalMb = total).withStorageLog(text))
    }

    /** Sets one app's share of the company storage, in MB. [confirmed] is true when the owner verified the change. */
    fun setAppStorageShare(id: String, shareMb: Long, confirmed: Boolean) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        val share = maxOf(0L, shareMb)
        if (share == app.storageShareMb) return
        val text = app.name + " storage share changed from " + StorageRules.textMb(app.storageShareMb) +
            " to " + StorageRules.textMb(share) + (if (confirmed) " (verified)" else "")
        saveLedger(
            ledger.copy(
                apps = ledger.apps.map { if (it.id == id) it.copy(storageShareMb = share) else it },
                assets = ledger.assets.withStorageLog(text)
            )
        )
    }

    /**
     * "Back up off-site now": the off-site copy is made equal to the live data right now. When the cloud is on, a
     * real cloud backup of all records is kept too.
     */
    fun backUpOffsiteNow() {
        val liveKb = StorageRules.usedKb(ledger.apps)
        saveAssets(
            ledger.assets.copy(offsiteKb = liveKb, offsiteMillis = System.currentTimeMillis())
                .withStorageLog("Off-site copy made: " + StorageRules.text(liveKb))
        )
        if (cloudConfig.cloudMode != CloudMode.ON || !cloudConfig.signedIn) {
            storageMessage = "Off-site copy made. Connect to the cloud to also keep a cloud backup of all records."
            return
        }
        if (cloudBackupBusy || cloudWorking || cloudSyncing || cloudHistory.restoring) {
            storageMessage = "Off-site copy made. The cloud is busy right now, so no cloud backup was kept this time."
            return
        }
        cloudBackupBusy = true
        storageMessage = "Off-site copy made. Saving a cloud backup…"
        Thread {
            val outcome = try {
                cloudEngine.createBackup("Off-site copy")
            } catch (e: Throwable) {
                BackupOutcome(false, "The backup did not finish. Your data on the phone is safe.")
            }
            mainHandler.post {
                cloudBackupBusy = false
                storageMessage = "Off-site copy made. " + outcome.message
                refreshCloudState()
            }
        }.start()
    }

    // ---------------------------------------------------------------------------------------------
    // Income you record yourself: straight into the app's income and profit, with no store cut
    // ---------------------------------------------------------------------------------------------

    /** Records income for a live app. It goes straight into the app's income and profit: no cut, no balance. */
    fun addAppIncome(id: String, title: String, amount: Long) {
        if (id == SOFTWARE_APP_ID || amount <= 0L) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled) return
        val line = Entry(
            title = title.trim().ifEmpty { "Income" },
            amount = amount,
            isIncome = true,
            dateMillis = System.currentTimeMillis()
        )
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) app.copy(entries = app.entries + line) else it }))
    }

    /**
     * A renewal paid outside the app for a user you added: the plan end date moves forward and the money goes
     * straight into the app's income and profit, with no cut.
     */
    fun recordRenewalIncome(id: String, userId: String, amount: Long) {
        if (id == SOFTWARE_APP_ID || amount <= 0L) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled) return
        val user = app.users.firstOrNull { it.id == userId } ?: return
        if (user.simulated || user.plan == Plan.FREE) return
        val now = System.currentTimeMillis()
        val renewed = app.withUserRenewed(userId, now)
        val label = if (user.plan == Plan.PREMIUM) "Premium" else "Pro"
        val line = Entry(
            title = user.name + " renewed " + label,
            amount = amount,
            isIncome = true,
            dateMillis = now
        )
        saveLedger(
            ledger.copy(apps = ledger.apps.map { if (it.id == id) renewed.copy(entries = renewed.entries + line) else it })
        )
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

    fun startAdvert(
        appId: String,
        name: String,
        type: AdvertType,
        channel: Channel,
        platforms: List<AdvertPlatform>,
        startInDays: Int,
        lastsDays: Int
    ) {
        if (appId == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return
        val updated = AdvertEngine.create(
            app, name, type, channel, platforms, startInDays, lastsDays, System.currentTimeMillis(),
            SocialsRules.extraAdvertNames(ledger.socials)
        )
        if (updated === app) return
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == appId) updated else it }))
    }

    /** The extra advert platforms: one for every Socials account that is not one of the six fixed names. */
    fun extraAdvertPlatformNames(): List<String> = SocialsRules.extraAdvertNames(ledger.socials)

    // ---------------------------------------------------------------------------------------------
    // Socials (Delivery C-1: accounts only; the social dice come in Delivery C-2)
    // ---------------------------------------------------------------------------------------------

    /** "Today" for Socials: never earlier than the highest time the app has seen (clock protection). */
    fun socialsNow(nowMillis: Long): Long =
        maxOf(nowMillis, ledger.casesClockMillis, ledger.software.clockMillis)

    /** The state of the "Today's roll" card of Socials. */
    fun socialsRollInfo(nowMillis: Long): SocialsRollInfo =
        SocialsEngine.rollInfo(ledger, dayKey(socialsNow(nowMillis)), aiActive)

    /** Adds an account. Returns one plain sentence when it is not allowed, or null when it was added. */
    fun addSocialAccount(name: String, handle: String, displayName: String): String? {
        val error = SocialsRules.addError(ledger.socials, name, handle)
        if (error != null) return error
        val added = SocialsRules.add(ledger.socials, name, handle, displayName, System.currentTimeMillis())
        saveLedger(ledger.copy(socials = added))
        return null
    }

    /** Why an account cannot be deleted now, or null when it can. */
    fun socialDeleteBlock(accountId: String, nowMillis: Long): String? {
        val account = ledger.socials.accounts.firstOrNull { it.id == accountId } ?: return null
        return SocialsRules.deleteBlock(ledger, account, dayKey(socialsNow(nowMillis)))
    }

    /** Deletes an account (its follower history is lost). Refused while a running or upcoming advert lists it. */
    fun deleteSocialAccount(accountId: String): String? {
        val account = ledger.socials.accounts.firstOrNull { it.id == accountId } ?: return null
        val block = SocialsRules.deleteBlock(ledger, account, dayKey(socialsNow(System.currentTimeMillis())))
        if (block != null) return block
        val hadPicture = account.profileImage != null
        saveLedger(ledger.copy(socials = SocialsRules.delete(ledger.socials, accountId)))
        // The picture goes too: from the phone now, and from the cloud when it is available.
        SocialMedia.deleteAccountFolder(getApplication(), accountId)
        if (hadPicture) mediaSync.enqueueSocialRemoval(accountId)
        refreshMediaState()
        return null
    }

    // ---- Profile pictures of the social accounts (Part C-2). Saved on the phone first; the cloud copy follows. ----

    fun socialPhotoFile(accountId: String): File = SocialMedia.file(getApplication(), accountId)

    /**
     * Shrinks the picked picture (longest side 512 pixels, JPEG), saves it on the phone, gives it the next
     * version number and puts it in the upload queue. A new picture replaces the old one. The change is saved
     * at once (it does not wait for the Save button of the page).
     */
    fun setSocialPhoto(accountId: String, uri: Uri) {
        if (photoBusy) return
        if (ledger.socials.accounts.none { it.id == accountId }) return
        photoBusy = true
        val app = getApplication<Application>()
        thread(start = true) {
            val processed = ImageProcessor.process(app, uri, SOCIAL_PHOTO_MAX_SIDE)
            mainHandler.post {
                photoBusy = false
                val latest = ledger.socials.accounts.firstOrNull { it.id == accountId }
                if (latest == null) return@post
                if (processed !is ProcessedImage.Ok) {
                    photoNotice = (processed as? ProcessedImage.Failed)?.message ?: "The photo could not be added."
                    return@post
                }
                val version = (latest.profileImage?.version ?: 0) + 1
                if (!SocialMedia.write(app, accountId, processed.bytes, version)) {
                    photoNotice = "The photo could not be saved."
                    return@post
                }
                val image = SocialImage(version, processed.bytes.size.toLong(), sha256Hex(processed.bytes))
                saveLedger(
                    ledger.copy(
                        socials = ledger.socials.copy(
                            accounts = ledger.socials.accounts.map {
                                if (it.id == accountId) it.copy(profileImage = image) else it
                            }
                        )
                    )
                )
                mediaSync.enqueueSocialUpload(accountId)
                mediaTick += 1
                refreshMediaState()
            }
        }
    }

    /** Removes the profile picture: the empty circle comes back. The phone file now, the cloud file when available. */
    fun removeSocialPhoto(accountId: String) {
        val account = ledger.socials.accounts.firstOrNull { it.id == accountId } ?: return
        if (account.profileImage == null) return
        saveLedger(
            ledger.copy(
                socials = ledger.socials.copy(
                    accounts = ledger.socials.accounts.map {
                        if (it.id == accountId) it.copy(profileImage = null) else it
                    }
                )
            )
        )
        SocialMedia.deleteProfile(getApplication(), accountId)
        mediaSync.enqueueSocialRemoval(accountId)
        mediaTick += 1
        refreshMediaState()
    }

    /** Saves the edited handle, display name, following, posts and likes. Followers are never typed. */
    fun saveSocialEdits(edits: List<SocialEdit>) {
        if (edits.isEmpty()) return
        saveLedger(ledger.copy(socials = SocialsRules.applyEdits(ledger.socials, edits)))
    }

    fun stopAdvert(appId: String, advertId: String) {
        if (appId == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return
        val updated = AdvertEngine.stop(app, advertId, System.currentTimeMillis())
        if (updated === app) return
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == appId) updated else it }))
    }

    /** Turns Google Play on for an app. One way only: it can never be turned off. */
    fun enableGooglePlay(id: String) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (app.googlePlayEnabled) return
        val updated = app.withGooglePlayOn(System.currentTimeMillis())
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Turns an app live (one way) with the chosen channels. At least one channel must be on. */
    fun enableLive(id: String, play: Boolean, apple: Boolean, website: Boolean) {
        if (id == SOFTWARE_APP_ID) return
        if (!play && !apple && !website) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (app.googlePlayEnabled) return
        val updated = app.withGooglePlayOn(System.currentTimeMillis())
            .copy(playOn = play, appleOn = apple, websiteOn = website)
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Switches the channels of a live app. At least one stays on. */
    fun setChannels(id: String, play: Boolean, apple: Boolean, website: Boolean) {
        if (id == SOFTWARE_APP_ID) return
        if (!play && !apple && !website) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled) return
        val updated = app.copy(playOn = play, appleOn = apple, websiteOn = website)
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Switches Neribo Deploy style sites on or off for a live app. */
    fun setDeployTracking(id: String, on: Boolean) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled || app.deployTracking == on || !app.isDeployApp) return
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) it.copy(deployTracking = on) else it }))
    }

    /**
     * What each plan offers per user, in MB. Sites share the account's storage, so [siteMb] is not used any more
     * (it stays in the signature so the callers do not change). The change is written to the storage log;
     * [verified] is true when the owner verified a lowering with the phone's fingerprint or screen lock.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setStorageOffer(id: String, freeMb: Long, proMb: Long, premiumMb: Long, siteMb: Long, verified: Boolean = false) {
        if (id == SOFTWARE_APP_ID) return
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        if (!app.googlePlayEnabled) return
        val updated = app.copy(
            storageFreeMb = maxOf(0L, freeMb), storageProMb = maxOf(0L, proMb),
            storagePremiumMb = maxOf(0L, premiumMb)
        )
        if (updated.storageFreeMb == app.storageFreeMb && updated.storageProMb == app.storageProMb &&
            updated.storagePremiumMb == app.storagePremiumMb
        ) return
        val text = app.name + " storage offered: Free " + StorageRules.textMb(updated.storageFreeMb) +
            ", Pro " + StorageRules.textMb(updated.storageProMb) +
            ", Premium " + StorageRules.textMb(updated.storagePremiumMb) +
            (if (verified) " (verified)" else "")
        saveLedger(
            ledger.copy(
                apps = ledger.apps.map { if (it.id == id) updated else it },
                assets = ledger.assets.withStorageLog(text)
            )
        )
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
        // Photos change only through addStaffPhotos / removeStaffPhoto, so an edit never loses or brings back one.
        val current = ledger.staff.firstOrNull { it.id == updated.id }
        val fixed = if (current != null) updated.copy(images = current.images) else updated
        saveLedger(
            ledger.copy(staff = ledger.staff.map { if (it.id == fixed.id) fixed else it })
        )
    }

    fun deleteStaff(id: String) {
        val gone = ledger.staff.firstOrNull { it.id == id }
        openStaffId = null
        saveLedger(ledger.copy(staff = ledger.staff.filter { it.id != id }))
        if (gone != null && gone.images.isNotEmpty()) {
            // Their photos go too: from the phone now, and from the cloud when it is available.
            for (img in gone.images) mediaSync.enqueueRemoval(id, img.id)
            StaffMedia.deleteStaffFolder(getApplication(), id)
            refreshMediaState()
        }
    }

    // ---- Staff photos (Part B). Saved on the phone first; the cloud copy follows when it is available. ----

    /** Goes up when a photo file arrives from the cloud, so thumbnails redraw. */
    var mediaTick by mutableStateOf(0)
        private set

    /** True while picked photos are being shrunk and saved. */
    var photoBusy by mutableStateOf(false)
        private set

    /** A one-time message about adding photos. Empty when there is nothing to say. */
    var photoNotice by mutableStateOf("")
        private set

    /** Ids of photos that are still waiting to be uploaded. */
    var pendingPhotoIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Ids of social accounts whose profile picture is still waiting to be uploaded. */
    var pendingSocialPhotoIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** A short line for the Cloud card about waiting or refused photos. Empty when all is well. */
    var mediaMessage by mutableStateOf("")
        private set

    @Volatile
    private var manualMediaRetry = false

    fun consumePhotoNotice() {
        photoNotice = ""
    }

    fun staffImageFile(staffId: String, imageId: String): File =
        StaffMedia.file(getApplication(), staffId, imageId)

    private fun refreshMediaState() {
        val s = mediaSync.summary()
        pendingPhotoIds = s.uploadingImageIds
        pendingSocialPhotoIds = s.uploadingSocialIds
        mediaMessage = when {
            s.failedUploads == 1 -> "1 photo could not be uploaded"
            s.failedUploads > 1 -> s.failedUploads.toString() + " photos could not be uploaded"
            s.waiting > 0 && cloudConfig.cloudMode == CloudMode.ON ->
                s.waiting.toString() + (if (s.waiting == 1) " photo change" else " photo changes") + " waiting to sync"
            else -> ""
        }
    }

    /** Shrinks and saves the picked photos for one staff member (never more than 5 in total). */
    fun addStaffPhotos(staffId: String, uris: List<Uri>) {
        if (uris.isEmpty() || photoBusy) return
        val staff = ledger.staff.firstOrNull { it.id == staffId } ?: return
        val room = MAX_STAFF_IMAGES - staff.images.size
        if (room <= 0) {
            photoNotice = "Maximum $MAX_STAFF_IMAGES photos. Delete one to add another."
            return
        }
        val picked = uris.take(room)
        val skippedForRoom = uris.size - picked.size
        photoBusy = true
        val app = getApplication<Application>()
        thread(start = true) {
            val added = ArrayList<StaffImage>()
            var failed = 0
            for (uri in picked) {
                val result = ImageProcessor.process(app, uri)
                if (result is ProcessedImage.Ok) {
                    val id = newImageId()
                    if (StaffMedia.write(app, staffId, id, result.bytes)) {
                        added.add(StaffImage(id, System.currentTimeMillis(), result.bytes.size.toLong(), sha256Hex(result.bytes)))
                    } else {
                        failed += 1
                    }
                } else {
                    failed += 1
                }
            }
            mainHandler.post {
                photoBusy = false
                val latest = ledger.staff.firstOrNull { it.id == staffId }
                if (latest == null) {
                    // The staff member was deleted while the photos were being saved.
                    for (img in added) StaffMedia.deleteImage(app, staffId, img.id)
                    return@post
                }
                val roomNow = MAX_STAFF_IMAGES - latest.images.size
                val keep = added.take(maxOf(0, roomNow))
                for (img in added.drop(keep.size)) StaffMedia.deleteImage(app, staffId, img.id)
                if (keep.isNotEmpty()) {
                    saveLedger(
                        ledger.copy(
                            staff = ledger.staff.map {
                                if (it.id == staffId) it.copy(images = it.images + keep) else it
                            }
                        )
                    )
                    mediaSync.enqueueUploads(staffId, keep.map { it.id })
                    refreshMediaState()
                }
                photoNotice = when {
                    keep.isEmpty() && failed > 0 -> "The photo could not be added."
                    failed > 0 -> "Added " + keep.size + ". " + failed + " could not be added."
                    skippedForRoom > 0 || added.size > keep.size ->
                        "Maximum $MAX_STAFF_IMAGES photos. Only " + keep.size + " were added."
                    else -> ""
                }
            }
        }
    }

    /** Removes one photo from a staff member: the phone file now, the cloud file when it is available. */
    fun removeStaffPhoto(staffId: String, imageId: String) {
        val staff = ledger.staff.firstOrNull { it.id == staffId } ?: return
        if (staff.images.none { it.id == imageId }) return
        saveLedger(
            ledger.copy(
                staff = ledger.staff.map {
                    if (it.id == staffId) it.copy(images = it.images.filter { img -> img.id != imageId }) else it
                }
            )
        )
        StaffMedia.deleteImage(getApplication(), staffId, imageId)
        mediaSync.enqueueRemoval(staffId, imageId)
        refreshMediaState()
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

    private companion object {
        /** Wait this long after the last change before syncing. */
        const val DEBOUNCE_MILLIS = 2_500L

        /** The foreground timer: one sync every 5 minutes while the app is open. */
        const val FOREGROUND_TICK_MILLIS = 5L * 60L * 1000L

        /** While AI mode is ON and the app is open, Claude's jobs are looked at this often. */
        const val AI_TICK_MILLIS = 20_000L

        /** Two resumes closer than this give one sync. */
        const val RESUME_SYNC_GAP_MILLIS = 5_000L
    }
}
