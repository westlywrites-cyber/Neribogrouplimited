package com.westly.opay.backend

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

const val TRANSACTIONS_LIMIT = 200L
const val NOTIFICATIONS_LIMIT = 100L

/** Everything one full read of the signed-in user's data returns. Row-level security limits every table to own rows. */
data class RemoteSnapshot(
    val profile: ProfileRow,
    val account: AccountRow?,
    val transactions: List<TransactionRow>,
    val notifications: List<NotificationRow>,
    val beneficiaries: List<BeneficiaryRow>,
    val banks: List<BankRow>,
    val savings: List<SavingsPositionRow>
)

/** The one read the repository needs. A fake of this is used in tests. */
interface RemoteReader {
    suspend fun fetch(): BackendResult<RemoteSnapshot>
}

/** Reads the user's rows through Postgrest. Read only: nothing here writes. */
class RemoteDataSource(private val provider: SupabaseProvider) : RemoteReader {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(): BackendResult<RemoteSnapshot> = backendCall {
        coroutineScope {
            val profiles = async { read("profiles", ProfileRow.serializer()) }
            val accounts = async { read("accounts", AccountRow.serializer()) }
            val transactions = async {
                read("transactions", TransactionRow.serializer()) {
                    order("created_at", Order.DESCENDING)
                    limit(TRANSACTIONS_LIMIT)
                }
            }
            val notifications = async {
                read("notifications", NotificationRow.serializer()) {
                    order("created_at", Order.DESCENDING)
                    limit(NOTIFICATIONS_LIMIT)
                }
            }
            val beneficiaries = async {
                read("beneficiaries", BeneficiaryRow.serializer()) {
                    order("last_used_at", Order.DESCENDING)
                }
            }
            val banks = async { read("banks", BankRow.serializer()) }
            val savings = async { read("savings_positions", SavingsPositionRow.serializer()) }
            val profile = profiles.await().firstOrNull()
                ?: throw BackendError(BackendCodes.NOT_FOUND, BackendCodes.messageFor(BackendCodes.NOT_FOUND))
            RemoteSnapshot(
                profile = profile,
                account = accounts.await().firstOrNull(),
                transactions = transactions.await(),
                notifications = notifications.await(),
                beneficiaries = beneficiaries.await(),
                banks = banks.await(),
                savings = savings.await()
            )
        }
    }

    private suspend fun <T> read(
        table: String,
        serializer: KSerializer<T>,
        request: PostgrestRequestBuilder.() -> Unit = {}
    ): List<T> {
        val result = provider.client.postgrest.from(table).select(request = request)
        return json.decodeFromString(ListSerializer(serializer), result.data)
    }
}
