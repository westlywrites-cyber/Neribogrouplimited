package com.westly.neribogroup.notify

import android.content.Context
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.SALARY_KEY
import com.westly.neribogroup.data.dayStartMillis
import com.westly.neribogroup.data.daysBetweenMillis
import com.westly.neribogroup.feature.storageroom.RoomBackup
import com.westly.neribogroup.feature.storageroom.RoomPower
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One reminder. [key] is never the same for two different reminders and never changes for the same one, so the
 * phone can remember it was sent and not send it twice. [server] reminders use the louder "Server" channel.
 */
data class Reminder(
    val key: String,
    val title: String,
    val text: String,
    val server: Boolean = false
)

/**
 * What the owner wants to be told about:
 * - a salary or allowance payment: 3 days before and on the day,
 * - a renewal: 3 days before and on the day,
 * - an app that has used 80% and then 100% of its storage,
 * - a server that is down.
 * These functions only work out the list; [ReminderNotifier] shows it. Nothing here changes any data.
 */
object ReminderRules {
    /** Reminders start this many days before the date. */
    private const val DAYS_BEFORE = 3L

    /** A reminder for a date that passed more than this many days ago is no longer sent (the phone may have been off). */
    private const val DAYS_LATE = 3L

    private fun timeText(millis: Long): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /**
     * The words for a date that is [left] days away: "in 3 days", "today" or "overdue". Returns null when it is
     * not time to remind yet, or it was too long ago. [stage] is "pre" before the date and "due" from the date on.
     */
    private fun stageFor(left: Long): String? = when {
        left in 1L..DAYS_BEFORE -> "pre"
        left <= 0L && left >= -DAYS_LATE -> "due"
        else -> null
    }

    private fun whenText(left: Long): String = when {
        left == 0L -> "today"
        left == 1L -> "tomorrow"
        left > 1L -> "in $left days"
        left == -1L -> "1 day ago"
        else -> (-left).toString() + " days ago"
    }

    /** Payments, renewals and storage: everything that comes from the saved ledger. */
    fun forLedger(ledger: Ledger, now: Long): List<Reminder> {
        val list = ArrayList<Reminder>()

        // Salary and allowances that have a date and are not paid yet.
        for (person in ledger.staff) {
            for (item in person.payItems()) {
                if (item.amount <= 0L) continue
                val due = person.dueMillis(item.key)
                if (due <= 0L) continue
                val left = daysBetweenMillis(now, due)
                val stage = stageFor(left) ?: continue
                val what = if (item.key == SALARY_KEY) "Salary" else item.name
                val title = if (left > 0L) "$what due " + whenText(left) else if (left == 0L) "$what due today" else "$what is overdue"
                list.add(
                    Reminder(
                        key = "pay:" + person.id + ":" + item.key + ":" + dayStartMillis(due) + ":" + stage,
                        title = title,
                        text = person.name + ": " + formatNaira(item.amount) + " " + what.lowercase(Locale.ENGLISH) +
                            " is due on " + formatDate(due) + " (" + whenText(left) + ")."
                    )
                )
            }
        }

        // Renewals: domains, hosting, subscriptions.
        for (renewal in ledger.renewals) {
            val left = renewal.daysLeft(now)
            val stage = stageFor(left) ?: continue
            list.add(
                Reminder(
                    key = "renewal:" + renewal.id + ":" + dayStartMillis(renewal.expiryMillis) + ":" + stage,
                    title = if (left > 0L) renewal.name + " runs out " + whenText(left)
                    else if (left == 0L) renewal.name + " runs out today" else renewal.name + " has run out",
                    text = renewal.name + " " + (if (left >= 0L) "runs out" else "ran out") + " on " +
                        formatDate(renewal.expiryMillis) + ". Renew it so it does not stop."
                )
            )
        }

        // Storage: an app that has used 80% of its share, then 100%.
        for (app in ledger.apps) {
            if (app.storageShareMb <= 0L) continue
            val shareKb = app.storageShareMb * 1024L
            val usedKb = app.storageAllocatedKb
            val level = when {
                usedKb * 100L >= shareKb * 100L -> 100
                usedKb * 100L >= shareKb * 80L -> 80
                else -> 0
            }
            if (level == 0) continue
            val percent = (usedKb * 100L / shareKb).coerceAtLeast(level.toLong())
            list.add(
                Reminder(
                    // The share is in the key, so raising the share starts the alerts again.
                    key = "storage:" + app.id + ":" + app.storageShareMb + ":" + level,
                    title = if (level == 100) app.name + " storage is full" else app.name + " storage is " + level + "% used",
                    text = app.name + " has used " + percent + "% of its storage share." +
                        if (level == 100) " Raise its share or lower what its users get, or users might lose data." else ""
                )
            )
        }
        return list
    }

    /** Servers: a main server fault in the last day, or the server switched off for a while. */
    fun forServer(context: Context, now: Long): List<Reminder> {
        val list = ArrayList<Reminder>()

        for (fault in RoomBackup.faultsAround(now, pastHours = 24L, futureHours = 0L)) {
            val back = fault.second <= now
            list.add(
                Reminder(
                    key = "server-fault:" + fault.first,
                    title = "Main server is down",
                    text = "The main server had a fault at " + timeText(fault.first) +
                        ". The backup server took over." +
                        if (back) " The main server was back at " + timeText(fault.second) + "." else "",
                    server = true
                )
            )
        }

        val offSince = RoomPower.offSinceMillis(context, now)
        if (offSince > 0L && now - offSince >= OFF_MINUTES * 60_000L) {
            list.add(
                Reminder(
                    key = "server-off:" + offSince,
                    title = "Server is off",
                    text = "The storage server has been off since " + timeText(offSince) +
                        ". The apps are offline until you switch it on.",
                    server = true
                )
            )
        }
        return list
    }

    /** A server that is switched off is only reported after it has been off this long. */
    private const val OFF_MINUTES = 10L
}
