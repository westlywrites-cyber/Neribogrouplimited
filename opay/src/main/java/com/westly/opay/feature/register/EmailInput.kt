package com.westly.opay.feature.register

/** Rules for the optional address typed on the Privacy Policy page. */
object EmailInput {
    const val MAX_LENGTH = 254
    private val pattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Trims and lowercases what the user typed. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    fun isValid(raw: String): Boolean {
        val value = normalize(raw)
        return value.length <= MAX_LENGTH && pattern.matches(value)
    }
}
