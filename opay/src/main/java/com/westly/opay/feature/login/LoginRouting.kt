package com.westly.opay.feature.login

import com.westly.opay.backend.PhoneFormat
import com.westly.opay.backend.RegistrationState
import com.westly.opay.feature.register.RegistrationRouting
import com.westly.opay.navigation.Routes

/** Pure rules that decide where the app opens and what the login screens show. */
object LoginRouting {

    /**
     * The first screen after the splash.
     *
     * [registration] is the server's answer for a signed-in user, or null when it could not be read. A signed-in
     * user whose state is unknown goes Home, so a person who is offline keeps the cached screens.
     */
    fun startRoute(
        signedIn: Boolean,
        registration: RegistrationState?,
        welcomeSeen: Boolean,
        rememberedPhone: String?,
        fingerprintReady: Boolean = false
    ): String = when {
        signedIn -> if (registration == null) Routes.Home else RegistrationRouting.nextRoute(registration)
        !welcomeSeen -> Routes.Welcome
        else -> signedOutRoute(rememberedPhone, fingerprintReady)
    }

    /**
     * The login screen for someone with no session: the fingerprint screen when a number is remembered and a
     * fingerprint token is ready, else the PIN screen when a number is remembered, else the phone screen.
     */
    fun signedOutRoute(rememberedPhone: String?, fingerprintReady: Boolean = false): String = when {
        rememberedPhone.isNullOrBlank() -> Routes.LoginPhone
        fingerprintReady -> Routes.LoginFingerprint
        else -> Routes.LoginPin
    }
}

/** Text helpers for the login screens. */
object LoginFormat {

    /** 8012345678 becomes 080****5678. */
    fun maskedPhone(phone10: String): String {
        val full = PhoneFormat.display(phone10)
        if (full.length != 11) return full
        return full.take(3) + "****" + full.takeLast(4)
    }

    /** The first name as stored for the "Hi, <NAME>" label: trimmed and upper case, or null when empty. */
    fun rememberedName(firstName: String?): String? =
        firstName?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

    /** WESTLY becomes Westly, for headers that show a name in normal case. */
    fun displayName(remembered: String?): String? =
        remembered?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.replaceFirstChar { it.uppercaseChar() }

    /** The letter shown in the round avatar, or an empty string when there is no name. */
    fun initial(remembered: String?): String =
        remembered?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()
}
