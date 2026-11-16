package com.westly.opay.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Tells whether the phone has a connection. The first value is the state at the time of collecting. */
interface NetworkMonitor {
    val online: Flow<Boolean>
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val appContext = context.applicationContext

    override val online: Flow<Boolean> = callbackFlow {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline(appContext))
            }
        }
        var registered = false
        if (manager != null) {
            try {
                manager.registerDefaultNetworkCallback(callback)
                registered = true
            } catch (e: RuntimeException) {
                // Without the callback only the state read below is reported.
            }
        }
        trySend(isOnline(appContext))
        awaitClose {
            if (registered && manager != null) manager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
