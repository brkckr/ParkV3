package com.brkckr.parkv3.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.brkckr.parkv3.domain.model.GeoPoint

/**
 * Sharing a car park as plain text: name, address and a map link. Occupancy is left out
 * because it is out of date within minutes.
 */
object ShareIntents {

    /** Opens in any browser or maps app; needs no app, account or API key. */
    fun mapUri(location: GeoPoint): Uri =
        "https://www.google.com/maps/search/?api=1&query=${location.uriCoordinates()}".toUri()

    fun text(name: String, address: String?, location: GeoPoint): String =
        listOfNotNull(name, address?.takeIf { it.isNotBlank() }, mapUri(location).toString()).joinToString("\n")

    fun chooser(name: String, address: String?, location: GeoPoint, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .putExtra(Intent.EXTRA_TEXT, text(name, address, location))
        return Intent.createChooser(send, title)
    }
}

/** Returns false (and never crashes) when nothing can share. */
fun Context.sharePark(name: String, address: String?, location: GeoPoint, chooserTitle: String): Boolean =
    tryStartActivity(ShareIntents.chooser(name, address, location, chooserTitle))
