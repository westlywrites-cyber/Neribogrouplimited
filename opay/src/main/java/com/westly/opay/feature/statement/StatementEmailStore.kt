package com.westly.opay.feature.statement

import com.westly.opay.backend.SecureStore

/**
 * Keeps the email each signed-in user chose for statements, on this phone only, in the app's encrypted store. The
 * address is never written to a database table; it only leaves the phone inside the `send-statement` call.
 * The calls read and write the encrypted file, so they must not run on the main thread.
 */
class StatementEmailStore(private val store: SecureStore) {
    private fun key(userId: String) = KEY_PREFIX + userId

    /** The saved address, or an empty text when none is saved. */
    fun get(userId: String): String =
        if (userId.isEmpty()) "" else store.getString(key(userId)).orEmpty()

    fun save(userId: String, email: String) {
        if (userId.isNotEmpty()) store.putString(key(userId), email)
    }

    fun clear(userId: String) {
        if (userId.isNotEmpty()) store.remove(key(userId))
    }

    private companion object {
        const val KEY_PREFIX = "statement_email_"
    }
}
