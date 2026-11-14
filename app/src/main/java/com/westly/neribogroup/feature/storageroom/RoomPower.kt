package com.westly.neribogroup.feature.storageroom

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File

/** The four states of the server in the Storage room. */
enum class PowerPhase { ON, SHUTTING, OFF, BOOTING }

/** What the server is doing at one moment. [elapsed] is the seconds since the shutdown or start-up began. */
data class PowerView(val phase: PowerPhase, val elapsed: Int) {
    val busy: Boolean get() = phase == PowerPhase.SHUTTING || phase == PowerPhase.BOOTING

    /** Seconds the whole shutdown or start-up takes (0 when nothing is running). */
    val total: Int
        get() = when (phase) {
            PowerPhase.SHUTTING -> RoomPower.SHUTDOWN_SECONDS
            PowerPhase.BOOTING -> RoomPower.BOOT_SECONDS
            else -> 0
        }

    val percent: Int get() = if (total == 0) 0 else (elapsed * 100 / total).coerceIn(0, 100)
    val secondsLeft: Int get() = (total - elapsed).coerceAtLeast(0)

    /** True while the apps count as offline: from 45 s into a shutdown until 585 s into a start-up. */
    val appsOffline: Boolean
        get() = when (phase) {
            PowerPhase.ON -> false
            PowerPhase.SHUTTING -> elapsed >= RoomPower.APPS_DOWN_AT
            PowerPhase.OFF -> true
            PowerPhase.BOOTING -> elapsed < RoomPower.APPS_UP_AT
        }

    companion object {
        val ON = PowerView(PowerPhase.ON, 0)
    }
}

/**
 * The power of the pretend server, kept only on this phone. It is a tiny file in the app's private folder (not backed
 * up, never sent anywhere): the phase and the time it began. The phase is worked out from the clock every time it is
 * read, so a shutdown or a start-up keeps counting while the app is closed.
 * Nothing here touches the ledger, the cloud sync or Supabase.
 */
object RoomPower {
    const val SHUTDOWN_SECONDS = 300
    const val BOOT_SECONDS = 600
    const val APPS_DOWN_AT = 45
    const val APPS_UP_AT = 585

    private const val FILE_NAME = "storage_room_power.txt"

    /** Changes on every switch, so screens that watch it redraw at once. */
    var changes by mutableIntStateOf(0)
        private set

    private var loaded = false
    private var phase = PowerPhase.ON
    private var startedAt = 0L

