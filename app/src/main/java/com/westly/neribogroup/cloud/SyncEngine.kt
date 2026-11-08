package com.westly.neribogroup.cloud

import android.content.Context
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.withClockAtLeast
import com.westly.neribogroup.data.withSoftwareApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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

/** What the sync engine needs from the app. The engine runs on a background thread. */
interface SyncHost {
    /** The phone's live ledger right now. */
    fun currentLedger(): Ledger

    /**
     * Replaces the live ledger with [newLedger] (saves ledger.json, updates the screen) only if the live ledger
     * is still [base]. Returns false when it changed meanwhile or the save failed; nothing is changed then.
     * Must not start another sync.
     */
    fun applyCloudLedger(base: Ledger, newLedger: Ledger): Boolean
}

/** How one sync ended. [ran] is false only when another sync was already running. */
data class SyncResult(
    val ran: Boolean,
    val status: CloudStatus,
    val message: String = "",
    val appliedCloudData: Boolean = false,
    val newConflicts: Int = 0,
    val unsyncedDocs: Int = 0
)

/** The answer of "Back up now". [message] is shown as it is. */
data class BackupOutcome(val ok: Boolean, val message: String)

/** One cloud document the owner can open the history of. */
data class HistoryDoc(val key: String, val name: String)

/** One saved version of a cloud document. [createdAt] is the server's time text. */
data class HistoryRow(val version: Int, val createdAt: String, val sizeBytes: Long, val deleted: Boolean)

/** The history list. [message] is empty when it worked (the list may still be empty). */
data class HistoryOutcome(val rows: List<HistoryRow>, val message: String)

