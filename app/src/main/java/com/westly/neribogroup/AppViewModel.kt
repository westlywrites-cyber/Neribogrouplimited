package com.westly.neribogroup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.Staff

/** The pages that open from the home screen tiles. */
enum class Section { APPS, RENEWALS, STAFF, COSTS, PLAY }

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var ledger by mutableStateOf(LedgerStorage.load(application))
        private set

    /** The page opened from a home tile (Apps, Renewals, Staff or Company costs), or null on the home screen. */
    var openSection by mutableStateOf<Section?>(null)

    /** The id of the app dashboard that is open, or null when it is not showing. */
    var openAppId by mutableStateOf<String?>(null)

    /** The id of the staff member that is open, or null when it is not showing. */
    var openStaffId by mutableStateOf<String?>(null)

    fun saveLedger(newLedger: Ledger) {
        ledger = newLedger
        LedgerStorage.save(getApplication(), newLedger)
    }

    fun saveApp(updated: AppData) {
        val current = ledger.apps.firstOrNull { it.id == updated.id }
        // Everything the dice own (their users, rolls, Play Balance, campaign) stays as it is saved.
        val finalApp = if (current != null) updated.keepingDiceData(current) else updated
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
            openSection = null
            saveLedger(imported)
            true
        } catch (e: Exception) {
            false
        }
    }
}
