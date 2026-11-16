package com.westly.opay.core.util

import java.util.Locale

private val nairaPattern = Regex("""\d+(\.\d{1,2})?""")

fun formatNaira(kobo: Long): String {
    val negative = kobo < 0
    val absolute = if (negative) -kobo else kobo
    val whole = String.format(Locale.US, "%,d", absolute / 100)
    val fraction = String.format(Locale.US, "%02d", absolute % 100)
    return (if (negative) "-" else "") + "\u20A6" + whole + "." + fraction
}

fun maskAccount(account: String): String {
    if (account.length <= 6) return account
    return account.take(3) + "*".repeat(account.length - 6) + account.takeLast(3)
}

fun parseNairaToKobo(input: String): Long? {
    val clean = input.trim().removePrefix("\u20A6").replace(",", "").trim()
    if (!nairaPattern.matches(clean)) return null
    val parts = clean.split('.')
    val whole = parts[0].toLongOrNull() ?: return null
    if (whole > (Long.MAX_VALUE - 99L) / 100L) return null
    val fraction = parts.getOrElse(1) { "" }.padEnd(2, '0').toLong()
    return whole * 100L + fraction
}
