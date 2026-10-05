package com.brkckr.parkv3.testutil

import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.location.LocationPermission
import com.brkckr.parkv3.location.LocationProvider
import kotlinx.coroutines.awaitCancellation

class FakeLocationProvider(
    var permission: LocationPermission = LocationPermission.NONE,
    var enabled: Boolean = true,
    /** null result = no fix; [hang] = never answers (timeout path). */
    var result: GeoPoint? = null,
    var hang: Boolean = false,
) : LocationProvider {
    var requests = 0
        private set
    var lastPrecise: Boolean? = null
        private set

    override fun permission(): LocationPermission = permission

    override fun isLocationEnabled(): Boolean = enabled

    override suspend fun currentLocation(precise: Boolean): GeoPoint? {
        requests++
        lastPrecise = precise
        if (hang) awaitCancellation()
        return result
    }
}
