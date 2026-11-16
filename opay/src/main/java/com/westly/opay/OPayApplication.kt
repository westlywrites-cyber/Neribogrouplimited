package com.westly.opay

import android.app.Application
import com.westly.opay.backend.AndroidNetworkMonitor
import com.westly.opay.backend.Backend
import com.westly.opay.backend.EdgeAccountResolver
import com.westly.opay.backend.RemoteDataSource
import com.westly.opay.backend.RpcProfileGateway
import com.westly.opay.backend.RpcNotificationGateway
import com.westly.opay.backend.RpcPurchaseGateway
import com.westly.opay.backend.RpcSavingsGateway
import com.westly.opay.backend.RpcFavouriteGateway
import com.westly.opay.backend.RpcTransferGateway
import com.westly.opay.backend.SupabaseRealtimeSource
import com.westly.opay.core.security.AppLock
import com.westly.opay.data.AppRepository
import com.westly.opay.data.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The OPay part of NCO. Everything is created the first time it is needed (not when the app starts), so NCO opens
 * as fast as before and only does OPay's work once OPay is opened.
 */
class OPayApplication : Application() {
    val backend: Backend by lazy { Backend(this) }

    val repository: AppRepository by lazy {
        AppRepository(
            store = AppStateStore(this),
            remote = RemoteDataSource(backend.provider),
            sessions = backend.session.sessionState,
            realtime = SupabaseRealtimeSource(backend.provider),
            network = AndroidNetworkMonitor(this),
            resolver = EdgeAccountResolver(backend.edge),
            transfers = RpcTransferGateway(backend.rpc, backend.edge, backend.deviceIdentity, backend::isOnline),
            favouriteGateway = RpcFavouriteGateway(backend.rpc, backend::isOnline),
            notificationGateway = RpcNotificationGateway(backend.rpc, backend::isOnline),
            purchaseGateway = RpcPurchaseGateway(backend.rpc, backend::isOnline),
            savingsGateway = RpcSavingsGateway(backend.rpc, backend::isOnline),
            profileGateway = RpcProfileGateway(backend.rpc, backend::isOnline)
        )
    }

    val appLock: AppLock by lazy {
        AppLock(backend, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)).also { it.start() }
    }
}
