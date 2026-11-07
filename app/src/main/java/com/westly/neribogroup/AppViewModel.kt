package com.westly.neribogroup

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import com.westly.neribogroup.ui.formatDate
import kotlin.concurrent.thread
import kotlin.random.Random

/** The pages that open from the home screen tiles. */
enum class Section { APPS, RENEWALS, STAFF, COSTS, PLAY, CASES }

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
