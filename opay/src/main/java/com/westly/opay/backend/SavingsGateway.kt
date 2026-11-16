package com.westly.opay.backend

import kotlinx.serialization.json.JsonObject

/** The backend calls savings need. A fake of this is used in unit tests. */
interface SavingsGateway {
    /** False while the phone has no connection. A savings action is never sent, queued or retried in that state. */
    fun isOnline(): Boolean

    suspend fun create(
        kind: String,
        name: String,
        amountKobo: Long,
        targetKobo: Long?,
        maturesAt: String?,
        meta: JsonObject?
    ): BackendResult<SavingsPositionRow>

    suspend fun deposit(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow>

    suspend fun withdraw(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow>
}

/** Sends savings actions through `savings_create`, `savings_deposit` and `savings_withdraw`, once per call. */
class RpcSavingsGateway(
    private val rpc: RpcApi,
    private val online: () -> Boolean
) : SavingsGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun create(
        kind: String,
        name: String,
        amountKobo: Long,
        targetKobo: Long?,
        maturesAt: String?,
        meta: JsonObject?
    ): BackendResult<SavingsPositionRow> = rpc.savingsCreate(kind, name, amountKobo, targetKobo, maturesAt, meta)

    override suspend fun deposit(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow> =
        rpc.savingsDeposit(positionId, amountKobo)

    override suspend fun withdraw(positionId: String, amountKobo: Long): BackendResult<SavingsPositionRow> =
        rpc.savingsWithdraw(positionId, amountKobo)
}
