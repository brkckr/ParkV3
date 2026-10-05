package com.brkckr.parkv3.ui.main

import androidx.lifecycle.SavedStateHandle
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.location.LocationPermission
import com.brkckr.parkv3.location.LocationStatus
import com.brkckr.parkv3.testutil.FakeLocationProvider
import com.brkckr.parkv3.testutil.FakeParkRepository
import com.brkckr.parkv3.testutil.MainDispatcherRule
import com.brkckr.parkv3.testutil.park
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.MapStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeParkRepository()
    private val location = FakeLocationProvider()

    private val kadikoy = GeoPoint(40.9903, 29.0236)
    private val taksim = GeoPoint(41.0369, 28.9850)
    private val sisli = GeoPoint(41.0602, 28.9877)

    private val parks = listOf(
        park(1, name = "Kadıköy Rıhtım", district = "KADIKÖY", location = kadikoy, emptyCapacity = 30),
        park(2, name = "Moda Sahil", district = "KADIKÖY", location = GeoPoint(40.9810, 29.0260), emptyCapacity = 0),
        park(3, name = "Şişli Merkez", district = "ŞİŞLİ", location = sisli, openState = OpenState.CLOSED),
        park(4, name = "Beşiktaş Sahil", district = "BEŞİKTAŞ", location = GeoPoint(41.0422, 29.0083), openState = OpenState.UNKNOWN),
        park(5, name = "Ümraniye Meydan", district = "ÜMRANİYE", location = null, emptyCapacity = 5),
    )

    private fun viewModel(mapStatus: MapStatus = MapStatus.NO_API_KEY) =
        MainViewModel(repository, location, MapAvailability { mapStatus }, SavedStateHandle())

    /** Keeps the WhileSubscribed state flow active for the duration of the test. */
    private fun TestScope.collect(vm: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun MainViewModel.ids() = uiState.value.items.map { it.park.id }

    @Test
    fun `cold start asks the repository to refresh only if stale`() = runTest {
        viewModel()
        assertThat(repository.refreshIfOlderCalls).isEqualTo(1)
        assertThat(repository.listRefreshCalls).isEqualTo(0)
    }

    @Test
    fun `combined filters and Turkish search narrow the shared result`() = runTest {
        repository.parks.value = parks
        repository.favorites.value = setOf(1, 3, 5)
        val vm = viewModel()
        collect(vm)

        vm.onToggleOpenFilter()
        assertThat(vm.ids()).containsExactly(1, 2, 5)

        vm.onToggleAvailableFilter()
        assertThat(vm.ids()).containsExactly(1, 5)

        vm.onToggleFavoritesFilter()
        vm.onQueryChange("kadikoy")
        assertThat(vm.ids()).containsExactly(1)

        vm.onQueryChange("UMRANIYE")
        assertThat(vm.ids()).containsExactly(5)

        vm.onClearFilters()
        vm.onQueryChange("SAHİL")
        assertThat(vm.ids()).containsExactly(2, 4)
    }

    @Test
    fun `main flow works without location permission`() = runTest {
        repository.parks.value = parks
        val vm = viewModel()
        collect(vm)

        val state = vm.uiState.value
        assertThat(state.content).isEqualTo(ListContent.Items)
        assertThat(state.reference).isNull()
        assertThat(state.items.map { it.park.name })
            .containsExactly("Beşiktaş Sahil", "Kadıköy Rıhtım", "Moda Sahil", "Şişli Merkez", "Ümraniye Meydan").inOrder()

        val event = backgroundScope.async { vm.eventFlow.first() }
        vm.onLocateRequested()
        assertThat(event.await()).isEqualTo(MainEvent.RequestLocationPermission)
        assertThat(location.requests).isEqualTo(0)

        vm.onLocationPermissionResult(granted = false, canAskAgain = true)
        assertThat(vm.uiState.value.location).isEqualTo(LocationStatus.PermissionDenied)
        assertThat(vm.uiState.value.items).hasSize(5)

        vm.onLocationPermissionResult(granted = false, canAskAgain = false)
        assertThat(vm.uiState.value.location).isEqualTo(LocationStatus.PermissionPermanentlyDenied)
    }

    @Test
    fun `disabled location services are reported without requesting a fix`() = runTest {
        location.permission = LocationPermission.PRECISE
        location.enabled = false
        val vm = viewModel()
        collect(vm)

        vm.onLocateRequested()

        assertThat(vm.uiState.value.location).isEqualTo(LocationStatus.ServicesDisabled)
        assertThat(location.requests).isEqualTo(0)
    }

    @Test
    fun `approximate location sorts by straight-line distance and is flagged`() = runTest {
        repository.parks.value = parks
        location.permission = LocationPermission.APPROXIMATE
        location.result = taksim
        val vm = viewModel()
        collect(vm)

        vm.onLocateRequested()

        val state = vm.uiState.value
        assertThat(location.lastPrecise).isFalse()
        assertThat(state.location).isEqualTo(LocationStatus.Available(taksim, approximate = true))
        assertThat(state.reference).isEqualTo(ReferencePoint.UserLocation(taksim, approximate = true))
        assertThat(state.items.first().park.id).isEqualTo(4) // Beşiktaş is closest to Taksim
        assertThat(state.items.last().park.id).isEqualTo(5) // no location → last
        assertThat(state.items.last().distanceMeters).isNull()
    }

    @Test
    fun `each locate request asks for a new fix`() = runTest {
        location.permission = LocationPermission.PRECISE
        location.result = taksim
        val vm = viewModel()
        collect(vm)

        vm.onLocateRequested()
        vm.onLocateRequested()

        assertThat(location.requests).isEqualTo(2)
        assertThat(location.lastPrecise).isTrue()
    }

    @Test
    fun `a location that never arrives times out as unavailable`() = runTest {
        location.permission = LocationPermission.PRECISE
        location.hang = true
        val vm = viewModel()
        collect(vm)

        vm.onLocateRequested()
        assertThat(vm.uiState.value.location).isEqualTo(LocationStatus.Locating)

        advanceTimeBy(20_001)
        assertThat(vm.uiState.value.location).isEqualTo(LocationStatus.Unavailable)
    }

    @Test
    fun `a chosen destination takes precedence over the user location`() = runTest {
        repository.parks.value = parks
        location.permission = LocationPermission.PRECISE
        location.result = taksim
        val vm = viewModel()
        collect(vm)
        vm.onLocateRequested()

        vm.onSetDestination(kadikoy)

        assertThat(vm.uiState.value.reference).isEqualTo(ReferencePoint.Destination(kadikoy))
        assertThat(vm.ids().first()).isEqualTo(1)

        vm.onClearDestination()
        assertThat(vm.uiState.value.reference).isInstanceOf(ReferencePoint.UserLocation::class.java)
    }

    @Test
    fun `without cache a network failure is an offline screen and data replaces it`() = runTest {
        val vm = viewModel()
        collect(vm)
        repository.sync.value = SyncInfo(lastAttemptAtMillis = 1L, lastError = RefreshError.Network)
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.OfflineWithoutCache)

        repository.sync.value = SyncInfo(lastAttemptAtMillis = 1L, lastError = RefreshError.Http(503))
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.SourceErrorWithoutCache(RefreshError.Http(503)))

        repository.parks.value = parks
        repository.sync.value = SyncInfo(lastSuccessAtMillis = 2L, lastAttemptAtMillis = 2L)
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.Items)
    }

    @Test
    fun `with cache a failed refresh keeps the items and the data reads as stale`() = runTest {
        repository.parks.value = parks
        repository.sync.value = SyncInfo(lastSuccessAtMillis = 1_000L, lastAttemptAtMillis = 9_000L, lastError = RefreshError.Network)
        val vm = viewModel()
        collect(vm)

        val state = vm.uiState.value
        assertThat(state.content).isEqualTo(ListContent.Items)
        assertThat(state.items).hasSize(5)
        assertThat(FreshnessPolicy.isListStale(state.sync, nowMillis = 9_000L)).isTrue()
    }

    @Test
    fun `manual refresh failure is reported as an event`() = runTest {
        repository.listResults += RefreshResult.Failure(RefreshError.Http(500))
        val vm = viewModel()
        val event = backgroundScope.async { vm.eventFlow.first() }

        vm.onRefresh()

        assertThat(event.await()).isEqualTo(MainEvent.RefreshFailed(RefreshError.Http(500)))
    }

    @Test
    fun `empty result cases have distinct content`() = runTest {
        repository.parks.value = parks
        val vm = viewModel()
        collect(vm)

        vm.onToggleFavoritesFilter()
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.NoFavorites)

        vm.onClearFilters()
        vm.onQueryChange("  zzz ")
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.NoSearchResults("zzz"))

        vm.onClearQuery()
        repository.parks.value = parks.filter { it.openState != OpenState.OPEN }
        vm.onToggleAvailableFilter()
        assertThat(vm.uiState.value.content).isEqualTo(ListContent.NoFilterResults)
    }

    @Test
    fun `show on map selects the park and switches to the map when available`() = runTest {
        repository.parks.value = parks
        val vm = viewModel(mapStatus = MapStatus.AVAILABLE)
        collect(vm)
        vm.onViewModeChange(ViewMode.LIST)

        vm.onShowOnMap(3)

        assertThat(vm.uiState.value.viewMode).isEqualTo(ViewMode.MAP)
        assertThat(vm.uiState.value.selectedItem?.park?.id).isEqualTo(3)
    }

    @Test
    fun `without a map the list is the default view`() = runTest {
        val vm = viewModel(mapStatus = MapStatus.NO_API_KEY)
        collect(vm)
        assertThat(vm.uiState.value.viewMode).isEqualTo(ViewMode.LIST)
    }
}
