package com.westly.neribogroup.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max

/** The id used for the Socials "app" in cloud jobs (used from Delivery C-2). */
const val SOCIALS_APP_ID = "socials"

/** The most social accounts the owner can have. */
const val MAX_SOCIAL_ACCOUNTS = 20

/** The name adverts use for "not one of the listed platforms". No account can have it. */
const val RESERVED_PLATFORM_NAME = "Other"

const val DEFAULT_SOCIAL_DISPLAY_NAME = "Neribo Group"
const val MAX_SOCIAL_HISTORY = 90
const val MAX_SOCIAL_ROLLS = 60

/** Fixed ids of the four accounts made the first time. Two phones that seed alone never make duplicates. */
const val SOCIAL_ID_FACEBOOK = "social-facebook"
const val SOCIAL_ID_X = "social-x"
const val SOCIAL_ID_INSTAGRAM = "social-instagram"
const val SOCIAL_ID_TIKTOK = "social-tiktok"

/** The picture of an account. [version] grows each time the picture changes (used from Delivery C-2). */
data class SocialImage(
    val version: Int,
    val sizeBytes: Long,
    val sha256: String
)

/** One celebrity and the followers they have on one platform. */
data class SocialCelebrity(
    val name: String,
    val followers: Long
)

/** One day's roll of one platform. */
data class SocialRoll(
    val dayKey: String,
    val base: Long,
    val gain: Long,
    val celebrities: List<SocialCelebrity> = emptyList(),
    /** Claude's short comment about the day. Empty until it is written. */
    val comment: String = ""
)

/**
 * One social media account of the company. [followers] only ever changes through the social dice
 * (Delivery C-2); it can never be typed.
 */
data class SocialAccount(
    val id: String,
    /** The platform name. Never changes after creation because adverts refer to it. */
    val name: String,
    val handle: String = "",
    val displayName: String = DEFAULT_SOCIAL_DISPLAY_NAME,
    val followers: Long = 0L,
    val following: Long = 0L,
    val posts: Long = 0L,
    val likes: Long = 0L,
    val profileImage: SocialImage? = null,
    val createdMillis: Long = 0L,
    /** The followers gained on each day, the last 90 days. */
    val history: List<DayCount> = emptyList(),
    /** The last 60 rolls of this platform. */
    val rolls: List<SocialRoll> = emptyList()
)

/**
 * Everything of the Socials section. [startDayKey] is a floor: Socials never makes followers for days
 * before it. [lastRollDayKey] is the last day that was applied. [seeded] stays true after the first
 * time, so deleted accounts never come back.
 */
data class SocialsData(
    val startDayKey: String = "",
    val lastRollDayKey: String = "",
    val seeded: Boolean = false,
    val accounts: List<SocialAccount> = emptyList()
)

/** The edited fields of one account (the name and the followers are never edited). */
data class SocialEdit(
    val id: String,
    val handle: String,
    val displayName: String,
    val following: Long,
    val posts: Long,
    val likes: Long
)

/** The numbers an account card shows. */
enum class SocialStat(val label: String) {
    POSTS("Posts"), FOLLOWERS("Followers"), FOLLOWING("Following"), LIKES("Likes")
}

/** One advert that counts on a day, with the name of its app. */
data class CountingAdvert(val appName: String, val advert: Advert)

/** The follower base of one platform on one day. */
data class PlatformBase(
    val key: String,
    val name: String,
    val base: Long,
    /** Biggest first. */
    val celebrities: List<SocialCelebrity>
)

enum class SocialsRollKind { ROLLED_TODAY, NOTHING_TO_ROLL, AI_OFF, WAITING_APPS, WAITING_CLAUDE }

/** What the "Today's roll" card says. */
data class SocialsRollInfo(
    val kind: SocialsRollKind,
    /** Past days (not today) that still wait for a roll and have followers to give. */
    val missedDays: Int,
    /** For ROLLED_TODAY: the gain of each platform today. */
    val gainsToday: List<Pair<String, Long>>
)

/** Pure rules for the accounts: names, adding, editing, deleting. No Android code. */
object SocialsRules {

    private val SPACES = Regex("\\s+")

    /** Trims the text and turns every run of spaces into one space. */
    fun cleanSpaces(text: String): String = text.trim().replace(SPACES, " ")

    /** Platform identity: the name trimmed and lower-cased. */
    fun platformKey(name: String): String = name.trim().lowercase()

