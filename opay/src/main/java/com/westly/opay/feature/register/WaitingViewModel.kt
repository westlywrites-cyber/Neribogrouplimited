package com.westly.opay.feature.register

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
import com.westly.opay.backend.RegistrationState
import com.westly.opay.navigation.Routes
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val STATUS_POLL_MILLIS = 5000L
const val STILL_UNDER_REVIEW_MESSAGE = "Your account is still under review"

private const val STATUS_PENDING = "pending"
private const val STATUS_ACTIVE = "active"
private const val STATUS_SUSPENDED = "suspended"

data class WaitingUiState(
    val approved: Boolean = false,
    val suspended: Boolean = false,
    val checking: Boolean = false
)

sealed interface WaitingEvent {
    data class ShowToast(val message: String) : WaitingEvent
    data class GoTo(val route: String) : WaitingEvent
}

/** The backend calls the waiting screen needs. A fake of this is used in unit tests. */
interface WaitingGateway {
    suspend fun registrationState(): BackendResult<RegistrationState>

    /** Emits once for every change of the signed-in user's profile row. Fails if Realtime cannot connect. */
    fun profileChanges(): Flow<Unit>
}

class BackendWaitingGateway(private val backend: Backend) : WaitingGateway {

    override suspend fun registrationState(): BackendResult<RegistrationState> {
        if (!backend.isOnline()) {
            return BackendResult.Failure(
                BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK))
            )
        }
        return backend.rpc.registrationState()
    }

    /** Row-level security lets only the user's own profile row through, so no filter is needed. */
    override fun profileChanges(): Flow<Unit> = flow {
        val client = backend.provider.client
        val channel = client.channel("profile-status-${UUID.randomUUID()}")
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "profiles"
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            updates.collect { emit(Unit) }
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.unsubscribe() }
                runCatching { client.realtime.removeChannel(channel) }
            }
        }
    }
}

/**
 * Drives the waiting-for-approval screen. [watch] runs only while the screen is visible: it asks the server for the
 * registration state every [pollMillis] and also whenever Realtime reports a change to the user's profile.
 */
class WaitingViewModel(
    private val gateway: WaitingGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val pollMillis: Long = STATUS_POLL_MILLIS
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(WaitingUiState())
    val state: StateFlow<WaitingUiState> = _state.asStateFlow()

    private val _events = Channel<WaitingEvent>(Channel.BUFFERED)
    val events: Flow<WaitingEvent> = _events.receiveAsFlow()

    private var redirected = false
    private var continued = false

    /** Polls and listens until the caller is cancelled. */
    suspend fun watch() {
        coroutineScope {
            launch { listenForChanges() }
            while (true) {
                pollOnce()
                delay(pollMillis)
            }
        }
    }

    /** The "Check status" button. */
    fun checkNow() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        scope.launch {
            when (val result = gateway.registrationState()) {
                is BackendResult.Success -> {
                    applyState(result.value)
                    if (result.value.status == STATUS_PENDING) {
                        emit(WaitingEvent.ShowToast(STILL_UNDER_REVIEW_MESSAGE))
                    }
                }
                is BackendResult.Failure -> emit(WaitingEvent.ShowToast(result.error.message))
            }
            _state.update { it.copy(checking = false) }
        }
    }

    /** "Continue" on the approval dialog. */
    fun onApprovedContinue() {
        if (!_state.value.approved || continued) return
        continued = true
        emit(WaitingEvent.GoTo(Routes.Home))
    }

    private suspend fun listenForChanges() {
        try {
            gateway.profileChanges().collect { pollOnce() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Realtime is only a faster path. The timed poll keeps running without it.
        }
    }

    private suspend fun pollOnce() {
        val result = gateway.registrationState()
        if (result is BackendResult.Success) applyState(result.value)
    }

    private fun applyState(registration: RegistrationState) {
        when (registration.status) {
            STATUS_ACTIVE -> _state.update { it.copy(approved = true, suspended = false) }
            STATUS_SUSPENDED -> _state.update { it.copy(suspended = true) }
            STATUS_PENDING -> _state.update { it.copy(suspended = false) }
            else -> if (!redirected) {
                redirected = true
                emit(WaitingEvent.GoTo(RegistrationRouting.nextRoute(registration)))
            }
        }
    }

    private fun emit(event: WaitingEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        scope.cancel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WaitingViewModel(gateway = BackendWaitingGateway((application as OPayApplication).backend))
            }
        }
    }
}
