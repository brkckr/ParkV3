package com.brkckr.parkv3.data.local

import com.brkckr.parkv3.data.remote.parse.ParkJsonParser
import com.brkckr.parkv3.data.remote.parse.ParsedDetail
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SourceTimestamp
import com.brkckr.parkv3.domain.model.SyncInfo

fun Park.toEntity(syncId: Long, nowMillis: Long) = ParkEntity(
    id = id,
    name = name,
    district = district,
    latitude = location?.latitude,
    longitude = location?.longitude,
    openState = openState,
    capacity = capacity,
    emptyCapacity = emptyCapacity,
    workHours = workHours,
    parkType = parkType,
    freeTime = freeTime,
    lastSeenSyncId = syncId,
    lastSeenAtMillis = nowMillis,
    missingSinceMillis = null,
)

fun ParkEntity.toDomain() = Park(
    id = id,
    name = name,
    district = district,
    location = GeoPoint.validOrNull(latitude, longitude),
    openState = openState,
    capacity = capacity,
    emptyCapacity = emptyCapacity,
    workHours = workHours,
    parkType = parkType,
    freeTime = freeTime,
)

fun ParsedDetail.toEntity() = ParkDetailEntity(
    parkId = detail.parkId,
    name = detail.name,
    district = detail.district,
    address = detail.address,
    parkType = detail.parkType,
    workHours = detail.workHours,
    latitude = detail.location?.latitude,
    longitude = detail.location?.longitude,
    capacity = detail.capacity,
    emptyCapacity = detail.emptyCapacity,
    freeTime = detail.freeTime,
    monthlyFee = detail.monthlyFee,
    tariffRaw = rawTariff,
    areaPolygonRaw = rawAreaPolygon,
    sourceUpdatedRaw = detail.sourceUpdatedAt?.raw,
    sourceUpdatedAtMillis = detail.sourceUpdatedAt?.epochMillis,
    fetchedAtMillis = detail.fetchedAtMillis,
)

fun ParkDetailEntity.toDomain() = ParkDetail(
    parkId = parkId,
    name = name,
    district = district,
    address = address,
    parkType = parkType,
    workHours = workHours,
    location = GeoPoint.validOrNull(latitude, longitude),
    capacity = capacity,
    emptyCapacity = emptyCapacity,
    freeTime = freeTime,
    monthlyFee = monthlyFee,
    tariffLines = ParkJsonParser.tariffLines(tariffRaw),
    sourceUpdatedAt = sourceUpdatedRaw?.let { SourceTimestamp(it, sourceUpdatedAtMillis) },
    fetchedAtMillis = fetchedAtMillis,
)

fun FavoriteEntity.toOrphan() = OrphanFavorite(parkId = parkId, name = nameSnapshot, district = districtSnapshot)

fun SyncStateEntity?.toDomain(): SyncInfo = if (this == null) {
    SyncInfo()
} else {
    SyncInfo(
        lastSuccessAtMillis = lastSuccessAtMillis,
        lastAttemptAtMillis = lastAttemptAtMillis,
        lastError = lastErrorKind?.let { refreshErrorOf(it, lastErrorCode) },
    )
}

internal val RefreshError.kind: String
    get() = when (this) {
        RefreshError.Network -> "network"
        is RefreshError.Http -> "http"
        RefreshError.Malformed -> "malformed"
        RefreshError.EmptyResponse -> "empty"
        RefreshError.NotFound -> "not_found"
        RefreshError.Unexpected -> "unexpected"
    }

internal val RefreshError.code: Int? get() = (this as? RefreshError.Http)?.code

internal fun refreshErrorOf(kind: String, code: Int?): RefreshError = when (kind) {
    "network" -> RefreshError.Network
    "http" -> RefreshError.Http(code ?: 0)
    "malformed" -> RefreshError.Malformed
    "empty" -> RefreshError.EmptyResponse
    "not_found" -> RefreshError.NotFound
    else -> RefreshError.Unexpected
}