    /** Celebrity identity: trimmed, inner spaces collapsed to one, lower-cased. */
    fun celebrityKey(name: String): String = cleanSpaces(name).lowercase()

    /** The four accounts made the first time. */
    fun seeded(today: String, now: Long): SocialsData {
        fun make(id: String, name: String) = SocialAccount(
            id = id, name = name, handle = "neribogroup", displayName = DEFAULT_SOCIAL_DISPLAY_NAME,
            createdMillis = now
        )
        return SocialsData(
            startDayKey = today,
            lastRollDayKey = "",
            seeded = true,
            accounts = listOf(
                make(SOCIAL_ID_FACEBOOK, "Facebook"),
                make(SOCIAL_ID_X, "X"),
                make(SOCIAL_ID_INSTAGRAM, "Instagram"),
                make(SOCIAL_ID_TIKTOK, "TikTok")
            )
        )
    }

    /** Null when a new account is allowed, otherwise one plain sentence. */
    fun addError(data: SocialsData, name: String, handle: String): String? {
        val clean = cleanSpaces(name)
        if (clean.length < 2) return "Type the platform name (at least 2 letters)."
        if (clean.length > 30) return "The platform name can have at most 30 letters."
        if (clean.lowercase() == RESERVED_PLATFORM_NAME.lowercase()) {
            return "\"Other\" is kept for adverts and cannot be an account name."
        }
        if (data.accounts.any { platformKey(it.name) == clean.lowercase() }) {
            return "There is already an account called " + clean + "."
        }
        if (data.accounts.size >= MAX_SOCIAL_ACCOUNTS) return "At most $MAX_SOCIAL_ACCOUNTS accounts."
        if (handle.trim().length > 40) return "The handle can have at most 40 letters."
        return null
    }

    /** Adds an account. Returns [data] unchanged when it is not allowed. */
    fun add(data: SocialsData, name: String, handle: String, displayName: String, now: Long): SocialsData {
        if (addError(data, name, handle) != null) return data
        val display = cleanSpaces(displayName).take(60).ifBlank { DEFAULT_SOCIAL_DISPLAY_NAME }
        val account = SocialAccount(
            id = "social-" + UUID.randomUUID().toString().lowercase(),
            name = cleanSpaces(name),
            handle = handle.trim().take(40),
            displayName = display,
            createdMillis = now
        )
        return data.copy(accounts = data.accounts + account)
    }

    /**
     * Applies the edited fields. The name, the followers, the picture, the history and the rolls of an
     * account are never touched here. Edits for an account that no longer exists are ignored.
     */
    fun applyEdits(data: SocialsData, edits: List<SocialEdit>): SocialsData {
        val byId = edits.associateBy { it.id }
        return data.copy(
            accounts = data.accounts.map { a ->
                val e = byId[a.id] ?: return@map a
                a.copy(
                    handle = e.handle.trim().take(40),
                    displayName = cleanSpaces(e.displayName).take(60).ifBlank { DEFAULT_SOCIAL_DISPLAY_NAME },
                    following = e.following.coerceIn(0L, MAX_SOCIAL_NUMBER),
                    posts = e.posts.coerceIn(0L, MAX_SOCIAL_NUMBER),
                    likes = e.likes.coerceIn(0L, MAX_SOCIAL_NUMBER)
                )
            }
        )
    }

    const val MAX_SOCIAL_NUMBER = 999_999_999_999L

    /**
     * Null when the account may be deleted. Otherwise one plain sentence that says which running or
     * upcoming advert lists this platform. Stopped and finished adverts do not block.
     */
    fun deleteBlock(ledger: Ledger, account: SocialAccount, today: String): String? {
        val key = platformKey(account.name)
        for (app in ledger.apps) {
            if (!app.googlePlayEnabled || app.id == SOFTWARE_APP_ID) continue
            for (advert in app.adverts) {
                val status = AdvertEngine.statusOn(advert, today)
                if (status == AdvertStatus.FINISHED) continue
                val lists = advert.platforms.any { platformKey(it.name) == key && it.followers > 0L }
                if (!lists) continue
                val word = if (status == AdvertStatus.RUNNING) "running" else "upcoming"
                return "You cannot delete " + account.name + " yet. The " + word + " advert of " +
                    advert.celebrityName + " in " + app.name + " lists " + account.name +
                    ". Stop that advert first, or wait until it has finished."
            }
        }
        return null
    }

    fun delete(data: SocialsData, accountId: String): SocialsData =
        data.copy(accounts = data.accounts.filter { it.id != accountId })

