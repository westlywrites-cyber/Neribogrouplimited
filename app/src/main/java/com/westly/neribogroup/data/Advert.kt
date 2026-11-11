package com.westly.neribogroup.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * The fixed social platforms an advert can use. Exactly these names, in this order.
 * Socials accounts the owner adds later are offered after these (see [AdvertEngine.allowedPlatformNames]).
 */
val ADVERT_PLATFORM_NAMES: List<String> = listOf("Instagram", "TikTok", "X", "YouTube", "Facebook", "Other")

/** One social platform of a celebrity. */
data class AdvertPlatform(val name: String, val followers: Long)

/** Who makes the advert. A blog's followers are worth less than a celebrity's (the backend decides how much). */
enum class AdvertType { CELEBRITY, BLOG }

/**
 * One day of an advert, exactly as the backend answered. Only final numbers are kept here: how many users
 * arrived that day and how many of them started on Premium or Pro the same day. [delivered] becomes true
 * when a roll has added those users to the app.
 */
data class AdvertDay(
    val dayKey: String,
    val users: Long,
    val premium: Long,
    val pro: Long,
    /** Claude's short comment about the day. Empty until it is written. */
    val comment: String = "",
    val delivered: Boolean = false
)

/** One advert for one app. All day keys are "yyyy-MM-dd" (same as Campaign). */
data class Advert(
    val id: String = UUID.randomUUID().toString(),
    val celebrityName: String,
    val type: AdvertType = AdvertType.CELEBRITY,
    /** Where its users arrive: Google Play, Apple Store or Website. */
    val channel: Channel = Channel.PLAY,
    /** Only platforms with followers above 0, with the fixed names above. */
    val platforms: List<AdvertPlatform>,
    /** The stored sum of the platforms. */
    val totalFollowers: Long,
    val startDayKey: String,
    /** Inclusive. */
    val endDayKey: String,
    /** Set when the advert is stopped. That day and later bring nothing. */
    val stoppedDayKey: String = "",
    /** The days the backend has answered so far, oldest first. */
    val days: List<AdvertDay> = emptyList(),
    val createdMillis: Long
) {
    val isStopped: Boolean get() = stoppedDayKey.isNotEmpty()

    /** Users already added to the app. */
    val deliveredUsers: Long get() = days.filter { it.delivered }.sumOf { it.users }

    /** Users the backend has answered for, delivered or not. */
    val receivedUsers: Long get() = days.sumOf { it.users }
}

enum class AdvertStatus { UPCOMING, RUNNING, FINISHED }

/** The result of delivering every advert of an app for one roll day. */
class AdvertDelivery(
    val updatedAdverts: List<Advert>,
    val users: Long,
    /** Of [users]: how many start on Premium and on Pro the day they arrive. The rest are Free. */
    val premium: Long = 0L,
    val pro: Long = 0L,
    /** The same numbers per channel (an advert's users arrive in its own channel). */
    val byChannel: Map<Channel, AdvertTotals> = emptyMap()
)

/** Users, and how many of them start on Premium and Pro, for one channel. */
class AdvertTotals(val users: Long, val premium: Long, val pro: Long)

/** What the backend answered for one day of one advert. */
class AdvertAnswer(val advertId: String, val day: AdvertDay)

/**
 * Adverts: a celebrity's (or a blog's) followers bring users. The conversion is NOT done here: the backend
 * rolls every advert day and answers with final numbers only (users, and how many of them are Premium or
 * Pro). This object only registers adverts, asks which days are still missing, and adds the answers to the
 * app on a roll. There is no conversion rate, no plan and no dice in the app or in what it saves.
 */
object AdvertEngine {

    const val MAX_FOLLOWERS_PER_PLATFORM = 2_000_000_000L
    const val MIN_TOTAL_FOLLOWERS = 100L
    const val MAX_TOTAL_FOLLOWERS = 2_000_000_000L
    const val MAX_START_IN_DAYS = 30
    const val MAX_LASTS_DAYS = 30
    const val MAX_FINISHED_KEPT = 100

