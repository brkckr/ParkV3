package com.brkckr.parkv3.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.brkckr.parkv3.domain.model.OpenState

/**
 * Last list snapshot. [lastSeenAtMillis] is the sync stamp of the last response that
 * contained the park; [missingSinceMillis] is set when a complete response no longer
 * contained it (docs/adr/0004). Coordinates are stored only when valid.
 */
@Entity(tableName = "parks", indices = [Index("missingSinceMillis")])
data class ParkEntity(
    @PrimaryKey val id: Int,
    val name: String?,
    val district: String?,
    val latitude: Double?,
    val longitude: Double?,
    val openState: OpenState,
    val capacity: Int?,
    val emptyCapacity: Int?,
    val workHours: String?,
    val parkType: String?,
    val freeTime: Int?,
    val lastSeenAtMillis: Long,
    val missingSinceMillis: Long?,
)

/** Detail cache; raw tariff and polygon text are kept verbatim. */
@Entity(tableName = "park_details")
data class ParkDetailEntity(
    @PrimaryKey val parkId: Int,
    val name: String?,
    val district: String?,
    val address: String?,
    val parkType: String?,
    val workHours: String?,
    val latitude: Double?,
    val longitude: Double?,
    val capacity: Int?,
    val emptyCapacity: Int?,
    val freeTime: Int?,
    val monthlyFee: Double?,
    val tariffRaw: String?,
    val areaPolygonRaw: String?,
    val sourceUpdatedRaw: String?,
    val sourceUpdatedAtMillis: Long?,
    val fetchedAtMillis: Long,
)

/**
 * Favorites live apart from network records: sync and purge never write this table. Name and
 * district are snapshots so a favorite stays recognizable after the source drops the park.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val parkId: Int,
    val nameSnapshot: String?,
    val districtSnapshot: String?,
    val addedAtMillis: Long,
)

/** Single-row list sync bookkeeping (id is always 0). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastSuccessAtMillis: Long? = null,
    val lastAttemptAtMillis: Long? = null,
    val lastErrorKind: String? = null,
    val lastErrorCode: Int? = null,
)
