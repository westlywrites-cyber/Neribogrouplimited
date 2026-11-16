package com.westly.opay.backend

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything the app needs to talk to the backend. Nothing here opens the network or the secure store at creation. */
class Backend(context: Context) {
    private val appContext = context.applicationContext

    val secureStore = SecureStore(appContext)
    val deviceIdentity = DeviceIdentity(secureStore)
    val provider = SupabaseProvider(secureStore)
    val edge = EdgeApi(provider)
    val rpc = RpcApi(provider)
    val session = SessionRepository(provider, secureStore, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    fun isOnline(): Boolean = isOnline(appContext)
}
