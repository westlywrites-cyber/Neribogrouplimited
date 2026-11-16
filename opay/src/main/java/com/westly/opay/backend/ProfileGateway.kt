package com.westly.opay.backend

import kotlinx.serialization.json.JsonObject

/** The one backend call the synced settings need. A fake of this is used in unit tests. */
interface ProfileGateway {
    /** False while the phone has no connection. */
    fun isOnline(): Boolean

    /** `update_profile`. Null arguments are left out, so the server keeps those columns; [settings] is merged. */
    suspend fun update(
        nickname: String?,
        themeMode: String?,
        hideBalance: Boolean?,
        settings: JsonObject?
    ): BackendResult<ProfileRow>
}

/** Saves the settings through the `update_profile` database function. */
class RpcProfileGateway(
    private val rpc: RpcApi,
    private val online: () -> Boolean
) : ProfileGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun update(
        nickname: String?,
        themeMode: String?,
        hideBalance: Boolean?,
        settings: JsonObject?
    ): BackendResult<ProfileRow> = rpc.updateProfile(
        nickname = nickname,
        themeMode = themeMode,
        hideBalance = hideBalance,
        settings = settings
    )
}
