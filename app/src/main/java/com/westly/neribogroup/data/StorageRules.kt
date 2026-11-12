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
