package com.westly.neribogroup.feature.dell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The four states of the Dell on the CEO desk. */
enum class DellPhase { ON, SHUTTING, OFF, BOOTING }

/**
 * Whether the Dell is switched off. It is kept only on this phone (a tiny private setting) and it belongs to the
 * Dell alone: it never touches the Storage room's server power ([com.westly.neribogroup.feature.storageroom.RoomPower]),
 * the ledger, the cloud sync or Supabase. Switching the Dell off leaves the Storage room servers running.
 */
object DellPower {
    private const val PREFS = "neribo_dell"
    private const val KEY_OFF = "dell_off"

    var isOff by mutableStateOf(false)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        isOff = try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false)
        } catch (e: Exception) {
            false
        }
    }

    fun setOff(context: Context, off: Boolean) {
        isOff = off
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OFF, off).apply()
        } catch (e: Exception) {
            // The state still works for this run of the app.
        }
    }
}
