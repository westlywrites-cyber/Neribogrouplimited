package com.westly.neribogroup.feature.storageroom

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.westly.neribogroup.BuildConfig
import com.westly.neribogroup.cloud.RoomState
import com.westly.neribogroup.data.StorageRules
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

/** What the plugged-in drive is doing. NONE = no drive is plugged into the backup server. */
enum class DrivePhase { NONE, PLUG, MOUNT, COPY, VERIFY, SAFE }

/** One off-site drive. [onDesk] is true while it stands in the dock on the table, false while it is with the owner. */
data class DriveInfo(val key: Char, val onDesk: Boolean, val copiedKb: Long, val lastMillis: Long, val failed: Boolean)

/** The last result of one drive on one day. [day] is like 2026-11-14. */
data class DriveDay(val day: String, val key: Char, val ok: Boolean, val kb: Long)

/** Everything the room needs to draw the drives at one moment. */
data class DriveView(
    val plugged: Char?,
    val phase: DrivePhase,
    val progress: Float,
    val toCopyKb: Long,
    val doneKb: Long,
    val mbPerSecond: Int,
    val realSecondsLeft: Long,
    val a: DriveInfo,
    val b: DriveInfo,
    val next: Char,
    val days: List<DriveDay>
) {
    fun info(key: Char): DriveInfo = if (key == 'A') a else b
    val onDesk: Char? get() = if (a.onDesk) 'A' else if (b.onDesk) 'B' else null
    val busy: Boolean get() = plugged != null && phase != DrivePhase.SAFE

    companion object {
        val NONE = DriveView(
            null, DrivePhase.NONE, 0f, 0L, 0L, 0, 0L,
            DriveInfo('A', true, 0L, 0L, false), DriveInfo('B', false, 0L, 0L, false), 'A', emptyList()
        )
    }
}

/** What the owner can do with the drives. Each returns a sentence when it is refused, or null when it worked. */
class DriveActions(
    val bring: (Char) -> String?,
    val plugIn: (Char) -> String?,
    val unplug: () -> String?,
    val takeHome: (Char) -> String?
) {
    companion object {
        val NONE = DriveActions({ null }, { null }, { null }, { null })
    }
}

/**
 * The two off-site drives (A and B) of the Storage room. The state is a small file in the app's private folder, and
 * in the Storage Room app the same text is also saved in the cloud (table room_state, key "drives") after every
 * change, so after a reinstall or an update the drives come back exactly as they were. The phase is worked out
 * from the clock every time it is read, so a copy keeps counting while the app is closed.
 * Nothing here touches the ledger or the normal cloud sync, and its console lines stay in the phone's memory.
 */
object RoomDrive {
    /** The size of each drive: 25 TB, counted in KB like the rest of the app (1 TB = 1024 x 1024 x 1024 KB). */
    const val CAPACITY_KB = 25L * 1024L * 1024L * 1024L

    /** 1 real second on the screen counts as this many seconds of a real copy (1 = real time: 27 h really takes 27 h). */
    const val COPY_SCALE = 1

    const val PLUG_SECONDS = 2
    const val MOUNT_SECONDS = 3
    const val MIN_COPY_SECONDS = 8

    private const val MB_START = 120.0
    private const val MB_END = 95.0
    private const val FILE_NAME = "storage_room_drives.txt"

    /** Changes on every action, so screens that watch it redraw at once. */
    var changes by mutableIntStateOf(0)
        private set

    private var loaded = false

    /** When the saved text was written (the "U" line). The newer of the phone's text and the cloud's text wins. */
    private var localU = 0L

    /** True when this phone had no saved file at all (a fresh install): the cloud copy always wins then. */
    private var fresh = false
    private var pulling = false
    private var pulledOnce = false
    private var lastTry = 0L
    private val onDesk = booleanArrayOf(true, false)
    private val copied = longArrayOf(0L, 0L)
    private val last = longArrayOf(0L, 0L)
    private val failed = booleanArrayOf(false, false)
    private var cur = -1
    private var phase = DrivePhase.NONE
    private var t0 = 0L
    private var durMs = 0L
    private var toCopy = 0L
    private var target = 0L
    private var next = 0
    private var bucket = 0
    private val days = mutableListOf<DriveDay>()

