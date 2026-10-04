package com.brkckr.parkv3.domain

import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Screens read persisted content only through the observe* flows (Room is the single source
 * of truth). Network activity is exposed separately through [isRefreshingList] and
 * [observeRefreshingDetails] so a refresh never hides existing content.
 */
interface ParkRepository {

    /** Parks currently listed by the source (records marked missing are excluded). */
    fun observeParks(): Flow<List<Park>>

    /** A park by id, including one the source stopped listing, or null if never cached. */
    fun observePark(parkId: Int): Flow<Park?>

    fun observeSyncInfo(): Flow<SyncInfo>

    val isRefreshingList: StateFlow<Boolean>

    /** Concurrent calls share a single network request (ADR-0006). */
    suspend fun refreshParks(): RefreshResult

    /** Refreshes only when the last success is older than [maxAgeMillis]; null if skipped. */
    suspend fun refreshParksIfOlderThan(maxAgeMillis: Long): RefreshResult?

    fun observeDetail(parkId: Int): Flow<ParkDetail?>

    /** Ids of parks whose detail is being fetched right now. */
    fun observeRefreshingDetails(): Flow<Set<Int>>

    suspend fun refreshDetail(parkId: Int): RefreshResult

    fun observeFavoriteIds(): Flow<Set<Int>>

    fun observeOrphanFavorites(): Flow<List<OrphanFavorite>>

    suspend fun setFavorite(parkId: Int, favorite: Boolean)
}
