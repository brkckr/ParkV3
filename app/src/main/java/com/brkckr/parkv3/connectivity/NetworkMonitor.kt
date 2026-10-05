package com.brkckr.parkv3.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Whether the device has a working internet connection. It only decides when to retry
 * automatically; requests are never blocked on it (docs/adr/0005).
 */
interface NetworkMonitor {
    /** Emits the current state on collection, then every change. */
    val isOnline: Flow<Boolean>
}

/** Emits each time the connection comes back after this collector has seen it lost. */
fun Flow<Boolean>.reconnections(): Flow<Unit> = flow {
    var lost = false
    collect { online ->
        if (online && lost) emit(Unit)
        lost = !online
    }
}
