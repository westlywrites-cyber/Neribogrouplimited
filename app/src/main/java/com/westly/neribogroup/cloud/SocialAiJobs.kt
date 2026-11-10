package com.westly.neribogroup.cloud

import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.PlatformBase
import com.westly.neribogroup.data.SOCIALS_APP_ID
import com.westly.neribogroup.data.SocialsEngine
import org.json.JSONArray
import org.json.JSONObject

/** One SOCIAL_ROLL job as read from the cloud. */
class SocialRow(
    val id: String,
    val dedupeKey: String,
    val dayKey: String,
    val status: String,
    val result: JSONObject?,
    val context: JSONObject?
) {
    /** The attempt number 1 to 3 taken from the dedupe key, or 0 when it is not readable. */
    val attempt: Int get() = SocialAiRules.attemptOfDedupeKey(dedupeKey) ?: 0
}

/** The check of one answer: [faces] maps a platform key to its face when [ok]. */
class SocialCheck(val ok: Boolean, val reason: String, val faces: Map<String, Int>)

/**
 * Delivery C-2: the daily social roll. One SOCIAL_ROLL job per day that needs rolling and has a base
 * above 0. Claude answers one face 0 to 5 per platform; the app turns the faces into followers
 * (SocialDice). Everything here follows SHARED CONTRACT v1 literally.
 */
object SocialAiRules {

    const val KIND = "SOCIAL_ROLL"
    const val PRIORITY = 120
    const val SORT_ORDER = 0
    const val MAX_ATTEMPTS = 3

    /** Catch-up covers at most 30 days, so an answer for a day up to 30 days old is still used. */
    const val MAX_AGE_DAYS = 30L

    private const val CONTEXT_LIMIT_BYTES = 6000
    private const val CELEBRITIES_IN_CONTEXT = 8
    private const val NAME_LIMIT = 60

    /** The text of the contract appendix, sent in every job context. */
    const val INSTRUCTIONS =
        "SOCIAL_ROLL is the daily follower roll for the company's social media accounts. " +
            "The job context has: day, dice (the meaning of faces 0 to 5), and platforms (a list; each item has key, name, baseFollowers, celebrities). " +
            "Roll ONE die per platform, honestly and independently, with a whole number from 0 to 5. " +
            "Submit {platforms:[{key, face}], comment (optional)} through submit_result. " +
            "Rules: 1. Return exactly one item for every platform in the context, no platform twice, no platform missing, and use the key exactly as given. " +
            "2. Do not choose faces to be generous or stingy. Do not try to make a platform grow more or less; roll as a fair die would, and different platforms may get different faces. " +
            "3. Do not calculate followers; the app does that from your faces. " +
            "4. Do not add any other field. comment, if you write one, is at most 200 characters, plain text, no at sign and no web address."

