package com.westly.neribogroup.data

/**
 * The social dice (Delivery C-2), now rolled by the BACKEND. For every platform the backend rolls a real
 * random face, turns it into followers with rules that only it knows, and answers with the number of
 * followers gained. This object only adds those gains to the accounts. Pure functions, no Android code.
 * The app never sees a face or a rate, and nothing like it is saved.
 */
object SocialDice {

    /** At most this many celebrities are kept in the saved roll of one platform. */
    private const val MAX_SAVED_CELEBRITIES = 20

    /** "Today" for Socials: never earlier than the highest time the app has ever seen. */
    fun nowFor(ledger: Ledger, wallNow: Long): Long =
        maxOf(wallNow, ledger.casesClockMillis, ledger.software.clockMillis)

    /**
     * Applies one day. [gains] maps a platform key to the followers the backend gave it. A platform that has a
     * base above 0 on [day] and an answer gets its gain; the others get nothing. The day is applied at most
     * once: when [day] is not after the last applied day (or is before the start day) the same ledger comes
     * back. An empty [gains] closes the day with nothing gained.
     */
    fun applyDay(ledger: Ledger, day: String, gains: Map<String, Long>, comment: String = ""): Ledger {
        val data = ledger.socials
        if (day.isBlank()) return ledger
        if (data.lastRollDayKey.isNotEmpty() && day <= data.lastRollDayKey) return ledger
        if (data.startDayKey.isNotEmpty() && day < data.startDayKey) return ledger

        val bases = SocialsEngine.basesOn(ledger, day).associateBy { it.key }
        val accounts = data.accounts.map { account ->
            val key = SocialsRules.platformKey(account.name)
            val base = bases[key]
            val rawGain = gains[key]
            if (base == null || base.base <= 0L || rawGain == null) return@map account
            val gain = if (rawGain < 0L) 0L else rawGain
            val roll = SocialRoll(
                dayKey = day,
                base = base.base,
                gain = gain,
                celebrities = base.celebrities.take(MAX_SAVED_CELEBRITIES),
                comment = comment
            )
            account.copy(
                followers = account.followers + gain,
                history = (account.history.filter { it.dayKey != day } + DayCount(day, gain)).takeLast(MAX_SOCIAL_HISTORY),
                rolls = (account.rolls.filter { it.dayKey != day } + roll).takeLast(MAX_SOCIAL_ROLLS)
            )
        }
        return ledger.copy(socials = data.copy(lastRollDayKey = day, accounts = accounts))
    }

    /** Fills in Claude's comments that arrived later. [comments] maps a day key to the comment. */
    fun applyComments(ledger: Ledger, comments: Map<String, String>): Ledger {
        if (comments.isEmpty()) return ledger
        var changed = false
        val accounts = ledger.socials.accounts.map { account ->
            val rolls = account.rolls.map { roll ->
                val text = comments[roll.dayKey]
                if (text != null && text.isNotEmpty() && roll.comment.isEmpty()) {
                    changed = true
                    roll.copy(comment = text)
                } else {
                    roll
                }
            }
            account.copy(rolls = rolls)
        }
        return if (changed) ledger.copy(socials = ledger.socials.copy(accounts = accounts)) else ledger
    }
}
