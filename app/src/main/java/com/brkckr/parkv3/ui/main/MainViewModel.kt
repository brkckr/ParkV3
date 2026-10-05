package com.brkckr.parkv3.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.ParkListQuery
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.AreaPolygon
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.location.LocationPermission
import com.brkckr.parkv3.location.LocationProvider
import com.brkckr.parkv3.location.LocationStatus
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.MapStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ParkRepository,
    private val locationProvider: LocationProvider,
    mapAvailability: MapAvailability,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val mapStatus = mapAvailability.status()

    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val availableOnly = savedState.getStateFlow(KEY_AVAILABLE, false)
    private val favoritesOnly = savedState.getStateFlow(KEY_FAVORITES, false)
    private val viewMode = savedState.getStateFlow(
        KEY_VIEW_MODE,
        if (mapStatus == MapStatus.AVAILABLE) ViewMode.MAP.name else ViewMode.LIST.name,
    )
    private val selectedParkId = savedState.getStateFlow<Int?>(KEY_SELECTED, null)
    private val destination = savedState.getStateFlow<DoubleArray?>(KEY_DESTINATION, null)
    private val location = MutableStateFlow<LocationStatus>(LocationStatus.Idle)
    private var locateJob: Job? = null

    private val events = Channel<MainEvent>(Channel.BUFFERED)
    val eventFlow: Flow<MainEvent> = events.receiveAsFlow()

    private val filters: Flow<ParkFilters> = combine(availableOnly, favoritesOnly) { available, favorites ->
        ParkFilters(availableOnly = available, favoritesOnly = favorites)
    }

    private data class Controls(
        val query: String,
        val filters: ParkFilters,
        val viewMode: ViewMode,
        val selectedParkId: Int?,
        val destination: GeoPoint?,
    )

    private val controls: Flow<Controls> = combine(query, filters, viewMode, selectedParkId, destination) { q, f, mode, selected, dest ->
        Controls(q, f, ViewMode.valueOf(mode), selected, dest?.let { GeoPoint(it[0], it[1]) })
    }

    private data class Content(
        val parks: List<Park>,
        val favoriteIds: Set<Int>,
        val orphans: List<OrphanFavorite>,
        val sync: SyncInfo,
        val isRefreshing: Boolean,
    )

    private val content: Flow<Content> = combine(
        repository.observeParks(),
        repository.observeFavoriteIds(),
        repository.observeOrphanFavorites(),
        repository.observeSyncInfo(),
        repository.isRefreshingList,
    ) { parks, favoriteIds, orphans, sync, refreshing -> Content(parks, favoriteIds, orphans, sync, refreshing) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedArea: Flow<List<AreaPolygon>> = selectedParkId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeDetail(id).map { it?.area.orEmpty() }
    }

    val uiState: StateFlow<MainUiState> = combine(controls, content, location, selectedArea) { c, data, loc, area ->
        val reference = c.destination ?: (loc as? LocationStatus.Available)?.point
        MainUiState(
            query = c.query,
            filters = c.filters,
            viewMode = c.viewMode,
            items = ParkListQuery.apply(data.parks, data.favoriteIds, c.query, c.filters, reference),
            totalCount = data.parks.size,
            favoriteCount = data.favoriteIds.size,
            orphanFavorites = data.orphans,
            selectedParkId = c.selectedParkId,
            selectedArea = area,
            destination = c.destination,
            location = loc,
            sync = data.sync,
            isRefreshing = data.isRefreshing,
            isCacheLoaded = true,
            mapStatus = mapStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            viewMode = ViewMode.valueOf(viewMode.value),
            mapStatus = mapStatus,
        ),
    )

    init {
        // Cold start: fetch unless the cache is fresh. Foreground returns are handled app-wide.
        viewModelScope.launch {
            repository.refreshParksIfOlderThan(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS)
        }
        // A park selected on the map gets its detail once, so its area outline can be drawn.
        // Failures stay silent: the selection card shows the list data either way.
        viewModelScope.launch {
            combine(selectedParkId, viewMode) { id, mode -> id.takeIf { mode == ViewMode.MAP.name } }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id != null && repository.observeDetail(id).first() == null) repository.refreshDetail(id)
                }
        }
    }

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun onClearQuery() = onQueryChange("")

    fun onToggleAvailableFilter() = toggle(KEY_AVAILABLE, availableOnly.value)

    fun onToggleFavoritesFilter() = toggle(KEY_FAVORITES, favoritesOnly.value)

    fun onClearFilters() {
        savedState[KEY_AVAILABLE] = false
        savedState[KEY_FAVORITES] = false
    }

    private fun toggle(key: String, current: Boolean) {
        savedState[key] = !current
    }

    fun onViewModeChange(mode: ViewMode) {
        savedState[KEY_VIEW_MODE] = mode.name
    }

    fun onSelectPark(parkId: Int?) {
        savedState[KEY_SELECTED] = parkId
    }

    /** Selects the park and switches to the map so list and map share the same selection. */
    fun onShowOnMap(parkId: Int) {
        onSelectPark(parkId)
        if (mapStatus == MapStatus.AVAILABLE) onViewModeChange(ViewMode.MAP)
    }

    fun onSetDestination(point: GeoPoint) {
        savedState[KEY_DESTINATION] = doubleArrayOf(point.latitude, point.longitude)
    }

    fun onClearDestination() {
        savedState[KEY_DESTINATION] = null
    }

    fun onToggleFavorite(parkId: Int, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(parkId, favorite) }
    }

    fun onRefresh() {
        viewModelScope.launch {
            when (val result = repository.refreshParks()) {
                is RefreshResult.Failure -> events.send(MainEvent.RefreshFailed(result.error))
                is RefreshResult.Success -> if (result.partial) events.send(MainEvent.RefreshPartial)
            }
        }
    }

    /** "My location" button: always asks for a fresh fix. */
    fun onLocateRequested() {
        if (locationProvider.permission() == LocationPermission.NONE) {
            events.trySend(MainEvent.RequestLocationPermission)
        } else {
            locate()
        }
    }

    /** Result of the system permission dialog; [canAskAgain] is false after "don't ask again". */
    fun onLocationPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        when {
            granted || locationProvider.permission() != LocationPermission.NONE -> locate()
            canAskAgain -> location.value = LocationStatus.PermissionDenied
            else -> location.value = LocationStatus.PermissionPermanentlyDenied
        }
    }

    fun onDismissLocationMessage() {
        if (location.value !is LocationStatus.Available) location.value = LocationStatus.Idle
    }

    private fun locate() {
        if (!locationProvider.isLocationEnabled()) {
            location.value = LocationStatus.ServicesDisabled
            return
        }
        val precise = locationProvider.permission() == LocationPermission.PRECISE
        locateJob?.cancel()
        location.value = LocationStatus.Locating
        locateJob = viewModelScope.launch {
            val point = withTimeoutOrNull(LOCATE_TIMEOUT_MS) { locationProvider.currentLocation(precise) }
            location.value = if (point != null) {
                LocationStatus.Available(point, approximate = !precise)
            } else {
                LocationStatus.Unavailable
            }
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_AVAILABLE = "filter_available"
        const val KEY_FAVORITES = "filter_favorites"
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_SELECTED = "selected_park"
        const val KEY_DESTINATION = "destination"
        const val LOCATE_TIMEOUT_MS = 20_000L
    }
}
