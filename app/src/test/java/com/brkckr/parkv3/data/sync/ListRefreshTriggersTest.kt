package com.brkckr.parkv3.data.sync

import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.testutil.FakeNetworkMonitor
import com.brkckr.parkv3.testutil.FakeParkRepository
import com.brkckr.parkv3.testutil.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ListRefreshTriggersTest {

    private val repository = FakeParkRepository()
    private val network = FakeNetworkMonitor()
    private val clock = MutableClock(now = 1_800_000_000_000L)
    private val triggers = ListRefreshTriggers(repository, network, clock)

    private fun TestScope.startForeground(): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { triggers.runWhileForeground() }

    @Test
    fun `coming to the foreground runs the staleness check once`() = runTest {
        startForeground()

        assertThat(repository.refreshIfOlderCalls).isEqualTo(1)
        assertThat(repository.listRefreshCalls).isEqualTo(0)
    }

    @Test
    fun `a refresh that failed for lack of network is retried when the connection comes back`() = runTest {
        network.online.value = false
        repository.sync.value = SyncInfo(clock.now - 60_000, clock.now, RefreshError.Network)
        startForeground()

        network.online.value = true

        assertThat(repository.listRefreshCalls).isEqualTo(1)
    }

    @Test
    fun `a server error is not retried on reconnect while the list is fresh`() = runTest {
        network.online.value = false
        repository.sync.value = SyncInfo(clock.now - 60_000, clock.now, RefreshError.Http(503))
        startForeground()

        network.online.value = true

        assertThat(repository.listRefreshCalls).isEqualTo(0)
    }

    @Test
    fun `a list that became due while offline is refreshed on reconnect`() = runTest {
        repository.sync.value = SyncInfo(lastSuccessAtMillis = clock.now - 60_000)
        startForeground()

        network.online.value = false
        clock.advanceBy(10 * 60_000)
        network.online.value = true

        assertThat(repository.listRefreshCalls).isEqualTo(1)
    }

    @Test
    fun `nothing is retried after the app leaves the foreground`() = runTest {
        repository.sync.value = SyncInfo(lastError = RefreshError.Network)
        val foreground = startForeground()
        network.online.value = false

        foreground.cancel()
        network.online.value = true

        assertThat(repository.listRefreshCalls).isEqualTo(0)
    }
}