    /**
     * The exact names of the accounts that are not one of the six fixed advert platforms (compared
     * ignoring case), in creation order. The advert form gets one extra followers field for each.
     */
    fun extraAdvertNames(data: SocialsData): List<String> {
        val fixed = ADVERT_PLATFORM_NAMES.map { platformKey(it) }.toSet()
        return data.accounts.filter { platformKey(it.name) !in fixed }.map { it.name }
    }

    fun totalFollowers(data: SocialsData): Long = data.accounts.sumOf { it.followers }

    /** The numbers a platform shows, in its own order. */
    fun statsFor(name: String): List<SocialStat> = when (platformKey(name)) {
        "instagram" -> listOf(SocialStat.POSTS, SocialStat.FOLLOWERS, SocialStat.FOLLOWING)
        "facebook" -> listOf(SocialStat.FOLLOWERS, SocialStat.LIKES)
        "tiktok" -> listOf(SocialStat.FOLLOWING, SocialStat.FOLLOWERS, SocialStat.LIKES)
        "x" -> listOf(SocialStat.FOLLOWING, SocialStat.FOLLOWERS, SocialStat.POSTS)
        else -> listOf(SocialStat.FOLLOWERS, SocialStat.FOLLOWING, SocialStat.POSTS)
    }

    fun statValue(account: SocialAccount, stat: SocialStat): Long = when (stat) {
        SocialStat.POSTS -> account.posts
        SocialStat.FOLLOWERS -> account.followers
        SocialStat.FOLLOWING -> account.following
        SocialStat.LIKES -> account.likes
    }
}

/**
 * Reading adverts for Socials: which adverts count on a day and what follower base each platform
 * has. Pure functions of the ledger. The dice themselves come in Delivery C-2.
 */
object SocialsEngine {

    private fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    /** Every advert of every Google Play app (never the software app) that counts on [day]. */
    fun countingAdverts(ledger: Ledger, day: String): List<CountingAdvert> {
        val list = ArrayList<CountingAdvert>()
        for (app in ledger.apps) {
            if (!app.googlePlayEnabled || app.id == SOFTWARE_APP_ID) continue
            for (advert in app.adverts) {
                if (AdvertEngine.runningOn(advert, day)) list.add(CountingAdvert(app.name, advert))
            }
        }
        return list
    }

    /**
     * The base of one platform: every counting advert that lists followers above 0 on it is grouped by
     * celebrity, each celebrity counts once with their highest number, and the base is the sum.
     */
    fun platformBase(adverts: List<CountingAdvert>, key: String, name: String): PlatformBase {
        val best = LinkedHashMap<String, SocialCelebrity>()
        for (ca in adverts) {
            var followers = 0L
            for (p in ca.advert.platforms) {
                if (SocialsRules.platformKey(p.name) == key && p.followers > followers) followers = p.followers
            }
            if (followers <= 0L) continue
            val celebrityKey = SocialsRules.celebrityKey(ca.advert.celebrityName)
            if (celebrityKey.isEmpty()) continue
            val old = best[celebrityKey]
            if (old == null || followers > old.followers) {
                best[celebrityKey] = SocialCelebrity(SocialsRules.cleanSpaces(ca.advert.celebrityName), followers)
            }
        }
        val celebrities = best.values.sortedWith(
            compareByDescending<SocialCelebrity> { it.followers }.thenBy { it.name.lowercase() }
        )
        return PlatformBase(key, name, celebrities.sumOf { it.followers }, celebrities)
    }

    /** One entry per account (in the order of the accounts), including platforms with base 0. */
    fun basesOn(ledger: Ledger, day: String): List<PlatformBase> {
        val adverts = countingAdverts(ledger, day)
        return ledger.socials.accounts.map { platformBase(adverts, SocialsRules.platformKey(it.name), it.name) }
    }

    /**
     * Names of platforms that counting adverts list (followers above 0) but that have no account.
     * One entry per platform, in the order they are met.
     */
    fun platformsWithoutAccount(ledger: Ledger, day: String): List<String> {
        val have = ledger.socials.accounts.map { SocialsRules.platformKey(it.name) }.toSet()
        val found = LinkedHashMap<String, String>()
        for (ca in countingAdverts(ledger, day)) {
            for (p in ca.advert.platforms) {
                if (p.followers <= 0L) continue
                val key = SocialsRules.platformKey(p.name)
                if (key.isEmpty() || key in have) continue
                if (!found.containsKey(key)) found[key] = SocialsRules.cleanSpaces(p.name)
            }
        }
        return found.values.toList()
    }

