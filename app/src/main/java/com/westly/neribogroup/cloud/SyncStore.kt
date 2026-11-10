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
    val conflicts: List<String> = emptyList(),
    /** Phase 4B: the day (yyyy-MM-dd) the dice jobs of each app were last published. Optional. */
    val publishedDays: Map<String, String> = emptyMap(),
    /** Phase 4B: jobs that were applied on the phone but not yet marked applied in the cloud. Optional. */
    val appliedPendingMark: List<String> = emptyList(),
    /** Phase 4B: per app, how its last AI-mode roll ended ("day|millis|C or B|jobs"). Optional. */
    val aiDone: Map<String, String> = emptyMap(),
    /** Phase 5: the WRITE_CASE jobs (JSON text) decided on this phone and not yet turned into a case. Optional. */
    val caseJobs: List<String> = emptyList(),
    /** Software roll: how many SOFTWARE_ROLL requests were made per day (yyyy-MM-dd to 1..3). The last 5 days. Optional. */
    val softwareAttempts: Map<String, Int> = emptyMap(),
    /** Socials roll: how many SOCIAL_ROLL requests were made for each day (yyyy-MM-dd to 1..3). The last 40 days. Optional. */
    val socialAttempts: Map<String, Int> = emptyMap()
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
        if (data.publishedDays.isNotEmpty()) {
            val o = JSONObject()
            for ((k, v) in data.publishedDays) o.put(k, v)
            root.put("publishedDays", o)
        }
        if (data.appliedPendingMark.isNotEmpty()) {
            val a = JSONArray()
            for (id in data.appliedPendingMark) a.put(id)
            root.put("appliedPendingMark", a)
        }
        if (data.aiDone.isNotEmpty()) {
            val o = JSONObject()
            for ((k, v) in data.aiDone) o.put(k, v)
            root.put("aiDone", o)
        }
        if (data.caseJobs.isNotEmpty()) {
            val a = JSONArray()
            for (j in data.caseJobs) a.put(j)
            root.put("caseJobs", a)
        }
        if (data.softwareAttempts.isNotEmpty()) {
            val o = JSONObject()
            for ((k, v) in data.softwareAttempts) o.put(k, v)
            root.put("softwareAttempts", o)
        }
        if (data.socialAttempts.isNotEmpty()) {
            val o = JSONObject()
            for ((k, v) in data.socialAttempts) o.put(k, v)
            root.put("socialAttempts", o)
        }
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
        val published = LinkedHashMap<String, String>()
        val publishedObject = root.optJSONObject("publishedDays")
        if (publishedObject != null) {
            val keys = publishedObject.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                published[k] = publishedObject.optString(k, "")
            }
        }
        val marks = ArrayList<String>()
        val marksArray = root.optJSONArray("appliedPendingMark")
        if (marksArray != null) {
            for (i in 0 until marksArray.length()) {
                val id = marksArray.optString(i, "")
                if (id.isNotEmpty()) marks.add(id)
            }
        }
        val done = LinkedHashMap<String, String>()
        val doneObject = root.optJSONObject("aiDone")
        if (doneObject != null) {
            val keys = doneObject.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                done[k] = doneObject.optString(k, "")
            }
        }
        val caseJobs = ArrayList<String>()
        val caseArray = root.optJSONArray("caseJobs")
        if (caseArray != null) {
            for (i in 0 until caseArray.length()) {
                val t = caseArray.optString(i, "")
                if (t.isNotEmpty()) caseJobs.add(t)
            }
        }
        val attempts = LinkedHashMap<String, Int>()
        val attemptsObject = root.optJSONObject("softwareAttempts")
        if (attemptsObject != null) {
            for (k in attemptsObject.keys()) attempts[k] = attemptsObject.optInt(k, 0)
        }
        val socialAttempts = LinkedHashMap<String, Int>()
        val socialAttemptsObject = root.optJSONObject("socialAttempts")
        if (socialAttemptsObject != null) {
            for (k in socialAttemptsObject.keys()) socialAttempts[k] = socialAttemptsObject.optInt(k, 0)
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
            conflicts = conflicts,
            publishedDays = published,
            appliedPendingMark = marks,
            aiDone = done,
            caseJobs = caseJobs,
            softwareAttempts = attempts,
            socialAttempts = socialAttempts
        )
    }
}
