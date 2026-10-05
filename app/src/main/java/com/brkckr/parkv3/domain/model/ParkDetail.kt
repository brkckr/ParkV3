package com.brkckr.parkv3.domain.model

/**
 * Detail record from the ParkDetay endpoint, as cached locally. The endpoint does not report
 * an open/closed state, so screens take that from the list record (see docs/API_CONTRACT.md).
 */
data class ParkDetail(
    val parkId: Int,
    val name: String?,
    val district: String?,
    val address: String?,
    val parkType: String?,
    val workHours: String?,
    val location: GeoPoint?,
    val capacity: Int?,
    val emptyCapacity: Int?,
    /** Free parking time as reported; unit undocumented. */
    val freeTime: Int?,
    /** Monthly subscription fee as reported; 0.0 is sent for unknown parks, so it is not "free". */
    val monthlyFee: Double?,
    /** Tariff text split into display lines; no price or duration meaning is inferred. */
    val tariffLines: List<TariffLine>,
    /** The source's own update time, never mixed up with [fetchedAtMillis]. */
    val sourceUpdatedAt: SourceTimestamp?,
    /** Device time of the successful detail fetch. */
    val fetchedAtMillis: Long,
    /** Parking area outline; empty unless it passed validation (docs/adr/0012). */
    val area: List<AreaPolygon> = emptyList(),
) {
    val occupancy: Occupancy get() = Occupancy.of(capacity, emptyCapacity)
}

/** One polygon of a parking area: [outer] boundary and [holes], in valid Istanbul coordinates. */
data class AreaPolygon(val outer: List<GeoPoint>, val holes: List<List<GeoPoint>> = emptyList())

/** One tariff line split at its first ':' ("0-1 Saat : 110,00"); [value] is null without one. */
data class TariffLine(val label: String, val value: String?)

/** A timestamp reported by the source. [epochMillis] is null when [raw] could not be parsed. */
data class SourceTimestamp(val raw: String, val epochMillis: Long?)
