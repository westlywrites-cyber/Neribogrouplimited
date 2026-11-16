package com.westly.opay.backend

object PhoneFormat {
    private val phone10Pattern = Regex("[789][0-9]{9}")

    /** Same rule as the backend: 0801..., +234801... and 234801... all become 801.... Returns null if invalid. */
    fun toPhone10(input: String): String? {
        val cleaned = input.filterNot { it.isWhitespace() || it == '+' || it == '-' || it == '(' || it == ')' }
        if (cleaned.isEmpty() || !cleaned.all { it in '0'..'9' }) return null
        val local = when {
            cleaned.startsWith("234") && cleaned.length == 13 -> cleaned.substring(3)
            cleaned.startsWith("0") && cleaned.length == 11 -> cleaned.substring(1)
            else -> cleaned
        }
        return if (phone10Pattern.matches(local)) local else null
    }

    fun display(phone10: String): String = "0" + phone10

    /** 8012345678 becomes 0801 234 5678. */
    fun displayGrouped(phone10: String): String {
        val full = display(phone10)
        if (full.length != 11) return full
        return full.substring(0, 4) + " " + full.substring(4, 7) + " " + full.substring(7)
    }
}
