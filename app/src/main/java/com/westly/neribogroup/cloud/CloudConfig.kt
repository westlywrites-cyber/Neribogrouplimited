package com.westly.neribogroup.cloud

import android.content.Context
import java.util.UUID

/** OFF = never connected or signed out (no network calls). LINKING = first upload not finished. ON = linked. */
enum class CloudMode { OFF, LINKING, ON }

/**
 * The cloud settings kept on the phone (SharedPreferences "neribo_cloud").
 * Holds the Project URL, the anon (public) key and the session tokens. The password is NEVER stored.
 */
class CloudConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("neribo_cloud", Context.MODE_PRIVATE)

    var projectUrl: String
        get() = prefs.getString("projectUrl", "") ?: ""
        set(value) {
            prefs.edit().putString("projectUrl", normalizeUrl(value) ?: "").commit()
        }

    var anonKey: String
        get() = prefs.getString("anonKey", "") ?: ""
        set(value) {
            prefs.edit().putString("anonKey", value.trim()).commit()
        }

    /** The last email used, only for convenience. */
    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(value) {
            prefs.edit().putString("email", value.trim()).commit()
        }

    /** Generated once and never changes. */
    val deviceId: String
        get() = synchronized(prefs) {
            val saved = prefs.getString("deviceId", null)
            if (!saved.isNullOrBlank()) {
                saved
            } else {
                val fresh = UUID.randomUUID().toString()
                prefs.edit().putString("deviceId", fresh).commit()
                fresh
            }
        }

    /** The device id if one was already made; empty otherwise (does not create one). */
    val deviceIdIfAny: String
        get() = prefs.getString("deviceId", "") ?: ""

    val accessToken: String
        get() = prefs.getString("accessToken", "") ?: ""

    val refreshToken: String
        get() = prefs.getString("refreshToken", "") ?: ""

    val expiresAtMillis: Long
        get() = prefs.getLong("expiresAtMillis", 0L)

    val signedIn: Boolean
        get() = prefs.getBoolean("signedIn", false)

    var cloudMode: CloudMode
        get() = runCatching { CloudMode.valueOf(prefs.getString("cloudMode", "OFF") ?: "OFF") }
            .getOrDefault(CloudMode.OFF)
        set(value) {
            prefs.edit().putString("cloudMode", value.name).commit()
        }

    fun saveSession(access: String, refresh: String, expiresAtMillis: Long) {
        prefs.edit()
            .putString("accessToken", access)
            .putString("refreshToken", refresh)
            .putLong("expiresAtMillis", expiresAtMillis)
            .putBoolean("signedIn", true)
            .commit()
    }

    /** Forgets the tokens. Never touches the ledger. */
    fun clearSession() {
        prefs.edit()
            .remove("accessToken")
            .remove("refreshToken")
            .remove("expiresAtMillis")
            .putBoolean("signedIn", false)
            .commit()
    }

    companion object {
        /** Trims, removes the trailing "/" and requires https://. Returns null when it does not look right. */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (!trimmed.startsWith("https://")) return null
            if (trimmed.length <= "https://".length) return null
            return trimmed
        }
    }
}
