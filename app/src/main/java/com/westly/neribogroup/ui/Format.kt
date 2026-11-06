package com.westly.neribogroup.ui

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
