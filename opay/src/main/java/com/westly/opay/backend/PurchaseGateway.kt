package com.westly.opay.backend

/** The one backend call a purchase needs. A fake of this is used in unit tests. */
interface PurchaseGateway {
    /** False while the phone has no connection. A purchase is never sent, queued or retried in that state. */
    fun isOnline(): Boolean

    /** Records the purchase through the server and returns the new transaction row. [type] is `Airtime`, `Data` or `Bill`. */
    suspend fun purchase(
        type: String,
        title: String,
        amountKobo: Long,
        counterparty: String?
    ): BackendResult<TransactionRow>
}

/** Sends the purchase through the `purchase` database function, exactly once per call. */
class RpcPurchaseGateway(
    private val rpc: RpcApi,
    private val online: () -> Boolean
) : PurchaseGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun purchase(
        type: String,
        title: String,
        amountKobo: Long,
        counterparty: String?
    ): BackendResult<TransactionRow> = rpc.purchase(type, title, amountKobo, counterparty)
}
