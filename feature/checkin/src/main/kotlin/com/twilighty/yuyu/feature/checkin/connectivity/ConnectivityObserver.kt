package com.twilighty.yuyu.feature.checkin.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the device has a usable internet connection. */
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
}

/** [ConnectivityManager] network callback: online while any network offers internet. */
@Singleton
class AndroidConnectivityObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityObserver {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val online = mutableSetOf<Network>()
        fun publish() = trySend(synchronized(online) { online.isNotEmpty() })
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                // Not requiring VALIDATED: a self-hosted server may sit on a venue LAN without
                // internet. Requests that fail still fall back to the offline roster.
                val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                synchronized(online) { if (usable) online += network else online -= network }
                publish()
            }

            override fun onLost(network: Network) {
                synchronized(online) { online -= network }
                publish()
            }
        }
        val current = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(current?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(request, callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectivityModule {
    @Binds abstract fun bindsConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver
}
