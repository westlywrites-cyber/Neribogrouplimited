package com.westly.opay.backend

/** The one backend call the notifications screen needs. A fake of this is used in unit tests. */
interface NotificationGateway {
    /** False while the phone has no connection. */
    fun isOnline(): Boolean

    /** Marks every notification of the signed-in user read on the server. */
    suspend fun markAllRead(): BackendResult<Unit>
}

/** Marks the notifications read through the `mark_notifications_read` database function. */
class RpcNotificationGateway(
    private val rpc: RpcApi,
    private val online: () -> Boolean
) : NotificationGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun markAllRead(): BackendResult<Unit> = rpc.markNotificationsRead()
}
