package com.brkckr.parkv3.location

import com.brkckr.parkv3.domain.model.GeoPoint

enum class LocationPermission { NONE, APPROXIMATE, PRECISE }

/** Platform location access; the permission dialog itself is launched by the UI. */
interface LocationProvider {
    fun permission(): LocationPermission

    fun isLocationEnabled(): Boolean

    /** A fresh fix (never a cached last-known location), or null if none could be obtained. */
    suspend fun currentLocation(precise: Boolean): GeoPoint?
}

sealed interface LocationStatus {
    data object Idle : LocationStatus
    data object Locating : LocationStatus
    data class Available(val point: GeoPoint, val approximate: Boolean) : LocationStatus
    data object PermissionDenied : LocationStatus
    data object PermissionPermanentlyDenied : LocationStatus
    data object ServicesDisabled : LocationStatus
    data object Unavailable : LocationStatus
}
