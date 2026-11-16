package com.westly.opay.feature.statement

import java.time.LocalDate

/** The allowed dates of one date field. [min] is never after [max]. */
data class DateBounds(val min: LocalDate, val max: LocalDate)

/** The period rules of the Download Statement form. Pure, so they can be unit tested. */
object StatementPeriods {
    const val END_AFTER_YESTERDAY = "End date cannot be later than yesterday"
    const val END_BEFORE_START = "End date cannot be before start date"
    const val START_AFTER_END = "Start date cannot be after end date"
    const val START_AFTER_YESTERDAY = "Start date cannot be later than yesterday"
    const val START_BEFORE_CREATED = "Start date cannot be before your account was created"
    const val NO_PERIOD = "No statement period is available for this account yet"

    fun yesterday(today: LocalDate): LocalDate = today.minusDays(1)

    /** One month before yesterday through yesterday, never starting before the account existed. */
    fun lastMonth(today: LocalDate, accountCreated: LocalDate): StatementPeriod? {
        val end = yesterday(today)
        return period(maxOf(end.minusMonths(1), accountCreated), end)
    }

    /** Two years before yesterday through yesterday, never starting before the account existed. */
    fun lastTwoYears(today: LocalDate, accountCreated: LocalDate): StatementPeriod? {
        val end = yesterday(today)
        return period(maxOf(end.minusYears(2), accountCreated), end)
    }

    fun preset(frame: TimeFrame, today: LocalDate, accountCreated: LocalDate): StatementPeriod? = when (frame) {
        TimeFrame.LastMonth -> lastMonth(today, accountCreated)
        TimeFrame.LastTwoYears -> lastTwoYears(today, accountCreated)
        TimeFrame.Custom -> null
    }

    private fun period(start: LocalDate, end: LocalDate): StatementPeriod? =
        if (start.isAfter(end)) null else StatementPeriod(start, end)

    /** The message for a rejected end date, or null when [date] is allowed. */
    fun checkEnd(date: LocalDate, today: LocalDate, start: LocalDate?): String? = when {
        date.isAfter(yesterday(today)) -> END_AFTER_YESTERDAY
        start != null && date.isBefore(start) -> END_BEFORE_START
        else -> null
    }

    /** The message for a rejected start date, or null when [date] is allowed. */
    fun checkStart(date: LocalDate, today: LocalDate, accountCreated: LocalDate, end: LocalDate?): String? = when {
        date.isBefore(accountCreated) -> START_BEFORE_CREATED
        end != null && date.isAfter(end) -> START_AFTER_END
        date.isAfter(yesterday(today)) -> START_AFTER_YESTERDAY
        else -> null
    }

    /** The dates the start field may take, or null when the account has no finished day yet. */
    fun startBounds(today: LocalDate, accountCreated: LocalDate, end: LocalDate?): DateBounds? =
        bounds(accountCreated, minOf(end ?: yesterday(today), yesterday(today)))

    /** The dates the end field may take, or null when the account has no finished day yet. */
    fun endBounds(today: LocalDate, accountCreated: LocalDate, start: LocalDate?): DateBounds? =
        bounds(maxOf(start ?: accountCreated, accountCreated), yesterday(today))

    private fun bounds(min: LocalDate, max: LocalDate): DateBounds? =
        if (min.isAfter(max)) null else DateBounds(min, max)

    /** The date of the three wheels, with the day cut to the month's length and the result held inside [bounds]. */
    fun clamp(year: Int, month: Int, day: Int, bounds: DateBounds): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        val length = java.time.YearMonth.of(year, safeMonth).lengthOfMonth()
        val date = LocalDate.of(year, safeMonth, day.coerceIn(1, length))
        return when {
            date.isBefore(bounds.min) -> bounds.min
            date.isAfter(bounds.max) -> bounds.max
            else -> date
        }
    }
}
