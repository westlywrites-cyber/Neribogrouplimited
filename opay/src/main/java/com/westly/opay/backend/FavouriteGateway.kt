package com.westly.opay.backend

/** The one backend call a favourite star needs. A fake of this is used in unit tests. */
interface FavouriteGateway {
    /** False while the phone has no connection. */
    fun isOnline(): Boolean

    /** Flips the favourite flag on the server and returns the flag as the server now holds it. */
    suspend fun toggle(accountNumber: String, bankCode: String): BackendResult<Boolean>
}

/** Flips the flag through the `toggle_favourite` database function. */
class RpcFavouriteGateway(
    private val rpc: RpcApi,
    private val online: () -> Boolean
) : FavouriteGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun toggle(accountNumber: String, bankCode: String): BackendResult<Boolean> =
        when (val result = rpc.toggleFavourite(accountNumber, bankCode)) {
            is BackendResult.Success -> BackendResult.Success(result.value.isFavourite)
            is BackendResult.Failure -> BackendResult.Failure(result.error)
        }
}
