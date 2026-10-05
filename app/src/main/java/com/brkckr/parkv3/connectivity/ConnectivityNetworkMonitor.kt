package com.brkckr.parkv3.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean>
        get() {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return flowOf(true)
            return callbackFlow {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(capabilities.hasWorkingInternet())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }
                // Snapshot first: registering delivers the current default network right away,
                // so anything that changes after the snapshot still arrives through the callback.
                // Some devices throw SecurityException from these calls. Automatic retry is only
                // a convenience, so the monitor then assumes "online" and stays quiet.
                trySend(runCatching { connectivity.isOnlineNow() }.getOrDefault(true))
                val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback) }.isSuccess
                awaitClose { if (registered) connectivity.unregisterNetworkCallback(callback) }
            }.conflate().distinctUntilChanged()
        }
}

private fun ConnectivityManager.isOnlineNow(): Boolean =
    activeNetwork?.let { getNetworkCapabilities(it) }?.hasWorkingInternet() == true

/** "Validated" means Android has checked that the network actually reaches the internet. */
internal fun NetworkCapabilities.hasWorkingInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
