package com.brkckr.parkv3.testutil

import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.Park

/** Loads SYNTHETIC fixtures from src/test/resources/fixtures (see the README there). */
object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText()

    val parkList: String get() = read("synthetic-park-list.json")
    val parkDetail: String get() = read("synthetic-park-detail.json")

    /** Shape of the placeholder the live endpoint returns for an unknown id (probe 2026-10-04). */
    val parkDetailUnknownId: String get() = read("synthetic-park-detail-unknown-id.json")
}

/** Builds a park with neutral defaults so each test states only what it relies on. */
fun park(
    id: Int,
    name: String? = "Park $id",
    district: String? = null,
    location: GeoPoint? = null,
    openState: OpenState = OpenState.OPEN,
    capacity: Int? = 100,
    emptyCapacity: Int? = 10,
) = Park(
    id = id,
    name = name,
    district = district,
    location = location,
    openState = openState,
    capacity = capacity,
    emptyCapacity = emptyCapacity,
    workHours = null,
    parkType = null,
    freeTime = null,
)

/** Controllable clock for freshness and purge tests. */
class MutableClock(var now: Long) : com.brkckr.parkv3.domain.model.Clock {
    override fun nowMillis(): Long = now
    fun advanceBy(millis: Long) {
        now += millis
    }
}

/** A minimal valid list body with ids [ids] (all open, consistent capacity, valid location). */
fun parkListJson(ids: Iterable<Int>): String = ids.joinToString(prefix = "[", postfix = "]") { id ->
    """{"parkID": $id, "parkName": "Otopark $id", "lat": "41.0${id % 10}", "lng": "29.0${id % 10}", "capacity": 100, "emptyCapacity": 10, "district": "TEST", "isOpen": 1}"""
}
