package com.westly.opay.backend

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Page size of the statement read. It is the largest page the Supabase API returns by default. */
const val STATEMENT_PAGE_SIZE = 1000L

private const val TRANSACTIONS_TABLE = "transactions"
private const val PROFILES_TABLE = "profiles"
private const val STATUS_SUCCESSFUL = "Successful"

/** The successful transactions a statement needs. [before] and [after] are the nearest rows outside the period. */
data class StatementRows(
    val inPeriod: List<TransactionRow>,
    val before: TransactionRow?,
    val after: TransactionRow?
)

/** The reads the Download Statement feature needs. Read only. A fake of this is used in tests. */
interface StatementGateway {
    /** False while the phone has no connection. */
    fun isOnline(): Boolean

    /** When the signed-in user's account was created, as the server stores it (`profiles.created_at`). */
    suspend fun accountCreatedAt(): BackendResult<String>

    /**
     * The successful transactions of the signed-in user created from [fromInclusive] up to, but not including,
     * [toExclusive], oldest first, read page by page. [StatementRows.after] is only read when nothing lies in the
     * period and nothing lies before it, because it is then the only way to know the balance at the period start.
     */
    suspend fun rowsBetween(fromInclusive: Instant, toExclusive: Instant): BackendResult<StatementRows>
}

/** Reads the rows through Postgrest. Row-level security limits every table to the signed-in user's own rows. */
class PostgrestStatementGateway(
    private val provider: SupabaseProvider,
    private val online: () -> Boolean
) : StatementGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override fun isOnline(): Boolean = online()

    override suspend fun accountCreatedAt(): BackendResult<String> = backendCall {
        val result = provider.client.postgrest.from(PROFILES_TABLE).select()
        val rows = json.decodeFromString(ListSerializer(ProfileRow.serializer()), result.data)
        rows.firstOrNull()?.createdAt
            ?: throw BackendError(BackendCodes.NOT_FOUND, BackendCodes.messageFor(BackendCodes.NOT_FOUND))
    }

    override suspend fun rowsBetween(
        fromInclusive: Instant,
        toExclusive: Instant
    ): BackendResult<StatementRows> = backendCall {
        val from = fromInclusive.toString()
        val to = toExclusive.toString()
        val inPeriod = ArrayList<TransactionRow>()
        val seen = HashSet<String>()
        var offset = 0L
        while (true) {
            val page = readRows(
                request = { start, end ->
                    filter {
                        gte("created_at", from)
                        lt("created_at", to)
                        eq("status", STATUS_SUCCESSFUL)
                    }
                    order("created_at", Order.ASCENDING)
                    range(start, end)
                },
                start = offset,
                end = offset + STATEMENT_PAGE_SIZE - 1
            )
            page.filter { seen.add(it.id) }.forEach { inPeriod.add(it) }
            if (page.size < STATEMENT_PAGE_SIZE) break
            offset += STATEMENT_PAGE_SIZE
        }
        val before = readRows(
            request = { start, end ->
                filter {
                    lt("created_at", from)
                    eq("status", STATUS_SUCCESSFUL)
                }
                order("created_at", Order.DESCENDING)
                range(start, end)
            },
            start = 0L,
            end = 0L
        ).firstOrNull()
        val after = if (inPeriod.isEmpty() && before == null) {
            readRows(
                request = { start, end ->
                    filter {
                        gte("created_at", to)
                        eq("status", STATUS_SUCCESSFUL)
                    }
                    order("created_at", Order.ASCENDING)
                    range(start, end)
                },
                start = 0L,
                end = 0L
            ).firstOrNull()
        } else {
            null
        }
        StatementRows(inPeriod = inPeriod, before = before, after = after)
    }

    private suspend fun readRows(
        request: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.(Long, Long) -> Unit,
        start: Long,
        end: Long
    ): List<TransactionRow> {
        val result = provider.client.postgrest.from(TRANSACTIONS_TABLE).select {
            request(start, end)
        }
        return json.decodeFromString(ListSerializer(TransactionRow.serializer()), result.data)
    }
}
