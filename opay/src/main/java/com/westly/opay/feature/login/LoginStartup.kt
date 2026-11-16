package com.westly.opay.feature.login

import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_WAIT_MILLIS = 4000L
private const val STATE_WAIT_MILLIS = 6000L

/** Works out the first screen after the splash from the stored session, the server and the saved flags. */
object LoginStartup {

    suspend fun resolve(backend: Backend, welcomeSeen: Boolean): String {
        val (remembered, fingerprintReady) = withContext(Dispatchers.IO) {
            Pair(
                backend.secureStore.getString(SecureKeys.REMEMBERED_PHONE),
                !backend.secureStore.getString(SecureKeys.DEVICE_TOKEN_LOGIN).isNullOrBlank()
            )
        }
        val session = withTimeoutOrNull(SESSION_WAIT_MILLIS) {
            backend.session.sessionState.first { it !is SessionState.Loading }
        }
        if (session !is SessionState.SignedIn) {
            return LoginRouting.startRoute(false, null, welcomeSeen, remembered, fingerprintReady)
        }
        val registration = withTimeoutOrNull(STATE_WAIT_MILLIS) {
            if (backend.isOnline()) backend.rpc.registrationState() else null
        }
        return when {
            registration is BackendResult.Success ->
                LoginRouting.startRoute(true, registration.value, welcomeSeen, remembered)
            registration is BackendResult.Failure && registration.error.code == BackendCodes.NOT_AUTHENTICATED ->
                LoginRouting.startRoute(false, null, welcomeSeen, remembered, fingerprintReady)
            else -> LoginRouting.startRoute(true, null, welcomeSeen, remembered)
        }
    }
}