    /**
     * The most users of one app that are kept as individual rows. More than this are only counted
     * ([AppData.audienceFree]).
     *
     * Measured with the real LedgerStorage.usersToJson code and the real name lists: a typical Free
     * user row is about 214 bytes of JSON, the longest possible row (longest names and a 4-digit email
     * tail, on a paid plan) is 258 bytes. Last seen adds `"la":1731234567890,` = 19 bytes to a row, so the
     * worst row is 277 bytes: 20,000 rows x 277 bytes = 5,540,000 bytes in the worst case.
     * The rest of an app document (entries, 200 Play lines, 2,000 plan events, 100 adverts, and the two
     * 30-day histories, about 2,000 bytes) is reserved at 1,500,000 bytes, so a worst-case app document
     * stays near 7,042,000 bytes: under the 8,000,000 we want and well under the 12,000,000 cloud limit
     * per document.
     */
    const val MAX_STORED_USER_ROWS_PER_APP = 20_000

    /** The most user rows one roll makes for adverts. Keeps one roll fast. */
    const val MAX_ROWS_PER_ROLL = 2_000

    // ---------------------------------------------------------------------------------------------
    // Reading days
    // ---------------------------------------------------------------------------------------------

    private fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    private fun daysBetween(fromKey: String, toKey: String): Long? {
        val from = parseDay(fromKey) ?: return null
        val to = parseDay(toKey) ?: return null
        return ChronoUnit.DAYS.between(from, to)
    }

    /** Where an advert stands on [today]. A stopped advert is FINISHED (the screens say "Stopped"). */
    fun statusOn(advert: Advert, today: String): AdvertStatus = when {
        advert.isStopped -> AdvertStatus.FINISHED
        today < advert.startDayKey -> AdvertStatus.UPCOMING
        today <= advert.endDayKey -> AdvertStatus.RUNNING
        else -> AdvertStatus.FINISHED
    }

    /**
     * Does this advert count on [day]? True from its start day to its end day (both inclusive),
     * and only before the stop day when it was stopped. Unlike [statusOn] this is correct for past days:
     * a stopped advert still counted on the days before it was stopped.
     */
    fun runningOn(advert: Advert, day: String): Boolean {
        if (day < advert.startDayKey || day > advert.endDayKey) return false
        return advert.stoppedDayKey.isEmpty() || day < advert.stoppedDayKey
    }

    /**
     * The platform names an advert form may use: the six fixed names in their fixed order, then
     * [extraNames] (the exact names of Socials accounts the owner added) in the order given. A name that
     * equals a fixed name (ignoring case and spaces at the ends) or an earlier extra name is left out.
     */
    fun allowedPlatformNames(extraNames: List<String>): List<String> {
        val result = ArrayList<String>(ADVERT_PLATFORM_NAMES)
        val seen = HashSet<String>()
        for (n in ADVERT_PLATFORM_NAMES) seen.add(n.trim().lowercase())
        for (extra in extraNames) {
            val clean = extra.trim()
            if (clean.isEmpty()) continue
            if (seen.add(clean.lowercase())) result.add(extra)
        }
        return result
    }

    /** The last day that may ever bring users (the end day, or the day before the stop). "" when none. */
    fun lastDeliverableDay(advert: Advert): String {
        var last = advert.endDayKey
        if (advert.isStopped) {
            val before = parseDay(advert.stoppedDayKey)?.minusDays(1L)?.toString() ?: return ""
            if (before < last) last = before
        }
        return if (last < advert.startDayKey) "" else last
    }

    /** How many days the advert lasts in total (start day to end day, both included), 1 to 30. */
    fun lengthDays(advert: Advert): Int {
        val n = daysBetween(advert.startDayKey, advert.endDayKey) ?: return 1
        return (n + 1L).toInt().coerceIn(1, MAX_LASTS_DAYS)
    }

    /** True when nothing more can ever arrive from this advert. */
    fun isSettled(advert: Advert): Boolean {
        val last = lastDeliverableDay(advert)
        if (last.isEmpty()) return true
        val needed = (daysBetween(advert.startDayKey, last) ?: return true) + 1L
        return advert.days.count { it.delivered && it.dayKey <= last }.toLong() >= needed
    }

    // ---------------------------------------------------------------------------------------------
    // Showing numbers
    // ---------------------------------------------------------------------------------------------

