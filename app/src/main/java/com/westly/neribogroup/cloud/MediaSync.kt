package com.westly.neribogroup.cloud

import android.content.Context
import android.util.Log
import com.westly.neribogroup.data.CompanyPhotos
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.MAX_STAFF_IMAGES
import com.westly.neribogroup.data.SocialMedia
import com.westly.neribogroup.data.StaffMedia
import com.westly.neribogroup.data.ToolItem
import com.westly.neribogroup.data.isValidMediaId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One waiting photo job. [type] is UPLOAD (send the local file) or DELETE (remove the cloud file).
 * [kind] says whose photo it is: a staff photo (staffId = staff id, imageId = photo id) or the profile picture of
 * a social account (staffId = account id, imageId = "profile"). Jobs saved before Socials have no kind: staff.
 */
data class MediaJob(
    val id: String,
    val type: String,
    val staffId: String,
    val imageId: String,
    val attempts: Int = 0,
    /** Do not try again before this time (back-off after a network or server problem). */
    val nextTryMillis: Long = 0L,
    /** True when the server refused it (for example "5 photos already"). Waits for a manual Sync now. */
    val failed: Boolean = false,
    val message: String = "",
    val kind: String = MediaSync.KIND_STAFF
)

/** What the photo queue looks like right now, for the screens. */
data class MediaSummary(
    /** Photos still to upload or delete. */
    val waiting: Int,
    /** Uploads the server refused. */
    val failedUploads: Int,
    /** Ids of photos that are waiting to be uploaded. */
    val uploadingImageIds: Set<String>,
    /** Ids of social accounts whose profile picture is waiting to be uploaded. */
    val uploadingSocialIds: Set<String> = emptySet()
)

/**
 * Keeps staff photos in step with the cloud's media storage (bucket neribo-media). Offline first:
 * every change is first saved on the phone and written in a small queue file (media_queue.json, next to
 * cloud_sync.json). The queue is worked through whenever a sync runs while signed in. Failed items stay and
 * are tried again later (with back-off); one bad item never blocks the others.
 *
 * Cloud path of a staff photo: <owner_uid>/staff/<staff_id>/<image_id>.jpg
 * Blocking functions: call [process] from a background thread.
 */
