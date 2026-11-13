package com.westly.neribogroup.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.abs

/** The units the owner can type storage in. */
enum class StorageUnit(val label: String, val mb: Long) {
    MB("MB", 1L),
    GB("GB", 1024L),
    TB("TB", 1024L * 1024L)
}

/**
 * Everything about the company's storage that is worked out and never typed: how sizes are shown, how the totals
 * add up, and the PIN check. Storage is kept in MB (the owner's numbers) and shown in KB, MB, GB, TB or PB.
 */
object StorageRules {

    /** The biggest number the owner can type in one box. */
    const val MAX_TYPED = 999_999L

    /** 1,536 KB -> "1.5 MB". The unit changes by itself as the size grows: KB, MB, GB, TB, PB. */
    fun text(kb: Long): String {
        val value = maxOf(0L, kb)
        return when {
            value < 1024L -> "$value KB"
            value < 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / 1024.0)
            value < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", value / (1024.0 * 1024.0))
            value < 1024L * 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f TB", value / (1024.0 * 1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f PB", value / (1024.0 * 1024.0 * 1024.0 * 1024.0))
        }
    }

    fun textMb(mb: Long): String = text(maxOf(0L, mb) * 1024L)

    /** A typed number in a unit, as MB. */
    fun toMb(value: Long, unit: StorageUnit): Long = value.coerceIn(0L, MAX_TYPED) * unit.mb

    /** The unit that shows [mb] as a whole number when it can (TB, then GB, then MB). */
    fun bestUnit(mb: Long): StorageUnit = when {
        mb > 0L && mb % StorageUnit.TB.mb == 0L -> StorageUnit.TB
        mb > 0L && mb % StorageUnit.GB.mb == 0L -> StorageUnit.GB
        else -> StorageUnit.MB
    }

    /** What every user of every app has been given, in KB (an app with no storage set counts 0). */
    fun usedKb(apps: List<AppData>): Long = apps.sumOf { it.storageAllocatedKb }

    /** What the owner gave to the apps (their shares), in MB. */
    fun sharesMb(apps: List<AppData>): Long = apps.sumOf { it.storageShareMb }

    /** The part of an app's storage that is above its share, in KB. 0 when it fits or no share is set. */
    fun overShareKb(app: AppData): Long =
        if (app.storageShareMb <= 0L) 0L else maxOf(0L, app.storageAllocatedKb - app.storageShareMb * 1024L)

    /** How different the off-site copy is from the live data, in KB. 0 = the same. */
    fun offsiteGapKb(assets: CompanyAssets, liveKb: Long): Long =
        if (assets.offsiteMillis <= 0L) liveKb else abs(liveKb - assets.offsiteKb)

    // ---- Limits: when users hold more storage than the company or an app has ----

    /** Live copy + standby copy + the off-site copy, in KB. The same sum the Storage page shows. */
    fun copiesKb(assets: CompanyAssets, apps: List<AppData>): Long {
        val live = usedKb(apps)
        return live + live + (if (assets.offsiteMillis > 0L) assets.offsiteKb else 0L)
    }

    /** How far all the copies are over the company total, in KB. 0 when they fit or no total is set. */
    fun companyOverKb(assets: CompanyAssets, apps: List<AppData>): Long {
        val totalKb = assets.storageTotalMb * 1024L
        if (totalKb <= 0L) return 0L
        return maxOf(0L, copiesKb(assets, apps) - totalKb)
    }

    /** How far the copies would be over the company total once a new off-site copy is made, in KB. */
    fun offsiteWouldOverKb(assets: CompanyAssets, apps: List<AppData>): Long {
        val totalKb = assets.storageTotalMb * 1024L
        if (totalKb <= 0L) return 0L
        return maxOf(0L, usedKb(apps) * 3L - totalKb)
    }

    fun syncStoppedText(overKb: Long): String =
        "Sync stopped: company storage is over its total by " + text(overKb) +
            ". Raise the company storage on the Storage page. Your data on the phone is safe."

    fun backupStoppedText(overKb: Long): String =
        "Backup stopped: company storage is over its total by " + text(overKb) +
            ". Raise the company storage on the Storage page. Your data on the phone is safe."

    fun offsiteStoppedText(overKb: Long): String =
        "Off-site copy not made: it would put company storage over its total by " + text(overKb) +
            ". Raise the company storage on the Storage page. Your data on the phone is safe."

    /** Show a warning for an app that would fill its share within this many hours at today's pace. */
    const val WARN_WITHIN_HOURS = 48L

    /** A serious warning about one app's storage: [title] is the headline and [text] says what may happen. */
    class AppStorageWarning(val title: String, val text: String)

    /**
     * The warning for an app, or null when there is none. An app with no share set has no limit.
     * - Over its share now: its users might lose data.
     * - Not over yet: with today's new users the share fills in about N hours (only when that is within
     *   [WARN_WITHIN_HOURS]). This is an estimate from today's new users and the average storage per user.
     */
    fun appWarning(app: AppData, now: Long): AppStorageWarning? {
        if (app.storageShareMb <= 0L) return null
        val shareKb = app.storageShareMb * 1024L
        val usedKb = app.storageAllocatedKb
        // Client Hosting has customers instead of users.
        val who = if (app.isHosting) "customers" else "users"
        if (usedKb > shareKb) {
            return AppStorageWarning(
                title = app.name + " might lose " + who + "' data",
                text = "Its " + who + " hold " + text(usedKb - shareKb) + " more than its share of " +
                    textMb(app.storageShareMb) + ". Raise this app's share or lower what its " + who + " get."
            )
        }
        val newToday = if (app.isHosting) app.hostedNewToday(now) else app.newUsersToday(now)
        val users = if (app.isHosting) app.hostedAddedMillis.size.toLong() else app.totalUsers
        if (newToday <= 0L || users <= 0L || usedKb <= 0L) return null
        val perDayKb = usedKb.toDouble() / users.toDouble() * newToday.toDouble()
        if (perDayKb <= 0.0) return null
        val hours = Math.ceil((shareKb - usedKb).toDouble() / perDayKb * 24.0).toLong()
        if (hours > WARN_WITHIN_HOURS) return null
        val shown = maxOf(1L, hours)
        return AppStorageWarning(
            title = app.name + " might lose " + who + "' data in about " + shown + (if (shown == 1L) " hour" else " hours"),
            text = "At today's pace (" + newToday + (if (newToday == 1L) " new " + who.dropLast(1) else " new " + who) +
                ") its share of " + textMb(app.storageShareMb) +
                " will be full in about " + shown + (if (shown == 1L) " hour" else " hours") +
                ". This is an estimate. Raise this app's share to be safe."
        )
    }

    // ---- The PIN that guards storage reductions ----

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun hasPin(assets: CompanyAssets): Boolean = assets.pinHash.isNotEmpty() && assets.pinSalt.isNotEmpty()

    fun pinMatches(assets: CompanyAssets, pin: String): Boolean =
        hasPin(assets) && hashPin(assets.pinSalt, pin) == assets.pinHash
}
