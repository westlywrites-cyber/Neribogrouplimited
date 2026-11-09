package com.westly.neribogroup.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** The social platforms an advert can use. Exactly these names, in this order. */
val ADVERT_PLATFORM_NAMES: List<String> = listOf("Instagram", "TikTok", "X", "YouTube", "Facebook", "Other")

/** One social platform of a celebrity. */
data class AdvertPlatform(val name: String, val followers: Long)

/** One advert for one app. All day keys are "yyyy-MM-dd" (same as Campaign). */
data class Advert(
    val id: String = UUID.randomUUID().toString(),
    val celebrityName: String,
    /** Only platforms with followers above 0, with the fixed names above. */
    val platforms: List<AdvertPlatform>,
    /** The stored sum of the platforms. */
    val totalFollowers: Long,
    /** Random 100..3000 parts per million: 0.01% = 100, 0.30% = 3000. Drawn once, when the advert is made. */
    val conversionPpm: Int,
    /** How many users this advert brings in total. */
    val targetUsers: Long,
    val startDayKey: String,
    /** Inclusive. */
    val endDayKey: String,
    /** Set when the advert is stopped. That day and later bring nothing. */
    val stoppedDayKey: String = "",
    /** One entry per advert day. The sum is [targetUsers]. */
    val plan: List<Long>,
    /** plan[0 until deliveredDays] are already delivered. Only ever grows. */
    val deliveredDays: Int = 0,
    val deliveredUsers: Long = 0,
    val createdMillis: Long
) {
    val isStopped: Boolean get() = stoppedDayKey.isNotEmpty()
}

enum class AdvertStatus { UPCOMING, RUNNING, FINISHED }

/** The result of delivering every advert of an app for one roll day. */
class AdvertDelivery(val updatedAdverts: List<Advert>, val users: Long)

/**
 * Adverts: a celebrity's followers bring users. Everything here is a pure function of its inputs
 * (plain data and a Random); there is no Android code and no floating point in what is saved.
 */
object AdvertEngine {

