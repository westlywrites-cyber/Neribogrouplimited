package com.westly.opay.feature.register

import com.westly.opay.backend.PhoneFormat

/** Pure rules for the phone number typed on the registration screen. */
object PhoneInput {
    const val MAX_DIGITS = 10

    /** Keeps digits only, drops a leading +234, 234 or 0, and keeps at most 10 digits. */
    fun normalize(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }
        val local = when {
            digits.startsWith("234") -> digits.substring(3)
            digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
        return local.take(MAX_DIGITS)
    }

    fun append(current: String, digit: Char): String = normalize(current + digit)

    fun backspace(current: String): String = current.dropLast(1)

    /** 8012345678 becomes 801 234 5678. */
    fun formatGroups(digits: String): String = buildString {
        digits.forEachIndexed { index, ch ->
            if (index == 3 || index == 6) append(' ')
            append(ch)
        }
    }

    /** 8012345678 becomes +234 801 234 5678. */
    fun international(digits: String): String = "+234 " + formatGroups(digits)

    fun isValid(digits: String): Boolean =
        digits.length == MAX_DIGITS && PhoneFormat.toPhone10(digits) != null

    /** True when the first digits can never lead to a valid number. A partly typed 234 prefix is not an error. */
    fun showError(digits: String): Boolean {
        if (digits.isEmpty()) return false
        if (digits.length < 3 && "234".startsWith(digits)) return false
        return digits.first() !in '7'..'9'
    }
}
