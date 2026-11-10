package com.westly.neribogroup.ui

import com.westly.neribogroup.data.calendarDaysLeft
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatCount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

fun formatNaira(value: Long): String {
    val sign = if (value < 0) "-" else ""
    return sign + "₦" + NumberFormat.getIntegerInstance(Locale.US).format(abs(value))
}

fun formatFullDate(millis: Long): String =
    SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date(millis))

fun formatMonth(millis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

/**
 * The line shown under a user: "Active" when seen today, otherwise "Last seen yesterday",
 * "Last seen 3 days ago", or the date and how long ago after 7 or more days ("Last seen 4 Nov · 12 days ago").
 */
fun lastSeenText(now: Long, lastSeenMillis: Long): String {
    val days = maxOf(0L, -calendarDaysLeft(now, lastSeenMillis))
    return when {
        days == 0L -> "Active"
        days == 1L -> "Last seen yesterday"
        days < 7L -> "Last seen $days days ago"
        else -> "Last seen " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(lastSeenMillis)) +
            " · $days days ago"
    }
}
