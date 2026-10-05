package com.brkckr.parkv3.data.sync

import com.brkckr.parkv3.connectivity.NetworkMonitor
import com.brkckr.parkv3.connectivity.reconnections
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Automatic list refreshes while the app is in the foreground (docs/adr/0005): a staleness
 * check when it comes to the foreground, and a retry when the connection comes back.
 */
class ListRefreshTriggers @Inject constructor(
    private val repository: ParkRepository,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
) {
    /** Call while the app is started; runs until cancelled. */
    suspend fun runWhileForeground() {
        coroutineScope {
            launch { repository.refreshParksIfOlderThan(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS) }
            networkMonitor.isOnline.reconnections().collect {
                if (FreshnessPolicy.shouldRefreshListOnReconnect(repository.observeSyncInfo().first(), clock.nowMillis())) {
                    repository.refreshParks()
                }
            }
        }
    }
}
