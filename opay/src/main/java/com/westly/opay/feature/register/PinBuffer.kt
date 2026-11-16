package com.westly.opay.feature.register

/**
 * Holds the digits of one PIN entry in a [CharArray] so they can be wiped. It is never saved, logged or shown; the
 * screen only learns how many digits were typed.
 */
class PinBuffer(val capacity: Int) {
    private val digits = CharArray(capacity)

    var length: Int = 0
        private set

    val isFull: Boolean get() = length == capacity

    /** Adds one digit. Returns false when the buffer is full or [digit] is not 0 to 9. */
    fun append(digit: Char): Boolean {
        if (digit !in '0'..'9' || length >= capacity) return false
        digits[length] = digit
        length += 1
        return true
    }

    fun backspace() {
        if (length == 0) return
        length -= 1
        digits[length] = WIPED
    }

    fun clear() {
        digits.fill(WIPED)
        length = 0
    }

    fun matches(other: PinBuffer): Boolean {
        if (length != other.length) return false
        var difference = 0
        for (index in 0 until length) {
            difference = difference or (digits[index].code xor other.digits[index].code)
        }
        return difference == 0
    }

    fun isTrivial(): Boolean = isFull && PinRules.isTrivial(toPinString())

    /** The typed digits as text, for the one backend call that needs them. */
    fun toPinString(): String = String(digits, 0, length)

    private companion object {
        const val WIPED = '\u0000'
    }
}
