package com.brkckr.parkv3.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.brkckr.parkv3.domain.model.Park
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkDao {

    // Parks

    @Query("SELECT * FROM parks WHERE missingSinceMillis IS NULL")
    fun observeActiveParks(): Flow<List<ParkEntity>>

    @Query("SELECT * FROM parks WHERE id = :parkId")
    fun observePark(parkId: Int): Flow<ParkEntity?>

    @Query("SELECT * FROM parks WHERE id = :parkId")
    suspend fun getPark(parkId: Int): ParkEntity?

    @Query("SELECT COUNT(*) FROM parks WHERE missingSinceMillis IS NULL")
    suspend fun countActiveParks(): Int

    @Upsert
    suspend fun upsertParks(parks: List<ParkEntity>)

    @Query(
        "UPDATE parks SET missingSinceMillis = :nowMillis " +
            "WHERE lastSeenSyncId != :syncId AND missingSinceMillis IS NULL",
    )
    suspend fun markUnseenAsMissing(syncId: Long, nowMillis: Long)

    @Query("DELETE FROM parks WHERE missingSinceMillis IS NOT NULL AND missingSinceMillis < :cutoffMillis")
    suspend fun purgeMissingBefore(cutoffMillis: Long)

    @Query("DELETE FROM park_details WHERE parkId NOT IN (SELECT id FROM parks)")
    suspend fun deleteOrphanDetails()

    /** Applies an accepted list response atomically (docs/adr/0004). */
    @Transaction
    suspend fun applyListSync(parks: List<Park>, markMissing: Boolean, nowMillis: Long, purgeCutoffMillis: Long) {
        ensureSyncStateRow()
        val syncId = currentSyncId() + 1
        upsertParks(parks.map { it.toEntity(syncId = syncId, nowMillis = nowMillis) })
        if (markMissing) markUnseenAsMissing(syncId, nowMillis)
        purgeMissingBefore(purgeCutoffMillis)
        deleteOrphanDetails()
        recordListSuccess(nowMillis, syncId)
    }

    // Sync state

    @Query("SELECT * FROM sync_state WHERE id = 0")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun getSyncState(): SyncStateEntity?

    @Query("INSERT OR IGNORE INTO sync_state (id, syncId) VALUES (0, 0)")
    suspend fun ensureSyncStateRow()

    @Query("SELECT syncId FROM sync_state WHERE id = 0")
    suspend fun currentSyncId(): Long

    @Query(
        "UPDATE sync_state SET syncId = :syncId, lastSuccessAtMillis = :nowMillis, " +
            "lastAttemptAtMillis = :nowMillis, lastErrorKind = NULL, lastErrorCode = NULL WHERE id = 0",
    )
    suspend fun recordListSuccess(nowMillis: Long, syncId: Long)

    /** Leaves lastSuccessAtMillis untouched on purpose. */
    @Query(
        "UPDATE sync_state SET lastAttemptAtMillis = :nowMillis, lastErrorKind = :kind, " +
            "lastErrorCode = :code WHERE id = 0",
    )
    suspend fun updateListFailure(nowMillis: Long, kind: String, code: Int?)

    @Transaction
    suspend fun recordListFailure(nowMillis: Long, kind: String, code: Int?) {
        ensureSyncStateRow()
        updateListFailure(nowMillis, kind, code)
    }

    // Details

    @Query("SELECT * FROM park_details WHERE parkId = :parkId")
    fun observeDetail(parkId: Int): Flow<ParkDetailEntity?>

    @Query("SELECT * FROM park_details WHERE parkId = :parkId")
    suspend fun getDetail(parkId: Int): ParkDetailEntity?

    @Upsert
    suspend fun upsertDetail(detail: ParkDetailEntity)

    // Favorites

    @Query("SELECT parkId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Int>>

    @Query(
        "SELECT f.* FROM favorites f LEFT JOIN parks p ON p.id = f.parkId " +
            "WHERE p.id IS NULL OR p.missingSinceMillis IS NOT NULL ORDER BY f.addedAtMillis",
    )
    fun observeOrphanFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE parkId = :parkId")
    suspend fun deleteFavorite(parkId: Int)
}
