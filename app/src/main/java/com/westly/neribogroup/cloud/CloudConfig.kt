package com.westly.neribogroup.cloud

import android.content.Context
import android.util.Base64
import org.json.JSONObject
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

    /**
     * The signed-in user's id (the "sub" of the sign-in token, a lowercase UUID). It is the first folder of every
     * cloud media path. Saved when a session is saved; for a phone that signed in before this was added it is
     * read from the saved token the first time. Kept after sign-out (waiting deletes need it). Empty if unknown.
     */
    val ownerUid: String
        get() {
            val saved = prefs.getString("ownerUid", "") ?: ""
            if (saved.isNotEmpty()) return saved
            val fromToken = uidFromToken(accessToken)
            if (fromToken.isNotEmpty()) prefs.edit().putString("ownerUid", fromToken).apply()
            return fromToken
        }

    var cloudMode: CloudMode
        get() = runCatching { CloudMode.valueOf(prefs.getString("cloudMode", "OFF") ?: "OFF") }
            .getOrDefault(CloudMode.OFF)
        set(value) {
            prefs.edit().putString("cloudMode", value.name).commit()
        }

    /** Phase 4B: when true, Claude rolls the Google Play dice. Off until the owner turns it on. */
    var aiMode: Boolean
        get() = prefs.getBoolean("aiMode", false)
        set(value) {
            prefs.edit().putBoolean("aiMode", value).commit()
        }

    fun saveSession(access: String, refresh: String, expiresAtMillis: Long) {
        prefs.edit()
            .putString("accessToken", access)
            .putString("refreshToken", refresh)
            .putLong("expiresAtMillis", expiresAtMillis)
            .putBoolean("signedIn", true)
            .commit()
        val uid = uidFromToken(access)
        if (uid.isNotEmpty()) prefs.edit().putString("ownerUid", uid).commit()
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
        private val UID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        /** Reads the "sub" claim of a JWT (no signature check: the server does that). Empty when it is not a UUID. */
        fun uidFromToken(token: String): String {
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return ""
                val payload = String(
                    Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                    Charsets.UTF_8
                )
                val sub = JSONObject(payload).optString("sub", "").trim().lowercase()
                if (UID_REGEX.matches(sub)) sub else ""
            } catch (e: Exception) {
                ""
            }
        }

        /** Trims, removes the trailing "/" and requires https://. Returns null when it does not look right. */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (!trimmed.startsWith("https://")) return null
            if (trimmed.length <= "https://".length) return null
            return trimmed
        }
    }
}
