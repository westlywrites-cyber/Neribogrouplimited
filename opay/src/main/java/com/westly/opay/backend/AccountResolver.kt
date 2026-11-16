package com.westly.opay.backend

/** The one backend call account-name lookup needs. A fake of this is used in unit tests. */
interface AccountResolver {
    suspend fun resolve(accountNumber: String, bankCode: String): BackendResult<ResolveAccountResponse>
}

/** Answers lookups through the `resolve-account` Edge Function. */
class EdgeAccountResolver(private val edge: EdgeApi) : AccountResolver {
    override suspend fun resolve(accountNumber: String, bankCode: String): BackendResult<ResolveAccountResponse> =
        edge.resolveAccount(ResolveAccountRequest(accountNumber, bankCode))
}
