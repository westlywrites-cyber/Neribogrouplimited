package com.westly.neribogroup.cloud

import com.westly.neribogroup.data.SOCIALS_APP_ID
import org.json.JSONObject

/**
 * One old SOCIAL_ROLL job as read from the cloud. Socials are not rolled by Claude any more (the backend rolls
 * them), so these rows are only found to be closed: an answer that is still waiting is marked as done.
 */
class SocialRow(
    val id: String,
    val dedupeKey: String,
    val dayKey: String,
    val status: String,
    val result: JSONObject?,
    val context: JSONObject?
)

object SocialAiRules {

    /** The job kind of the old way. Nothing publishes it any more. */
    const val KIND = "SOCIAL_ROLL"

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    /** Reads the old social rows of a list_jobs answer. */
    fun parseRows(json: JSONObject): List<SocialRow> {
        val array = json.optJSONArray("jobs") ?: return emptyList()
        val rows = ArrayList<SocialRow>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (text(o, "kind") != KIND) continue
            if (text(o, "app_id") != SOCIALS_APP_ID) continue
            val id = text(o, "id")
            val key = text(o, "dedupe_key")
            if (id.isEmpty() || key.isEmpty()) continue
            rows.add(
                SocialRow(
                    id = id,
                    dedupeKey = key,
                    dayKey = text(o, "day_key"),
                    status = text(o, "status"),
                    result = o.optJSONObject("result"),
                    context = o.optJSONObject("context")
                )
            )
        }
        return rows
    }
}
