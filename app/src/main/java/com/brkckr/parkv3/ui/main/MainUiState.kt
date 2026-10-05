package com.brkckr.parkv3.ui.main

import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.ParkListItem
import com.brkckr.parkv3.domain.model.AreaPolygon
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.location.LocationStatus
import com.brkckr.parkv3.ui.map.MapStatus

enum class ViewMode { MAP, LIST }

/** The point distances are measured from: a chosen destination wins over the user location. */
sealed interface ReferencePoint {
    val point: GeoPoint

    data class Destination(override val point: GeoPoint) : ReferencePoint
    data class UserLocation(override val point: GeoPoint, val approximate: Boolean) : ReferencePoint
}

/** What the list area shows; each empty case has its own explanatory screen. */
sealed interface ListContent {
    data object Loading : ListContent
    data object OfflineWithoutCache : ListContent
    data class SourceErrorWithoutCache(val error: RefreshError) : ListContent
    data object NoFavorites : ListContent
    data class NoSearchResults(val query: String) : ListContent
    data object NoFilterResults : ListContent
    data object Items : ListContent
}

data class MainUiState(
    val query: String = "",
    val filters: ParkFilters = ParkFilters(),
    val viewMode: ViewMode = ViewMode.LIST,
    /** Search and filter result, shared by the map and the list. */
    val items: List<ParkListItem> = emptyList(),
    /** Parks currently listed by the source, before search and filters. */
    val totalCount: Int = 0,
    val favoriteCount: Int = 0,
    val orphanFavorites: List<OrphanFavorite> = emptyList(),
    val selectedParkId: Int? = null,
    /** Area outline of the selected park from its cached detail; empty if unknown or invalid. */
    val selectedArea: List<AreaPolygon> = emptyList(),
    val destination: GeoPoint? = null,
    val location: LocationStatus = LocationStatus.Idle,
    /** Persisted sync bookkeeping (content freshness). */
    val sync: SyncInfo = SyncInfo(),
    /** Network activity, modelled apart from content so it never hides it. */
    val isRefreshing: Boolean = false,
    val isCacheLoaded: Boolean = false,
    val mapStatus: MapStatus = MapStatus.NO_API_KEY,
) {
    val reference: ReferencePoint?
        get() = destination?.let { ReferencePoint.Destination(it) }
            ?: (location as? LocationStatus.Available)?.let { ReferencePoint.UserLocation(it.point, it.approximate) }

    val selectedItem: ParkListItem?
        get() = selectedParkId?.let { id -> items.firstOrNull { it.park.id == id } }

    val content: ListContent
        get() = when {
            totalCount == 0 -> when {
                !isCacheLoaded || isRefreshing -> ListContent.Loading
                sync.lastError == RefreshError.Network -> ListContent.OfflineWithoutCache
                sync.lastError != null -> ListContent.SourceErrorWithoutCache(sync.lastError)
                else -> ListContent.Loading
            }
            items.isNotEmpty() -> ListContent.Items
            filters.favoritesOnly && favoriteCount == 0 -> ListContent.NoFavorites
            filters.favoritesOnly && orphanFavorites.isNotEmpty() && query.isBlank() &&
                !filters.openOnly && !filters.availableOnly -> ListContent.Items
            query.isNotBlank() -> ListContent.NoSearchResults(query.trim())
            else -> ListContent.NoFilterResults
        }
}

sealed interface MainEvent {
    data class RefreshFailed(val error: RefreshError) : MainEvent
    data object RefreshPartial : MainEvent
    data object RequestLocationPermission : MainEvent
}