    const val MIN_PPM = 100
    const val MAX_PPM = 3000
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
     * tail, on a paid plan) is 258 bytes. 20,000 rows x 258 bytes = 5,160,000 bytes in the worst case.
     * The rest of an app document (entries, 200 Play lines, 2,000 plan events, 100 adverts) is
     * reserved at 1,500,000 bytes, so a worst-case app document stays near 6,700,000 bytes: under the
     * 8,000,000 we want and well under the 12,000,000 cloud limit per document.
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

    /** The last plan index that may ever be delivered (end of the advert, or the day before the stop). */
    fun lastDeliverableIndex(advert: Advert): Int {
        var last = advert.plan.size - 1
        if (advert.isStopped) {
            val stopIndex = daysBetween(advert.startDayKey, advert.stoppedDayKey) ?: return -1
            last = min(last.toLong(), stopIndex - 1L).toInt()
        }
        return last
    }

    /** True when nothing more can ever arrive from this advert. */
    fun isSettled(advert: Advert): Boolean = advert.deliveredDays > lastDeliverableIndex(advert)

    // ---------------------------------------------------------------------------------------------
    // Showing numbers
    // ---------------------------------------------------------------------------------------------

    /** 1,400 ppm is "0.14%". */
    fun percentText(ppm: Int): String = String.format(Locale.US, "%.2f%%", ppm / 10_000.0)

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
        platforms: List<AdvertPlatform>,
        startInDays: Int,
        lastsDays: Int
    ): String? {
        if (!app.googlePlayEnabled) return "Adverts are only for Google Play apps."
        if (app.id == SOFTWARE_APP_ID) return "Adverts are only for Google Play apps."
        val clean = name.trim()
        if (clean.length < 2) return "Type the celebrity's name (at least 2 letters)."
        if (clean.length > 60) return "The celebrity's name can have at most 60 letters."
        for (p in platforms) {
            if (p.followers < 0L || p.followers > MAX_FOLLOWERS_PER_PLATFORM) {
                return "Each platform can have 0 to 2,000,000,000 followers."
            }
        }
        val total = platforms.sumOf { max(0L, it.followers) }
        if (platforms.none { it.followers > 0L }) return "Type the followers of at least one platform."
        if (total < MIN_TOTAL_FOLLOWERS) return "The celebrity needs at least 100 followers in total."
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

    /** Users from followers: total x ppm / 1,000,000, rounded half up, at least 1. Whole-number maths. */
    fun targetFor(totalFollowers: Long, ppm: Int): Long =
        max(1L, (totalFollowers * ppm + 500_000L) / 1_000_000L)

    /**
     * Spreads [target] users over [days] days: biggest first, tapering (weights 0.8^i), each day moved
     * by a random 0.85 to 1.15, then fixed so the parts add up to exactly [target] (largest remainder).
     */
    fun makePlan(target: Long, days: Int, random: Random): List<Long> {
        if (days <= 1) return listOf(target)
        val weights = DoubleArray(days) { i ->
            0.8.pow(i.toDouble()) * (0.85 + random.nextDouble() * 0.30)
        }
        val sum = weights.sum()
        val exact = DoubleArray(days) { i -> target.toDouble() * weights[i] / sum }
        val parts = LongArray(days) { i -> exact[i].toLong() }
        var left = target - parts.sum()
        // Give the missing users to the parts with the biggest fractions (earlier day wins a tie).
        val order = (0 until days).sortedWith(
            compareByDescending<Int> { exact[it] - parts[it] }.thenBy { it }
        )
        var i = 0
        while (left > 0L) {
            parts[order[i % days]] += 1L
            left -= 1L
            i += 1
        }
        // Rounding can never overshoot, but be safe: take back from the biggest part.
        while (left < 0L) {
            val biggest = (0 until days).maxByOrNull { parts[it] } ?: 0
            parts[biggest] -= 1L
            left += 1L
        }
        return parts.toList()
    }

    /**
     * Makes an advert and adds it to [app]. The conversion rate is drawn here, once, and saved.
     * Returns [app] unchanged when the advert is not allowed.
     */
    fun create(
        app: AppData,
        name: String,
        platforms: List<AdvertPlatform>,
        startInDays: Int,
        lastsDays: Int,
        now: Long,
        random: Random
    ): AppData {
        if (advertError(app, name, platforms, startInDays, lastsDays) != null) return app
        // Fixed names in the fixed order; only platforms with followers.
        val clean = ADVERT_PLATFORM_NAMES.mapNotNull { platformName ->
            val followers = platforms.filter { it.name == platformName }.sumOf { max(0L, it.followers) }
            if (followers > 0L) AdvertPlatform(platformName, followers) else null
        }
        val total = clean.sumOf { it.followers }
        val ppm = random.nextInt(MIN_PPM, MAX_PPM + 1)
        val target = targetFor(total, ppm)
        val plan = makePlan(target, lastsDays, random)
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val start = today.plusDays(startInDays.toLong())
        val end = start.plusDays((lastsDays - 1).toLong())
        val advert = Advert(
            celebrityName = name.trim(),
            platforms = clean,
            totalFollowers = total,
            conversionPpm = ppm,
            targetUsers = target,
            startDayKey = start.toString(),
            endDayKey = end.toString(),
            plan = plan,
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
    // Delivering users on a roll
    // ---------------------------------------------------------------------------------------------

    /**
     * How many users one advert owes on [today], and the advert moved forward. Days that were missed
     * (no roll) are caught up, including days after the advert's end that were never delivered.
     */
    fun due(advert: Advert, today: String): Pair<Advert, Long> {
        if (advert.plan.isEmpty()) return Pair(advert, 0L)
        if (today < advert.startDayKey) return Pair(advert, 0L)
        val capped = if (today < advert.endDayKey) today else advert.endDayKey
        val sinceStart = daysBetween(advert.startDayKey, capped) ?: return Pair(advert, 0L)
        if (sinceStart < 0L) return Pair(advert, 0L)
        val lastIndex = min(min(advert.plan.size - 1, sinceStart.toInt()), lastDeliverableIndex(advert))
        val from = advert.deliveredDays
        if (lastIndex < from) return Pair(advert, 0L)
        var owed = 0L
        for (i in from..lastIndex) owed += advert.plan[i]
        return Pair(
            advert.copy(deliveredDays = lastIndex + 1, deliveredUsers = advert.deliveredUsers + owed),
            owed
        )
    }

    /** Every advert of the app, for the roll of [today]. Each advert moves only its own progress. */
    fun deliverAll(app: AppData, today: String): AdvertDelivery {
        if (app.adverts.isEmpty()) return AdvertDelivery(app.adverts, 0L)
        var total = 0L
        val updated = app.adverts.map { advert ->
            val (next, owed) = due(advert, today)
            total += owed
            next
        }
        return AdvertDelivery(updated, total)
    }
}
