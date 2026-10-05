package com.brkckr.parkv3.ui.map

import android.content.Context
import com.brkckr.parkv3.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class MapStatus { AVAILABLE, NO_API_KEY, NO_PLAY_SERVICES }

/** Whether a Google map can be shown; without one the app falls back to the list (ADR-0007). */
fun interface MapAvailability {
    fun status(): MapStatus
}

class PlayServicesMapAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MapAvailability {
    private val cached: MapStatus by lazy {
        when {
            !BuildConfig.HAS_MAPS_KEY -> MapStatus.NO_API_KEY
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS ->
                MapStatus.NO_PLAY_SERVICES
            else -> MapStatus.AVAILABLE
        }
    }

    override fun status(): MapStatus = cached
}
