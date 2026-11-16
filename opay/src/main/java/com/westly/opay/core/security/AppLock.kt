package com.westly.opay.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SessionState
import com.westly.opay.backend.backendCall
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val KEY_LOCKED = "app_locked"
private const val KEY_LOGIN_MODE = "login_mode_cache"
private const val MODE_SEPARATOR = '|'

/** The login mode last read for one user. It is only used while that same user is signed in. */
private data class CachedMode(val userId: String, val mode: String)

/**
 * Locks the app according to the Auto-logout mode. It records when the app goes to the background, decides on
 * return with [AppLockPolicy], and asks the navigation host to show the lock screen through [lockRequests].
 *
 * The locked state is also kept in the secure store, so an app that is closed while locked is still locked on the
 * next start and a locked screen never lets the 60-minute clock restart. Fingerprint data never reaches this class.
 */
class AppLock(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the app has just come back to the front and must show the lock screen. */
    val lockRequests: SharedFlow<Unit> = requests.asSharedFlow()

    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var lastActiveMillis: Long? = null

    @Volatile
    private var locked = false

    @Volatile
    private var cachedMode: CachedMode? = null

    @Volatile
    private var inBackground = false

    @Volatile
    private var stopIgnored = false

    private val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = handleBackground()

        override fun onStart(owner: LifecycleOwner) = handleForeground()
    }

    /** Starts watching the app's foreground and background changes and the session. Called once at app start. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        scope.launch {
            backend.session.sessionState.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> refreshMode(state.userId)
                    SessionState.SignedOut -> clearLocked()
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    /**
     * Cold start: true when the app must open on the lock screen. Used once, right after the splash, for a user who
     * is signed in. A locked state from before the app was closed always counts.
     */
    suspend fun shouldLockOnColdStart(): Boolean {
        if (currentUserId() == null) return false
        ensureLoaded()
        if (locked) return true
        val lock = decide()
        if (lock) setLocked(true)
        return lock
    }

    /** The lock screen was passed: the app is unlocked and counts as active from now. */
    fun onUnlocked() {
        scope.launch {
            ensureLoaded()
            val now = clock()
            lastActiveMillis = now
            locked = false
            withContext(Dispatchers.IO) {
                backend.secureStore.putString(SecureKeys.LAST_ACTIVE_AT, now.toString())
                backend.secureStore.remove(KEY_LOCKED)
            }
        }
    }

    /** Remembers [mode] for the signed-in user right after it was saved on the server. */
    suspend fun rememberMode(mode: String) {
        val userId = currentUserId() ?: return
        storeMode(userId, mode)
    }

    /** The mode stored for the signed-in user, or null when none is known yet. */
    suspend fun cachedLoginMode(): String? {
        ensureLoaded()
        return effectiveMode()
    }

    private fun handleBackground() {
        inBackground = true
        if (BiometricGate.isPromptShowing) {
            stopIgnored = true
            return
        }
        stopIgnored = false
        if (locked) return
        val now = clock()
        lastActiveMillis = now
        scope.launch {
            withContext(Dispatchers.IO) { backend.secureStore.putString(SecureKeys.LAST_ACTIVE_AT, now.toString()) }
        }
    }

    private fun handleForeground() {
        if (!inBackground) return
        inBackground = false
        val ignored = stopIgnored
        stopIgnored = false
        if (ignored) return
        scope.launch { evaluateOnReturn() }
    }

    private suspend fun evaluateOnReturn() {
        if (currentUserId() == null) return
        ensureLoaded()
        if (!locked) {
            if (!decide()) return
            setLocked(true)
        }
        requests.tryEmit(Unit)
    }

    private fun decide(): Boolean =
        AppLockPolicy.shouldLock(effectiveMode(), lastActiveMillis, clock(), BiometricGate.isPromptShowing)

    private fun currentUserId(): String? = (backend.session.sessionState.value as? SessionState.SignedIn)?.userId

    private fun effectiveMode(): String? {
        val entry = cachedMode ?: return null
        return if (entry.userId == currentUserId()) entry.mode else null
    }

    private suspend fun refreshMode(userId: String) {
        ensureLoaded()
        if (!backend.isOnline()) return
        val result = backendCall {
            backend.provider.client.postgrest.from("profiles").select().decodeSingleOrNull<ProfileRow>()
        }
        if (result !is BackendResult.Success) return
        val mode = result.value?.loginMode ?: return
        storeMode(userId, mode)
    }

    private suspend fun storeMode(userId: String, mode: String) {
        ensureLoaded()
        cachedMode = CachedMode(userId, mode)
        withContext(Dispatchers.IO) {
            backend.secureStore.putString(KEY_LOGIN_MODE, "$userId$MODE_SEPARATOR$mode")
        }
    }

    private suspend fun setLocked(value: Boolean) {
        locked = value
        withContext(Dispatchers.IO) {
            if (value) backend.secureStore.putBoolean(KEY_LOCKED, true) else backend.secureStore.remove(KEY_LOCKED)
        }
    }

    /** Without a session there is nothing to lock, so a stored locked state ends. */
    private suspend fun clearLocked() {
        setLocked(false)
    }

    /** Reads the stored values once. Values already set in memory by a newer event are never overwritten. */
    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            val store = backend.secureStore
            val stored = withContext(Dispatchers.IO) {
                Triple(
                    store.getString(SecureKeys.LAST_ACTIVE_AT)?.toLongOrNull(),
                    store.getBoolean(KEY_LOCKED),
                    store.getString(KEY_LOGIN_MODE)
                )
            }
            if (lastActiveMillis == null) lastActiveMillis = stored.first
            if (stored.second) locked = true
            if (cachedMode == null) cachedMode = parseMode(stored.third)
            loaded = true
        }
    }

    private fun parseMode(raw: String?): CachedMode? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(MODE_SEPARATOR, limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
        return CachedMode(parts[0], parts[1])
    }
}
