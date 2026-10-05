package com.brkckr.parkv3.data

import com.brkckr.parkv3.data.local.FavoriteEntity
import com.brkckr.parkv3.data.local.ParkDao
import com.brkckr.parkv3.data.local.code
import com.brkckr.parkv3.data.local.kind
import com.brkckr.parkv3.data.local.toDomain
import com.brkckr.parkv3.data.local.toEntity
import com.brkckr.parkv3.data.local.toOrphan
import com.brkckr.parkv3.data.remote.IsparkRemoteDataSource
import com.brkckr.parkv3.data.remote.RemoteResult
import com.brkckr.parkv3.data.remote.parse.DetailParseResult
import com.brkckr.parkv3.data.sync.ListSyncDecision
import com.brkckr.parkv3.data.sync.ListSyncPolicy
import com.brkckr.parkv3.di.ApplicationScope
import com.brkckr.parkv3.domain.ParkRepository
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the single source of truth; the network only feeds it. Refreshes run in the
 * application scope so one caller's cancellation does not abort a request other callers are
 * waiting on, and concurrent requests for the same data share one call (docs/adr/0006).
 */
@Singleton
class OfflineFirstParkRepository @Inject constructor(
    private val remote: IsparkRemoteDataSource,
    private val dao: ParkDao,
    private val clock: Clock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ParkRepository {

    private val refreshingList = MutableStateFlow(false)
    private val refreshingDetails = MutableStateFlow<Set<Int>>(emptySet())

    private val lock = Any()
    private var listInFlight: Deferred<RefreshResult>? = null
    private val detailsInFlight = HashMap<Int, Deferred<RefreshResult>>()

    override val isRefreshingList: StateFlow<Boolean> = refreshingList.asStateFlow()

    override fun observeParks(): Flow<List<Park>> =
        dao.observeActiveParks().map { rows -> rows.map { it.toDomain() } }

    override fun observePark(parkId: Int): Flow<Park?> =
        dao.observePark(parkId).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeSyncInfo(): Flow<SyncInfo> =
        dao.observeSyncState().map { it.toDomain() }.distinctUntilChanged()

    override suspend fun refreshParks(): RefreshResult {
        val shared = synchronized(lock) {
            listInFlight?.takeIf { it.isActive }
                ?: appScope.async { refreshParksNow() }.also { listInFlight = it }
        }
        return shared.await()
    }

    override suspend fun refreshParksIfOlderThan(maxAgeMillis: Long): RefreshResult? {
        val lastSuccess = dao.getSyncState()?.lastSuccessAtMillis
        if (!FreshnessPolicy.isOlderThan(lastSuccess, clock.nowMillis(), maxAgeMillis)) return null
        return refreshParks()
    }

    private suspend fun refreshParksNow(): RefreshResult {
        refreshingList.value = true
        try {
            val result = guarded { fetchAndApplyList() }
            if (result is RefreshResult.Failure) {
                guarded {
                    dao.recordListFailure(clock.nowMillis(), result.error.kind, result.error.code)
                    result
                }
            }
            return result
        } finally {
            refreshingList.value = false
        }
    }

    private suspend fun fetchAndApplyList(): RefreshResult {
        val parsed = when (val response = remote.fetchParks()) {
            is RemoteResult.Failure -> return RefreshResult.Failure(response.error)
            is RemoteResult.Success -> response.value
        }
        return when (val decision = ListSyncPolicy.decide(parsed, dao.countActiveParks())) {
            is ListSyncDecision.Reject -> RefreshResult.Failure(decision.error)
            is ListSyncDecision.Apply -> {
                val now = clock.nowMillis()
                dao.applyListSync(
                    parks = decision.parks,
                    markMissing = decision.markMissing,
                    nowMillis = now,
                    purgeCutoffMillis = now - PURGE_MISSING_AFTER_MS,
                )
                RefreshResult.Success(partial = !decision.markMissing)
            }
        }
    }

    override fun observeDetail(parkId: Int): Flow<ParkDetail?> =
        dao.observeDetail(parkId).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeRefreshingDetails(): Flow<Set<Int>> = refreshingDetails.asStateFlow()

    override suspend fun refreshDetail(parkId: Int): RefreshResult {
        val shared = synchronized(lock) {
            detailsInFlight[parkId]?.takeIf { it.isActive }
                ?: appScope.async { refreshDetailNow(parkId) }.also { deferred ->
                    detailsInFlight[parkId] = deferred
                    deferred.invokeOnCompletion { synchronized(lock) { detailsInFlight.remove(parkId, deferred) } }
                }
        }
        return shared.await()
    }

    private suspend fun refreshDetailNow(parkId: Int): RefreshResult {
        refreshingDetails.update { it + parkId }
        try {
            return guarded {
                when (val response = remote.fetchDetail(parkId, fetchedAtMillis = clock.nowMillis())) {
                    is RemoteResult.Failure -> RefreshResult.Failure(response.error)
                    is RemoteResult.Success -> when (val parsed = response.value) {
                        is DetailParseResult.Parsed -> {
                            dao.upsertDetail(parsed.value.toEntity())
                            RefreshResult.Success()
                        }
                        DetailParseResult.NotFound -> RefreshResult.Failure(RefreshError.NotFound)
                        DetailParseResult.Malformed -> RefreshResult.Failure(RefreshError.Malformed)
                    }
                }
            }
        } finally {
            refreshingDetails.update { it - parkId }
        }
    }

    override fun observeFavoriteIds(): Flow<Set<Int>> =
        dao.observeFavoriteIds().map { it.toSet() }.distinctUntilChanged()

    override fun observeOrphanFavorites(): Flow<List<OrphanFavorite>> =
        dao.observeOrphanFavorites().map { rows -> rows.map { it.toOrphan() } }

    override suspend fun setFavorite(parkId: Int, favorite: Boolean) {
        if (favorite) {
            val park = dao.getPark(parkId)
            val detail = if (park == null) dao.getDetail(parkId) else null
            dao.insertFavorite(
                FavoriteEntity(
                    parkId = parkId,
                    nameSnapshot = park?.name ?: detail?.name,
                    districtSnapshot = park?.district ?: detail?.district,
                    addedAtMillis = clock.nowMillis(),
                ),
            )
        } else {
            dao.deleteFavorite(parkId)
        }
    }

    /**
     * Storage failures (e.g. disk full) become [RefreshError.Unexpected] instead of crashing
     * the caller; cancellation is always rethrown and never recorded as an error.
     */
    private inline fun guarded(block: () -> RefreshResult): RefreshResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshResult.Failure(RefreshError.Unexpected)
    }

    companion object {
        const val PURGE_MISSING_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
