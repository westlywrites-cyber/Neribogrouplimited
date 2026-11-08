package com.westly.neribogroup.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** The cloud version this phone last fully agreed on for one document, and the hash of that text. */
data class DocState(val version: Int, val sha256: String, val deleted: Boolean = false)

/** One document in the outbox batch. */
data class PendingChange(
    val docKey: String,
    val baseVersion: Int,
    val payload: String,
    val payloadSha256: String,
    val deleted: Boolean
)

/** The batch being sent. It is saved to disk BEFORE it is sent and re-sent unchanged until answered. */
data class PendingBatch(
    val opId: String,
    val mode: String,
    val changes: List<PendingChange>,
    val createdMillis: Long
)

/** Everything in cloud_sync.json. [conflicts] holds the JSON text of each entry (3B defines their shape). */
data class SyncData(
    val schema: Int = 1,
    val deviceId: String,
    val lastSeq: Long = 0L,
    val initialized: Boolean = false,
    val docs: Map<String, DocState> = emptyMap(),
    val pending: PendingBatch? = null,
    val lastSyncMillis: Long = 0L,
    val lastError: String = "",
    val conflicts: List<String> = emptyList()
)

/** Reads and writes cloud_sync.json. Writes are atomic (tmp file, then rename). */
object SyncStore {

    private const val FILE_NAME = "cloud_sync.json"
    private val lock = Any()
    private var cache: SyncData? = null

    fun exists(context: Context): Boolean = File(context.applicationContext.filesDir, FILE_NAME).exists()

    /** Null when the file does not exist (or cannot be read). Does not create anything. */
    fun loadIfExists(context: Context): SyncData? {
        synchronized(lock) {
            cache?.let { return it }
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (!file.exists()) return null
            return try {
                val data = fromJson(file.readText(Charsets.UTF_8))
                cache = data
                data
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * The saved state, or a fresh "not initialized" one. An unreadable file is kept as
     * cloud_sync.corrupt.json (never deleted); the first link then asks the cloud what to do.
     */
    fun load(context: Context, deviceId: String): SyncData {
        synchronized(lock) {
            cache?.let { return it }
            val dir = context.applicationContext.filesDir
            val file = File(dir, FILE_NAME)
            if (file.exists()) {
                try {
                    val data = fromJson(file.readText(Charsets.UTF_8))
                    cache = data
                    return data
                } catch (e: Exception) {
                    runCatching { file.renameTo(File(dir, "cloud_sync.corrupt.json")) }
                }
            }
            val fresh = SyncData(deviceId = deviceId)
            cache = fresh
            return fresh
        }
    }

    /** Returns true when the file was written. The in-memory copy is updated only after the write worked. */
    fun save(context: Context, data: SyncData): Boolean {
        synchronized(lock) {
            return try {
                val dir = context.applicationContext.filesDir
                val file = File(dir, FILE_NAME)
                val tmp = File(dir, "$FILE_NAME.tmp")
                val bytes = toJson(data).toByteArray(Charsets.UTF_8)
                FileOutputStream(tmp).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!tmp.renameTo(file)) {
                    FileOutputStream(file).use { out ->
                        out.write(bytes)
                        out.fd.sync()
                    }
                    tmp.delete()
                }
                cache = data
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun toJson(data: SyncData): String {
        val root = JSONObject()
        root.put("schema", data.schema)
        root.put("deviceId", data.deviceId)
        root.put("lastSeq", data.lastSeq)
        root.put("initialized", data.initialized)
        val docs = JSONObject()
        for ((key, state) in data.docs) {
            val o = JSONObject()
            o.put("version", state.version)
            o.put("sha256", state.sha256)
            if (state.deleted) o.put("deleted", true)
            docs.put(key, o)
        }
        root.put("docs", docs)
        val pending = data.pending
        if (pending == null) {
            root.put("pending", JSONObject.NULL)
        } else {
            val p = JSONObject()
            p.put("opId", pending.opId)
            p.put("mode", pending.mode)
            val changes = JSONArray()
            for (c in pending.changes) {
                val o = JSONObject()
                o.put("doc_key", c.docKey)
                o.put("base_version", c.baseVersion)
                o.put("payload", c.payload)
                o.put("payload_sha256", c.payloadSha256)
                o.put("deleted", c.deleted)
                changes.put(o)
            }
            p.put("changes", changes)
            p.put("createdMillis", pending.createdMillis)
            root.put("pending", p)
        }
        root.put("lastSyncMillis", data.lastSyncMillis)
        root.put("lastError", data.lastError)
        val conflicts = JSONArray()
        for (text in data.conflicts) {
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            if (parsed != null) conflicts.put(parsed) else conflicts.put(text)
        }
        root.put("conflicts", conflicts)
        return root.toString(2)
    }

    /** Throws if the text is not valid JSON. */
    fun fromJson(text: String): SyncData {
        val root = JSONObject(text)
        val docs = LinkedHashMap<String, DocState>()
        val docsObject = root.optJSONObject("docs")
        if (docsObject != null) {
            val names = docsObject.keys()
            while (names.hasNext()) {
                val key = names.next()
                val o = docsObject.optJSONObject(key) ?: continue
                docs[key] = DocState(o.optInt("version", 0), o.optString("sha256", ""), o.optBoolean("deleted", false))
            }
        }
        var pending: PendingBatch? = null
        val p = root.optJSONObject("pending")
        if (p != null) {
            val changes = ArrayList<PendingChange>()
            val array = p.optJSONArray("changes") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                changes.add(
                    PendingChange(
                        docKey = o.optString("doc_key", ""),
                        baseVersion = o.optInt("base_version", 0),
                        payload = o.optString("payload", ""),
                        payloadSha256 = o.optString("payload_sha256", ""),
                        deleted = o.optBoolean("deleted", false)
                    )
                )
            }
            pending = PendingBatch(
                opId = p.optString("opId", ""),
                mode = p.optString("mode", "normal"),
                changes = changes,
                createdMillis = p.optLong("createdMillis", 0L)
            )
        }
        val conflicts = ArrayList<String>()
        val conflictsArray = root.optJSONArray("conflicts")
        if (conflictsArray != null) {
            for (i in 0 until conflictsArray.length()) conflicts.add(conflictsArray.get(i).toString())
        }
        return SyncData(
            schema = root.optInt("schema", 1),
            deviceId = root.optString("deviceId", ""),
            lastSeq = root.optLong("lastSeq", 0L),
            initialized = root.optBoolean("initialized", false),
            docs = docs,
            pending = pending,
            lastSyncMillis = root.optLong("lastSyncMillis", 0L),
            lastError = root.optString("lastError", ""),
            conflicts = conflicts
        )
    }
}
