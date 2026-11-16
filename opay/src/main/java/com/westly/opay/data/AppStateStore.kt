package com.westly.opay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Version 2 keeps only local settings; version 1 also held sample balance, transactions and beneficiaries.
 * Version 3 adds the cache of the last known account values. The nickname is no longer kept here: it lives in the
 * profile on the server. A blob that still holds one is read as before and the nickname is ignored.
 */
const val STATE_VERSION = 3

@Serializable
data class PersistedState(
    val version: Int = STATE_VERSION,
    val hideBalance: Boolean = false,
    val themeMode: String = "Dark",
    val cache: CachedAccount? = null,
    val appSettings: JsonObject = JsonObject(emptyMap())
)

/** Where the saved state lives. A fake of this is used in tests. */
interface StateStore {
    fun loadBlocking(): PersistedState?
    suspend fun save(state: PersistedState)
}

internal val stateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Brings an older blob up to [STATE_VERSION], or returns null when the blob is newer than this app understands.
 * Version 1 loses its sample data. Version 2 keeps everything and starts with an empty cache.
 */
internal fun migrateState(decoded: PersistedState): PersistedState? = when {
    decoded.version == STATE_VERSION -> decoded
    decoded.version == 2 -> decoded.copy(version = STATE_VERSION, cache = null)
    decoded.version < 2 -> decoded.copy(version = STATE_VERSION, cache = null)
    else -> null
}

private val Context.opayDataStore: DataStore<Preferences> by preferencesDataStore(name = "opay_state")

class AppStateStore(context: Context) : StateStore {
    private val dataStore: DataStore<Preferences> = context.applicationContext.opayDataStore
    private val key = stringPreferencesKey("state_json")

    /** Reads the saved state. An older blob is migrated here and the migrated blob is written back at once. */
    override fun loadBlocking(): PersistedState? {
        return try {
            val raw = runBlocking { dataStore.data.first()[key] } ?: return null
            val decoded = stateJson.decodeFromString(PersistedState.serializer(), raw)
            val migrated = migrateState(decoded) ?: return null
            if (migrated.version != decoded.version) runBlocking { save(migrated) }
            migrated
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun save(state: PersistedState) {
        try {
            val raw = stateJson.encodeToString(PersistedState.serializer(), state)
            dataStore.edit { it[key] = raw }
        } catch (e: Exception) {
            return
        }
    }
}
