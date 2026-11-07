package com.westly.neribogroup.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** The symbol (or code) written before an amount, for example "$", "£" or "AED ". */
fun currencySymbol(currency: String): String = when (currency) {
    "NGN" -> "₦"
    "USD" -> "$"
    "GBP" -> "£"
    "EUR" -> "€"
    "CAD" -> "CA$"
    "AUD" -> "A$"
    "AED" -> "AED "
    "ZAR" -> "R"
    "GHS" -> "GH₵"
    "KES" -> "KSh "
    else -> "$currency "
}

/** Whole units of a currency with grouped digits and the symbol first, for example "£1,800". */
fun formatMoney(units: Long, currency: String): String {
    val sign = if (units < 0) "-" else ""
    return sign + currencySymbol(currency) +
        NumberFormat.getIntegerInstance(Locale.US).format(abs(units))
}

/** The $ account: cents shown as dollars with two decimals, for example "$1,234.56". */
fun formatUsd(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = abs(cents)
    val dollars = absolute / 100
    val rest = absolute % 100
    return sign + "$" + NumberFormat.getIntegerInstance(Locale.US).format(dollars) +
        "." + rest.toString().padStart(2, '0')
}

/** An exchange rate with up to 4 decimals and no trailing zeros, for example "1,500" or "0.78". */
fun formatRate(value: Double): String {
    val text = String.format(Locale.US, "%,.4f", value)
    return text.trimEnd('0').trimEnd('.')
}

/** A date with the time, for example "06 Nov 2026, 14:30". */
fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
