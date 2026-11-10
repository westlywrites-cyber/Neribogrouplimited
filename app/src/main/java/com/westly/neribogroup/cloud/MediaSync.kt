package com.westly.neribogroup.cloud

import android.content.Context
import android.util.Log
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.MAX_STAFF_IMAGES
import com.westly.neribogroup.data.StaffMedia
import com.westly.neribogroup.data.isValidMediaId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One waiting photo job. [type] is UPLOAD (send the local file) or DELETE (remove the cloud file). */
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
    val message: String = ""
)

/** What the photo queue looks like right now, for the screens. */
data class MediaSummary(
    /** Photos still to upload or delete. */
    val waiting: Int,
    /** Uploads the server refused. */
    val failedUploads: Int,
    /** Ids of photos that are waiting to be uploaded. */
    val uploadingImageIds: Set<String>
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
                if ((type != UPLOAD && type != DELETE) || !isValidMediaId(staffId) || !isValidMediaId(imageId)) continue
                list.add(
                    MediaJob(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        staffId = staffId,
                        imageId = imageId,
                        attempts = o.optInt("attempts"),
                        nextTryMillis = o.optLong("next"),
                        failed = o.optBoolean("failed"),
                        message = o.optString("msg")
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
                if (jobs.any { it.type == UPLOAD && it.staffId == staffId && it.imageId == imageId }) continue
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
            jobs.removeAll { it.type == UPLOAD && it.staffId == staffId && it.imageId == imageId }
            if (config.ownerUid.isNotEmpty() &&
                jobs.none { it.type == DELETE && it.staffId == staffId && it.imageId == imageId }
            ) {
                jobs.add(MediaJob(UUID.randomUUID().toString(), DELETE, staffId, imageId))
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
            uploadingImageIds = jobs.filter { it.type == UPLOAD }.map { it.imageId }.toSet()
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

        cleanLeftovers(ledgerNow())
        return changedFiles
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
        const val UPLOAD = "UPLOAD"
        const val DELETE = "DELETE"
        private const val BASE_BACKOFF_MILLIS = 30_000L
        private const val MAX_BACKOFF_MILLIS = 3_600_000L
        private const val LEFTOVER_AGE_MILLIS = 10 * 60_000L
    }
}