    /** 1,240 -> "1,240", 240,000 -> "240K", 2,400,000 -> "2.4M". */
    fun compactFollowers(value: Long): String {
        fun trim(v: Double): String {
            val text = String.format(Locale.US, "%.1f", v)
            return if (text.endsWith(".0")) text.dropLast(2) else text
        }
        return when {
            value < 100_000L -> String.format(Locale.US, "%,d", value)
            value < 1_000_000L -> trim(value / 1_000.0) + "K"
            value < 1_000_000_000L -> trim(value / 1_000_000.0) + "M"
            else -> trim(value / 1_000_000_000.0) + "B"
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Checking a new advert
    // ---------------------------------------------------------------------------------------------

    /** Null when the advert is allowed, otherwise one plain sentence. */
    fun advertError(
        app: AppData,
        name: String,
        allPlatforms: List<AdvertPlatform>,
        startInDays: Int,
        lastsDays: Int,
        extraPlatformNames: List<String> = emptyList()
    ): String? {
        val allowed = allowedPlatformNames(extraPlatformNames)
        val platforms = allPlatforms.filter { it.name in allowed }
        if (!app.googlePlayEnabled) return "Adverts are only for apps that are on Google Play, the Apple Store or a website."
        if (app.id == SOFTWARE_APP_ID) return "Adverts are only for apps that are on Google Play, the Apple Store or a website."
        val clean = name.trim()
        if (clean.length < 2) return "Type the name (at least 2 letters)."
        if (clean.length > 60) return "The name can have at most 60 letters."
        for (p in platforms) {
            if (p.followers < 0L || p.followers > MAX_FOLLOWERS_PER_PLATFORM) {
                return "Each platform can have 0 to 2,000,000,000 followers."
            }
        }
        val total = platforms.sumOf { max(0L, it.followers) }
        if (platforms.none { it.followers > 0L }) return "Type the followers of at least one platform."
        if (total < MIN_TOTAL_FOLLOWERS) return "At least 100 followers in total are needed."
        if (total > MAX_TOTAL_FOLLOWERS) return "The total followers can be at most 2,000,000,000."
        if (startInDays < 0) return "An advert cannot start in the past."
        if (startInDays > MAX_START_IN_DAYS) return "An advert must start within the next 30 days."
        if (lastsDays < 1) return "An advert lasts at least 1 day."
        if (lastsDays > MAX_LASTS_DAYS) return "An advert lasts at most 30 days."
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Making an advert
    // ---------------------------------------------------------------------------------------------

    /**
     * Makes an advert and adds it to [app]. Nothing is converted here: the backend does that, day by day.
     * Returns [app] unchanged when the advert is not allowed.
     */
    fun create(
        app: AppData,
        name: String,
        type: AdvertType,
        channel: Channel,
        platforms: List<AdvertPlatform>,
        startInDays: Int,
        lastsDays: Int,
        now: Long,
        extraPlatformNames: List<String> = emptyList()
    ): AppData {
        if (advertError(app, name, platforms, startInDays, lastsDays, extraPlatformNames) != null) return app
        if (channel !in ChannelRules.enabled(app)) return app
        // Fixed names in the fixed order, then the added Socials platforms; only platforms with followers.
        val clean = allowedPlatformNames(extraPlatformNames).mapNotNull { platformName ->
            val followers = platforms.filter { it.name == platformName }.sumOf { max(0L, it.followers) }
            if (followers > 0L) AdvertPlatform(platformName, followers) else null
        }
        val total = clean.sumOf { it.followers }
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val start = today.plusDays(startInDays.toLong())
        val end = start.plusDays((lastsDays - 1).toLong())
        val advert = Advert(
            celebrityName = name.trim(),
            type = type,
            channel = channel,
            platforms = clean,
            totalFollowers = total,
            startDayKey = start.toString(),
            endDayKey = end.toString(),
            createdMillis = now
        )
        return app.copy(adverts = pruneFinished(app.adverts + advert, dayKey(now)))
    }

    /** Keeps every running and upcoming advert and the 100 newest finished ones (settled ones go first). */
    fun pruneFinished(adverts: List<Advert>, today: String): List<Advert> {
        val finished = adverts.filter { statusOn(it, today) == AdvertStatus.FINISHED }
        if (finished.size <= MAX_FINISHED_KEPT) return adverts
        val removable = finished.filter { isSettled(it) }
            .sortedWith(compareBy<Advert> { it.endDayKey }.thenBy { it.createdMillis })
        val dropCount = min(finished.size - MAX_FINISHED_KEPT, removable.size)
        val drop = removable.take(dropCount).map { it.id }.toSet()
        return adverts.filter { it.id !in drop }
    }

    /** Stops an upcoming or running advert. It can never be extended or restarted. */
    fun stop(app: AppData, advertId: String, now: Long): AppData {
        val today = dayKey(now)
        val advert = app.adverts.firstOrNull { it.id == advertId } ?: return app
        if (statusOn(advert, today) == AdvertStatus.FINISHED) return app
        return app.copy(
            adverts = app.adverts.map { if (it.id == advertId) it.copy(stoppedDayKey = today) else it }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Asking the backend, and delivering its answers on a roll
    // ---------------------------------------------------------------------------------------------

    /** The days of [advert] up to [today] that the backend has not answered yet, oldest first. */
    fun daysToAsk(advert: Advert, today: String): List<String> {
        val last = lastDeliverableDay(advert)
        if (last.isEmpty()) return emptyList()
        val upTo = if (today < last) today else last
        if (upTo < advert.startDayKey) return emptyList()
        val first = parseDay(advert.startDayKey) ?: return emptyList()
        val end = parseDay(upTo) ?: return emptyList()
        val have = advert.days.map { it.dayKey }.toSet()
        val list = ArrayList<String>()
        var d = first
        while (!d.isAfter(end)) {
            val key = d.toString()
            if (key !in have) list.add(key)
            d = d.plusDays(1L)
        }
        return list
    }

    /**
     * Adds the backend's answers to the adverts. A day that is already there keeps its numbers and its
     * delivered mark (a day is never changed once it is known); only a missing comment is filled in.
     * Answers for a day outside the advert, or for an unknown advert, are ignored.
     */
    fun mergeAnswers(adverts: List<Advert>, answers: List<AdvertAnswer>): List<Advert> {
        if (answers.isEmpty()) return adverts
        return adverts.map { advert ->
            val mine = answers.filter { it.advertId == advert.id }
            if (mine.isEmpty()) return@map advert
            val last = lastDeliverableDay(advert)
            val byDay = LinkedHashMap<String, AdvertDay>()
            for (d in advert.days) byDay[d.dayKey] = d
            for (a in mine) {
                val day = a.day
                if (last.isEmpty() || day.dayKey < advert.startDayKey || day.dayKey > last) continue
                val old = byDay[day.dayKey]
                if (old == null) {
                    byDay[day.dayKey] = day.copy(delivered = false)
                } else if (old.comment.isEmpty() && day.comment.isNotEmpty()) {
                    byDay[day.dayKey] = old.copy(comment = day.comment)
                }
            }
            advert.copy(days = byDay.values.sortedBy { it.dayKey })
        }
    }

    /** Fills in Claude's comments that arrived later. [comments] maps "advertId|dayKey" to the comment. */
    fun applyComments(adverts: List<Advert>, comments: Map<String, String>): List<Advert> {
        if (comments.isEmpty()) return adverts
        return adverts.map { advert ->
            var changed = false
            val days = advert.days.map { d ->
                val text = comments[advert.id + "|" + d.dayKey]
                if (text != null && text.isNotEmpty() && d.comment.isEmpty()) {
                    changed = true
                    d.copy(comment = text)
                } else {
                    d
                }
            }
            if (changed) advert.copy(days = days) else advert
        }
    }

    /**
     * Every advert of the app, for the roll of [today]: the answered days up to today (and up to the last
     * deliverable day) that are not delivered yet are delivered now. Days that were missed are caught up.
     */
    fun deliverAll(app: AppData, today: String): AdvertDelivery {
        if (app.adverts.isEmpty()) return AdvertDelivery(app.adverts, 0L)
        var users = 0L
        var premium = 0L
        var pro = 0L
        val perUsers = HashMap<Channel, Long>()
        val perPremium = HashMap<Channel, Long>()
        val perPro = HashMap<Channel, Long>()
        val updated = app.adverts.map { advert ->
            val last = lastDeliverableDay(advert)
            if (last.isEmpty()) return@map advert
            val limit = if (today < last) today else last
            var changed = false
            val days = advert.days.map { day ->
                if (!day.delivered && day.dayKey >= advert.startDayKey && day.dayKey <= limit) {
                    users += day.users
                    premium += day.premium
                    pro += day.pro
                    perUsers[advert.channel] = (perUsers[advert.channel] ?: 0L) + day.users
                    perPremium[advert.channel] = (perPremium[advert.channel] ?: 0L) + day.premium
                    perPro[advert.channel] = (perPro[advert.channel] ?: 0L) + day.pro
                    changed = true
                    day.copy(delivered = true)
                } else {
                    day
                }
            }
            if (changed) advert.copy(days = days) else advert
        }
        val byChannel = HashMap<Channel, AdvertTotals>()
        for (channel in perUsers.keys) {
            byChannel[channel] = AdvertTotals(perUsers[channel] ?: 0L, perPremium[channel] ?: 0L, perPro[channel] ?: 0L)
        }
        return AdvertDelivery(updated, users, premium, pro, byChannel)
    }
}
