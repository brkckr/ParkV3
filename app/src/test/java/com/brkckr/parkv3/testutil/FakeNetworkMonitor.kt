package com.brkckr.parkv3.testutil

import com.brkckr.parkv3.connectivity.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(initiallyOnline)

    override val isOnline: Flow<Boolean> = online
}
