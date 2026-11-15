package com.westly.neribogroup.feature.storageroom

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.westly.neribogroup.data.StorageRules
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One night of the pretend nightly backup (it runs at 2:00 AM). [fixedAtMillis] is set when the owner ran it again by hand. */
data class BackupNight(val day: LocalDate, val ok: Boolean, val reason: String, val fixedAtMillis: Long)

/** Everything the room needs to draw the backup story at one moment. */
data class BackupView(
    /** The last 7 nights, newest first. */
    val nights: List<BackupNight>,
    /** The latest night failed and nobody has run it again yet. */
    val lastFailed: Boolean,
    val lastReason: String,
    val retryRunning: Boolean,
    val retryProgress: Float,
    val retryLeftSeconds: Long,
    /** The Starlink signal is weak right now (a short, harmless event). */
    val routerWeak: Boolean,
    /** The main server has a fault right now and the backup server is serving as the main one. */
    val failover: Boolean,
    /** 0 when an off-site drive was copied lately, otherwise the days since the last drive copy (5 or more). */
    val driveDueDays: Int
) {
    /** The "Run backup now" line of the Copies page shows while the last night is failed or the run is going. */
    val showRetryRow: Boolean get() = lastFailed || retryRunning

    companion object {
        val NONE = BackupView(emptyList(), false, "", false, 0f, 0L, false, false, 0)
    }
}

/** What the owner can do with the backup. Returns a sentence when it is refused, or null when it worked. */
class BackupActions(val retry: () -> String?) {
    companion object {
        val NONE = BackupActions { null }
    }
}

/**
 * The story of the pretend company's backups. Nothing here is real and nothing here needs the internet.
 *
 * Every result is worked out from the date alone (the same date always gives the same answer), so opening the
 * app after some days away fills in the missed nights, and an old night never changes. About 1 night in 10 fails,
 * never two nights in a row, so the night after a failure always passes (that is how it "fixes itself").
 * The owner can also run the backup again by hand while the last night is failed.
 *
 * Only two things are stored, in a small private file on this phone (not backed up, never sent anywhere, like the
 * server power and the drives): the day the room was first opened, and the hand-made reruns.
 * Nothing here touches the ledger, the cloud sync or Supabase, and its console lines stay in the phone's memory.
 */
object RoomBackup {
    private const val FILE_NAME = "storage_room_backup.txt"

    /** The nightly backup starts at 2:00 AM. */
    private val RUN_TIME: LocalTime = LocalTime.of(2, 0)

    /** The first day of the story. Nights are worked out in a chain from here, so "never two in a row" always holds. */
    private val ANCHOR: Long = LocalDate.of(2026, 1, 1).toEpochDay()

    /** A hand-made rerun lasts this long. */
    private const val RETRY_SECONDS = 15L

    /** About 1 night in 10 fails (11 in 100 before the "never two in a row" rule). */
    private const val FAIL_PER_100 = 11

    /** Starlink weak signal: about 1 or 2 days a month. */
    private const val ROUTER_PER_100 = 5

    /** Main server fault: about 1 day in 60 (once every two months). */
    private const val FAULT_ONE_IN = 60

    private const val DRIVE_DUE_DAYS = 5

    private val REASONS = listOf(
        "Starlink dropped during the copy",
        "UPS battery test paused the backup",
        "backup server was busy with another job"
    )

    private val WEAK_REASONS = listOf("heavy rain", "thick cloud", "a dish self-check")

    /** Changes on every action, so screens that watch it redraw at once. */
    var changes by mutableIntStateOf(0)
        private set

    private var loaded = false
    private var firstSeenMillis = 0L

    /** night epoch day -> (start millis, duration millis) of the hand-made rerun. */
    private val retries = LinkedHashMap<Long, LongArray>()
    private val failCache = HashMap<Long, Boolean>()

    /** Console lines already written during this run of the app, so each is written once. */
    private val logged = HashSet<String>()

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    @Synchronized
    private fun load(context: Context, now: Long) {
        if (loaded) return
        loaded = true
        try {
            val f = file(context)
            if (f.exists()) {
                for (line in f.readLines()) {
                    val p = line.split("|")
                    when (p.getOrNull(0)) {
                        "F" -> firstSeenMillis = p[1].toLong()
                        "R" -> retries[p[1].toLong()] = longArrayOf(p[2].toLong(), p[3].toLong())
                    }
                }
            }
        } catch (e: Exception) {
            // A damaged file is ignored: the story starts fresh (the nights themselves come from the date).
            retries.clear()
        }
        if (firstSeenMillis == 0L) {
            firstSeenMillis = now
            save(context)
        }
    }

