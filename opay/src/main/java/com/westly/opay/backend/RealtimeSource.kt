package com.westly.opay.backend

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** One change pushed by the backend for the signed-in user. */
sealed interface RealtimeEvent {
    /** The subscription is live, first time or after a reconnect. Anything missed meanwhile must be read again. */
    data object Connected : RealtimeEvent

    /** A change arrived that could not be read; the data must be read again. */
    data object Resync : RealtimeEvent

    data class AccountChanged(val row: AccountRow) : RealtimeEvent
    data class ProfileChanged(val row: ProfileRow) : RealtimeEvent
    data class TransactionInserted(val row: TransactionRow) : RealtimeEvent
    data class NotificationInserted(val row: NotificationRow) : RealtimeEvent
    data class SavingsChanged(val row: SavingsPositionRow) : RealtimeEvent
}

/** The one realtime feed the repository needs. Collecting subscribes; cancelling the collection unsubscribes. */
interface RealtimeSource {
    fun events(userId: String): Flow<RealtimeEvent>
}

/** Subscribes to the user's rows in `accounts`, `transactions`, `notifications`, `profiles` and `savings_positions`. Row-level security limits each table to own rows. */
class SupabaseRealtimeSource(private val provider: SupabaseProvider) : RealtimeSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun events(userId: String): Flow<RealtimeEvent> = channelFlow {
        val live = provider.client.channel("opay-live-$userId")
        val accounts = live.postgresChangeFlow<PostgresAction>(schema = "public") { table = "accounts" }
        val profiles = live.postgresChangeFlow<PostgresAction>(schema = "public") { table = "profiles" }
        val transactions = live.postgresChangeFlow<PostgresAction>(schema = "public") { table = "transactions" }
        val notifications = live.postgresChangeFlow<PostgresAction>(schema = "public") { table = "notifications" }
        val savings = live.postgresChangeFlow<PostgresAction>(schema = "public") { table = "savings_positions" }

        launch {
            accounts.collect { action ->
                changedRecord(action)?.let { record ->
                    forward(record, AccountRow.serializer()) { RealtimeEvent.AccountChanged(it) }
                }
            }
        }
        launch {
            profiles.collect { action ->
                changedRecord(action)?.let { record ->
                    forward(record, ProfileRow.serializer()) { RealtimeEvent.ProfileChanged(it) }
                }
            }
        }
        launch {
            transactions.collect { action ->
                insertedRecord(action)?.let { record ->
                    forward(record, TransactionRow.serializer()) { RealtimeEvent.TransactionInserted(it) }
                }
            }
        }
        launch {
            notifications.collect { action ->
                insertedRecord(action)?.let { record ->
                    forward(record, NotificationRow.serializer()) { RealtimeEvent.NotificationInserted(it) }
                }
            }
        }
        launch {
            savings.collect { action ->
                changedRecord(action)?.let { record ->
                    forward(record, SavingsPositionRow.serializer()) { RealtimeEvent.SavingsChanged(it) }
                }
            }
        }
        launch {
            live.status.collect { status ->
                if (status == RealtimeChannel.Status.SUBSCRIBED) send(RealtimeEvent.Connected)
            }
        }

        try {
            live.subscribe()
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                try {
                    provider.client.realtime.removeChannel(live)
                } catch (e: Exception) {
                    // The connection may already be gone; the channel is dropped with it.
                }
            }
        }
    }

    private suspend fun <T> ProducerScope<RealtimeEvent>.forward(
        record: JsonObject,
        serializer: KSerializer<T>,
        build: (T) -> RealtimeEvent
    ) {
        val row = try {
            json.decodeFromJsonElement(serializer, record)
        } catch (e: IllegalArgumentException) {
            null
        }
        send(if (row == null) RealtimeEvent.Resync else build(row))
    }

    private fun changedRecord(action: PostgresAction): JsonObject? = when (action) {
        is PostgresAction.Insert -> action.record
        is PostgresAction.Update -> action.record
        else -> null
    }

    private fun insertedRecord(action: PostgresAction): JsonObject? =
        (action as? PostgresAction.Insert)?.record
}
