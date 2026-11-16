package com.westly.opay.backend

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

class SessionRepository(
    private val provider: SupabaseProvider,
    private val store: SecureStore,
    scope: CoroutineScope
) {
    private val state = MutableStateFlow<SessionState>(
        if (BackendConfig.isConfigured) SessionState.Loading else SessionState.SignedOut
    )
    val sessionState: StateFlow<SessionState> = state.asStateFlow()

    private val expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once when a signed-in session ends without the user asking for it, for example a failed refresh. */
    val sessionExpired: SharedFlow<Unit> = expired.asSharedFlow()

    @Volatile
    private var wasSignedIn = false

    @Volatile
    private var userSignOut = false

    init {
        if (BackendConfig.isConfigured) {
            scope.launch {
                try {
                    provider.client.auth.sessionStatus.collect { status -> publish(map(status)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.value = SessionState.SignedOut
                }
            }
        }
    }

    /** Imports the tokens an Edge Function returned. The Auth plugin then keeps them fresh and stores them. */
    suspend fun signInWithTokens(session: SessionPayload): BackendResult<Unit> = backendCall {
        provider.client.auth.importAuthToken(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            retrieveUser = false,
            autoRefresh = true
        )
    }

    /** Local sign-out. Keeps device_id, the remembered phone and the biometric tokens. */
    suspend fun signOut() {
        userSignOut = true
        if (BackendConfig.isConfigured) {
            try {
                provider.client.auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    provider.client.auth.clearSession()
                } catch (inner: CancellationException) {
                    throw inner
                } catch (inner: Exception) {
                    // The stored copy is removed below either way.
                }
            }
        }
        store.remove(SecureKeys.SESSION_JSON)
    }

    private fun publish(next: SessionState) {
        state.value = next
        if (next is SessionState.SignedIn) {
            wasSignedIn = true
            userSignOut = false
        } else if (next is SessionState.SignedOut && wasSignedIn) {
            wasSignedIn = false
            if (userSignOut) {
                userSignOut = false
            } else {
                store.remove(SecureKeys.SESSION_JSON)
                expired.tryEmit(Unit)
            }
        }
    }

    private fun map(status: SessionStatus): SessionState = when (status) {
        is SessionStatus.Authenticated -> SessionState.SignedIn(userIdOf(status.session))
        is SessionStatus.NotAuthenticated -> SessionState.SignedOut
        else -> {
            val current = provider.client.auth.currentSessionOrNull()
            if (current != null) SessionState.SignedIn(userIdOf(current)) else SessionState.Loading
        }
    }

    private fun userIdOf(session: UserSession): String =
        session.user?.id ?: subjectOf(session.accessToken)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** The `sub` claim of a JWT, or an empty string if the token cannot be read. */
        fun subjectOf(token: String): String {
            val payload = token.split('.').getOrNull(1) ?: return ""
            return try {
                val text = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
                val claims = json.parseToJsonElement(text) as? JsonObject
                (claims?.get("sub") as? JsonPrimitive)?.contentOrNull.orEmpty()
            } catch (e: IllegalArgumentException) {
                ""
            }
        }
    }
}
