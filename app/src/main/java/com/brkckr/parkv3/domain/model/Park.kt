package com.brkckr.parkv3.domain.model

/**
 * A parking lot as last reported by the İSPARK list endpoint.
 *
 * Every source field is nullable on purpose: a missing value is never the same thing as 0
 * or an empty string (see docs/adr/0003). The source's `isOpen` is not read at all: its
 * meaning is undocumented and contradicts the published hours (docs/adr/0014).
 */
data class Park(
    val id: Int,
    val name: String?,
    val district: String?,
    val location: GeoPoint?,
    val capacity: Int?,
    val emptyCapacity: Int?,
    val workHours: String?,
    val parkType: String?,
    /** Free parking time as reported; the source does not document the unit. */
    val freeTime: Int?,
) {
    val occupancy: Occupancy get() = Occupancy.of(capacity, emptyCapacity)
    val availability: Availability get() = Availability.of(occupancy)
}

/** WGS84 point. Only constructed through [validOrNull] when coming from the source. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    companion object {
        // Generous bounding box around Istanbul province. Values outside are treated as
        // invalid instead of being "fixed" (e.g. by swapping lat/lng).
        private const val MIN_LAT = 40.5
        private const val MAX_LAT = 41.9
        private const val MIN_LNG = 27.5
        private const val MAX_LNG = 30.0

        fun validOrNull(latitude: Double?, longitude: Double?): GeoPoint? {
            if (latitude == null || longitude == null) return null
            if (!latitude.isFinite() || !longitude.isFinite()) return null
            if (latitude !in MIN_LAT..MAX_LAT || longitude !in MIN_LNG..MAX_LNG) return null
            return GeoPoint(latitude, longitude)
        }
    }
}

sealed interface Occupancy {
    /** Both values present and consistent: capacity > 0 and 0 <= empty <= capacity. */
    data class Known(val capacity: Int, val empty: Int) : Occupancy

    /** At least one value is absent and nothing present contradicts the other. */
    data object Missing : Occupancy

    /** Values are present but cannot both be true; they must not be shown as facts. */
    data class Inconsistent(val capacity: Int?, val empty: Int?) : Occupancy

    companion object {
        fun of(capacity: Int?, empty: Int?): Occupancy = when {
            capacity != null && capacity <= 0 -> Inconsistent(capacity, empty)
            empty != null && empty < 0 -> Inconsistent(capacity, empty)
            capacity != null && empty != null && empty > capacity -> Inconsistent(capacity, empty)
            capacity != null && empty != null -> Known(capacity, empty)
            else -> Missing
        }
    }
}

/** What can be said about free spaces; based on occupancy only (docs/adr/0014). */
enum class Availability {
    AVAILABLE,
    FULL,
    /** Occupancy missing or inconsistent; never shown as free or full. */
    UNKNOWN;

    companion object {
        fun of(occupancy: Occupancy): Availability = when (occupancy) {
            is Occupancy.Known -> if (occupancy.empty > 0) AVAILABLE else FULL
            else -> UNKNOWN
        }
    }
}