    private fun save(context: Context) {
        try {
            val sb = StringBuilder()
            sb.append("F|").append(firstSeenMillis).append('\n')
            for ((day, r) in retries) {
                sb.append("R|").append(day).append('|').append(r[0]).append('|').append(r[1]).append('\n')
            }
            file(context).writeText(sb.toString())
        } catch (e: Exception) {
            // The state still works for this run of the app.
        }
    }

    // ---- The story, worked out from the date ----

    private fun mix(input: Long): Long {
        var z = input * -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    /** A steady "random" number from 0 until [bound] for one day and one purpose. */
    private fun roll(day: Long, salt: Int, bound: Int): Int =
        ((mix(day * 1_000_003L + salt) ushr 1) % bound.toLong()).toInt()

    @Synchronized
    private fun nightFailed(epochDay: Long): Boolean {
        if (epochDay < ANCHOR) return false
        var previous = false
        for (e in ANCHOR..epochDay) {
            val cached = failCache[e]
            previous = if (cached != null) {
                cached
            } else {
                val value = !previous && roll(e, 1, 100) < FAIL_PER_100
                failCache[e] = value
                value
            }
        }
        return previous
    }

    private fun reasonFor(epochDay: Long): String = REASONS[roll(epochDay, 2, REASONS.size)]

    /** When the night's backup of that day finishes: 20 to 50 minutes after 2:00 AM. */
    private fun finishMillis(day: LocalDate, zone: ZoneId): Long =
        day.atTime(RUN_TIME).atZone(zone).toInstant().toEpochMilli() +
            (20L + roll(day.toEpochDay(), 3, 31)) * 60_000L

    private fun startMillis(day: LocalDate, zone: ZoneId): Long =
        day.atTime(RUN_TIME).atZone(zone).toInstant().toEpochMilli()

    /** The newest night whose backup has finished by [now]. */
    private fun lastFinishedNight(now: Long, zone: ZoneId): LocalDate {
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        var day = today
        while (finishMillis(day, zone) > now) day = day.minusDays(1)
        return day
    }

    /** A short Starlink weak-signal event of one day: (start, end) in millis, or null. */
    private fun routerEvent(day: LocalDate, zone: ZoneId): Triple<Long, Long, String>? {
        val e = day.toEpochDay()
        if (roll(e, 11, 100) >= ROUTER_PER_100) return null
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli() + roll(e, 12, 1440 - 40) * 60_000L
        val length = (10L + roll(e, 13, 21)) * 60_000L
        return Triple(start, start + length, WEAK_REASONS[roll(e, 14, WEAK_REASONS.size)])
    }

    /** A short main server fault of one day: (start, end) in millis, or null. */
    private fun faultEvent(day: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        val e = day.toEpochDay()
        if (roll(e, 21, FAULT_ONE_IN) != 0) return null
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli() + roll(e, 22, 1440 - 15) * 60_000L
        val length = (3L + roll(e, 23, 7)) * 60_000L
        return start to start + length
    }

    private fun timeText(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Writes one console line with its real time, once per run of the app, and only for the last 24 hours. */
    private fun logOnce(now: Long, key: String, at: Long, level: RoomLevel, source: String, text: String) {
        if (at > now || at < now - 24L * 60L * 60_000L) return
        if (!logged.add(key)) return
        StorageRoomLog.addAt(at, level, source, text)
    }

    /** The whole backup story at this moment. Console lines for what happened in the last 24 hours are written here. */
    @Synchronized
    fun view(
        context: Context,
        liveKb: Long,
        lastDriveMillis: Long,
        now: Long = System.currentTimeMillis()
    ): BackupView {
        load(context, now)
        val zone = ZoneId.systemDefault()
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        val lastNight = lastFinishedNight(now, zone)

        // The last 7 nights, newest first, with any hand-made rerun that fixed one.
        val nights = ArrayList<BackupNight>()
        for (i in 0..6) {
            val day = lastNight.minusDays(i.toLong())
            val e = day.toEpochDay()
            val failed = nightFailed(e)
            val retry = retries[e]
            val doneAt = if (retry != null && retry[0] + retry[1] <= now) retry[0] + retry[1] else 0L
            nights.add(BackupNight(day, !failed, if (failed) reasonFor(e) else "", if (failed) doneAt else 0L))
        }

        val last = nights[0]
        val lastRetry = retries[last.day.toEpochDay()]
        val retryRunning = !last.ok && lastRetry != null && lastRetry[0] + lastRetry[1] > now
        val lastFailed = !last.ok && last.fixedAtMillis == 0L
        val progress = if (retryRunning && lastRetry != null) {
            ((now - lastRetry[0]).toFloat() / lastRetry[1].toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val left = if (retryRunning && lastRetry != null) ((lastRetry[0] + lastRetry[1] - now) / 1000L) + 1L else 0L

        // Console lines for the last day or so.
        for (night in nights.take(2)) {
            val e = night.day.toEpochDay()
            val start = startMillis(night.day, zone)
            val end = finishMillis(night.day, zone)
            logOnce(now, "n-start-$e", start, RoomLevel.INFO, "backup", "nightly backup started")
            if (night.ok) {
                logOnce(now, "n-ok-$e", end, RoomLevel.SYNC, "backup", "nightly backup finished: " + StorageRules.text(liveKb))
                if (nightFailed(e - 1)) {
                    logOnce(now, "n-again-$e", end + 1000L, RoomLevel.INFO, "backup", "backup OK again")
                }
            } else {
                logOnce(now, "n-fail-$e", end, RoomLevel.ERROR, "backup", "nightly backup failed: " + night.reason)
                val retry = retries[e]
                if (retry != null) {
                    logOnce(now, "n-rs-$e", retry[0], RoomLevel.CHANGE, "backup", "backup run again by hand")
                    logOnce(
                        now, "n-rd-$e", retry[0] + retry[1], RoomLevel.INFO, "backup",
                        "backup finished: " + StorageRules.text(liveKb)
                    )
                }
            }
        }

        var routerWeak = false
        var failover = false
        for (offset in 0..1) {
            val day = today.minusDays(offset.toLong())
            val router = routerEvent(day, zone)
            if (router != null) {
                val e = day.toEpochDay()
                logOnce(now, "r-s-$e", router.first, RoomLevel.WARN, "router", "Starlink signal weak (" + router.third + ")")
                logOnce(now, "r-e-$e", router.second, RoomLevel.INFO, "router", "Starlink signal back to normal")
                if (now >= router.first && now < router.second) routerWeak = true
            }
            val fault = faultEvent(day, zone)
            if (fault != null) {
                val e = day.toEpochDay()
                logOnce(now, "f-s-$e", fault.first, RoomLevel.ERROR, "server", "main server fault: backup server took over")
                logOnce(now, "f-e-$e", fault.second, RoomLevel.INFO, "server", "main server is back, backup server is standby again")
                if (now >= fault.first && now < fault.second) failover = true
            }
        }

        // No off-site drive copy for 5 days: only copying a drive clears it.
        val base = maxOf(lastDriveMillis, firstSeenMillis)
        val daysSince = ((now - base) / (24L * 60L * 60_000L)).toInt()
        val driveDue = if (daysSince >= DRIVE_DUE_DAYS) daysSince else 0
        if (driveDue > 0) {
            logOnce(
                now, "drive-due-" + base, now, RoomLevel.WARN, "offsite",
                "no off-site drive copy for $driveDue days"
            )
        }

        return BackupView(
            nights = nights,
            lastFailed = lastFailed,
            lastReason = if (lastFailed) last.reason else "",
            retryRunning = retryRunning,
            retryProgress = progress,
            retryLeftSeconds = left,
            routerWeak = routerWeak,
            failover = failover,
            driveDueDays = driveDue
        )
    }

    /** "Run backup now": runs the failed night again by hand. It takes a few seconds and then the night is fixed. */
    @Synchronized
    fun retry(context: Context, powerOn: Boolean, now: Long = System.currentTimeMillis()): String? {
        load(context, now)
        if (!powerOn) return "The server is off. Switch it on first."
        val zone = ZoneId.systemDefault()
        val night = lastFinishedNight(now, zone).toEpochDay()
        if (!nightFailed(night)) return "The last backup did not fail. Nothing to run again."
        val existing = retries[night]
        if (existing != null) {
            return if (existing[0] + existing[1] > now) "The backup is already running." else "The last backup was already fixed."
        }
        retries[night] = longArrayOf(now, RETRY_SECONDS * 1000L)
        while (retries.size > 30) retries.remove(retries.keys.first())
        save(context)
        changes += 1
        return null
    }

    /** "fixed 9:14 AM" for the night history. */
    fun fixedText(millis: Long): String = timeText(millis, ZoneId.systemDefault())
}

/**
 * The live backup story, redrawn every second or two (twice a second while a rerun is going). Because it is worked
 * out from the date and the clock, it is right the moment the app is opened again.
 */
@Composable
fun rememberRoomBackup(live: State<Long>, drive: State<DriveView>): State<BackupView> {
    val context = LocalContext.current.applicationContext
    fun lastDrive(): Long = maxOf(drive.value.a.lastMillis, drive.value.b.lastMillis)
    val state = remember { mutableStateOf(RoomBackup.view(context, live.value, lastDrive())) }
    LaunchedEffect(RoomBackup.changes) {
        while (true) {
            val v = RoomBackup.view(context, live.value, lastDrive())
            if (v != state.value) state.value = v
            delay(if (v.retryRunning) 500L else 1500L)
        }
    }
    return state
}
