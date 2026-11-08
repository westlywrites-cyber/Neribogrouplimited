package com.westly.neribogroup.cloud

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Cloud card, the dot and the other screens read. */
enum class CloudStatus { NOT_CONNECTED, SYNCING, UP_TO_DATE, WAITING_INTERNET, SIGN_IN_AGAIN, PROBLEM }

/** What to do with one pulled document. */
enum class SyncDecision { CONFLICT, APPLY, NOTHING }

/**
 * One conflict kept in cloud_sync.json ("conflicts"). [file] is the name of the ledger.conflict-*.json copy
 * that holds the phone's version. [notified] is false until the dialog (3B-1b) has been shown once.
 */
data class SyncConflict(
    val key: String,
    val cloudVersion: Int,
    val file: String,
    val handled: Boolean,
    val notified: Boolean,
    val createdMillis: Long
) {
    fun toJsonText(): String {
        val o = JSONObject()
        o.put("key", key)
        o.put("cloudVersion", cloudVersion)
        o.put("file", file)
        o.put("handled", handled)
        o.put("notified", notified)
        o.put("createdMillis", createdMillis)
        return o.toString()
    }

    companion object {
        /** Null when the text is not a conflict entry. */
        fun fromJsonText(text: String): SyncConflict? {
            return try {
                val o = JSONObject(text)
                val key = o.optString("key", "")
                if (key.isEmpty()) {
                    null
                } else {
                    SyncConflict(
                        key = key,
                        cloudVersion = o.optInt("cloudVersion", 0),
                        file = o.optString("file", ""),
                        handled = o.optBoolean("handled", false),
                        notified = o.optBoolean("notified", false),
                        createdMillis = o.optLong("createdMillis", 0L)
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** A conflict as the screens see it: the document in friendly words. */
data class CloudConflictInfo(
    val key: String,
    val friendlyName: String,
    val cloudVersion: Int,
    val fileName: String,
    val notified: Boolean
)

/** Pure rules of the sync. No Android, no network: easy to reason about. */
object SyncRules {

    const val MAX_CONFLICT_COPIES = 10

    /** dirty + cloud newer = CONFLICT; clean + cloud newer = APPLY; anything else = NOTHING. */
    fun decide(localDirty: Boolean, localVersion: Int, cloudVersion: Int): SyncDecision {
        if (cloudVersion <= localVersion) return SyncDecision.NOTHING
        return if (localDirty) SyncDecision.CONFLICT else SyncDecision.APPLY
    }

    /**
     * Seconds to wait before the next try after [failureCount] failures in a row:
     * 30 s, 60 s, 2 min, 5 min, then 10 min. 0 when there was no failure.
     */
    fun backoffDelayMillis(failureCount: Int): Long = when {
        failureCount <= 0 -> 0L
        failureCount == 1 -> 30_000L
        failureCount == 2 -> 60_000L
        failureCount == 3 -> 120_000L
        failureCount == 4 -> 300_000L
        else -> 600_000L
    }

    /**
     * Is this document changed on the phone since the last agreement with the cloud?
     * [localSha] is the hash of the phone's text, or null when the phone has no such document
     * (an app that was removed). A removed app counts as changed until its removal is pushed.
     */
    fun isDirty(localSha: String?, state: DocState?, isApp: Boolean): Boolean {
        if (localSha == null) {
            return isApp && state != null && !state.deleted
        }
        return state == null || state.sha256 != localSha
    }

    fun conflictFileName(millis: Long, attempt: Int): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(millis))
        return if (attempt <= 1) "ledger.conflict-$stamp.json" else "ledger.conflict-$stamp-$attempt.json"
    }

    /** [appNames] maps a document key ("app:xyz") to the app's name. */
    fun friendlyDocName(key: String, appNames: Map<String, String>): String = when (key) {
        LedgerSplitter.DOC_COMPANY -> "Company details"
        LedgerSplitter.DOC_STAFF -> "Staff"
        LedgerSplitter.DOC_CASES -> "Cases"
        LedgerSplitter.DOC_SOFTWARE -> "Software"
        else -> appNames[key] ?: ("App " + key.removePrefix(LedgerSplitter.APP_PREFIX))
    }
}
