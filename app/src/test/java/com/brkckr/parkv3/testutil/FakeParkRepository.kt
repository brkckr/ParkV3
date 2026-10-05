package com.brkckr.parkv3.testutil

import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory test double. Refresh outcomes are scripted per call; a detail refresh scripted
 * with a detail stores it, mirroring how the real repository writes to Room.
 */
class FakeParkRepository : ParkRepository {
    val parks = MutableStateFlow<List<Park>>(emptyList())
    /** Parks no longer listed but still cached (observePark finds them, observeParks does not). */
    val unlistedParks = MutableStateFlow<List<Park>>(emptyList())
    val details = MutableStateFlow<Map<Int, ParkDetail>>(emptyMap())
    val favorites = MutableStateFlow<Set<Int>>(emptySet())
    val orphans = MutableStateFlow<List<OrphanFavorite>>(emptyList())
    val sync = MutableStateFlow(SyncInfo())
    private val refreshingList = MutableStateFlow(false)
    private val refreshingDetails = MutableStateFlow<Set<Int>>(emptySet())

    val listResults = ArrayDeque<RefreshResult>()
    val detailResults = ArrayDeque<Pair<RefreshResult, ParkDetail?>>()
    var listRefreshCalls = 0
        private set
    var detailRefreshCalls = 0
        private set
    var refreshIfOlderCalls = 0
        private set

    override val isRefreshingList: StateFlow<Boolean> = refreshingList

    override fun observeParks(): Flow<List<Park>> = parks

    override fun observePark(parkId: Int): Flow<Park?> =
        parks.map { listed -> listed.firstOrNull { it.id == parkId } ?: unlistedParks.value.firstOrNull { it.id == parkId } }

    override fun observeSyncInfo(): Flow<SyncInfo> = sync

    override suspend fun refreshParks(): RefreshResult {
        listRefreshCalls++
        return listResults.removeFirstOrNull() ?: RefreshResult.Success()
    }

    override suspend fun refreshParksIfOlderThan(maxAgeMillis: Long): RefreshResult? {
        refreshIfOlderCalls++
        return null
    }

    override fun observeDetail(parkId: Int): Flow<ParkDetail?> = details.map { it[parkId] }

    override fun observeRefreshingDetails(): Flow<Set<Int>> = refreshingDetails

    override suspend fun refreshDetail(parkId: Int): RefreshResult {
        detailRefreshCalls++
        val (result, detail) = detailResults.removeFirstOrNull() ?: (RefreshResult.Success() to null)
        if (detail != null) details.update { it + (parkId to detail) }
        return result
    }

    override fun observeFavoriteIds(): Flow<Set<Int>> = favorites

    override fun observeOrphanFavorites(): Flow<List<OrphanFavorite>> = orphans

    override suspend fun setFavorite(parkId: Int, favorite: Boolean) {
        favorites.update { if (favorite) it + parkId else it - parkId }
    }
}
