package com.brkckr.parkv3.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.ui.map.MapAvailability
import com.brkckr.parkv3.ui.map.MapStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailContent {
    data object Loading : DetailContent

    /** Nothing cached for this park and the fetch failed; a retry is offered. */
    data class Error(val error: RefreshError) : DetailContent

    /** Only the list record is available (detail loading or failed). */
    data object ListDataOnly : DetailContent
    data object Full : DetailContent
}

data class DetailUiState(
    val parkId: Int,
    /** List record; the source of the open/closed state (ParkDetay has none). */
    val park: Park? = null,
    val isListed: Boolean = false,
    val detail: ParkDetail? = null,
    val isFavorite: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Outcome of the last detail request made by this screen. */
    val error: RefreshError? = null,
    val listUpdatedAtMillis: Long? = null,
    val isLoaded: Boolean = false,
) {
    /** Occupancy from whichever source was downloaded more recently. */
    private val detailIsNewer: Boolean
        get() = detail != null && (listUpdatedAtMillis == null || detail.fetchedAtMillis >= listUpdatedAtMillis)

    val capacity: Int? get() = if (detailIsNewer) detail?.capacity else park?.capacity ?: detail?.capacity
    val occupancy: Occupancy
        get() = when {
            detailIsNewer -> detail!!.occupancy
            park != null -> park.occupancy
            else -> Occupancy.Missing
        }
    val openState: OpenState get() = park?.openState ?: OpenState.UNKNOWN
    val availability: Availability get() = Availability.of(openState, occupancy)
    val name: String? get() = detail?.name ?: park?.name
    val district: String? get() = detail?.district ?: park?.district
    val location: GeoPoint? get() = detail?.location ?: park?.location
    val workHours: String? get() = detail?.workHours ?: park?.workHours
    val parkType: String? get() = detail?.parkType ?: park?.parkType
    val freeTime: Int? get() = detail?.freeTime ?: park?.freeTime

    val content: DetailContent
        get() = when {
            !isLoaded -> DetailContent.Loading
            detail != null -> DetailContent.Full
            park != null -> DetailContent.ListDataOnly
            error != null && !isRefreshing -> DetailContent.Error(error)
            else -> DetailContent.Loading
        }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ParkRepository,
    private val clock: Clock,
    mapAvailability: MapAvailability,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val canShowOnMap: Boolean = mapAvailability.status() == MapStatus.AVAILABLE

    private val parkId: Int = checkNotNull(savedStateHandle.get<Int>(ARG_PARK_ID)) { "Missing $ARG_PARK_ID" }
    private val lastError = MutableStateFlow<RefreshError?>(null)

    private val listed = repository.observeParks().map { parks -> parks.any { it.id == parkId } }
    private val refreshing = repository.observeRefreshingDetails().map { parkId in it }

    val uiState: StateFlow<DetailUiState> = combine(
        combine(repository.observePark(parkId), listed, repository.observeDetail(parkId)) { p, l, d -> Triple(p, l, d) },
        repository.observeFavoriteIds().map { parkId in it },
        refreshing,
        lastError,
        repository.observeSyncInfo(),
    ) { (park, isListed, detail), favorite, isRefreshing, error, sync ->
        DetailUiState(
            parkId = parkId,
            park = park,
            isListed = isListed,
            detail = detail,
            isFavorite = favorite,
            isRefreshing = isRefreshing,
            error = error,
            listUpdatedAtMillis = sync.lastSuccessAtMillis,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState(parkId))

    private var refreshJob: Job? = null

    /**
     * Called each time the screen starts (first open and every return to the foreground);
     * the single trigger avoids racing duplicate requests on open.
     */
    fun onForeground() = refreshIfStale()

    fun onRetry() = refresh()

    fun onToggleFavorite() {
        val favorite = !uiState.value.isFavorite
        viewModelScope.launch { repository.setFavorite(parkId, favorite) }
    }

    private fun refreshIfStale() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val fetchedAt = repository.observeDetail(parkId).first()?.fetchedAtMillis
            if (FreshnessPolicy.isOlderThan(fetchedAt, clock.nowMillis(), FreshnessPolicy.DETAIL_REFRESH_AFTER_MS)) {
                refreshNow()
            }
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        lastError.value = when (val result = repository.refreshDetail(parkId)) {
            is RefreshResult.Success -> null
            is RefreshResult.Failure -> result.error
        }
    }

    companion object {
        /** Matches DetailDestination.parkId (navigation stores route arguments by name). */
        const val ARG_PARK_ID = "parkId"
    }
}
