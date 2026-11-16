package com.westly.opay.core.security

/** The three Auto-logout modes as the backend stores them in `profiles.login_mode`. */
object LoginModes {
    const val PASSWORD_FREE = "password_free"
    const val SIXTY_MINUTES = "sixty_minutes"
    const val ALWAYS = "always"
}

/** The rule that decides whether the app must be locked when it comes back to the front. It holds no state. */
object AppLockPolicy {
    const val SIXTY_MINUTES_MILLIS = 60L * 60L * 1000L

    /**
     * True when the lock screen must be shown.
     *
     * [mode] is the stored login mode (null or unknown means no lock). [lastActiveMillis] is the time the app last
     * went to the background, or null when none was recorded. [promptShowing] is true while the system fingerprint
     * prompt is on screen: the prompt itself can move the app to the background and back, so that never locks.
     * A clock that moved backwards (negative difference) locks in the 60-minute mode, because the time away cannot
     * be trusted.
     */
    fun shouldLock(mode: String?, lastActiveMillis: Long?, nowMillis: Long, promptShowing: Boolean): Boolean {
        if (promptShowing) return false
        return when (mode) {
            LoginModes.ALWAYS -> true
            LoginModes.SIXTY_MINUTES -> {
                if (lastActiveMillis == null) return true
                val away = nowMillis - lastActiveMillis
                away < 0L || away >= SIXTY_MINUTES_MILLIS
            }
            else -> false
        }
    }
}
