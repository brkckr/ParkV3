package com.brkckr.parkv3.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.testutil.FakeNetworkMonitor
import com.brkckr.parkv3.testutil.FakeParkRepository
import com.brkckr.parkv3.testutil.MainDispatcherRule
import com.brkckr.parkv3.testutil.MutableClock
import com.brkckr.parkv3.testutil.park
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.MapStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeParkRepository()
    private val clock = MutableClock(now = 1_800_000_000_000L)
    private val network = FakeNetworkMonitor()

    private fun detail(parkId: Int = 7, fetchedAt: Long = clock.now, capacity: Int? = 100, empty: Int? = 40) = ParkDetail(
        parkId = parkId,
        name = "Detay adı",
        district = "FATİH",
        address = "Adres 1",
        parkType = "KAPALI OTOPARK",
        workHours = "24 Saat",
        location = GeoPoint(41.01, 28.95),
        capacity = capacity,
        emptyCapacity = empty,
        freeTime = 15,
        monthlyFee = 0.0,
        tariffLines = emptyList(),
        sourceUpdatedAt = null,
        fetchedAtMillis = fetchedAt,
    )

    private fun viewModel(parkId: Int = 7) =
        DetailViewModel(repository, clock, network, MapAvailability { MapStatus.NO_API_KEY }, SavedStateHandle(mapOf(DetailViewModel.ARG_PARK_ID to parkId)))

    private fun TestScope.collect(vm: DetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `retry works after the first load fails`() = runTest {
        repository.detailResults += RefreshResult.Failure(RefreshError.Network) to null
        repository.detailResults += RefreshResult.Success() to detail()

        val vm = viewModel()
        collect(vm)
        vm.onForeground()
        assertThat(vm.uiState.value.content).isEqualTo(DetailContent.Error(RefreshError.Network))

        vm.onRetry()

        assertThat(vm.uiState.value.content).isEqualTo(DetailContent.Full)
        assertThat(vm.uiState.value.detail?.address).isEqualTo("Adres 1")
        assertThat(vm.uiState.value.error).isNull()
        assertThat(repository.detailRefreshCalls).isEqualTo(2)
    }

    @Test
    fun `retry can fail again and stays retryable`() = runTest {
        repository.detailResults += RefreshResult.Failure(RefreshError.Http(500)) to null
        repository.detailResults += RefreshResult.Failure(RefreshError.Network) to null
        val vm = viewModel()
        collect(vm)
        vm.onForeground()

        vm.onRetry()

        assertThat(vm.uiState.value.content).isEqualTo(DetailContent.Error(RefreshError.Network))
    }

    @Test
    fun `list data is shown when the detail cannot be loaded`() = runTest {
        repository.parks.value = listOf(park(7, openState = OpenState.CLOSED))
        repository.detailResults += RefreshResult.Failure(RefreshError.Http(503)) to null
        val vm = viewModel()
        collect(vm)
        vm.onForeground()

        val state = vm.uiState.value
        assertThat(state.content).isEqualTo(DetailContent.ListDataOnly)
        assertThat(state.error).isEqualTo(RefreshError.Http(503))
        assertThat(state.availability).isEqualTo(Availability.CLOSED)
    }

    @Test
    fun `missing isOpen stays unknown while an explicit closed state is closed`() = runTest {
        repository.parks.value = listOf(park(7, openState = OpenState.UNKNOWN), park(8, openState = OpenState.CLOSED))
        repository.details.value = mapOf(7 to detail(7), 8 to detail(8))

        val unknown = viewModel(7).also { collect(it) }
        val closed = viewModel(8).also { collect(it) }

        assertThat(unknown.uiState.value.availability).isEqualTo(Availability.UNKNOWN)
        assertThat(closed.uiState.value.availability).isEqualTo(Availability.CLOSED)
    }

    @Test
    fun `a fresh cached detail is not fetched again`() = runTest {
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now - 60_000))
        viewModel().onForeground()
        assertThat(repository.detailRefreshCalls).isEqualTo(0)
    }

    @Test
    fun `a stale cached detail is refreshed on open and on returning to the foreground`() = runTest {
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now - 10 * 60_000))
        val vm = viewModel()
        assertThat(repository.detailRefreshCalls).isEqualTo(0) // nothing before the screen starts

        vm.onForeground()
        assertThat(repository.detailRefreshCalls).isEqualTo(1)

        clock.advanceBy(60_000) // the cached copy is still stale: the refresh above stored nothing
        vm.onForeground()
        assertThat(repository.detailRefreshCalls).isEqualTo(2)
    }

    @Test
    fun `occupancy comes from the more recently downloaded source`() = runTest {
        repository.parks.value = listOf(park(7, capacity = 100, emptyCapacity = 5))
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now - 60_000, capacity = 100, empty = 40))

        repository.sync.value = SyncInfo(lastSuccessAtMillis = clock.now - 120_000)
        val vm = viewModel()
        collect(vm)
        assertThat(vm.uiState.value.occupancy).isEqualTo(Occupancy.Known(100, 40))

        repository.sync.value = SyncInfo(lastSuccessAtMillis = clock.now)
        assertThat(vm.uiState.value.occupancy).isEqualTo(Occupancy.Known(100, 5))
    }

    @Test
    fun `favorite toggles from the detail screen`() = runTest {
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now))
        val vm = viewModel()
        collect(vm)

        vm.onToggleFavorite()
        assertThat(repository.favorites.value).containsExactly(7)
        assertThat(vm.uiState.value.isFavorite).isTrue()

        vm.onToggleFavorite()
        assertThat(repository.favorites.value).isEmpty()
    }

    @Test
    fun `a park no longer listed by the source is flagged`() = runTest {
        repository.unlistedParks.value = listOf(park(7))
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now))
        val vm = viewModel()
        collect(vm)

        assertThat(vm.uiState.value.park).isNotNull()
        assertThat(vm.uiState.value.isListed).isFalse()
    }

    @Test
    fun `a detail that failed for lack of network loads when the connection comes back`() = runTest {
        network.online.value = false
        repository.detailResults += RefreshResult.Failure(RefreshError.Network) to null
        repository.detailResults += RefreshResult.Success() to detail()
        val vm = viewModel()
        collect(vm)
        vm.onForeground()
        assertThat(vm.uiState.value.content).isEqualTo(DetailContent.Error(RefreshError.Network))

        network.online.value = true

        assertThat(vm.uiState.value.content).isEqualTo(DetailContent.Full)
        assertThat(repository.detailRefreshCalls).isEqualTo(2)
    }

    @Test
    fun `a server error is not retried on reconnect while the cached detail is fresh`() = runTest {
        repository.details.value = mapOf(7 to detail(fetchedAt = clock.now))
        repository.detailResults += RefreshResult.Failure(RefreshError.Http(503)) to null
        val vm = viewModel()
        collect(vm)
        vm.onForeground()
        vm.onRetry()
        assertThat(vm.uiState.value.error).isEqualTo(RefreshError.Http(503))

        network.online.value = false
        network.online.value = true

        assertThat(repository.detailRefreshCalls).isEqualTo(1)
    }

    @Test
    fun `a stopped screen does not retry when the connection comes back`() = runTest {
        network.online.value = false
        repository.detailResults += RefreshResult.Failure(RefreshError.Network) to null
        val vm = viewModel()
        collect(vm)
        vm.onForeground()
        vm.onBackground()

        network.online.value = true

        assertThat(repository.detailRefreshCalls).isEqualTo(1)
    }
}
