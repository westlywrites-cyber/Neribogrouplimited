package com.westly.opay.feature.welcome

import com.westly.opay.backend.SecureStore

/** Remembers whether the user has already passed the welcome slides on this install. */
class WelcomeFlag(private val store: SecureStore) {
    fun isSeen(): Boolean = store.getBoolean(KEY, false)

    fun markSeen() {
        store.putBoolean(KEY, true)
    }

    companion object {
        const val KEY = "welcome_seen"
    }
}