    private fun idx(key: Char) = if (key == 'B') 1 else 0
    private fun ch(i: Int) = if (i == 1) 'B' else 'A'
    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
    private fun today(): String = LocalDate.now().toString()

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(context)
            if (!f.exists()) {
                fresh = true
                return
            }
            applyText(f.readText())
        } catch (e: Exception) {
            // A damaged file is ignored: the drives start fresh (and the cloud copy fills them in).
            cur = -1
            phase = DrivePhase.NONE
            fresh = true
        }
    }

    /** Reads a saved text (the phone's file or the cloud's copy) into the drives. */
    private fun applyText(text: String) {
        onDesk[0] = true
        onDesk[1] = false
        copied[0] = 0L
        copied[1] = 0L
        last[0] = 0L
        last[1] = 0L
        failed[0] = false
        failed[1] = false
        cur = -1
        phase = DrivePhase.NONE
        t0 = 0L
        durMs = 0L
        toCopy = 0L
        target = 0L
        next = 0
        bucket = 0
        localU = 0L
        days.clear()
        for (line in text.lines()) {
            val p = line.split("|")
            when (p.getOrNull(0)) {
                "U" -> localU = p[1].toLong()
                "D" -> {
                    val i = idx(p[1].firstOrNull() ?: 'A')
                    onDesk[i] = p[2] == "1"
                    copied[i] = p[3].toLong()
                    last[i] = p[4].toLong()
                    failed[i] = p[5] == "1"
                }
                "C" -> {
                    cur = p[1].toInt()
                    phase = DrivePhase.valueOf(p[2])
                    t0 = p[3].toLong()
                    durMs = p[4].toLong()
                    toCopy = p[5].toLong()
                    target = p[6].toLong()
                }
                "N" -> next = p[1].toInt()
                "H" -> days.add(DriveDay(p[1], p[2][0], p[3] == "1", p[4].toLong()))
            }
        }
    }

    private fun serialize(): String {
        val sb = StringBuilder()
        sb.append("U|").append(localU).append('\n')
        for (i in 0..1) {
            sb.append("D|").append(ch(i)).append('|').append(if (onDesk[i]) 1 else 0).append('|').append(copied[i])
                .append('|').append(last[i]).append('|').append(if (failed[i]) 1 else 0).append('\n')
        }
        sb.append("C|").append(cur).append('|').append(phase.name).append('|').append(t0).append('|').append(durMs)
            .append('|').append(toCopy).append('|').append(target).append('\n')
        sb.append("N|").append(next).append('\n')
        for (d in days) {
            sb.append("H|").append(d.day).append('|').append(d.key).append('|').append(if (d.ok) 1 else 0)
                .append('|').append(d.kb).append('\n')
        }
        return sb.toString()
    }

    private fun save(context: Context) {
        localU = System.currentTimeMillis()
        val text = serialize()
        try {
            file(context).writeText(text)
        } catch (e: Exception) {
            // The state still works for this run of the app.
        }
        // The Storage Room app also keeps it in the cloud (sent at once, kept until the cloud confirms it).
        if (BuildConfig.ROOM_ONLY) RoomState.push("drives", text)
    }

    private fun bump() {
        Handler(Looper.getMainLooper()).post { changes += 1 }
    }

    /**
     * Storage Room app only: the first time (and again after a failed try) the cloud copy is read. A fresh install, or
     * a cloud text that is newer, replaces what is on the phone; a phone text that is newer is sent to the cloud.
     * After that, anything waiting to be saved is sent again every time this is called.
     */
    private fun cloudSync(context: Context, now: Long) {
        if (!BuildConfig.ROOM_ONLY) return
        if (pulledOnce) {
            RoomState.flushAsync()
            return
        }
        if (pulling || now - lastTry < 4_000L) return
        pulling = true
        lastTry = now
        val app = context.applicationContext
        Thread {
            try {
                when (val r = RoomState.pull("drives")) {
                    is RoomState.Pulled.Found -> {
                        val cloudU = RoomState.stamp(r.content)
                        var replaced = false
                        synchronized(this@RoomDrive) {
                            if (fresh || cloudU > localU) {
                                RoomState.discard("drives")
                                applyText(r.content)
                                fresh = false
                                try {
                                    file(app).writeText(serialize())
                                } catch (e: Exception) {
                                    // Kept in memory for this run.
                                }
                                replaced = true
                            } else if (localU > cloudU) {
                                RoomState.push("drives", serialize())
                            }
                            pulledOnce = true
                        }
                        RoomState.markReady("drives")
                        if (replaced) bump()
                    }
                    RoomState.Pulled.None -> {
                        synchronized(this@RoomDrive) {
                            if (!fresh) RoomState.push("drives", serialize())
                            pulledOnce = true
                        }
                        RoomState.markReady("drives")
                    }
                    RoomState.Pulled.Unavailable -> Unit
                }
            } catch (e: Exception) {
                // Tried again a little later.
            } finally {
                pulling = false
            }
        }.start()
    }

    private fun addDay(key: Char, ok: Boolean, kb: Long) {
        val day = today()
        days.removeAll { it.day == day && it.key == key }
        days.add(DriveDay(day, key, ok, kb))
        while (days.size > 60) days.removeAt(0)
    }

    private fun verifyMs() = maxOf(2000L, durMs / 25L)

    private fun fail(context: Context, text: String) {
        val i = cur
        failed[i] = true
        addDay(ch(i), false, 0L)
        cur = -1
        phase = DrivePhase.NONE
        StorageRoomLog.add(RoomLevel.ERROR, "usb", text)
        save(context)
    }

    /** What the drives are doing now. A finished step (plugging, mounting, copying, checking) is settled here. */
    @Synchronized
    fun view(context: Context, powerOn: Boolean, liveKb: Long, now: Long = System.currentTimeMillis()): DriveView {
        load(context)
        cloudSync(context, now)
        if (cur >= 0) {
            val k = ch(cur)
            if (!powerOn && phase != DrivePhase.SAFE) {
                fail(context, "server off: drive $k lost power. The copy is incomplete")
            } else {
                var steps = 0
                var changed = false
                while (cur >= 0 && steps++ < 6) {
                    val el = now - t0
                    if (phase == DrivePhase.PLUG && el >= PLUG_SECONDS * 1000L) {
                        phase = DrivePhase.MOUNT
                        t0 += PLUG_SECONDS * 1000L
                        StorageRoomLog.add(RoomLevel.INFO, "usb", "drive $k detected (25 TB, USB 3.0)")
                        changed = true
                    } else if (phase == DrivePhase.MOUNT && el >= MOUNT_SECONDS * 1000L) {
                        phase = DrivePhase.COPY
                        t0 += MOUNT_SECONDS * 1000L
                        StorageRoomLog.add(RoomLevel.INFO, "mount", "/mnt/drive-$k ready")
                        val what = if (last[cur] == 0L || copied[cur] == 0L) "first full copy: " else "only the changes: "
                        StorageRoomLog.add(RoomLevel.SYNC, "rsync", what + StorageRules.text(toCopy))
                        changed = true
                    } else if (phase == DrivePhase.COPY && el >= durMs) {
                        phase = DrivePhase.VERIFY
                        t0 += durMs
                        StorageRoomLog.add(RoomLevel.SYNC, "rsync", "copy finished (" + StorageRules.text(toCopy) + "). Checking the files…")
                        changed = true
                    } else if (phase == DrivePhase.VERIFY && el >= verifyMs()) {
                        phase = DrivePhase.SAFE
                        copied[cur] = target
                        last[cur] = now
                        failed[cur] = false
                        addDay(k, true, target)
                        StorageRoomLog.add(RoomLevel.INFO, "verify", "all files match. Drive $k is safe to remove")
                        changed = true
                    } else {
                        break
                    }
                }
                if (phase == DrivePhase.COPY && durMs > 0L) {
                    val b = (((now - t0).toFloat() / durMs.toFloat()) * 10f).toInt()
                    if (b in 1..9 && b > bucket) {
                        bucket = b
                        val kb = toCopy * b / 10L
                        StorageRoomLog.add(RoomLevel.SYNC, "rsync", "copied " + StorageRules.text(kb) + " of " + StorageRules.text(toCopy))
                    }
                }
                if (changed) save(context)
            }
        }
        val el = now - t0
        val p = when (phase) {
            DrivePhase.COPY -> if (durMs > 0L) (el.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 1f
            DrivePhase.VERIFY, DrivePhase.SAFE -> 1f
            else -> 0f
        }
        val mb = if (phase == DrivePhase.COPY) (MB_START - (MB_START - MB_END) * p + ((now / 1000L) % 5L - 2L)).toInt() else 0
        val left = if (phase == DrivePhase.COPY) ((durMs - el).coerceAtLeast(0L) * COPY_SCALE) / 1000L else 0L
        return DriveView(
            if (cur >= 0) ch(cur) else null, if (cur >= 0) phase else DrivePhase.NONE, p, toCopy, (toCopy * p).toLong(), mb, left,
            DriveInfo('A', onDesk[0], copied[0], last[0], failed[0]),
            DriveInfo('B', onDesk[1], copied[1], last[1], failed[1]),
            ch(next), days.toList()
        )
    }

    /** Brings a drive from the owner's home to the dock on the table. */
    @Synchronized
    fun bring(context: Context, key: Char): String? {
        load(context)
        if (onDesk[0] || onDesk[1]) return "Drive " + ch(if (onDesk[0]) 0 else 1) + " is already on the table."
        onDesk[idx(key)] = true
        save(context)
        StorageRoomLog.add(RoomLevel.CHANGE, "drive", "drive $key brought from home and placed in the dock")
        changes += 1
        return null
    }

    /** Plugs the drive that stands on the table into the backup server and starts the copy. */
    @Synchronized
    fun plugIn(context: Context, key: Char, powerOn: Boolean, liveKb: Long, now: Long = System.currentTimeMillis()): String? {
        load(context)
        val i = idx(key)
        if (cur >= 0) return "Drive " + ch(cur) + " is already plugged in."
        if (!onDesk[i]) return "Drive $key is with you. Bring it to the table first."
        if (!powerOn) return "The server is off. Switch it on first."
        if (liveKb > CAPACITY_KB) return "Drive too small: live data is " + StorageRules.text(liveKb) + ", the drive holds 25 TB."
        val first = last[i] == 0L || copied[i] == 0L
        toCopy = if (first) liveKb else maxOf(0L, liveKb - copied[i]) + liveKb / 100L
        target = liveKb
        val seconds = (toCopy / 1024.0) / ((MB_START + MB_END) / 2.0)
        durMs = maxOf(MIN_COPY_SECONDS * 1000L, (seconds * 1000.0 / COPY_SCALE).toLong())
        cur = i
        phase = DrivePhase.PLUG
        t0 = now
        failed[i] = false
        bucket = 0
        save(context)
        StorageRoomLog.add(RoomLevel.CHANGE, "usb", "drive $key plugged into the backup server")
        changes += 1
        return null
    }

    /** Unplugs the drive. Before the copy is checked this leaves an incomplete copy. The drive stays on the table. */
    @Synchronized
    fun unplug(context: Context): String? {
        load(context)
        if (cur < 0) return null
        val k = ch(cur)
        if (phase != DrivePhase.SAFE) {
            fail(context, "drive $k removed during the copy. The copy is incomplete, make it again")
        } else {
            cur = -1
            phase = DrivePhase.NONE
            StorageRoomLog.add(RoomLevel.INFO, "usb", "drive $k removed safely")
            save(context)
        }
        changes += 1
        return null
    }

    /** The owner takes the drive from the table and carries it home. */
    @Synchronized
    fun takeHome(context: Context, key: Char): String? {
        load(context)
        val i = idx(key)
        if (cur == i) return "Unplug drive $key first."
        if (!onDesk[i]) return null
        onDesk[i] = false
        next = 1 - i
        save(context)
        StorageRoomLog.add(RoomLevel.INFO, "drive", "drive $key taken home")
        changes += 1
        return null
    }
}

/**
 * The live state of the drives, redrawn twice a second while one is plugged in and every second or two otherwise.
 * Because it is worked out from the clock, it is right the moment the app is opened again.
 */
@Composable
fun rememberRoomDrive(power: State<PowerView>, live: State<Long>): State<DriveView> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(RoomDrive.view(context, power.value.phase == PowerPhase.ON, live.value)) }
    LaunchedEffect(RoomDrive.changes) {
        while (true) {
            val v = RoomDrive.view(context, power.value.phase == PowerPhase.ON, live.value)
            if (v != state.value) state.value = v
            delay(if (v.busy) 500L else 1500L)
        }
    }
    return state
}
