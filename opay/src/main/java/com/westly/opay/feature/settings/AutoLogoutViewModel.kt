package com.westly.opay.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westly.opay.OPayApplication
import com.westly.opay.backend.Backend
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.backendCall
import com.westly.opay.core.security.AppLock
import com.westly.opay.core.security.LoginModes
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the Auto-logout list. */
data class AutoLogoutOption(val mode: String, val title: String, val description: String)

/** The three rows of the Auto-logout list, in the order they are shown. */
object AutoLogoutOptions {
    val all: List<AutoLogoutOption> = listOf(
        AutoLogoutOption(
            LoginModes.PASSWORD_FREE,
            "Password-free",
            "No password needed while the app stays signed in"
        ),
        AutoLogoutOption(
            LoginModes.SIXTY_MINUTES,
            "60 minutes",
            "Ask for password after 60 minutes away"
        ),
        AutoLogoutOption(
            LoginModes.ALWAYS,
            "Always",
            "Ask for password every time you open the app"
        )
    )
}

/** The calls the Auto-logout screen needs. A fake of this is used in unit tests. */
interface AutoLogoutGateway {
    /** The mode kept on this phone for the signed-in user, or null when none is known. */
    suspend fun cachedMode(): String?

    /** The mode saved on the server (`profiles.login_mode`). */
    suspend fun serverMode(): BackendResult<String?>

    /** `update_profile(p_login_mode)`. On success the new mode is also kept on this phone for the app lock. */
    suspend fun save(mode: String): BackendResult<Unit>
}

class BackendAutoLogoutGateway(
    private val backend: Backend,
    private val appLock: AppLock
) : AutoLogoutGateway {

    override suspend fun cachedMode(): String? = appLock.cachedLoginMode()

    override suspend fun serverMode(): BackendResult<String?> {
        if (!backend.isOnline()) return offline()
        return when (
            val result = backendCall {
                backend.provider.client.postgrest.from("profiles").select().decodeSingleOrNull<ProfileRow>()
            }
        ) {
            is BackendResult.Success -> BackendResult.Success(result.value?.loginMode)
            is BackendResult.Failure -> result
        }
    }

    override suspend fun save(mode: String): BackendResult<Unit> {
        if (!backend.isOnline()) return offline()
        return when (val result = backend.rpc.updateProfile(loginMode = mode)) {
            is BackendResult.Success -> {
                appLock.rememberMode(result.value.loginMode)
                BackendResult.Success(Unit)
            }
            is BackendResult.Failure -> result
        }
    }

    private fun offline(): BackendResult.Failure =
        BackendResult.Failure(BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK)))
}

/** What the Auto-logout list draws. [selected] is null until a mode is known. */
data class AutoLogoutUiState(
    val selected: String? = null,
    val saving: Boolean = false
)

sealed interface AutoLogoutEvent {
    data class ShowToast(val message: String) : AutoLogoutEvent
}

/**
 * Runs the Auto-logout list (reference D4). Tapping a row saves it at once. The row is marked straight away and
 * put back, with a toast, when the save fails. Taps are ignored while a save is running.
 */
class AutoLogoutViewModel(
    private val gateway: AutoLogoutGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(AutoLogoutUiState())
    val state: StateFlow<AutoLogoutUiState> = _state.asStateFlow()

    private val _events = Channel<AutoLogoutEvent>(Channel.BUFFERED)
    val events: Flow<AutoLogoutEvent> = _events.receiveAsFlow()

    private var entered = false
    private var chosenByUser = false

    /** Called when the screen opens: shows the mode kept on the phone, then the one saved on the server. */
    fun onEntered() {
        if (entered) return
        entered = true
        scope.launch {
            val cached = gateway.cachedMode()
            if (cached != null) showLoaded(cached)
            val server = gateway.serverMode()
            if (server is BackendResult.Success && server.value != null) showLoaded(server.value)
        }
    }

    fun select(mode: String) {
        val current = _state.value
        if (current.saving || mode == current.selected) return
        val previous = current.selected
        chosenByUser = true
        _state.value = current.copy(selected = mode, saving = true)
        scope.launch {
            when (val result = gateway.save(mode)) {
                is BackendResult.Success -> _state.update { it.copy(saving = false) }
                is BackendResult.Failure -> {
                    _state.update { it.copy(selected = previous, saving = false) }
                    _events.trySend(AutoLogoutEvent.ShowToast(result.error.message))
                }
            }
        }
    }

    private fun showLoaded(mode: String) {
        if (chosenByUser) return
        _state.update { it.copy(selected = mode) }
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as OPayApplication
                AutoLogoutViewModel(BackendAutoLogoutGateway(app.backend, app.appLock))
            }
        }
    }
}
