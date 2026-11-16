package com.westly.opay.backend

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureKeys {
    const val REMEMBERED_PHONE = "remembered_phone"
    const val REMEMBERED_NAME_MASKED = "remembered_name_masked"
    const val DEVICE_TOKEN_LOGIN = "device_token_login"
    const val DEVICE_TOKEN_PAY = "device_token_pay"
    const val DEVICE_ID = "device_id"
    const val SESSION_JSON = "session_json"
    const val LAST_ACTIVE_AT = "last_active_at"
}

/** Encrypted key-value store (AES256-GCM master key). Opened on first use, never at app start. */
class SecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { openOrReset() }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun openOrReset(): SharedPreferences = try {
        open()
    } catch (e: Exception) {
        // The key can be lost (for example after a restore); the stored values are unreadable then.
        appContext.deleteSharedPreferences(FILE_NAME)
        open()
    }

    private fun open(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val FILE_NAME = "opay_secure_store"
    }
}
