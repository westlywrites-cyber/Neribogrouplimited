package com.westly.opay.feature.biz

/** Shows a 10-digit number as `XXX XXX XXXX`. Anything else is shown as it is. */
internal fun groupNumber(raw: String): String {
    val digits = raw.trim()
    if (digits.length != 10 || !digits.all { it in '0'..'9' }) return digits
    return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6)
}

/** `1 Trxn >`, `2 Trxns >`, `0 Trxns >`. */
internal fun trxnLabel(count: Int): String = (if (count == 1) "1 Trxn" else "$count Trxns") + " >"
