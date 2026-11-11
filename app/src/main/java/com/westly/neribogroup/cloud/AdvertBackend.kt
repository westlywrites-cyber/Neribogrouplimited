package com.westly.neribogroup.cloud

import com.westly.neribogroup.data.Advert
import com.westly.neribogroup.data.AdvertAnswer
import com.westly.neribogroup.data.AdvertDay
import com.westly.neribogroup.data.AdvertEngine
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.PlatformBase
import org.json.JSONArray
import org.json.JSONObject

/** The backend's answers for advert days. [failure] is null when the call worked. */
class AdvertFetch(val answers: List<AdvertAnswer>, val failure: ApiResult? = null)

/** The backend's answer for one Socials day: followers gained per platform key, and Claude's comment if it is already written. */
class SocialFetch(val gains: Map<String, Long>, val comment: String, val failure: ApiResult? = null)

/** Claude's comments written since. Adverts: "advertId|dayKey" to comment. Socials: dayKey to comment. */
class NotesFetch(
    val advertComments: Map<String, String>,
    val socialComments: Map<String, String>,
    val failure: ApiResult? = null
)

/**
 * Talks to the backend about adverts and Socials. The backend rolls the days with a real random face and its own
 * secret rules and answers with final numbers only. Nothing here (and nothing the app saves) knows a face, a rate,
 * a table or how a Blog differs from a celebrity: the app only says WHO the advert is and WHICH day it wants.
 */
object AdvertBackend {

    private const val MAX_PER_CALL = 60

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    private val badAnswer = ApiResult.Http(-1, "unexpected answer")

    /** Asks for every advert day of [app] that is still unanswered up to [today]. Days already known are never asked again. */
    fun fetchAdvertDays(api: SupabaseApi, deviceId: String, app: AppData, today: String): AdvertFetch {
        val asks = ArrayList<Pair<Advert, String>>()
        for (advert in app.adverts) {
            for (day in AdvertEngine.daysToAsk(advert, today)) asks.add(Pair(advert, day))
        }
        if (asks.isEmpty()) return AdvertFetch(emptyList())

        val answers = ArrayList<AdvertAnswer>()
        var index = 0
        while (index < asks.size) {
            val end = minOf(asks.size, index + MAX_PER_CALL)
            val chunk = asks.subList(index, end)
            index = end
            val list = JSONArray()
            for ((advert, day) in chunk) {
                list.put(
                    JSONObject()
                        .put("advert_id", advert.id)
                        .put("app_id", app.id)
                        .put("app_name", app.name)
                        .put("kind", advert.type.name)
                        .put("celebrity", advert.celebrityName)
                        .put("followers", advert.totalFollowers)
                        .put("total_days", AdvertEngine.lengthDays(advert))
                        .put("start_day", advert.startDayKey)
                        .put("day_key", day)
                )
            }
            val r = api.rpc(
                "advert_roll_days",
                JSONObject().put("p_device_id", deviceId).put("p_requests", list)
            )
            if (r !is ApiResult.Ok) return AdvertFetch(answers, r)
            val results = r.json.optJSONArray("results") ?: return AdvertFetch(answers, badAnswer)
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val id = o.optString("advert_id")
                val day = o.optString("day_key")
                if (id.isBlank() || day.isBlank()) continue
                answers.add(
                    AdvertAnswer(
                        id,
                        AdvertDay(
                            dayKey = day,
                            users = o.optLong("users"),
                            premium = o.optLong("premium"),
                            pro = o.optLong("pro"),
                            comment = text(o, "comment")
                        )
                    )
                )
            }
        }
        return AdvertFetch(answers)
    }

    /** Asks for one Socials day. Only platforms with a base above 0 are sent. */
    fun fetchSocialDay(api: SupabaseApi, deviceId: String, day: String, bases: List<PlatformBase>): SocialFetch {
        val list = JSONArray()
        for (b in bases) {
            if (b.base <= 0L) continue
            list.put(JSONObject().put("key", b.key).put("name", b.name).put("base", b.base))
        }
        if (list.length() == 0) return SocialFetch(emptyMap(), "")
        val r = api.rpc(
            "social_roll_day",
            JSONObject().put("p_device_id", deviceId).put("p_day", day).put("p_platforms", list)
        )
        if (r !is ApiResult.Ok) return SocialFetch(emptyMap(), "", r)
        val platforms = r.json.optJSONArray("platforms") ?: return SocialFetch(emptyMap(), "", badAnswer)
        val gains = LinkedHashMap<String, Long>()
        for (i in 0 until platforms.length()) {
            val o = platforms.optJSONObject(i) ?: continue
            val key = o.optString("key")
            if (key.isBlank()) continue
            gains[key] = o.optLong("gain")
        }
        return SocialFetch(gains, text(r.json, "comment"))
    }

    /** Reads the comments Claude has written lately (adverts and Socials). */
    fun fetchNotes(api: SupabaseApi): NotesFetch {
        val r = api.rpc("roll_notes", JSONObject())
        if (r !is ApiResult.Ok) return NotesFetch(emptyMap(), emptyMap(), r)
        val adverts = LinkedHashMap<String, String>()
        val socials = LinkedHashMap<String, String>()
        val a = r.json.optJSONArray("adverts")
        if (a != null) {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val comment = text(o, "comment")
                if (comment.isEmpty()) continue
                adverts[o.optString("advert_id") + "|" + o.optString("day_key")] = comment
            }
        }
        val s = r.json.optJSONArray("socials")
        if (s != null) {
            for (i in 0 until s.length()) {
                val o = s.optJSONObject(i) ?: continue
                val comment = text(o, "comment")
                if (comment.isEmpty()) continue
                socials[o.optString("day_key")] = comment
            }
        }
        return NotesFetch(adverts, socials)
    }
}
