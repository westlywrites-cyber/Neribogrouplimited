package com.westly.neribogroup.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.westly.neribogroup.MainActivity
import com.westly.neribogroup.R
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.feature.storageroom.RoomBackup

/**
 * Works out which reminders are due and shows the ones this phone has not shown yet.
 * It reads the saved ledger without changing it. Every shown reminder is remembered (by its key) for 120 days,
 * so each one appears once.
 */
object ReminderNotifier {
    private const val CHANNEL_REMINDERS = "neribo_reminders"
    private const val CHANNEL_SERVER = "neribo_server"
    private const val PREFS = "neribo_reminders"
    private const val SENT = "sent"
    private const val KEEP_MILLIS = 120L * 86_400_000L

    /** Checks everything now. Safe to call from any thread, and it never throws. */
    @Synchronized
    fun run(context: Context) {
        try {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            val reminders = ArrayList<Reminder>()
            val ledger = LedgerStorage.peek(app)
            if (ledger != null) reminders.addAll(ReminderRules.forLedger(ledger, now))
            reminders.addAll(ReminderRules.forServer(app, now))
            if (reminders.isNotEmpty()) deliver(app, reminders, now)
        } catch (e: Exception) {
            Log.w("ReminderNotifier", "Reminder check failed: " + e.message)
        }
    }

    /** True when this phone lets the app show notifications. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    private fun deliver(context: Context, reminders: List<Reminder>, now: Long) {
        // Without permission nothing is marked as shown, so the reminders appear once the owner allows them.
        if (!canNotify(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // The remembered reminders are saved as "key@time". Old ones are dropped.
        val sent = HashMap<String, Long>()
        val saved = prefs.getStringSet(SENT, null)
        if (saved != null) {
            for (entry in saved) {
                val at = entry.substringAfterLast('@', "").toLongOrNull() ?: continue
                if (now - at < KEEP_MILLIS) sent[entry.substringBeforeLast('@')] = at
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        makeChannels(manager)
        var changed = false
        for (reminder in reminders) {
            if (sent.containsKey(reminder.key)) continue
            post(context, manager, reminder)
            sent[reminder.key] = now
            changed = true
        }
        if (changed) {
            val out = HashSet<String>()
            for ((key, at) in sent) out.add("$key@$at")
            prefs.edit().putStringSet(SENT, out).apply()
        }
    }

    private fun makeChannels(manager: NotificationManager) {
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS, "Payments, renewals and storage", NotificationManager.IMPORTANCE_DEFAULT
        )
        reminders.description = "Salary and allowance dates, renewals, and apps that are running out of storage"
        manager.createNotificationChannel(reminders)

        val server = NotificationChannel(
            CHANNEL_SERVER, "Server alerts", NotificationManager.IMPORTANCE_HIGH
        )
        server.description = "When a server is down or switched off"
        manager.createNotificationChannel(server)
    }

    private fun post(context: Context, manager: NotificationManager, reminder: Reminder) {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, reminder.key.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = Notification.Builder(context, if (reminder.server) CHANNEL_SERVER else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_neribo)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(Notification.BigTextStyle().bigText(reminder.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
        manager.notify(reminder.key.hashCode(), builder.build())
    }
}

/** Sets the alarms that wake the app to check for reminders. */
object ReminderScheduler {
    private const val REQ_REPEAT = 4101
    private const val REQ_FAULT = 4102

    private fun receiverIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Checks every hour (Android may move a check a little), and sets the alarm for the next server fault. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 10L * 60_000L,
            AlarmManager.INTERVAL_HOUR,
            receiverIntent(app, REQ_REPEAT)
        )
        scheduleNextFault(app)
    }

    /**
     * The server faults of the story are worked out from the date, so the next one in the coming 2 days is known.
     * One alarm is set for it, so the alert comes about when the fault starts and not up to an hour later.
     */
    fun scheduleNextFault(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val next = RoomBackup.faultsAround(now, 0L, 48L).firstOrNull { it.first > now } ?: return
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.first + 30_000L,
            receiverIntent(app, REQ_FAULT)
        )
    }
}

/** Runs when an alarm goes off: checks the reminders, then sets the next fault alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                ReminderNotifier.run(app)
                ReminderScheduler.scheduleNextFault(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Sets the alarms again after the phone restarts or the app is updated (Android clears them). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderScheduler.schedule(context)
    }
}
