package com.brkckr.parkv3.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.brkckr.parkv3.domain.model.GeoPoint
import java.util.Locale

/**
 * External directions, tried in order until one starts (docs/adr/0008): the Google Maps app,
 * any app handling geo: URIs, then a browser. No in-app routing is done.
 */
object DirectionsIntents {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Decimal point regardless of the UI locale; a Turkish "41,0" would break the URI. */
    private fun GeoPoint.coordinates() = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

    fun webUri(destination: GeoPoint): Uri =
        "https://www.google.com/maps/dir/?api=1&destination=${destination.coordinates()}&travelmode=driving".toUri()

    fun candidates(destination: GeoPoint, label: String): List<Intent> {
        val coordinates = destination.coordinates()
        return listOf(
            Intent(Intent.ACTION_VIEW, webUri(destination)).setPackage(GOOGLE_MAPS_PACKAGE),
            Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$coordinates(${Uri.encode(label)})".toUri()),
            Intent(Intent.ACTION_VIEW, webUri(destination)).addCategory(Intent.CATEGORY_BROWSABLE),
        )
    }
}

/** Returns false (and never crashes) when no app can show directions. */
fun Context.openDirections(destination: GeoPoint, label: String): Boolean =
    DirectionsIntents.candidates(destination, label).any { intent -> tryStartActivity(intent) }

fun Context.tryStartActivity(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