    /**
     * The days that still wait for a roll: from the latest of the start day, the day after the last
     * rolled day and 29 days before [today], up to [today]. Empty when [today] is already rolled or the
     * phone date went backwards.
     */
    fun pendingDays(data: SocialsData, today: String): List<String> {
        val todayDate = parseDay(today) ?: return emptyList()
        var from = todayDate.minusDays(29)
        val start = parseDay(data.startDayKey) ?: todayDate
        if (start.isAfter(from)) from = start
        val last = parseDay(data.lastRollDayKey)
        if (last != null) {
            if (!last.isBefore(todayDate)) return emptyList()
            val next = last.plusDays(1)
            if (next.isAfter(from)) from = next
        }
        val days = ArrayList<String>()
        var d = from
        while (!d.isAfter(todayDate)) {
            days.add(d.toString())
            d = d.plusDays(1)
        }
        return days
    }

    private fun anyBase(ledger: Ledger, day: String): Boolean = basesOn(ledger, day).any { it.base > 0L }

    /** True when a Google Play app has an advert counting on [today] that the backend has not rolled for today yet. */
    fun waitingForApps(ledger: Ledger, today: String): Boolean {
        for (app in ledger.apps) {
            if (!app.googlePlayEnabled || app.id == SOFTWARE_APP_ID) continue
            val waiting = app.adverts.any { advert ->
                AdvertEngine.runningOn(advert, today) && advert.days.none { it.dayKey == today }
            }
            if (waiting) return true
        }
        return false
    }

    /** What the "Today's roll" card says. [aiOn] is true when cloud sign-in and AI mode are both on. */
    fun rollInfo(ledger: Ledger, today: String, aiOn: Boolean): SocialsRollInfo {
        val data = ledger.socials
        val gains = data.accounts.mapNotNull { a ->
            val roll = a.rolls.firstOrNull { it.dayKey == today } ?: return@mapNotNull null
            Pair(a.name, roll.gain)
        }
        val pending = pendingDays(data, today)
        val missed = pending.count { it != today && anyBase(ledger, it) }
        val todayPending = today in pending

        if (!todayPending) {
            val kind = if (gains.isNotEmpty()) SocialsRollKind.ROLLED_TODAY else SocialsRollKind.NOTHING_TO_ROLL
            return SocialsRollInfo(kind, 0, gains)
        }
        val kind = when {
            !anyBase(ledger, today) ->
                if (missed > 0 && !aiOn) SocialsRollKind.AI_OFF else SocialsRollKind.NOTHING_TO_ROLL
            !aiOn -> SocialsRollKind.AI_OFF
            waitingForApps(ledger, today) -> SocialsRollKind.WAITING_APPS
            else -> SocialsRollKind.WAITING_CLAUDE
        }
        return SocialsRollInfo(kind, missed, gains)
    }

    /** "Celebrity A 5M + Celebrity B 2M = 7M", or "" when the base is 0. */
    fun baseLine(base: PlatformBase): String {
        if (base.base <= 0L) return ""
        val shown = base.celebrities.take(4)
        val parts = shown.joinToString(" + ") { it.name + " " + AdvertEngine.compactFollowers(it.followers) }
        val more = base.celebrities.size - shown.size
        val tail = if (more > 0) " + $more more" else ""
        return parts + tail + " = " + AdvertEngine.compactFollowers(base.base)
    }
}

/**
 * Reads and writes the root "socials" object of ledger.json. Every field is read with a default, so
 * older files and older backups load without error.
 */
object SocialsJson {

    fun toJson(data: SocialsData): JSONObject {
        val root = JSONObject()
        root.put("startDayKey", data.startDayKey)
        root.put("lastRollDayKey", data.lastRollDayKey)
        root.put("seeded", data.seeded)
        val accounts = JSONArray()
        for (a in data.accounts) accounts.put(accountToJson(a))
        root.put("accounts", accounts)
        return root
    }