class MediaSync(
    context: Context,
    private val config: CloudConfig,
    private val api: SupabaseApi
) {
    private val app: Context = context.applicationContext
    private val lock = Any()
    private val busy = AtomicBoolean(false)

    // ---------------------------------------------------------------------------------------------
    // The queue (saved in filesDir/media_queue.json)
    // ---------------------------------------------------------------------------------------------

    private fun queueFile(): File = File(app.filesDir, FILE_NAME)

    private fun loadJobs(): MutableList<MediaJob> {
        val file = queueFile()
        if (!file.exists()) return ArrayList()
        return try {
            val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("jobs") ?: JSONArray()
            val list = ArrayList<MediaJob>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val type = o.optString("t")
                val staffId = o.optString("staff")
                val imageId = o.optString("image")
                val kind = if (o.optString("k") == KIND_SOCIAL) KIND_SOCIAL else KIND_STAFF
                if ((type != UPLOAD && type != DELETE) || !isValidMediaId(staffId) || !isValidMediaId(imageId)) continue
                if (kind == KIND_SOCIAL && imageId != SOCIAL_IMAGE_ID) continue
                list.add(
                    MediaJob(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        staffId = staffId,
                        imageId = imageId,
                        attempts = o.optInt("attempts"),
                        nextTryMillis = o.optLong("next"),
                        failed = o.optBoolean("failed"),
                        message = o.optString("msg"),
                        kind = kind
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "The photo queue could not be read; starting with an empty one.")
            runCatching { file.renameTo(File(app.filesDir, "media_queue.corrupt.json")) }
            ArrayList()
        }
    }

    private fun saveJobs(jobs: List<MediaJob>): Boolean {
        return try {
            val array = JSONArray()
            for (j in jobs) {
                val o = JSONObject()
                o.put("id", j.id)
                o.put("t", j.type)
                o.put("staff", j.staffId)
                o.put("image", j.imageId)
                o.put("attempts", j.attempts)
                o.put("next", j.nextTryMillis)
                o.put("failed", j.failed)
                if (j.message.isNotEmpty()) o.put("msg", j.message)
                if (j.kind == KIND_SOCIAL) o.put("k", KIND_SOCIAL)
                array.put(o)
            }
            val text = JSONObject().put("jobs", array).toString()
            val file = queueFile()
            val tmp = File(app.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (e: Exception) {
            false
        }
    }

    /** A new photo was added on the phone: send it when the cloud is available. */
    fun enqueueUploads(staffId: String, imageIds: List<String>) {
        if (imageIds.isEmpty()) return
        synchronized(lock) {
            val jobs = loadJobs()
            for (imageId in imageIds) {
                if (jobs.any { it.kind == KIND_STAFF && it.type == UPLOAD && it.staffId == staffId && it.imageId == imageId }) continue
                jobs.add(MediaJob(UUID.randomUUID().toString(), UPLOAD, staffId, imageId))
            }
            saveJobs(jobs)
        }
    }

    /**
     * A photo was removed on the phone: a waiting upload of it is cancelled, and (when the owner id is known,
     * which means the cloud may hold it) its cloud file is deleted when the cloud is available.
     */
    fun enqueueRemoval(staffId: String, imageId: String) {
        synchronized(lock) {
            val jobs = loadJobs()
            jobs.removeAll { it.kind == KIND_STAFF && it.type == UPLOAD && it.staffId == staffId && it.imageId == imageId }
            if (config.ownerUid.isNotEmpty() &&
                jobs.none { it.kind == KIND_STAFF && it.type == DELETE && it.staffId == staffId && it.imageId == imageId }
            ) {
                jobs.add(MediaJob(UUID.randomUUID().toString(), DELETE, staffId, imageId))
            }
            saveJobs(jobs)
        }
    }

    /**
     * A new profile picture was saved for a social account: send it when the cloud is available. Any waiting upload
     * or delete of this account's picture is replaced by one fresh upload (a new job id), so the newest file is
     * always the one that goes up, even when the picture is changed while an older upload is in progress.
     */
    fun enqueueSocialUpload(accountId: String) {
        synchronized(lock) {
            val jobs = loadJobs()
            jobs.removeAll { it.kind == KIND_SOCIAL && it.staffId == accountId }
            jobs.add(MediaJob(UUID.randomUUID().toString(), UPLOAD, accountId, SOCIAL_IMAGE_ID, kind = KIND_SOCIAL))
            saveJobs(jobs)
        }
    }

    /**
     * A profile picture was removed (or its account deleted): a waiting upload is cancelled, and (when the owner id
     * is known, which means the cloud may hold it) the cloud file is deleted when the cloud is available.
     */
    fun enqueueSocialRemoval(accountId: String) {
        synchronized(lock) {
            val jobs = loadJobs()
            jobs.removeAll { it.kind == KIND_SOCIAL && it.staffId == accountId }
            if (config.ownerUid.isNotEmpty()) {
                jobs.add(MediaJob(UUID.randomUUID().toString(), DELETE, accountId, SOCIAL_IMAGE_ID, kind = KIND_SOCIAL))
            }
            saveJobs(jobs)
        }
    }

    /** What is waiting right now. */
    fun summary(): MediaSummary {
        val jobs = synchronized(lock) { loadJobs() }
        return MediaSummary(
            waiting = jobs.size,
            failedUploads = jobs.count { it.type == UPLOAD && it.failed },
            uploadingImageIds = jobs.filter { it.type == UPLOAD && it.kind == KIND_STAFF }.map { it.imageId }.toSet(),
            uploadingSocialIds = jobs.filter { it.type == UPLOAD && it.kind == KIND_SOCIAL }.map { it.staffId }.toSet()
        )
    }

    private fun removeJob(id: String) {
        synchronized(lock) {
            val jobs = loadJobs()
            if (jobs.removeAll { it.id == id }) saveJobs(jobs)
        }
    }

    private fun replaceJob(job: MediaJob) {
        synchronized(lock) {
            val jobs = loadJobs()
            val index = jobs.indexOfFirst { it.id == job.id }
            if (index >= 0) {
                jobs[index] = job
                saveJobs(jobs)
            }
        }
    }

    /** "Sync now" by hand: items the server refused are given one more chance. */
    private fun clearFailedFlags() {
        synchronized(lock) {
            val jobs = loadJobs()
            if (jobs.none { it.failed }) return
            saveJobs(jobs.map { if (it.failed) it.copy(failed = false, nextTryMillis = 0L, message = "") else it })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Working through the queue, then downloading what other phones added
    // ---------------------------------------------------------------------------------------------

    /**
     * One pass. Does nothing unless the cloud is ON and the owner is signed in. Returns true when a photo file
     * on this phone changed (a download arrived), so the screen can redraw. Never throws.
     * @param manual true for the owner's own "Sync now": failed items are tried again and back-off is ignored.
     */
    fun process(ledgerNow: () -> Ledger, manual: Boolean): Boolean {
        if (config.cloudMode != CloudMode.ON || !config.signedIn) return false
        if (!busy.compareAndSet(false, true)) return false
        return try {
            run(ledgerNow, manual)
        } catch (e: Throwable) {
            Log.w(TAG, "Photo sync stopped: " + e.javaClass.simpleName)
            false
        } finally {
            busy.set(false)
        }
    }

    private fun run(ledgerNow: () -> Ledger, manual: Boolean): Boolean {
        val uid = config.ownerUid
        if (uid.isEmpty()) return false
        if (manual) clearFailedFlags()
        var changedFiles = false
        var offline = false

        // 1) Uploads and deletes written down on this phone.
        val jobs = synchronized(lock) { loadJobs() }
        val ledger = ledgerNow()
        for (job in jobs) {
            if (offline) break
            val now = System.currentTimeMillis()
            if (job.failed) continue
            if (!manual && job.nextTryMillis > now) continue
            if (job.kind == KIND_SOCIAL) {
                if (runSocialJob(job, uid, ledger, now)) offline = true
                continue
            }
            val path = "$uid/staff/${job.staffId}/${job.imageId}.jpg"
            if (job.type == UPLOAD) {
                val stillWanted = ledger.staff.firstOrNull { it.id == job.staffId }
                    ?.images?.any { it.id == job.imageId } == true
                val file = StaffMedia.file(app, job.staffId, job.imageId)
                if (!stillWanted || !file.exists()) {
                    removeJob(job.id)
                    continue
                }
                val bytes = try {
                    file.readBytes()
                } catch (e: Exception) {
                    removeJob(job.id)
                    continue
                }
                when (val r = api.uploadMedia(path, bytes, upsert = false)) {
                    is MediaResult.Ok, MediaResult.Duplicate -> removeJob(job.id)
                    MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                    MediaResult.NotFound -> replaceJob(backOff(job, now))
                    is MediaResult.Http -> replaceJob(afterHttpFailure(job, r, now))
                }
            } else {
                when (val r = api.deleteMedia(path)) {
                    is MediaResult.Ok, MediaResult.NotFound -> removeJob(job.id)
                    MediaResult.Duplicate -> removeJob(job.id)
                    MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                    is MediaResult.Http -> replaceJob(backOff(job, now).copy(message = r.snippet.take(80)))
                }
            }
        }

        // 2) Photos other phones added: the description is in the staff document but the file is missing here.
        if (!offline) {
            val latest = ledgerNow()
            outer@ for (staff in latest.staff) {
                if (!isValidMediaId(staff.id)) continue
                for (img in staff.images.take(MAX_STAFF_IMAGES)) {
                    if (!isValidMediaId(img.id)) continue
                    val file = StaffMedia.file(app, staff.id, img.id)
                    if (file.exists()) continue
                    when (val r = api.downloadMedia("$uid/staff/${staff.id}/${img.id}.jpg")) {
                        is MediaResult.Ok -> {
                            // The file must be exactly the one that was described, or it is thrown away.
                            if (img.sha256.isNotEmpty() && sha256Hex(r.bytes) != img.sha256) {
                                Log.w(TAG, "A downloaded photo did not match its checksum; it was discarded.")
                            } else if (StaffMedia.write(app, staff.id, img.id, r.bytes)) {
                                changedFiles = true
                            }
                        }
                        // The other phone may not have finished uploading yet: ask again at the next sync.
                        MediaResult.NotFound, MediaResult.Duplicate -> {}
                        MediaResult.Offline, MediaResult.AuthExpired -> {
                            offline = true
                            break@outer
                        }
                        is MediaResult.Http -> {}
                    }
                }
            }
        }

        // 3) Profile pictures other phones added or replaced: the version in the socials document is newer than the
        // file here (or the file is missing).
        if (!offline) {
            val latest = ledgerNow()
            val uploading = synchronized(lock) { loadJobs() }
                .filter { it.kind == KIND_SOCIAL && it.type == UPLOAD }.map { it.staffId }.toSet()
            for (account in latest.socials.accounts) {
                val image = account.profileImage ?: continue
                if (!isValidMediaId(account.id)) continue
                // This phone's own newer picture is still going up: never replace it with an older one.
                if (account.id in uploading) continue
                val file = SocialMedia.file(app, account.id)
                if (file.exists() && SocialMedia.readVersion(app, account.id) >= image.version) continue
                when (val r = api.downloadMedia("$uid/socials/${account.id}/profile.jpg")) {
                    is MediaResult.Ok -> {
                        // The file must be exactly the one that was described, or it is thrown away.
                        if (image.sha256.isNotEmpty() && sha256Hex(r.bytes) != image.sha256) {
                            Log.w(TAG, "A downloaded profile picture did not match its checksum; it was discarded.")
                        } else if (SocialMedia.write(app, account.id, r.bytes, image.version)) {
                            changedFiles = true
                        }
                    }
                    // The other phone may not have finished uploading yet: ask again at the next sync.
                    MediaResult.NotFound, MediaResult.Duplicate -> {}
                    MediaResult.Offline, MediaResult.AuthExpired -> {
                        offline = true
                        break
                    }
                    is MediaResult.Http -> {}
                }
            }
        }

        // 4) Equipment photos: send new or changed ones, fetch the ones this phone is missing (for example after a
        // reset), and remove the cloud copy of photos removed here.
        if (!offline) {
            if (syncToolPhotos(uid, ledgerNow())) changedFiles = true
        }

        cleanLeftovers(ledgerNow())
        cleanSocialLeftovers(ledgerNow())
        return changedFiles
    }

    // ---------------------------------------------------------------------------------------------
    // Equipment photos
    // ---------------------------------------------------------------------------------------------

    private fun toolStateFile(): File = File(app.filesDir, TOOL_STATE_FILE)

    /**
     * For each piece of equipment: the SHA-256 of the photo this phone last sent to the cloud, for photo 0 to 4
     * ("" = nothing sent for that place). Saved in tool_photo_sync.json.
     */
    private fun loadToolState(): MutableMap<String, MutableList<String>> {
        val result = HashMap<String, MutableList<String>>()
        val file = toolStateFile()
        if (!file.exists()) return result
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                if (!isValidMediaId(id)) continue
                val array = root.optJSONArray(id) ?: continue
                val list = ArrayList<String>()
                for (i in 0 until ToolItem.MAX_PHOTOS) list.add(array.optString(i, ""))
                result[id] = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "The equipment photo record could not be read; starting with an empty one.")
            result.clear()
        }
        return result
    }

    private fun saveToolState(state: Map<String, List<String>>) {
        try {
            val root = JSONObject()
            for ((id, list) in state) {
                if (list.all { it.isEmpty() }) continue
                val array = JSONArray()
                for (sha in list) array.put(sha)
                root.put(id, array)
            }
            val file = toolStateFile()
            val tmp = File(app.filesDir, "$TOOL_STATE_FILE.tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (e: Exception) {
            // The next sync simply sends the photos again.
        }
    }

    /**
     * Keeps the equipment photos in step with the cloud. Cloud path: <owner_uid>/tool/<equipment_id>/<n>.jpg where
     * n is 0 to 4, the same place the photo has on this phone. Returns true when a photo file on this phone changed
     * (a download arrived). Stops quietly when the cloud cannot be reached.
     */
    private fun syncToolPhotos(uid: String, ledger: Ledger): Boolean {
        var changed = false
        var dirty = false
        var offline = false
        val state = loadToolState()
        val places = ToolItem.MAX_PHOTOS

        for (tool in ledger.assets.tools) {
            if (offline) break
            if (!isValidMediaId(tool.id)) continue
            val sent = state.getOrPut(tool.id) { MutableList(places) { "" } }
            val count = tool.photoCount
            for (index in 0 until places) {
                if (offline) break
                val file = CompanyPhotos.fileAt(app, "tool", tool.id, index)
                val path = "$uid/tool/${tool.id}/$index.jpg"
                if (index < count) {
                    if (file.exists()) {
                        // On this phone: send it when it is new or different from what was sent before.
                        val bytes = try {
                            file.readBytes()
                        } catch (e: Exception) {
                            continue
                        }
                        val sha = sha256Hex(bytes)
                        if (sent[index] == sha) continue
                        when (api.uploadMedia(path, bytes, upsert = true)) {
                            is MediaResult.Ok, MediaResult.Duplicate -> {
                                sent[index] = sha
                                dirty = true
                            }
                            MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                            MediaResult.NotFound -> {}
                            is MediaResult.Http -> {}
                        }
                    } else if (sent[index].isEmpty()) {
                        // Missing here and never sent from this phone (for example after a reset): fetch it.
                        // A place this phone did send before is never fetched back: it was removed here.
                        when (val r = api.downloadMedia(path)) {
                            is MediaResult.Ok -> {
                                if (CompanyPhotos.writeAt(app, "tool", tool.id, index, r.bytes)) {
                                    sent[index] = sha256Hex(r.bytes)
                                    dirty = true
                                    changed = true
                                }
                            }
                            MediaResult.NotFound, MediaResult.Duplicate -> {}
                            MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                            is MediaResult.Http -> {}
                        }
                    }
                } else if (sent[index].isNotEmpty() && !file.exists()) {
                    // The photo was removed on this phone: remove its cloud copy.
                    when (api.deleteMedia(path)) {
                        is MediaResult.Ok, MediaResult.NotFound, MediaResult.Duplicate -> {
                            sent[index] = ""
                            dirty = true
                        }
                        MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                        is MediaResult.Http -> {}
                    }
                }
            }
        }

        // Equipment that was deleted on this phone: its cloud photos go too, but only once no photo file of it is
        // left on this phone, so a ledger that is briefly out of date never removes photos that are still here.
        val liveIds = ledger.assets.tools.map { it.id }.toSet()
        for (id in state.keys.toList()) {
            if (offline) break
            if (id in liveIds) continue
            val sent = state[id] ?: continue
            for (index in 0 until places) {
                if (offline) break
                if (sent[index].isEmpty()) continue
                if (CompanyPhotos.fileAt(app, "tool", id, index).exists()) continue
                when (api.deleteMedia("$uid/tool/$id/$index.jpg")) {
                    is MediaResult.Ok, MediaResult.NotFound, MediaResult.Duplicate -> {
                        sent[index] = ""
                        dirty = true
                    }
                    MediaResult.Offline, MediaResult.AuthExpired -> offline = true
                    is MediaResult.Http -> {}
                }
            }
            if (sent.all { it.isEmpty() }) {
                state.remove(id)
                dirty = true
            }
        }

        if (dirty) saveToolState(state)
        return changed
    }

    /**
     * One waiting profile-picture job. Returns true when the cloud cannot be reached (so the pass stops).
     * Cloud path: <owner_uid>/socials/<account_id>/profile.jpg (an upload replaces the old picture, x-upsert true).
     */
    private fun runSocialJob(job: MediaJob, uid: String, ledger: Ledger, now: Long): Boolean {
        val path = "$uid/socials/${job.staffId}/profile.jpg"
        val account = ledger.socials.accounts.firstOrNull { it.id == job.staffId }
        val image = account?.profileImage
        if (job.type == UPLOAD) {
            val file = SocialMedia.file(app, job.staffId)
            if (image == null || !file.exists()) {
                removeJob(job.id)
                return false
            }
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                removeJob(job.id)
                return false
            }
            // The file must be the picture the socials document describes. If it is not, the picture was changed
            // (a fresh job exists) or another phone's newer picture won: this upload is no longer wanted.
            if (image.sha256.isNotEmpty() && sha256Hex(bytes) != image.sha256) {
                removeJob(job.id)
                return false
            }
            when (val r = api.uploadMedia(path, bytes, upsert = true)) {
                is MediaResult.Ok, MediaResult.Duplicate -> removeJob(job.id)
                MediaResult.Offline, MediaResult.AuthExpired -> return true
                MediaResult.NotFound -> replaceJob(backOff(job, now))
                is MediaResult.Http -> replaceJob(afterHttpFailure(job, r, now))
            }
            return false
        }
        // DELETE: never remove the cloud file of an account that has a picture again.
        if (image != null) {
            removeJob(job.id)
            return false
        }
        when (val r = api.deleteMedia(path)) {
            is MediaResult.Ok, MediaResult.NotFound, MediaResult.Duplicate -> removeJob(job.id)
            MediaResult.Offline, MediaResult.AuthExpired -> return true
            is MediaResult.Http -> replaceJob(backOff(job, now).copy(message = r.snippet.take(80)))
        }
        return false
    }

    /** Removes profile picture files the ledger no longer describes (deleted accounts, pictures removed elsewhere). */
    private fun cleanSocialLeftovers(ledger: Ledger) {
        try {
            val folders = SocialMedia.root(app).listFiles() ?: return
            val cutoff = System.currentTimeMillis() - LEFTOVER_AGE_MILLIS
            for (folder in folders) {
                if (!folder.isDirectory) continue
                val hasPicture = ledger.socials.accounts.firstOrNull { it.id == folder.name }?.profileImage != null
                for (file in folder.listFiles() ?: emptyArray()) {
                    // A young file may belong to a picture that is only now being saved: leave it alone.
                    if (file.lastModified() > cutoff) continue
                    val keep = hasPicture && (file.name == "profile.jpg" || file.name == "profile.ver")
                    if (!keep) file.delete()
                }
                if ((folder.list()?.isEmpty()) == true) folder.delete()
            }
        } catch (e: Exception) {
            // Cleaning up is optional.
        }
    }

    private fun backOff(job: MediaJob, now: Long): MediaJob {
        val attempts = job.attempts + 1
        val wait = minOf(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS shl minOf(attempts, 7))
        return job.copy(attempts = attempts, nextTryMillis = now + wait)
    }

    /** 5xx, 408 and 429 are temporary (back off). Any other refusal (for example "5 photos already") stops retrying. */
    private fun afterHttpFailure(job: MediaJob, r: MediaResult.Http, now: Long): MediaJob {
        val temporary = r.code <= 0 || r.code >= 500 || r.code == 408 || r.code == 429
        return if (temporary) {
            backOff(job, now)
        } else {
            job.copy(failed = true, attempts = job.attempts + 1, message = r.snippet.take(80))
        }
    }

    /** Removes photo files the ledger no longer describes (deleted staff, photos removed on another phone). */
    private fun cleanLeftovers(ledger: Ledger) {
        try {
            val root = StaffMedia.root(app)
            val folders = root.listFiles() ?: return
            val cutoff = System.currentTimeMillis() - LEFTOVER_AGE_MILLIS
            for (folder in folders) {
                if (!folder.isDirectory) continue
                val staff = ledger.staff.firstOrNull { it.id == folder.name }
                val wanted = staff?.images?.map { it.id }?.toSet() ?: emptySet()
                for (file in folder.listFiles() ?: emptyArray()) {
                    // A young file may belong to a photo that is only now being saved: leave it alone.
                    if (file.lastModified() > cutoff) continue
                    val name = file.name
                    val keep = name.endsWith(".jpg") && name.removeSuffix(".jpg") in wanted
                    if (!keep) file.delete()
                }
                if ((folder.list()?.isEmpty()) == true) folder.delete()
            }
        } catch (e: Exception) {
            // Cleaning up is optional.
        }
    }

    companion object {
        private const val TAG = "MediaSync"
        private const val FILE_NAME = "media_queue.json"
        private const val TOOL_STATE_FILE = "tool_photo_sync.json"
        const val UPLOAD = "UPLOAD"
        const val DELETE = "DELETE"
        const val KIND_STAFF = "staff"
        const val KIND_SOCIAL = "social"
        /** The image id used in the queue for a social profile picture (there is only one per account). */
        const val SOCIAL_IMAGE_ID = "profile"
        private const val BASE_BACKOFF_MILLIS = 30_000L
        private const val MAX_BACKOFF_MILLIS = 3_600_000L
        private const val LEFTOVER_AGE_MILLIS = 10 * 60_000L
    }
}