/** The answer of "Restore this version". [sync] is the pull that followed a successful restore. */
data class RestoreOutcome(
    val ok: Boolean,
    val message: String,
    val unchanged: Boolean,
    val newVersion: Int,
    val sync: SyncResult?
)

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
        const val RPC_GET_VERSION = "get_doc_version"
        const val RPC_LIST_HISTORY = "list_doc_history"
        const val RPC_RESTORE = "restore_doc_version"

        const val P_OP_ID = "p_op_id"
        const val P_DEVICE_ID = "p_device_id"
        const val P_MODE = "p_mode"
        const val P_CHANGES = "p_changes"
        const val P_EXPECT = "p_expect"
        const val P_SINCE_SEQ = "p_since_seq"
        const val P_AFTER_KEY = "p_after_key"
        const val P_BACKUP_KIND = "p_reason"
        const val P_BACKUP_NOTE = "p_note"
        const val P_LABEL = "p_label"
        const val P_APP_VERSION = "p_app_version"
        const val P_DOC_KEY = "p_doc_key"
        const val P_VERSION = "p_version"
        const val P_LIMIT = "p_limit"

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
    // Phase 3B-1a: the sync cycle (resend, pull, apply, push), conflicts and their resolution
    // ---------------------------------------------------------------------------------------------

    private val running = AtomicBoolean(false)
    private var consecutiveFailures = 0

    /** How long to wait before the next try after a network problem. 0 after a success. 3B-1b schedules it. */
    @Volatile
    var nextRetryDelayMillis: Long = 0L
        private set

    private class PullChanges(val docs: Map<String, PulledDoc>, val newLastSeq: Long, val failure: SyncResult?)

    private class StepOutcome(val stop: SyncResult?, val applied: Boolean, val newConflicts: Int, val retry: Boolean = false)

    private class BuildResult(val batch: PendingBatch?, val failure: SyncResult?)

    private class PushLoopOutcome(val stop: SyncResult?, val hadConflict: Boolean)

    private class JoinOutcome(val applied: Boolean, val retry: Boolean, val failure: SyncResult?)

    private sealed class PushAnswer {
        object Ok : PushAnswer()
        object Conflict : PushAnswer()
        class Stop(val result: SyncResult) : PushAnswer()
    }

    /**
     * One full sync: resend a saved batch, pull, apply, push. Blocks: call it from a background thread.
     * A second call while one is running returns at once with ran = false. With the cloud OFF nothing is sent.
     */
    fun syncNow(host: SyncHost): SyncResult {
        if (config.cloudMode != CloudMode.ON) return SyncResult(true, CloudStatus.NOT_CONNECTED)
        if (!config.signedIn) return SyncResult(true, CloudStatus.SIGN_IN_AGAIN, "Please sign in again. Your data on the phone is safe.")
        if (!running.compareAndSet(false, true)) return SyncResult(false, CloudStatus.SYNCING)
        try {
            if (!loadSync().initialized) return SyncResult(true, CloudStatus.NOT_CONNECTED)
            return try {
                cycle(host)
            } catch (e: Throwable) {
                problem("Sync problem (" + e.javaClass.simpleName + ") — your data on the phone is safe.")
            }
        } finally {
            running.set(false)
        }
    }

    /**
     * Called when the app starts (3B-1b): if a batch was saved but never answered, send it again first,
     * unchanged (same opId). Does not pull. Blocks: call it from a background thread.
     */
    fun resendPendingOnStart(): SyncResult {
        if (config.cloudMode != CloudMode.ON) return SyncResult(true, CloudStatus.NOT_CONNECTED)
        if (!config.signedIn) return SyncResult(true, CloudStatus.SIGN_IN_AGAIN, "Please sign in again. Your data on the phone is safe.")
        if (!running.compareAndSet(false, true)) return SyncResult(false, CloudStatus.SYNCING)
        try {
            val sync = loadSync()
            val saved = sync.pending
            if (!sync.initialized || saved == null) return SyncResult(true, CloudStatus.UP_TO_DATE)
            return try {
                when (val answer = sendPending(saved)) {
                    PushAnswer.Ok -> {
                        resetBackoff()
                        SyncResult(true, CloudStatus.UP_TO_DATE)
                    }
                    PushAnswer.Conflict -> SyncResult(true, CloudStatus.UP_TO_DATE)
                    is PushAnswer.Stop -> answer.result
                }
            } catch (e: Throwable) {
                problem("Sync problem (" + e.javaClass.simpleName + ") — your data on the phone is safe.")
            }
        } finally {
            running.set(false)
        }
    }

    /** How many documents differ from the cloud right now (changed, new or removed on the phone). */
    fun countUnsynced(ledger: Ledger): Int {
        return try {
            val sync = loadSync()
            val docs = LedgerSplitter.split(LedgerStorage.toJson(ledger))
            changedKeys(docs, sync, emptySet()).size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * The owner chose "Keep phone version": the phone's document will be pushed on top of the newer cloud
     * version (base_version = that version). Marks the conflict handled, then syncs.
     */
    fun resolveConflictKeepPhone(key: String, host: SyncHost): SyncResult {
        if (config.cloudMode != CloudMode.ON) return SyncResult(true, CloudStatus.NOT_CONNECTED)
        if (!running.compareAndSet(false, true)) return SyncResult(false, CloudStatus.SYNCING)
        try {
            val sync = loadSync()
            val conflicts = parseConflicts(sync)
            val index = conflicts.indexOfFirst { it.key == key && !it.handled }
            if (index >= 0) {
                val c = conflicts[index]
                val docs = LinkedHashMap(sync.docs)
                // An empty hash keeps the document "changed", so the next push sends the phone's version.
                docs[key] = DocState(c.cloudVersion, "", false)
                conflicts[index] = c.copy(handled = true)
                if (!SyncStore.save(app, sync.copy(docs = docs, conflicts = conflictTexts(conflicts)))) {
                    return problem("Could not save the sync notes on the phone. Nothing was changed.")
                }
            }
        } finally {
            running.set(false)
        }
        return syncNow(host)
    }

    /**
     * The owner chose "Use cloud version": the newer cloud version of that one document replaces the phone's
     * (the phone's version stays in the conflict copy file). Marks the conflict handled, then syncs.
     */
    fun resolveConflictUseCloud(key: String, host: SyncHost): SyncResult {
        if (config.cloudMode != CloudMode.ON) return SyncResult(true, CloudStatus.NOT_CONNECTED)
        if (!running.compareAndSet(false, true)) return SyncResult(false, CloudStatus.SYNCING)
        var failure: SyncResult? = null
        try {
            failure = try {
                useCloudInner(key, host)
            } catch (e: Throwable) {
                problem("Sync problem (" + e.javaClass.simpleName + ") — your data on the phone is safe.")
            }
        } finally {
            running.set(false)
        }
        if (failure != null) return failure
        return syncNow(host)
    }

    /** Returns null when it worked (or there was nothing to do), else the reason it stopped. */
    private fun useCloudInner(key: String, host: SyncHost): SyncResult? {
        val sync = loadSync()
        val conflicts = parseConflicts(sync)
        val index = conflicts.indexOfFirst { it.key == key && !it.handled }
        if (index < 0) return null
        val c = conflicts[index]

        val body = JSONObject()
        body.put(Contract.P_DOC_KEY, key)
        body.put(Contract.P_VERSION, c.cloudVersion)
        val r = api.rpc(Contract.RPC_GET_VERSION, body, true)
        if (r !is ApiResult.Ok) return fromApiFailure(r)
        val deleted = r.json.optBoolean("deleted", false)
        val raw = r.json.opt("payload")
        val text = if (raw is String) raw else (raw?.toString() ?: "")
        val serverSha = r.json.optString("payload_sha256", "")
        if (!deleted && (serverSha.isEmpty() || sha256Hex(text) != serverSha)) {
            return problem("The data from the cloud did not match its checksum. Your phone data is untouched.")
        }
        val version = r.json.optInt("version", c.cloudVersion)

        var applyTries = 0
        while (true) {
            applyTries++
            val base = host.currentLedger()
            val baseText = LedgerStorage.toJson(base)
            val local = LedgerSplitter.split(baseText)
            val copyName = makeConflictCopy(baseText)
                ?: return problem("Could not make a safety copy. Nothing was changed.")
            val texts = LinkedHashMap<String, String>()
            for (d in local) texts[d.key] = d.text
            val isApp = key.startsWith(LedgerSplitter.APP_PREFIX)
            var changesPhone = true
            if (deleted) {
                if (isApp) texts.remove(key) else changesPhone = false
            } else if (texts[key] == text) {
                changesPhone = false
            } else {
                texts[key] = text
            }
            var applied = !changesPhone
            if (changesPhone) {
                val outcome = joinAndApply(host, base, texts)
                val problemFound = outcome.failure
                if (problemFound != null) return problemFound
                applied = outcome.applied
            }
            if (applied) {
                val fresh = loadSync()
                val docs = LinkedHashMap(fresh.docs)
                docs[key] = DocState(version, if (deleted) "" else serverSha, deleted)
                val list = parseConflicts(fresh)
                val at = list.indexOfFirst { it.key == key && !it.handled }
                if (at >= 0) list[at] = list[at].copy(handled = true, file = copyName)
                pruneConflictCopies(unhandledFiles(list))
                if (!SyncStore.save(app, fresh.copy(docs = docs, conflicts = conflictTexts(list)))) {
                    return problem("The cloud version is on the phone, but the sync notes could not be saved. Tap Sync now again.")
                }
                return null
            }
            if (applyTries >= 3) {
                return problem("Could not copy the cloud version onto the phone right now. Your phone data is untouched. Try again.")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 3B-2: Back up now, cloud history and Restore this version
    // ---------------------------------------------------------------------------------------------

    /** A calm sentence for a failed call. An expired session is cleared (local data is never touched). */
    private fun calmText(r: ApiResult): String {
        return when (r) {
            ApiResult.Offline -> "No internet. Try again later."
            ApiResult.AuthExpired -> {
                config.clearSession()
                "Please sign in again. Your data on the phone is safe."
            }
            is ApiResult.Http -> {
                if (r.code == 429 || r.code in 500..599) {
                    "The cloud is busy. Try again later."
                } else {
                    "The cloud had a problem (code " + r.code + "). Try again later."
                }
            }
            is ApiResult.AppError -> {
                val code = if (r.code.isEmpty()) "error" else r.code
                "The cloud had a problem ($code). Try again later."
            }
            is ApiResult.Ok -> "The cloud answered in a way this app did not expect. Try again later."
        }
    }

    /** "Back up now": asks the cloud to keep a full copy of every document. Blocks: call from a background thread. */
    fun createBackup(label: String): BackupOutcome {
        return try {
            if (config.cloudMode != CloudMode.ON || !loadSync().initialized) {
                return BackupOutcome(false, "Connect to the cloud first.")
            }
            if (!config.signedIn) return BackupOutcome(false, "Please sign in again. Your data on the phone is safe.")
            val body = JSONObject()
            body.put(Contract.P_BACKUP_KIND, "manual")
            val note = label.trim()
            if (note.isNotEmpty() && note != "manual") body.put(Contract.P_BACKUP_NOTE, note.take(200))
            val r = api.rpc(Contract.RPC_BACKUP, body, true)
            if (r is ApiResult.Ok) {
                val n = r.json.optInt("doc_count", 0)
                BackupOutcome(true, "Backup saved: " + n + (if (n == 1) " document" else " documents"))
            } else {
                BackupOutcome(false, calmText(r))
            }
        } catch (e: Throwable) {
            BackupOutcome(false, "The backup did not finish. Your data on the phone is safe.")
        }
    }

    /** The latest [HISTORY_ROWS] versions of one document, newest first. Blocks: call from a background thread. */
    fun listDocHistory(docKey: String): HistoryOutcome {
        return try {
            if (config.cloudMode != CloudMode.ON || !loadSync().initialized) {
                return HistoryOutcome(emptyList(), "Connect to the cloud first.")
            }
            if (!config.signedIn) {
                return HistoryOutcome(emptyList(), "Please sign in again. Your data on the phone is safe.")
            }
            val body = JSONObject()
            body.put(Contract.P_DOC_KEY, docKey)
            body.put(Contract.P_LIMIT, HISTORY_ROWS)
            val r = api.rpc(Contract.RPC_LIST_HISTORY, body)
            if (r is ApiResult.Ok) {
                val array = r.json.optJSONArray("versions") ?: JSONArray()
                val rows = ArrayList<HistoryRow>()
                for (i in 0 until array.length()) {
                    if (rows.size >= HISTORY_ROWS) break
                    val o = array.optJSONObject(i) ?: continue
                    rows.add(
                        HistoryRow(
                            version = o.optInt("version", 0),
                            createdAt = o.optString("created_at", ""),
                            sizeBytes = o.optLong("size_bytes", 0L),
                            deleted = o.optBoolean("deleted", false)
                        )
                    )
                }
                HistoryOutcome(rows, "")
            } else {
                HistoryOutcome(emptyList(), calmText(r))
            }
        } catch (e: Throwable) {
            HistoryOutcome(emptyList(), "Could not load the history. Try again later.")
        }
    }

    /**
     * Asks the cloud to make [version] of [docKey] the newest version (a new version is created; nothing is
     * erased). Then runs one normal sync, so the phone gets the result only through the safe apply path
     * (safety copy first; a phone document with unsynced edits becomes a conflict, never a silent overwrite).
     * Refuses while a sync is running. Blocks: call from a background thread.
     */
    fun restoreDocVersion(docKey: String, version: Int, host: SyncHost): RestoreOutcome {
        if (config.cloudMode != CloudMode.ON || !loadSync().initialized) {
            return RestoreOutcome(false, "Connect to the cloud first.", false, 0, null)
        }
        if (!config.signedIn) {
            return RestoreOutcome(false, "Please sign in again. Your data on the phone is safe.", false, 0, null)
        }
        if (!running.compareAndSet(false, true)) {
            return RestoreOutcome(false, "Please wait, syncing…", false, 0, null)
        }
        // While this call runs no sync can start (they share the same flag).
        val restored: ApiResult? = try {
            val body = JSONObject()
            body.put(Contract.P_OP_ID, UUID.randomUUID().toString())
            body.put(Contract.P_DEVICE_ID, config.deviceId)
            body.put(Contract.P_DOC_KEY, docKey)
            body.put(Contract.P_VERSION, version)
            api.rpc(Contract.RPC_RESTORE, body, true)
        } catch (e: Throwable) {
            null
        } finally {
            running.set(false)
        }
        if (restored == null) {
            return RestoreOutcome(false, "The restore did not finish. Your data on the phone is safe.", false, 0, null)
        }
        if (restored is ApiResult.AppError && (restored.code == "version_not_found" || restored.code == "doc_not_found")) {
            return RestoreOutcome(false, "That version could not be found in the cloud. Nothing was changed.", false, 0, null)
        }
        if (restored !is ApiResult.Ok) {
            return RestoreOutcome(false, calmText(restored), false, 0, null)
        }
        val unchanged = restored.json.optString("outcome", "") == "unchanged"
        val newVersion = restored.json.optInt("version", 0)
        val after = try {
            syncNow(host)
        } catch (e: Throwable) {
            null
        }
        return RestoreOutcome(true, "", unchanged, newVersion, after)
    }

    // ---- the cycle ----

    private fun cycle(host: SyncHost): SyncResult {
        var appliedAny = false
        var newConflicts = 0

        // Step 2: a saved batch is always sent again first, unchanged.
        val saved = loadSync().pending
        if (saved != null) {
            when (val answer = sendPending(saved)) {
                PushAnswer.Ok -> {}
                PushAnswer.Conflict -> {}
                is PushAnswer.Stop -> return withState(answer.result, host)
            }
        }

        var conflictRetried = false
        while (true) {
            // Step 3: pull first, then apply
            val pulledStep = pullAndApply(host)
            val stop = pulledStep.stop
            if (stop != null) return withState(stop, host)
            if (pulledStep.applied) appliedAny = true
            newConflicts += pulledStep.newConflicts

            // Steps 4 and 5: push what changed on the phone
            val pushStep = pushLoop(host)
            val pushStop = pushStep.stop
            if (pushStop != null) return withState(pushStop, host)
            if (pushStep.hadConflict) {
                if (!conflictRetried) {
                    conflictRetried = true
                    continue
                }
                return withState(problem("The cloud changed while syncing. Tap Sync now again. Your data on the phone is safe."), host)
            }
            break
        }

        resetBackoff()
        val after = loadSync()
        val waiting = parseConflicts(after).count { !it.handled }
        val warnings = pendingWarnings
        pendingWarnings = ""
        SyncStore.save(app, after.copy(lastSyncMillis = System.currentTimeMillis(), lastError = warnings))
        val message = if (waiting > 0) {
            "Synced, except for a part that needs your choice. Nothing was overwritten."
        } else {
            "Synced ✔"
        }
        return SyncResult(
            ran = true,
            status = CloudStatus.UP_TO_DATE,
            message = message,
            appliedCloudData = appliedAny,
            newConflicts = newConflicts,
            unsyncedDocs = safeUnsynced(host)
        )
    }

    /** Adds the number of unsynced documents to a stopping result. */
    private fun withState(result: SyncResult, host: SyncHost): SyncResult {
        return result.copy(unsyncedDocs = safeUnsynced(host))
    }

    private fun safeUnsynced(host: SyncHost): Int {
        return try {
            countUnsynced(host.currentLedger())
        } catch (e: Throwable) {
            0
        }
    }

    @Volatile
    private var pendingWarnings: String = ""

    private fun pullAndApply(host: SyncHost): StepOutcome {
        var tries = 0
        while (true) {
            tries++
            val sync = loadSync()
            val pull = pullChanges(sync)
            val failure = pull.failure
            if (failure != null) return StepOutcome(failure, false, 0)
            val result = applyChanges(host, sync, pull)
            // retry = the phone's data changed while the cloud changes were being applied: read again.
            if (!result.retry) return result
            if (tries >= 3) {
                return StepOutcome(
                    problem("Could not copy the cloud changes onto the phone right now. Your phone data is untouched. Tap Sync now again."),
                    false,
                    0
                )
            }
        }
    }

    /** Pages through pull_docs with the contract's cursor rule. Nothing is applied here. */
    private fun pullChanges(sync: SyncData): PullChanges {
        var since = sync.lastSeq
        var after = ""
        var pages = 0
        val all = LinkedHashMap<String, PulledDoc>()
        while (true) {
            pages++
            if (pages > 1000) return PullChanges(emptyMap(), sync.lastSeq, problem("The cloud has too much to read at once. Your phone data is untouched."))
            val body = JSONObject()
            body.put(Contract.P_SINCE_SEQ, since)
            body.put(Contract.P_AFTER_KEY, after)
            val r = api.rpc(Contract.RPC_PULL, body, true)
            if (r !is ApiResult.Ok) return PullChanges(emptyMap(), sync.lastSeq, fromApiFailure(r))
            val j = r.json
            val array = j.optJSONArray("docs") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val key = o.optString("doc_key", "")
                if (key.isEmpty()) continue
                val deleted = o.optBoolean("deleted", false)
                val raw = if (o.has("payload") && !o.isNull("payload")) o.get("payload") else ""
                val text = if (raw is String) raw else raw.toString()
                val serverSha = o.optString("payload_sha256", "")
                val ourSha = sha256Hex(text)
                if (!deleted && (serverSha.isEmpty() || serverSha != ourSha)) {
                    return PullChanges(
                        emptyMap(),
                        sync.lastSeq,
                        problem("The data from the cloud did not match its checksum, so nothing was applied. Your phone data is untouched.")
                    )
                }
                val version = o.optInt("version", 0)
                val known = all[key]
                if (known == null || version > known.version) {
                    all[key] = PulledDoc(key, version, text, deleted, if (deleted) "" else ourSha)
                }
            }
            val cursor = j.optJSONObject("next_cursor")
            val cursorSeq = if (cursor != null) cursor.optLong("seq", since) else j.optLong("server_last_seq", since)
            val cursorKey = if (cursor != null) cursor.optString("doc_key", "") else ""
            val more = j.optBoolean("has_more", false)
            if (!more) {
                return PullChanges(all, cursorSeq, null)
            }
            if (cursorSeq == since && cursorKey == after) {
                return PullChanges(emptyMap(), sync.lastSeq, problem("Could not read all of the cloud data. Your phone data is untouched."))
            }
            since = cursorSeq
            after = cursorKey
        }
    }

    /** Section 9: decide per document, keep copies for conflicts, join, save, tell the app. */
    private fun applyChanges(host: SyncHost, sync: SyncData, pull: PullChanges): StepOutcome {
        val base = host.currentLedger()
        val baseText = LedgerStorage.toJson(base)
        val local = LedgerSplitter.split(baseText)
        val localByKey = HashMap<String, SplitDoc>()
        for (d in local) localByKey[d.key] = d

        val conflicts = parseConflicts(sync)
        val blocked = HashSet<String>()
        for (c in conflicts) if (!c.handled) blocked.add(c.key)

        val docs = LinkedHashMap(sync.docs)
        val overrides = LinkedHashMap<String, String>()
        val removals = HashSet<String>()
        val conflictKeys = LinkedHashMap<String, Int>()
        val warnings = ArrayList<String>()

        for (d in pull.docs.values) {
            val state = sync.docs[d.key]
            val localVersion = state?.version ?: 0
            if (d.version <= localVersion) continue
            val isApp = d.key.startsWith(LedgerSplitter.APP_PREFIX)
            val localDoc = localByKey[d.key]
            val sameAsLocal = if (d.deleted) localDoc == null else (localDoc != null && localDoc.sha256 == d.sha)
            val dirty = SyncRules.isDirty(localDoc?.sha256, state, isApp) && !sameAsLocal
            val decision = SyncRules.decide(dirty || blocked.contains(d.key), localVersion, d.version)
            when (decision) {
                SyncDecision.NOTHING -> {}
                SyncDecision.CONFLICT -> conflictKeys[d.key] = d.version
                SyncDecision.APPLY -> {
                    if (sameAsLocal) {
                        docs[d.key] = DocState(d.version, d.sha, d.deleted)
                    } else if (d.deleted) {
                        if (isApp) {
                            removals.add(d.key)
                        } else {
                            warnings.add("The cloud tried to remove \"" + d.key + "\". It was ignored.")
                        }
                        docs[d.key] = DocState(d.version, "", true)
                    } else {
                        overrides[d.key] = d.text
                        docs[d.key] = DocState(d.version, d.sha, false)
                    }
                }
            }
        }

        // Conflicts: the phone's version is copied first. If that fails, nothing at all is applied.
        var copyName = ""
        if (conflictKeys.isNotEmpty()) {
            copyName = makeConflictCopy(baseText)
                ?: return StepOutcome(problem("Could not make a safety copy. Nothing was changed."), false, 0)
        }
        var newConflicts = 0
        val now = System.currentTimeMillis()
        for ((key, version) in conflictKeys) {
            val at = conflicts.indexOfFirst { it.key == key && !it.handled }
            if (at >= 0) {
                conflicts[at] = conflicts[at].copy(cloudVersion = maxOf(conflicts[at].cloudVersion, version))
            } else {
                conflicts.add(SyncConflict(key, version, copyName, false, false, now))
                newConflicts++
            }
        }

        var applied = false
        if (overrides.isNotEmpty() || removals.isNotEmpty()) {
            val texts = LinkedHashMap<String, String>()
            for (d in local) texts[d.key] = d.text
            for (k in removals) texts.remove(k)
            for ((k, t) in overrides) texts[k] = t
            val outcome = joinAndApply(host, base, texts)
            val failure = outcome.failure
            if (failure != null) return StepOutcome(failure, false, 0)
            if (outcome.retry) return StepOutcome(null, false, 0, true)
            applied = outcome.applied
        }

        // The notes are written last: if the app dies before this, the next pull finds the phone already
        // equal to the cloud for those documents and only does the bookkeeping.
        val changedSeq = maxOf(sync.lastSeq, pull.newLastSeq)
        pruneConflictCopies(unhandledFiles(conflicts))
        val trimmed = trimConflicts(conflicts)
        val updated = sync.copy(lastSeq = changedSeq, docs = docs, conflicts = conflictTexts(trimmed))
        if (!SyncStore.save(app, updated)) {
            return StepOutcome(
                problem("The cloud changes are on the phone, but the sync notes could not be saved. Tap Sync now again."),
                applied,
                newConflicts
            )
        }
        if (warnings.isNotEmpty()) pendingWarnings = warnings.joinToString(" ")
        return StepOutcome(null, applied, newConflicts)
    }

    /** Joins the documents, checks them, and hands the new ledger to the app. */
    private fun joinAndApply(host: SyncHost, base: Ledger, texts: Map<String, String>): JoinOutcome {
        val newLedger: Ledger = try {
            val joined = LedgerSplitter.join(texts)
            val parsed = LedgerStorage.fromJson(joined)
            // The clocks never go backwards.
            val withCases = parsed.withClockAtLeast(base.casesClockMillis)
            SoftwareEngine.withClockAtLeast(withCases, base.software.clockMillis)
        } catch (e: Exception) {
            return JoinOutcome(false, false, problem("Could not read the cloud data. Your phone data is untouched."))
        }
        val ok = host.applyCloudLedger(base, newLedger)
        return if (ok) JoinOutcome(true, false, null) else JoinOutcome(false, true, null)
    }

    // ---- push ----

    private fun pushLoop(host: SyncHost): PushLoopOutcome {
        var loops = 0
        while (loops < 3) {
            loops++
            val built = buildDirtyBatch(host.currentLedger(), loadSync())
            val failure = built.failure
            if (failure != null) return PushLoopOutcome(failure, false)
            val batch = built.batch ?: return PushLoopOutcome(null, false)
            // Saved BEFORE it is sent.
            if (!SyncStore.save(app, loadSync().copy(pending = batch))) {
                return PushLoopOutcome(problem("Could not save the sync notes on the phone. Nothing was uploaded."), false)
            }
            when (val answer = sendPending(batch)) {
                PushAnswer.Ok -> {}
                PushAnswer.Conflict -> return PushLoopOutcome(null, true)
                is PushAnswer.Stop -> return PushLoopOutcome(answer.result, false)
            }
        }
        return PushLoopOutcome(null, false)
    }

    /** The keys that differ from the cloud: changed or new documents, and removed apps. */
    private fun changedKeys(docs: List<SplitDoc>, sync: SyncData, blocked: Set<String>): List<String> {
        val result = ArrayList<String>()
        val present = HashSet<String>()
        for (d in docs) {
            present.add(d.key)
            if (blocked.contains(d.key)) continue
            if (SyncRules.isDirty(d.sha256, sync.docs[d.key], d.key.startsWith(LedgerSplitter.APP_PREFIX))) result.add(d.key)
        }
        for ((key, state) in sync.docs) {
            if (present.contains(key) || blocked.contains(key)) continue
            if (SyncRules.isDirty(null, state, key.startsWith(LedgerSplitter.APP_PREFIX))) result.add(key)
        }
        return result
    }

    private fun buildDirtyBatch(ledger: Ledger, sync: SyncData): BuildResult {
        val text = LedgerStorage.toJson(ledger)
        val docs = LedgerSplitter.split(text)
        val blocked = HashSet<String>()
        for (c in parseConflicts(sync)) if (!c.handled) blocked.add(c.key)
        val keys = changedKeys(docs, sync, blocked)
        if (keys.isEmpty()) return BuildResult(null, null)

        // Safety: joining the documents again must give exactly the same ledger. If not, nothing is sent.
        val rebuilt = LedgerStorage.toJson(
            LedgerStorage.fromJson(LedgerSplitter.join(docs.associate { it.key to it.text }))
        )
        if (rebuilt != text) {
            return BuildResult(null, problem("The safety check before the upload failed, so nothing was uploaded. Your data is untouched."))
        }
        var biggest = 0
        for (d in docs) {
            val size = d.text.toByteArray(Charsets.UTF_8).size
            if (size > biggest) biggest = size
        }
        largestDocBytes = biggest

        val byKey = HashMap<String, SplitDoc>()
        for (d in docs) byKey[d.key] = d
        val changes = ArrayList<PendingChange>()
        for (key in keys) {
            val doc = byKey[key]
            val baseVersion = sync.docs[key]?.version ?: 0
            if (doc != null) {
                changes.add(PendingChange(key, baseVersion, doc.text, doc.sha256, false))
            } else {
                val removed = "{\"deleted\":true}"
                changes.add(PendingChange(key, baseVersion, removed, sha256Hex(removed), true))
            }
            if (changes.size >= MAX_DOCS_PER_BATCH) break
        }
        val batch = PendingBatch(UUID.randomUUID().toString(), "normal", changes, System.currentTimeMillis())
        return BuildResult(batch, null)
    }

    private fun pushBody(batch: PendingBatch): JSONObject {
        val body = JSONObject()
        body.put(Contract.P_OP_ID, batch.opId)
        body.put(Contract.P_DEVICE_ID, config.deviceId)
        body.put(Contract.P_LABEL, "phone sync")
        body.put(Contract.P_APP_VERSION, appVersionName())
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
        return body
    }

    private fun appVersionName(): String {
        return try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Sends a saved batch and handles every kind of answer. The batch stays saved until the server answers. */
    private fun sendPending(batch: PendingBatch): PushAnswer {
        var attempt = 0
        while (attempt < 3) {
            attempt++
            val r = api.rpc(Contract.RPC_PUSH, pushBody(batch), true)
            if (r is ApiResult.AppError && r.code == Contract.ERR_CHECKSUM) continue
            return classifyPush(batch, r)
        }
        // The same data failed its checksum three times: the notes are cleared (the changes are found again
        // from the ledger next time) and the problem is shown. Local data is untouched.
        SyncStore.save(app, loadSync().copy(pending = null))
        return PushAnswer.Stop(problem("Sync problem — your data on the phone is safe. The upload did not pass its check. Try again later."))
    }

    private fun classifyPush(batch: PendingBatch, r: ApiResult): PushAnswer {
        if (r is ApiResult.Ok) {
            val status = r.json.optString("status", if (r.json.has("results")) "ok" else "")
            if (status == "conflict") {
                SyncStore.save(app, loadSync().copy(pending = null))
                return PushAnswer.Conflict
            }
            if (status != "ok") {
                return PushAnswer.Stop(problem("The cloud answered in a way this app did not expect. Your data on the phone is safe."))
            }
            return recordPushOk(batch, r.json)
        }
        if (r is ApiResult.AppError) {
            // Nothing was written on the server. Do not block later syncs with a batch that can never pass.
            val code = if (r.code.isEmpty()) "error" else r.code
            SyncStore.save(app, loadSync().copy(pending = null))
            return PushAnswer.Stop(problem("The cloud did not accept the upload ($code). Your data on the phone is safe."))
        }
        return PushAnswer.Stop(fromApiFailure(r))
    }

    private fun recordPushOk(batch: PendingBatch, json: JSONObject): PushAnswer {
        val byKey = HashMap<String, JSONObject>()
        val results = json.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                byKey[o.optString("doc_key", "")] = o
            }
        }
        val sync = loadSync()
        val docs = LinkedHashMap(sync.docs)
        for (c in batch.changes) {
            val res = byKey[c.docKey]
            val version = if (res != null) res.optInt("version", c.baseVersion + 1) else c.baseVersion + 1
            // The server stored exactly what was sent (an unchanged answer means the same text was already there).
            docs[c.docKey] = DocState(version, if (c.deleted) "" else c.payloadSha256, c.deleted)
        }
        // lastSeq moves up only when our push is the very next one: otherwise another phone pushed in between
        // and its changes must still be pulled.
        val seq = json.optLong("seq", sync.lastSeq)
        val newSeq = if (seq == sync.lastSeq + 1) seq else sync.lastSeq
        val done = sync.copy(
            lastSeq = newSeq,
            docs = docs,
            pending = null,
            lastSyncMillis = System.currentTimeMillis(),
            lastError = ""
        )
        if (!SyncStore.save(app, done)) {
            return PushAnswer.Stop(problem("The cloud has your changes, but this phone could not save its sync notes. Tap Sync now again."))
        }
        resetBackoff()
        return PushAnswer.Ok
    }

    // ---- results ----

    private fun resetBackoff() {
        consecutiveFailures = 0
        nextRetryDelayMillis = 0L
    }

    private fun waiting(): SyncResult {
        consecutiveFailures++
        nextRetryDelayMillis = SyncRules.backoffDelayMillis(consecutiveFailures)
        return SyncResult(true, CloudStatus.WAITING_INTERNET, "No internet right now. Your data is safe on the phone.")
    }

    private fun signInAgain(): SyncResult {
        config.clearSession()
        return SyncResult(true, CloudStatus.SIGN_IN_AGAIN, "Please sign in again. Your data on the phone is safe.")
    }

    /** Remembers the problem in cloud_sync.json (lastError) and returns it as a result. */
    private fun problem(message: String): SyncResult {
        runCatching { SyncStore.save(app, loadSync().copy(lastError = message)) }
        return SyncResult(true, CloudStatus.PROBLEM, message)
    }

    private fun fromApiFailure(r: ApiResult): SyncResult {
        return when (r) {
            ApiResult.Offline -> waiting()
            ApiResult.AuthExpired -> signInAgain()
            is ApiResult.Http -> {
                if (r.code == 429 || r.code in 500..599) {
                    waiting()
                } else {
                    problem("Sync problem (code " + r.code + ") — your data on the phone is safe.")
                }
            }
            is ApiResult.AppError -> {
                if (r.code == "cursor_ahead") {
                    problem("The cloud was reset or restored, so this phone cannot sync safely. Your phone data is untouched. Ask the owner's helper.")
                } else {
                    val code = if (r.code.isEmpty()) "error" else r.code
                    problem("Sync problem ($code) — your data on the phone is safe.")
                }
            }
            is ApiResult.Ok -> problem("The cloud answered in a way this app did not expect. Your data on the phone is safe.")
        }
    }

    // ---- conflicts: the list in cloud_sync.json and the copy files ----

    private fun parseConflicts(sync: SyncData): ArrayList<SyncConflict> {
        val list = ArrayList<SyncConflict>()
        for (text in sync.conflicts) {
            val c = SyncConflict.fromJsonText(text)
            if (c != null) list.add(c)
        }
        return list
    }

    private fun conflictTexts(list: List<SyncConflict>): List<String> = list.map { it.toJsonText() }

    /** Keeps every open conflict and the 30 newest handled ones. */
    private fun trimConflicts(list: List<SyncConflict>): List<SyncConflict> {
        val handled = list.filter { it.handled }.sortedBy { it.createdMillis }
        val drop: Set<SyncConflict> = if (handled.size > 30) handled.take(handled.size - 30).toSet() else emptySet()
        return list.filter { !drop.contains(it) }
    }

    private fun unhandledFiles(list: List<SyncConflict>): Set<String> {
        val names = HashSet<String>()
        for (c in list) if (!c.handled && c.file.isNotEmpty()) names.add(c.file)
        return names
    }

    /** Writes the phone's ledger text as ledger.conflict-<time>.json (checked by reading it back). Null on failure. */
    private fun makeConflictCopy(ledgerText: String): String? {
        return try {
            val now = System.currentTimeMillis()
            var attempt = 1
            var name = SyncRules.conflictFileName(now, attempt)
            while (File(app.filesDir, name).exists()) {
                attempt++
                name = SyncRules.conflictFileName(now, attempt)
            }
            val dst = File(app.filesDir, name)
            val bytes = ledgerText.toByteArray(Charsets.UTF_8)
            val tmp = File(app.filesDir, "$name.tmp")
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(dst)) return null
            val back = dst.readBytes()
            if (back.size == bytes.size && sha256Hex(back) == sha256Hex(bytes)) name else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Keeps the newest [SyncRules.MAX_CONFLICT_COPIES] conflict copies. Only ledger.conflict-*.json files are ever
     * deleted, and never one that an open conflict still needs ([protect]).
     */
    private fun pruneConflictCopies(protect: Set<String>) {
        try {
            val files = (app.filesDir.listFiles() ?: return).filter {
                it.isFile && it.name.startsWith("ledger.conflict-") && it.name.endsWith(".json")
            }.sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))
            var extra = files.size - SyncRules.MAX_CONFLICT_COPIES
            for (f in files) {
                if (extra <= 0) break
                if (protect.contains(f.name)) continue
                if (f.delete()) extra--
            }
        } catch (e: Exception) {
            // Cleaning up is optional; never a problem.
        }
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

        /** The history list shows at most this many versions. */
        const val HISTORY_ROWS = 20
    }
}
