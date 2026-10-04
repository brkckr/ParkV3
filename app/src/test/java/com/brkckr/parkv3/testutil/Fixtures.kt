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