    private fun accountToJson(a: SocialAccount): JSONObject {
        val o = JSONObject()
        o.put("id", a.id)
        o.put("name", a.name)
        o.put("handle", a.handle)
        o.put("displayName", a.displayName)
        o.put("followers", a.followers)
        o.put("following", a.following)
        o.put("posts", a.posts)
        o.put("likes", a.likes)
        val image = a.profileImage
        if (image != null) {
            o.put(
                "profileImage",
                JSONObject().put("version", image.version).put("sizeBytes", image.sizeBytes).put("sha256", image.sha256)
            )
        }
        o.put("createdMillis", a.createdMillis)
        val history = JSONArray()
        for (h in a.history) history.put(JSONObject().put("day", h.dayKey).put("count", h.count))
        o.put("history", history)
        val rolls = JSONArray()
        for (r in a.rolls) {
            val ro = JSONObject()
            ro.put("day", r.dayKey)
            ro.put("base", r.base)
            ro.put("gain", r.gain)
            if (r.comment.isNotEmpty()) ro.put("comment", r.comment)
            val celebrities = JSONArray()
            for (c in r.celebrities) celebrities.put(JSONObject().put("name", c.name).put("followers", c.followers))
            ro.put("celebrities", celebrities)
            rolls.put(ro)
        }
        o.put("rolls", rolls)
        return o
    }

    /** Never throws for odd content: a broken account is skipped, a missing field gets its default. */
    fun fromJson(o: JSONObject): SocialsData {
        val accounts = ArrayList<SocialAccount>()
        val seen = HashSet<String>()
        val array = o.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until array.length()) {
            val ao = array.optJSONObject(i) ?: continue
            val account = runCatching { accountFromJson(ao) }.getOrNull() ?: continue
            if (!seen.add(account.id)) continue
            accounts.add(account)
        }
        return SocialsData(
            startDayKey = o.optString("startDayKey", ""),
            lastRollDayKey = o.optString("lastRollDayKey", ""),
            seeded = o.optBoolean("seeded", true),
            accounts = accounts
        )
    }

    private fun accountFromJson(o: JSONObject): SocialAccount? {
        val id = o.optString("id", "")
        val name = o.optString("name", "")
        if (id.isBlank() || name.isBlank()) return null
        val image = o.optJSONObject("profileImage")?.let {
            SocialImage(
                version = max(0, it.optInt("version", 0)),
                sizeBytes = max(0L, it.optLong("sizeBytes", 0L)),
                sha256 = it.optString("sha256", "")
            )
        }
        val history = ArrayList<DayCount>()
        val ha = o.optJSONArray("history") ?: JSONArray()
        for (i in 0 until ha.length()) {
            val h = ha.optJSONObject(i) ?: continue
            val day = h.optString("day", "")
            if (day.isBlank()) continue
            history.add(DayCount(day, h.optLong("count", 0L)))
        }
        val rolls = ArrayList<SocialRoll>()
        val ra = o.optJSONArray("rolls") ?: JSONArray()
        for (i in 0 until ra.length()) {
            val r = ra.optJSONObject(i) ?: continue
            val day = r.optString("day", "")
            if (day.isBlank()) continue
            val celebrities = ArrayList<SocialCelebrity>()
            val ca = r.optJSONArray("celebrities") ?: JSONArray()
            for (j in 0 until ca.length()) {
                val c = ca.optJSONObject(j) ?: continue
                val cn = c.optString("name", "")
                if (cn.isBlank()) continue
                celebrities.add(SocialCelebrity(cn, c.optLong("followers", 0L)))
            }
            rolls.add(
                SocialRoll(
                    dayKey = day,
                    base = r.optLong("base", 0L),
                    gain = r.optLong("gain", 0L),
                    celebrities = celebrities,
                    comment = r.optString("comment", "")
                )
            )
        }
        return SocialAccount(
            id = id,
            name = name,
            handle = o.optString("handle", ""),
            displayName = o.optString("displayName", DEFAULT_SOCIAL_DISPLAY_NAME),
            followers = max(0L, o.optLong("followers", 0L)),
            following = max(0L, o.optLong("following", 0L)),
            posts = max(0L, o.optLong("posts", 0L)),
            likes = max(0L, o.optLong("likes", 0L)),
            profileImage = image,
            createdMillis = o.optLong("createdMillis", 0L),
            history = history.takeLast(MAX_SOCIAL_HISTORY),
            rolls = rolls.takeLast(MAX_SOCIAL_ROLLS)
        )
    }
}

/**
 * Makes sure the four first accounts exist once. Does nothing when the Socials data was seeded before
 * (so accounts the owner deleted never come back).
 */
fun Ledger.withSocialsSeeded(now: Long): Ledger {
    if (socials.seeded) return this
    val protectedNow = max(now, casesClockMillis)
    return copy(socials = SocialsRules.seeded(dayKey(protectedNow), now))
}