    private fun file(context: Context): File = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(context)
            if (f.exists()) {
                val parts = f.readText().trim().split("|")
                val p = PowerPhase.values().firstOrNull { it.name == parts.getOrNull(0) }
                val t = parts.getOrNull(1)?.toLongOrNull()
                if (p != null && t != null) {
                    phase = p
                    startedAt = t
                }
            }
        } catch (e: Exception) {
            phase = PowerPhase.ON
        }
    }

    private fun save(context: Context) {
        try {
            file(context).writeText(phase.name + "|" + startedAt)
        } catch (e: Exception) {
            // The state still works for this run of the app.
        }
    }

    /** What the server is doing now. A finished shutdown or start-up is settled here. */
    @Synchronized
    fun view(context: Context, now: Long = System.currentTimeMillis()): PowerView {
        load(context)
        val elapsed = ((now - startedAt) / 1000L).coerceAtLeast(0L)
        if (phase == PowerPhase.SHUTTING && elapsed >= SHUTDOWN_SECONDS) {
            phase = PowerPhase.OFF
            startedAt += SHUTDOWN_SECONDS * 1000L
            save(context)
        } else if (phase == PowerPhase.BOOTING && elapsed >= BOOT_SECONDS) {
            phase = PowerPhase.ON
            startedAt += BOOT_SECONDS * 1000L
            save(context)
        }
        val sinceStart = ((now - startedAt) / 1000L).coerceAtLeast(0L).toInt()
        return when (phase) {
            PowerPhase.SHUTTING, PowerPhase.BOOTING -> PowerView(phase, sinceStart.coerceAtMost(total(phase)))
            else -> PowerView(phase, 0)
        }
    }

    private fun total(p: PowerPhase) = if (p == PowerPhase.SHUTTING) SHUTDOWN_SECONDS else BOOT_SECONDS

    @Synchronized
    fun startShutdown(context: Context) {
        load(context)
        if (view(context).phase != PowerPhase.ON) return
        phase = PowerPhase.SHUTTING
        startedAt = System.currentTimeMillis()
        save(context)
        StorageRoomLog.add(RoomLevel.CHANGE, "server", "shutdown requested (graceful)")
        changes += 1
    }

    @Synchronized
    fun startBoot(context: Context) {
        load(context)
        if (view(context).phase != PowerPhase.OFF) return
        phase = PowerPhase.BOOTING
        startedAt = System.currentTimeMillis()
        save(context)
        StorageRoomLog.add(RoomLevel.CHANGE, "server", "power switched on")
        changes += 1
    }

    // ---- The lines the monitor prints (seconds after the start) ----

    private val SHUT_LINES = listOf(
        0 to "Shutdown signal sent",
        10 to "Warning connected users…",
        30 to "Stopping apps and services…",
        70 to "Flushing writes to disk…",
        110 to "Final sync: main → backup…",
        160 to "Stopping the nightly backup service…",
        190 to "Unmounting main storage…",
        220 to "Unmounting backup storage…",
        250 to "Stopping the operating system…",
        272 to "System halted. Powering off…"
    )

    private val BOOT_LINES = listOf(
        5 to "Power applied. UPS 1 and 2 online",
        20 to "Power-on self-test…",
        45 to "Checking memory…",
        75 to "Detecting drives…",
        110 to "Drive health check: 4 of 4 OK",
        150 to "Starting the operating system…",
        200 to "Loading kernel and drivers…",
        260 to "Starting system services…",
        330 to "Network link up. Router reached",
        365 to "10G link to backup server up",
        400 to "Mounting main storage…",
        450 to "Checking the file system…",
        490 to "Mounting backup storage…",
        520 to "Starting sync: main → backup",
        560 to "Starting apps and services…",
        595 to "Server online"
    )

    /** The lines printed so far, oldest first. */
    fun lines(view: PowerView): List<String> {
        val source = when (view.phase) {
            PowerPhase.SHUTTING -> SHUT_LINES
            PowerPhase.BOOTING -> BOOT_LINES
            else -> return emptyList()
        }
        return source.filter { it.first <= view.elapsed }.map { it.second }
    }

    /** Which picture of the server room to show: "on", "off", "s1" (first lights) or "s2" (all lights, starting). */
    fun picture(view: PowerView): String = when (view.phase) {
        PowerPhase.ON -> "on"
        PowerPhase.OFF -> "off"
        PowerPhase.SHUTTING -> if (view.elapsed >= 275) "off" else "on"
        PowerPhase.BOOTING -> when {
            view.elapsed >= 590 -> "on"
            view.elapsed >= 30 -> "s2"
            view.elapsed >= 3 -> "s1"
            else -> "off"
        }
    }

    /** The hum plays while the servers are running: until 282 s into a shutdown, and from 30 s into a start-up. */
    fun humPlaying(view: PowerView): Boolean = when (view.phase) {
        PowerPhase.ON -> true
        PowerPhase.OFF -> false
        PowerPhase.SHUTTING -> view.elapsed < 282
        PowerPhase.BOOTING -> view.elapsed >= 30
    }
}

/**
 * The live power state, redrawn twice a second while a shutdown or start-up runs and every few seconds otherwise.
 * Because it is worked out from the clock, it is right the moment the app is opened again.
 */
@Composable
fun rememberRoomPower(): State<PowerView> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(RoomPower.view(context)) }
    LaunchedEffect(RoomPower.changes) {
        while (true) {
            val v = RoomPower.view(context)
            val before = state.value.phase
            if (v != state.value) state.value = v
            // Lines for the Log page, written once per change that is seen.
            if (before == PowerPhase.SHUTTING && v.phase != PowerPhase.SHUTTING) {
                StorageRoomLog.addUnique(RoomLevel.ERROR, "server", "server is off, apps offline")
            }
            if (before == PowerPhase.BOOTING && v.phase != PowerPhase.BOOTING) {
                StorageRoomLog.addUnique(RoomLevel.INFO, "server", "server is on, apps online")
            }
            delay(if (v.busy) 500L else 3000L)
        }
    }
    return state
}
