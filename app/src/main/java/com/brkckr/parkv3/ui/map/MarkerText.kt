package com.brkckr.parkv3.ui.map

import android.content.res.Resources
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.ui.components.availabilityLabel
import com.brkckr.parkv3.ui.components.occupancyLabel

/**
 * What a screen reader announces for a park pin: the name (or the unnamed fallback), then
 * status and occupancy as shown in the list. The info window itself is never opened.
 */
data class MarkerText(val title: String, val snippet: String)

fun Resources.markerText(park: Park): MarkerText = MarkerText(
    title = park.name ?: getString(R.string.park_unnamed, park.id),
    snippet = getString(availabilityLabel(park.availability)) + " · " + occupancyLabel(park.occupancy, park.capacity),
)
