package com.brkckr.parkv3.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.brkckr.parkv3.domain.model.GeoPoint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class FusedLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun permission(): LocationPermission = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.APPROXIMATE
        else -> LocationPermission.NONE
    }

    override fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // Callers check permission() first; SecurityException is handled.
    override suspend fun currentLocation(precise: Boolean): GeoPoint? {
        if (permission() == LocationPermission.NONE) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(0) // the button must yield the current position
            .setDurationMillis(TIMEOUT_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellation.cancel() }
                client.getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { continuation.resume(null) }
                    .addOnCanceledListener { continuation.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
