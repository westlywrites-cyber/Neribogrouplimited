package com.westly.opay.feature.register

const val LOGIN_PIN_LENGTH = 6
const val PAYMENT_PIN_LENGTH = 4

/** The on-device half of the PIN rules. The backend applies the same rules again and answers INVALID_INPUT. */
object PinRules {
    /** True for identical digits (000000) and straight runs up or down (123456, 654321, 1234, 4321). */
    fun isTrivial(pin: String): Boolean {
        if (pin.length < 2 || pin.any { it !in '0'..'9' }) return false
        if (pin.all { it == pin[0] }) return true
        val steps = (1 until pin.length).map { pin[it] - pin[it - 1] }
        return steps.all { it == 1 } || steps.all { it == -1 }
    }
}
