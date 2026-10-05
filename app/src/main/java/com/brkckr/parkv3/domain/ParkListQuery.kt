package com.brkckr.parkv3.domain

import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.Park
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Filters are combined with AND. */
data class ParkFilters(
    val openOnly: Boolean = false,
    val availableOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
) {
    val isAnyActive: Boolean get() = openOnly || availableOnly || favoritesOnly
}

data class ParkListItem(
    val park: Park,
    val isFavorite: Boolean,
    /** Straight-line ("as the crow flies") distance to the reference point. */
    val distanceMeters: Double?,
)

object ParkListQuery {

    private val turkish: Locale = Locale.forLanguageTag("tr-TR")

    /**
     * Applies search and filters, then sorts by distance to [reference] when given (parks
     * without a valid location go last), otherwise by name with Turkish collation.
     */
    fun apply(
        parks: List<Park>,
        favoriteIds: Set<Int>,
        query: String,
        filters: ParkFilters,
        reference: GeoPoint?,
    ): List<ParkListItem> {
        val tokens = SearchText.tokens(query)
        val items = parks.asSequence()
            .filter { park -> SearchText.matches(tokens, park) }
            .filter { park -> !filters.openOnly || park.openState == OpenState.OPEN }
            .filter { park -> !filters.availableOnly || park.availability == Availability.AVAILABLE }
            .filter { park -> !filters.favoritesOnly || park.id in favoriteIds }
            .map { park ->
                ParkListItem(
                    park = park,
                    isFavorite = park.id in favoriteIds,
                    distanceMeters = if (reference != null && park.location != null) {
                        Geo.distanceMeters(reference, park.location)
                    } else {
                        null
                    },
                )
            }
            .toList()

        val collator = Collator.getInstance(turkish).apply { strength = Collator.SECONDARY }
        val byName = compareBy<ParkListItem, String>(collator) { it.park.name.orEmpty() }
            .thenBy { it.park.id }
        val comparator = if (reference != null) {
            compareBy<ParkListItem, Double?>(nullsLast()) { it.distanceMeters }.then(byName)
        } else {
            byName
        }
        return items.sortedWith(comparator)
    }
}

/** Turkish-aware, accent-insensitive search matching on name and district. */
object SearchText {

    private val turkish: Locale = Locale.forLanguageTag("tr-TR")
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String {
        // Turkish lowercasing maps İ→i and I→ı; ı is then folded to i so that queries typed
        // on a non-Turkish keyboard ("kadikoy", "ISTINYE") still match.
        val lower = text.lowercase(turkish).replace('ı', 'i')
        val withoutMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarks, "")
        return withoutMarks.replace(whitespace, " ").trim()
    }

    fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    fun matches(tokens: List<String>, park: Park): Boolean {
        if (tokens.isEmpty()) return true
        val haystack = normalize("${park.name.orEmpty()} ${park.district.orEmpty()}")
        return tokens.all { haystack.contains(it) }
    }
}

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Haversine great-circle distance. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
