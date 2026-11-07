package com.westly.neribogroup

import android.app.Application
import android.content.Context
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
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.data.markCasePaid
import com.westly.neribogroup.data.withCaseNote
import com.westly.neribogroup.data.withClockAtLeast

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
        saveLedger(DiceEngine.roll(ledger, id, System.currentTimeMillis()))
    }

    /** Moves one app's Google Play Balance into profit. */
    fun withdrawApp(id: String) {
        saveLedger(DiceEngine.withdrawApp(ledger, id, System.currentTimeMillis()))
    }

    /** Moves every app's Google Play Balance into profit. */
    fun withdrawAll() {
        saveLedger(DiceEngine.withdrawAll(ledger, System.currentTimeMillis()))
    }

    fun startCampaign(id: String, size: CampaignSize, startInDays: Int, lastsDays: Int) {
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        val updated = DiceEngine.startCampaign(
            app, size, startInDays, lastsDays, System.currentTimeMillis()
        )
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    fun stopCampaign(id: String) {
        val app = ledger.apps.firstOrNull { it.id == id } ?: return
        val updated = DiceEngine.stopCampaign(app, System.currentTimeMillis())
        saveLedger(ledger.copy(apps = ledger.apps.map { if (it.id == id) updated else it }))
    }

    /** Turns Google Play on for an app. One way only: it can never be turned off. */
    fun enableGooglePlay(id: String) {
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

    fun deleteApp(id: String) {
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
            val imported = LedgerStorage.fromJson(text)
            openAppId = null
            openStaffId = null
            openCaseId = null
            openSection = null
            showSettings = false
            saveLedger(imported)
            true
        } catch (e: Exception) {
            false
        }
    }
}
