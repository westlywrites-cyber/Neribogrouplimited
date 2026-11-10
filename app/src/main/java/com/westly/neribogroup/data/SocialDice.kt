package com.westly.neribogroup.data

import kotlin.random.Random

/**
 * The social dice (Delivery C-2): Claude supplies one face 0 to 5 for every platform and this object
 * turns the faces into follower numbers. Pure functions, no Android code. The numbers are
 * deterministic: the same day, platform and face always give the same gain, so a retry or a second
 * phone gets the same result.
 */
object SocialDice {

    /** Daily gain in parts per million of the base followers, for faces 0 to 5 (both ends included). */
    val RANGES: List<IntRange> = listOf(0..0, 20..60, 60..140, 140..260, 260..400, 400..600)

    const val MAX_FACE = 5

    /** At most this many celebrities are kept in the saved roll of one platform. */
    private const val MAX_SAVED_CELEBRITIES = 20

    /** "Today" for Socials: never earlier than the highest time the app has ever seen. */
    fun nowFor(ledger: Ledger, wallNow: Long): Long =
        maxOf(wallNow, ledger.casesClockMillis, ledger.software.clockMillis)

    /** A 64-bit number made from "social|day|platformKey" (FNV-1a), the same on every phone. */
    fun seedFor(day: String, platformKey: String): Long {
        var h = -3750763034362895579L // FNV offset basis 0xcbf29ce484222325
        val prime = 1099511628211L // FNV prime 0x100000001b3
        for (b in ("social|" + day + "|" + platformKey).toByteArray(Charsets.UTF_8)) {
            h = (h xor (b.toLong() and 0xffL)) * prime
        }
        return h
    }

    /** The parts-per-million chosen for [face]: a whole number inside the range of that face. */
    fun ppmFor(day: String, platformKey: String, face: Int): Int {
        if (face <= 0) return 0
        val range = RANGES[face.coerceIn(0, MAX_FACE)]
        val random = Random(seedFor(day, platformKey))
        return range.first + random.nextInt(range.last - range.first + 1)
    }

    /** Whole-number maths. At least 1 when the face and the base are above 0. Face 0 gives 0. */
    fun gainFor(base: Long, ppm: Int, face: Int): Long {
        if (face <= 0 || base <= 0L || ppm <= 0) return 0L
        val gain = (base * ppm + 500_000L) / 1_000_000L
        return if (gain < 1L) 1L else gain
    }

    /**
     * Applies one day. [faces] maps a platform key to its face. A platform that has a base above 0 on
     * [day] and a face gets its gain; the others get nothing. The day is applied at most once: when
     * [day] is not after the last applied day (or is before the start day) the same ledger comes back.
     * An empty [faces] closes the day with nothing gained.
     */
    fun applyDay(ledger: Ledger, day: String, faces: Map<String, Int>): Ledger {
        val data = ledger.socials
        if (day.isBlank()) return ledger
        if (data.lastRollDayKey.isNotEmpty() && day <= data.lastRollDayKey) return ledger
        if (data.startDayKey.isNotEmpty() && day < data.startDayKey) return ledger

        val bases = SocialsEngine.basesOn(ledger, day).associateBy { it.key }
        val accounts = data.accounts.map { account ->
            val key = SocialsRules.platformKey(account.name)
            val base = bases[key]
            val rawFace = faces[key]
            if (base == null || base.base <= 0L || rawFace == null) return@map account
            val face = rawFace.coerceIn(0, MAX_FACE)
            val ppm = ppmFor(day, key, face)
            val gain = gainFor(base.base, ppm, face)
            val roll = SocialRoll(
                dayKey = day,
                base = base.base,
                face = face,
                ppm = ppm,
                gain = gain,
                celebrities = base.celebrities.take(MAX_SAVED_CELEBRITIES)
            )
            account.copy(
                followers = account.followers + gain,
                history = (account.history.filter { it.dayKey != day } + DayCount(day, gain)).takeLast(MAX_SOCIAL_HISTORY),
                rolls = (account.rolls.filter { it.dayKey != day } + roll).takeLast(MAX_SOCIAL_ROLLS)
            )
        }
        return ledger.copy(socials = data.copy(lastRollDayKey = day, accounts = accounts))
    }
}
