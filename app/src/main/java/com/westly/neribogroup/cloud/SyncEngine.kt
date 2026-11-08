package com.westly.neribogroup.cloud

import android.content.Context
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.withSoftwareApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** What a link step ended with. */
sealed class LinkOutcome {
    /** The phone data is in the cloud and checked (or the session was restored on an already linked phone). */
    object Done : LinkOutcome()
    /** The cloud data was put on the phone; [ledger] is the new live ledger (already saved to ledger.json). */
    data class DoneWithCloudData(val ledger: Ledger) : LinkOutcome()
    /** Case B: the cloud already has data; the owner must choose. */
    object AskUseCloud : LinkOutcome()
    /** Case C: the cloud holds documents of an unfinished upload; the owner must choose. */
    object AskCaseC : LinkOutcome()
    data class Failed(val message: String, val needsSignIn: Boolean = false) : LinkOutcome()
}

/**
 * Phase 3A: only the first-link protocol and its helpers. Every function blocks: call it from a background
 * thread. It never changes ledger.json except in [useCloudData] (after the safety copies exist).
 *
 * The names of the server's functions, parameters and answer fields are all in [Contract] below.
 */
class SyncEngine(
    context: Context,
    private val config: CloudConfig,
    private val api: SupabaseApi
) {

    /** The server function names, parameter names and mode names (NERIBO BACKEND CONTRACT v1 decides). */
    object Contract {
        const val RPC_ENSURE = "ensure_user_setup"
        const val RPC_STATUS = "sync_status"
        const val RPC_PULL = "pull_docs"
        const val RPC_PUSH = "push_docs"
        const val RPC_VERIFY = "verify_docs"
        const val RPC_BACKUP = "create_backup"

        const val P_OP_ID = "p_op_id"
        const val P_DEVICE_ID = "p_device_id"
        const val P_MODE = "p_mode"
        const val P_CHANGES = "p_changes"
        const val P_EXPECT = "p_expect"
        const val P_SINCE_SEQ = "p_since_seq"
        const val P_AFTER_KEY = "p_after_key"
        const val P_BACKUP_KIND = "p_kind"
        const val P_BACKUP_NOTE = "p_note"

        const val MODE_INITIAL = "initial"
        const val MODE_REPLACE_ALL = "replace_all"

        const val ERR_CLOUD_HAS_DATA = "cloud_has_data"
        const val ERR_CHECKSUM = "checksum_mismatch"
    }

    private val app: Context = context.applicationContext

    /** Size of the biggest document of the last split, for the "very large document" warning. */
    @Volatile
    var largestDocBytes: Int = 0
        private set

    private class PulledDoc(val key: String, val version: Int, val text: String, val deleted: Boolean, val sha: String)

    private class PulledCloud(val docs: List<PulledDoc>, val lastSeq: Long, val joinedText: String)

    private class PullResult(val cloud: PulledCloud?, val failed: LinkOutcome.Failed?)

    private class BatchResult(val batch: PendingBatch?, val failed: LinkOutcome.Failed?)

    private class Expect(val key: String, val version: Int, val sha: String)

    /** The cloud data read in case B / C, waiting for the owner's choice. Memory only. */
    private var pulled: PulledCloud? = null

    // ---------------------------------------------------------------------------------------------
    // Public functions
    // ---------------------------------------------------------------------------------------------

    fun loadSync(): SyncData = SyncStore.load(app, config.deviceId)

    /** Forgets the link (initialized, versions, outbox). Never touches ledger.json or the safety copies. */
    fun resetLinkState() {
        pulled = null
        SyncStore.save(app, SyncData(deviceId = config.deviceId))
    }

    /** Signs out (best effort on the server) and switches the cloud off on this phone. Local data stays. */
    fun signOutLocal() {
        api.signOut()
        config.cloudMode = CloudMode.OFF
    }

    /** First-link protocol, steps 1 to 7 of section 7. [ledger] is the phone's current ledger. */
    fun link(ledger: Ledger, onStep: (Int) -> Unit): LinkOutcome {
        return try {
            linkInner(ledger, onStep)
        } catch (e: Throwable) {
            failed("Something went wrong (" + e.javaClass.simpleName + "). Your data on the phone is untouched.")
        }
    }

    /** The owner chose "Use cloud data": safety copy, then replace ledger.json with the cloud ledger. */
    fun useCloudData(onStep: (Int) -> Unit): LinkOutcome {
        return try {
            if (pulled == null) {
                onStep(STEP_DOWNLOAD)
                val result = pullAll(0)
                val problem = result.failed
                if (problem != null) return problem
                pulled = result.cloud
            }
            applyPulled(onStep)
        } catch (e: Throwable) {
            failed("Something went wrong (" + e.javaClass.simpleName + "). Your data on the phone is untouched.")
        }
    }

    /** Case C, after the owner typed REPLACE: push the phone data with mode replace_all. */
    fun replaceCloudWithPhone(ledger: Ledger, onStep: (Int) -> Unit): LinkOutcome {
        return try {
            onStep(STEP_SAFETY)
            val copyProblem = makeSafetyCopy(ledger)
            if (copyProblem != null) return LinkOutcome.Failed(copyProblem)
            val built = buildBatch(ledger, Contract.MODE_REPLACE_ALL)
            val problem = built.failed
            if (problem != null) return problem
            val batch = built.batch ?: return failed("Could not prepare the upload. Nothing was changed.")
            if (!SyncStore.save(app, loadSync().copy(pending = batch, lastError = ""))) {
                return failed("Could not save the sync notes on the phone. Nothing was uploaded.")
            }
            sendBatch(batch, onStep, true)
        } catch (e: Throwable) {
            failed("Something went wrong (" + e.javaClass.simpleName + "). Your data on the phone is untouched.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The protocol
    // ---------------------------------------------------------------------------------------------

    private fun linkInner(ledger: Ledger, onStep: (Int) -> Unit): LinkOutcome {
        pulled = null

        // Step 1: freeze a safety copy
        onStep(STEP_SAFETY)
        val copyProblem = makeSafetyCopy(ledger)
        if (copyProblem != null) return LinkOutcome.Failed(copyProblem)

        // Step 2
        val setup = api.rpc(Contract.RPC_ENSURE, JSONObject())
        if (setup !is ApiResult.Ok) return failFrom(setup)

        // A saved batch is always re-sent first, unchanged (same opId): this makes a lost answer harmless.
        val sync = loadSync()
        val saved = sync.pending
        if (saved != null && saved.changes.isNotEmpty()) {
            if (saved.mode == Contract.MODE_REPLACE_ALL) return LinkOutcome.AskCaseC
            return sendBatch(saved, onStep, false)
        }

        // Step 3
        val status = api.rpc(Contract.RPC_STATUS, JSONObject())
        if (status !is ApiResult.Ok) return failFrom(status)
        val cloudInitialized = status.json.optBoolean("initialized", false)
        val docCount = status.json.optInt("doc_count", 0)

        if (cloudInitialized) {
            // Case B: never upload. Read the cloud first.
            onStep(STEP_DOWNLOAD)
            val result = pullAll(docCount)
            val problem = result.failed
            if (problem != null) return problem
            pulled = result.cloud
            if (isFreshLedger(ledger)) return applyPulled(onStep)
            return LinkOutcome.AskUseCloud
        }
        if (docCount > 0) return LinkOutcome.AskCaseC

        // Step 4: Case A, the cloud is empty. One batch with every document, saved BEFORE it is sent.
        val built = buildBatch(ledger, Contract.MODE_INITIAL)
        val buildProblem = built.failed
        if (buildProblem != null) return buildProblem
        val batch = built.batch ?: return failed("Could not prepare the upload. Nothing was changed.")
        if (!SyncStore.save(app, sync.copy(pending = batch, lastError = ""))) {
            return failed("Could not save the sync notes on the phone. Nothing was uploaded.")
        }
        return sendBatch(batch, onStep, false)
    }

    /** Steps 4 (send), 5 (verify) and 6 (mark synced) of section 7, for a batch that is already saved. */
    private fun sendBatch(batch: PendingBatch, onStep: (Int) -> Unit, helperHint: Boolean): LinkOutcome {
        onStep(STEP_UPLOAD)
        val body = JSONObject()
        body.put(Contract.P_OP_ID, batch.opId)
        body.put(Contract.P_DEVICE_ID, config.deviceId)
        body.put(Contract.P_MODE, batch.mode)
        val changes = JSONArray()
        for (c in batch.changes) {
            val o = JSONObject()
            o.put("doc_key", c.docKey)
            o.put("base_version", c.baseVersion)
            o.put("payload", c.payload)
            o.put("payload_sha256", c.payloadSha256)
            o.put("deleted", c.deleted)
            changes.put(o)
        }
        body.put(Contract.P_CHANGES, changes)

        val pushed = api.rpc(Contract.RPC_PUSH, body, true)
        val pushJson: JSONObject = when (pushed) {
            is ApiResult.Ok -> pushed.json
            is ApiResult.AppError -> {
                if (pushed.code == Contract.ERR_CLOUD_HAS_DATA) return LinkOutcome.AskCaseC
                if (pushed.code == Contract.ERR_CHECKSUM) {
                    return failed("The cloud received different data than was sent (checksum mismatch). Nothing on the phone was changed. Tap Try again.")
                }
                return if (helperHint) helperProblem() else failFrom(pushed)
            }
            is ApiResult.Http -> {
                if (pushed.snippet.contains(Contract.ERR_CLOUD_HAS_DATA)) return LinkOutcome.AskCaseC
                return if (helperHint && pushed.code in 400..499) helperProblem() else failFrom(pushed)
            }
            ApiResult.Offline -> return failFrom(pushed)
            ApiResult.AuthExpired -> return failFrom(pushed)
        }

        val answer = pushJson.optString("status", if (pushJson.has("results")) "ok" else "")
        if (answer == "conflict") {
            return if (helperHint) {
                helperProblem()
            } else {
                failed("The cloud has changes this phone does not know about, so nothing was uploaded (conflict). Your data on the phone is untouched. Tap Try again.")
            }
        }
        if (answer != "ok") {
            return failed("The cloud answered in a way this app did not expect. Your data on the phone is untouched. Tap Try again.")
        }

        // Step 5: build the expectation from the server's results and ask the server to prove it
        onStep(STEP_VERIFY)
        val byKey = HashMap<String, JSONObject>()
        val results = pushJson.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                byKey[o.optString("doc_key", "")] = o
            }
        }
        val expect = ArrayList<Expect>()
        for (c in batch.changes) {
            val r = byKey[c.docKey]
            val version = if (r != null) r.optInt("version", c.baseVersion + 1) else c.baseVersion + 1
            val serverSha = if (r != null) r.optString("payload_sha256", "") else ""
            if (serverSha.isNotEmpty() && serverSha != c.payloadSha256) {
                return failed("The cloud stored different data than was sent (checksum mismatch). Nothing was marked as saved. Tap Try again.")
            }
            expect.add(Expect(c.docKey, version, c.payloadSha256))
        }
        val expectArray = JSONArray()
        for (e in expect) {
            val o = JSONObject()
            o.put("doc_key", e.key)
            o.put("version", e.version)
            o.put("payload_sha256", e.sha)
            expectArray.put(o)
        }
        val verifyBody = JSONObject()
        verifyBody.put(Contract.P_EXPECT, expectArray)
        val verified = api.rpc(Contract.RPC_VERIFY, verifyBody, true)
        if (verified !is ApiResult.Ok) return failFrom(verified)
        val allMatch = verified.json.optBoolean("all_match", false)
        val extra = verified.json.optJSONArray("extra_on_server")
        val noExtra = extra == null || extra.length() == 0
        if (!allMatch || !noExtra) {
            return failed("The check after the upload did not match. Nothing was marked as saved. Tap Try again.")
        }

        // Step 6: only now is the phone marked as synced
        val docs = LinkedHashMap<String, DocState>()
        for (e in expect) docs[e.key] = DocState(e.version, e.sha)
        val done = loadSync().copy(
            lastSeq = pushJson.optLong("seq", 0L),
            initialized = true,
            docs = docs,
            pending = null,
            lastSyncMillis = System.currentTimeMillis(),
            lastError = ""
        )
        if (!SyncStore.save(app, done)) {
            return LinkOutcome.Failed("The cloud has your data, but this phone could not save its sync notes. Tap Try again.")
        }
        config.cloudMode = CloudMode.ON

        // Best effort: a first backup point in the cloud
        val backupBody = JSONObject()
        backupBody.put(Contract.P_BACKUP_KIND, "manual")
        backupBody.put(Contract.P_BACKUP_NOTE, "after first upload")
        api.rpc(Contract.RPC_BACKUP, backupBody)

        onStep(STEP_DONE)
        return LinkOutcome.Done
    }

    /** Applies the cloud data that [pulled] holds. Keeps a safety copy of ledger.json first. */
    private fun applyPulled(onStep: (Int) -> Unit): LinkOutcome {
        val cloud = pulled ?: return failed("The cloud data is not ready. Nothing was changed.")
        onStep(STEP_SAFETY)
        val src = File(app.filesDir, "ledger.json")
        if (src.exists()) {
            val copy = uniqueCopyFile("ledger.before-cloud-download")
            if (!copyVerified(src, copy)) {
                return failed("Could not make a safety copy. Nothing was changed.")
            }
        }
        onStep(STEP_VERIFY)
        val newLedger = try {
            LedgerStorage.fromJson(cloud.joinedText)
        } catch (e: Exception) {
            return failed("The cloud data could not be read. Nothing was changed.")
        }
        val text = LedgerStorage.toJson(newLedger)
        if (!LedgerStorage.saveRawText(app, text)) {
            return failed("Could not save the cloud data on the phone. Nothing was changed.")
        }
        val docs = LinkedHashMap<String, DocState>()
        for (d in cloud.docs) docs[d.key] = DocState(d.version, if (d.deleted) "" else d.sha, d.deleted)
        val done = loadSync().copy(
            lastSeq = cloud.lastSeq,
            initialized = true,
            docs = docs,
            pending = null,
            lastSyncMillis = System.currentTimeMillis(),
            lastError = ""
        )
        if (!SyncStore.save(app, done)) {
            return LinkOutcome.Failed("The cloud data is on the phone, but the sync notes could not be saved. Tap Try again.")
        }
        config.cloudMode = CloudMode.ON
        pulled = null
        onStep(STEP_DONE)
        return LinkOutcome.DoneWithCloudData(newLedger)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun buildBatch(ledger: Ledger, mode: String): BatchResult {
        val ledgerText = LedgerStorage.toJson(ledger)
        val docs = LedgerSplitter.split(ledgerText)
        // Safety: joining the documents again must give exactly the same ledger. If not, nothing is uploaded.
        val rebuilt = LedgerStorage.toJson(
            LedgerStorage.fromJson(LedgerSplitter.join(docs.associate { it.key to it.text }))
        )
        if (rebuilt != ledgerText) {
            return BatchResult(
                null,
                failed("The safety check before the upload failed, so nothing was uploaded. Your data is untouched. Please show this message to the owner's helper (split check).")
            )
        }
        var biggest = 0
        for (d in docs) {
            val size = d.text.toByteArray(Charsets.UTF_8).size
            if (size > biggest) biggest = size
        }
        largestDocBytes = biggest
        if (docs.size > MAX_DOCS_PER_BATCH) {
            return BatchResult(
                null,
                failed("This company has more than 200 cloud documents. Ask the owner's helper (cloud limit). Nothing was changed.")
            )
        }
        val changes = docs.map { PendingChange(it.key, 0, it.text, it.sha256, false) }
        val batch = PendingBatch(UUID.randomUUID().toString(), mode, changes, System.currentTimeMillis())
        return BatchResult(batch, null)
    }

    /** Reads every document from the cloud, in pages, and joins them. Nothing is applied here. */
    private fun pullAll(expectedCount: Int): PullResult {
        var since = 0L
        var after = ""
        var maxSeq = 0L
        var pages = 0
        val all = LinkedHashMap<String, PulledDoc>()
        while (true) {
            pages++
            if (pages > 1000) return PullResult(null, failed("The cloud has too much to read. Nothing was changed."))
            val body = JSONObject()
            body.put(Contract.P_SINCE_SEQ, since)
            body.put(Contract.P_AFTER_KEY, after)
            val r = api.rpc(Contract.RPC_PULL, body, true)
            if (r !is ApiResult.Ok) return PullResult(null, failFrom(r))
            val j = r.json
            val array = j.optJSONArray("docs") ?: j.optJSONArray("rows") ?: JSONArray()
            var lastSeq = since
            var lastKey = after
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val key = o.optString("doc_key", "")
                if (key.isEmpty()) continue
                val raw = if (o.has("payload")) o.get("payload") else ""
                val text = if (raw is String) raw else raw.toString()
                val serverSha = o.optString("sha256", o.optString("payload_sha256", ""))
                val ourSha = sha256Hex(text)
                if (raw is String && serverSha.isNotEmpty() && serverSha != ourSha) {
                    return PullResult(null, failed("The data from the cloud did not match its checksum. Nothing was changed."))
                }
                val seq = o.optLong("seq", 0L)
                all[key] = PulledDoc(key, o.optInt("version", 0), text, o.optBoolean("deleted", false), ourSha)
                if (seq > maxSeq) maxSeq = seq
                lastSeq = seq
                lastKey = key
            }
            if (j.has("seq")) maxSeq = maxOf(maxSeq, j.optLong("seq", 0L))
            val more = j.optBoolean("has_more", false)
            if (!more) break
            val cursor = j.optJSONObject("cursor")
            val nextSince = when {
                j.has("next_since_seq") -> j.optLong("next_since_seq", lastSeq)
                cursor != null -> cursor.optLong("since_seq", lastSeq)
                else -> lastSeq
            }
            val nextAfter = when {
                j.has("next_after_key") -> j.optString("next_after_key", lastKey)
                cursor != null -> cursor.optString("after_key", lastKey)
                else -> lastKey
            }
            if (nextSince == since && nextAfter == after) {
                return PullResult(null, failed("Could not read all of the cloud data. Nothing was changed."))
            }
            since = nextSince
            after = nextAfter
        }
        if (expectedCount > 0 && all.size < expectedCount) {
            return PullResult(null, failed("Only part of the cloud data could be read. Nothing was changed."))
        }
        val live = LinkedHashMap<String, String>()
        for (d in all.values) if (!d.deleted) live[d.key] = d.text
        val joined = try {
            LedgerSplitter.join(live)
        } catch (e: Exception) {
            return PullResult(null, failed("The cloud data could not be read. Nothing was changed."))
        }
        return PullResult(PulledCloud(all.values.toList(), maxSeq, joined), null)
    }

    /** Safety copy of ledger.json as ledger.pre-cloud.json. Returns a message when it failed, else null. */
    private fun makeSafetyCopy(ledger: Ledger): String? {
        val problem = "Could not make a safety copy. Nothing was changed."
        val src = File(app.filesDir, "ledger.json")
        if (!src.exists()) {
            // The phone ledger must be on disk before anything depends on it.
            LedgerStorage.save(app, ledger)
        }
        if (!src.exists() || src.length() == 0L) return problem
        val pre = File(app.filesDir, "ledger.pre-cloud.json")
        if (pre.exists()) {
            // Made on an earlier try. Never overwritten, never deleted.
            return if (pre.length() > 0L) null else problem
        }
        return if (copyVerified(src, pre)) null else problem
    }

    /** Copies bytes through a tmp file, then reads the copy back and compares length and SHA-256. */
    private fun copyVerified(src: File, dst: File): Boolean {
        return try {
            val bytes = src.readBytes()
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(dst)) {
                false
            } else {
                val back = dst.readBytes()
                back.size == bytes.size && sha256Hex(back) == sha256Hex(bytes)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun uniqueCopyFile(base: String): File {
        var file = File(app.filesDir, "$base.json")
        var n = 2
        while (file.exists()) {
            file = File(app.filesDir, "$base.$n.json")
            n++
        }
        return file
    }

    /** True for a brand-new phone ledger (nothing entered yet). */
    private fun isFreshLedger(ledger: Ledger): Boolean {
        return try {
            LedgerStorage.toJson(ledger) == LedgerStorage.toJson(Ledger().withSoftwareApp())
        } catch (e: Exception) {
            false
        }
    }

    /** Remembers the problem in cloud_sync.json (lastError) and returns it. */
    private fun failed(message: String, needsSignIn: Boolean = false): LinkOutcome.Failed {
        runCatching { SyncStore.save(app, loadSync().copy(lastError = message)) }
        return LinkOutcome.Failed(message, needsSignIn)
    }

    private fun helperProblem(): LinkOutcome.Failed =
        failed("Ask the owner's helper to reset the cloud. Your data on the phone is untouched.")

    private fun failFrom(r: ApiResult): LinkOutcome.Failed {
        return when (r) {
            is ApiResult.Ok -> failed("The cloud answered in a way this app did not expect. Try again.")
            ApiResult.Offline -> failed("No internet. Try again when you are online.")
            ApiResult.AuthExpired -> failed("Please sign in again.", true)
            is ApiResult.Http -> failed("The cloud had a problem (code " + r.code + "). Try again.")
            is ApiResult.AppError -> {
                val code = if (r.code.isEmpty()) "error" else r.code
                failed("The cloud had a problem ($code). Try again.")
            }
        }
    }

    companion object {
        const val STEP_SAFETY = 0
        const val STEP_UPLOAD = 1
        const val STEP_VERIFY = 2
        const val STEP_DONE = 3
        /** Like STEP_UPLOAD but for reading the cloud (the screen says "Downloading…"). */
        const val STEP_DOWNLOAD = 5

        const val MAX_DOCS_PER_BATCH = 200
    }
}
