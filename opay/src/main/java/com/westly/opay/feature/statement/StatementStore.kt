package com.westly.opay.feature.statement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.statementDataStore: DataStore<Preferences> by preferencesDataStore(name = "opay_statements")

/** How the records are written and read. A value of an unknown status is read as the default status (Failed). */
internal val StatementJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

/** Keeps the download records of each signed-in user (DataStore) and their PDF files (private storage). */
class StatementStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.statementDataStore
    private val serializer = ListSerializer(StatementRecord.serializer())

    private fun key(userId: String) = stringPreferencesKey("records_$userId")

    /** The folder of one user's PDF files. */
    fun userDir(userId: String): File = File(File(appContext.filesDir, "statements"), userId)

    suspend fun load(userId: String): List<StatementRecord> = try {
        val raw = dataStore.data.first()[key(userId)]
        if (raw == null) emptyList() else StatementJson.decodeFromString(serializer, raw)
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun save(userId: String, records: List<StatementRecord>) {
        try {
            val raw = StatementJson.encodeToString(serializer, records)
            dataStore.edit { it[key(userId)] = raw }
        } catch (e: Exception) {
            return
        }
    }

    /** Removes the records and every file of one user. */
    suspend fun deleteUser(userId: String) {
        try {
            dataStore.edit { it.remove(key(userId)) }
        } catch (e: Exception) {
            // The files below are removed either way.
        }
        userDir(userId).deleteRecursively()
    }
}