    /** The result_schema of the contract, exactly. */
    private const val RESULT_SCHEMA_TEXT =
        "{\"type\":\"object\",\"properties\":{\"platforms\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":20," +
            "\"items\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":40}," +
            "\"face\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":5}},\"required\":[\"key\",\"face\"]," +
            "\"additionalProperties\":false}},\"comment\":{\"type\":\"string\",\"maxLength\":200}}," +
            "\"required\":[\"platforms\"],\"additionalProperties\":false}"

    fun resultSchema(): JSONObject = JSONObject(RESULT_SCHEMA_TEXT)

    fun dedupeKey(day: String, attempt: Int): String = day + ":" + SOCIALS_APP_ID + ":" + KIND + ":" + attempt

    fun attemptOfDedupeKey(key: String): Int? = key.substringAfterLast(':').toIntOrNull()

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    /** Reads the social rows of a list_jobs answer. */
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

    // ---- The job ----

    private fun diceJson(): JSONObject {
        val table = JSONArray()
        table.put(JSONArray().put(0).put(0))
        table.put(JSONArray().put(20).put(60))
        table.put(JSONArray().put(60).put(140))
        table.put(JSONArray().put(140).put(260))
        table.put(JSONArray().put(260).put(400))
        table.put(JSONArray().put(400).put(600))
        return JSONObject()
            .put("faces", "0-5")
            .put("dailyGainPartsPerMillionOfBase", table)
            .put(
                "note",
                "Each platform has its own die. The face only picks a range of the day's gain for that platform; the app turns it into a number."
            )
    }

    private fun clip(text: String): String = if (text.length <= NAME_LIMIT) text else text.substring(0, NAME_LIMIT)

    private fun contextWith(day: String, bases: List<PlatformBase>, celebrityLimit: Int): JSONObject {
        val platforms = JSONArray()
        for (b in bases) {
            val celebrities = JSONArray()
            for (c in b.celebrities.take(celebrityLimit)) {
                celebrities.put(JSONObject().put("name", clip(c.name)).put("followers", c.followers))
            }
            platforms.put(
                JSONObject()
                    .put("key", b.key)
                    .put("name", clip(b.name))
                    .put("baseFollowers", b.base)
                    .put("celebrities", celebrities)
            )
        }
        return JSONObject()
            .put("job", KIND)
            .put("company", "Neribo Group")
            .put("app", JSONObject().put("name", "Socials").put("id", SOCIALS_APP_ID))
            .put("day", day)
            .put("dice", diceJson())
            .put("platforms", platforms)
            .put("instructions", INSTRUCTIONS)
    }

    /**
     * The context of the job of [day]: only platforms with a base above 0. Each celebrity list keeps the 8
     * biggest; when the context is still over 6,000 bytes the lists are dropped entirely
     * (baseFollowers is always the full number).
     */
    fun contextFor(day: String, bases: List<PlatformBase>): JSONObject {
        val trimmed = contextWith(day, bases, CELEBRITIES_IN_CONTEXT)
        if (trimmed.toString().toByteArray(Charsets.UTF_8).size <= CONTEXT_LIMIT_BYTES) return trimmed
        return contextWith(day, bases, 0)
    }

    /** The job of [day] for [attempt] (1 to 3), or null when no platform has a base above 0 that day. */
    fun buildJob(ledger: Ledger, day: String, attempt: Int): JSONObject? {
        val bases = SocialsEngine.basesOn(ledger, day).filter { it.base > 0L }
        if (bases.isEmpty()) return null
        return JSONObject()
            .put("dedupe_key", dedupeKey(day, attempt))
            .put("kind", KIND)
            .put("app_id", SOCIALS_APP_ID)
            .put("day_key", day)
            .put("priority", PRIORITY)
            .put("sort_order", SORT_ORDER)
            .put("context", contextFor(day, bases))
            .put("result_schema", resultSchema())
    }

    /** The platform keys inside a job context, or null when the context has no readable platforms. */
    fun keysOf(context: JSONObject?): List<String>? {
        val array = context?.optJSONArray("platforms") ?: return null
        val keys = ArrayList<String>()
        for (i in 0 until array.length()) {
            val key = array.optJSONObject(i)?.optString("key", "").orEmpty()
            if (key.isEmpty()) return null
            keys.add(key)
        }
        return if (keys.isEmpty()) null else keys
    }

    // ---- Checking an answer (the server schema is not trusted alone) ----

    private fun bad(reason: String) = SocialCheck(false, reason, emptyMap())

    /**
     * Applies only an answer that has exactly the keys of the job once each, a whole-number face 0 to 5 for
     * each, and no extra field anywhere.
     */
    fun validate(expectedKeys: Set<String>, result: JSONObject?): SocialCheck {
        if (result == null) return bad("no answer")
        for (name in result.keys()) {
            if (name != "platforms" && name != "comment") return bad("extra field")
        }
        if (result.has("comment") && !result.isNull("comment")) {
            val comment = result.opt("comment") as? String ?: return bad("comment is not text")
            if (comment.length > 200) return bad("comment too long")
        }
        val list = result.opt("platforms") as? JSONArray ?: return bad("platforms is not a list")
        if (list.length() != expectedKeys.size || list.length() < 1) return bad("wrong number of platforms")
        val faces = LinkedHashMap<String, Int>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: return bad("platform is not an object")
            for (name in item.keys()) {
                if (name != "key" && name != "face") return bad("extra field")
            }
            val key = item.opt("key") as? String ?: return bad("key is not text")
            if (key !in expectedKeys) return bad("unknown platform")
            if (faces.containsKey(key)) return bad("platform twice")
            val number = item.opt("face") as? Number ?: return bad("face is not a number")
            val d = number.toDouble()
            if (d != Math.floor(d) || d < 0.0 || d > 5.0) return bad("face out of range")
            faces[key] = d.toInt()
        }
        if (faces.size != expectedKeys.size) return bad("platform missing")
        return SocialCheck(true, "", faces)
    }
}
