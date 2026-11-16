package com.westly.opay.backend

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/** Keeps the Auth plugin's session in [SecureStore]. The session is never written to DataStore or logs. */
class SecureSessionManager(private val store: SecureStore) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        store.putString(SecureKeys.SESSION_JSON, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession? {
        val raw = store.getString(SecureKeys.SESSION_JSON) ?: return null
        return try {
            json.decodeFromString(UserSession.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            store.remove(SecureKeys.SESSION_JSON)
            null
        }
    }

    override suspend fun deleteSession() {
        store.remove(SecureKeys.SESSION_JSON)
    }
}
