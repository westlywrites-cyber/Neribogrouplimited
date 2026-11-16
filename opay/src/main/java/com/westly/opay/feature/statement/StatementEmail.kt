package com.westly.opay.feature.statement

/**
 * The address rules of the Download Statement email. They are the same rules the `send-statement` Edge Function
 * applies, so an address accepted here is never refused there. Pure, so it can be unit tested.
 */
object StatementEmail {
    const val MAX_LENGTH = 254
    private const val FORBIDDEN = "<>,;\"'"

    /** The address without the spaces around it. */
    fun clean(raw: String): String = raw.trim()

    /**
     * One address of printable ASCII (no space, no control character, no line break, none of `< > , ; " '`), with
     * exactly one `@`, text before it, and a domain that has a dot and does not start or end with a dot.
     */
    fun isValid(raw: String): Boolean {
        val email = clean(raw)
        if (email.isEmpty() || email.length > MAX_LENGTH) return false
        if (email.any { it.code < 33 || it.code > 126 || it in FORBIDDEN }) return false
        val at = email.indexOf('@')
        if (at <= 0 || at != email.lastIndexOf('@')) return false
        val domain = email.substring(at + 1)
        if (domain.isEmpty() || !domain.contains('.')) return false
        if (domain.startsWith('.') || domain.endsWith('.') || domain.contains("..")) return false
        return true
    }
}
