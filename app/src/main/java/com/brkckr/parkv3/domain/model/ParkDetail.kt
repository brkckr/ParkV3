package com.brkckr.parkv3.domain.model

/** Detail record from the ParkDetay endpoint, as cached locally. */
data class ParkDetail(
    val parkId: Int,
    val name: String?,
    val district: String?,
    val address: String?,
    val phone: String?,
    val parkType: String?,
    val workHours: String?,
    val location: GeoPoint?,
    val openState: OpenState,
    val capacity: Int?,
    val emptyCapacity: Int?,
    val freeTime: String?,
    val fee: String?,
    val monthlyFee: String?,
    /** Free-text tariff split into display lines; no price/duration meaning is inferred. */
    val tariffLines: List<String>,
    /** The source's own update time, never mixed up with [fetchedAtMillis]. */
    val sourceUpdatedAt: SourceTimestamp?,
    /** Device time of the successful detail fetch. */
    val fetchedAtMillis: Long,
) {
    val occupancy: Occupancy get() = Occupancy.of(capacity, emptyCapacity)
    val availability: Availability get() = Availability.of(openState, occupancy)
}

/** A timestamp reported by the source. [epochMillis] is null when [raw] could not be parsed. */
data class SourceTimestamp(val raw: String, val epochMillis: Long?)
