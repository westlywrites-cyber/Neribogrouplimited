package com.westly.neribogroup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var ledger by mutableStateOf(LedgerStorage.load(application))
        private set

    /** The id of the app dashboard that is open, or null when the main dashboard is showing. */
    var openAppId by mutableStateOf<String?>(null)

    fun saveLedger(newLedger: Ledger) {
        ledger = newLedger
        LedgerStorage.save(getApplication(), newLedger)
    }

    fun saveApp(updated: AppData) {
        saveLedger(
            ledger.copy(apps = ledger.apps.map { if (it.id == updated.id) updated else it })
        )
    }

    fun deleteApp(id: String) {
        openAppId = null
        saveLedger(ledger.copy(apps = ledger.apps.filter { it.id != id }))
    }

    fun exportJson(): String = LedgerStorage.toJson(ledger)

    /** Returns true if the backup was valid and has replaced the current data. */
    fun importJson(text: String): Boolean {
        return try {
            val imported = LedgerStorage.fromJson(text)
            openAppId = null
            saveLedger(imported)
            true
        } catch (e: Exception) {
            false
        }
    }
}
