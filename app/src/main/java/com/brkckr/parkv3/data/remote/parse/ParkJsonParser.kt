package com.brkckr.parkv3.data.remote.parse

import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.SourceTimestamp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

sealed interface ListParseResult {
    data class Parsed(
        val parks: List<Park>,
        /** Records without a usable parkID. */
        val skippedCount: Int,
        val duplicateCount: Int,
    ) : ListParseResult {
        val totalCount: Int get() = parks.size + skippedCount + duplicateCount
    }

    data object NotAnArray : ListParseResult
}

/** Detail parse output; [rawAreaPolygon] is kept verbatim and never interpreted (ADR-0003). */
data class ParsedDetail(val detail: ParkDetail, val rawAreaPolygon: String?)

sealed interface DetailParseResult {
    data class Parsed(val value: ParsedDetail) : DetailParseResult
    data object Empty : DetailParseResult
    data object Malformed : DetailParseResult
}

object ParkJsonParser {

    private val istanbul: ZoneId = ZoneId.of("Europe/Istanbul")
    private val localPatterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
    )
    private val msDateRegex = Regex("""^/Date\((-?\d+)([+-]\d{4})?\)/$""")

    fun parseList(root: JsonElement): ListParseResult {
        if (root !is JsonArray) return ListParseResult.NotAnArray
        val seen = HashSet<Int>()
        val parks = ArrayList<Park>(root.size)
        var skipped = 0
        var duplicates = 0
        for (element in root) {
            val park = (element as? JsonObject)?.let(::parsePark)
            when {
                park == null -> skipped++
                !seen.add(park.id) -> duplicates++
                else -> parks += park
            }
        }
        return ListParseResult.Parsed(parks, skipped, duplicates)
    }

    /** ParkDetay is expected to be an array with one element; a bare object is accepted too. */
    fun parseDetail(root: JsonElement, fetchedAtMillis: Long): DetailParseResult {
        val obj = when (root) {
            is JsonArray -> {
                if (root.isEmpty()) return DetailParseResult.Empty
                root.first() as? JsonObject ?: return DetailParseResult.Malformed
            }
            is JsonObject -> root
            else -> return DetailParseResult.Malformed
        }
        val f = JsonFields(obj)
        val id = parkId(f) ?: return DetailParseResult.Malformed
        val detail = ParkDetail(
            parkId = id,
            name = f.string("parkName"),
            district = f.string("district"),
            address = f.string("address"),
            phone = f.string("phone"),
            parkType = f.string("parkType"),
            workHours = f.string("workHours"),
            location = location(f),
            openState = f.openState("isOpen"),
            capacity = f.int("capacity"),
            emptyCapacity = f.int("emptyCapacity"),
            freeTime = f.string("freeTime"),
            fee = f.string("fee"),
            monthlyFee = f.string("monthlyFee"),
            tariffLines = tariffLines(f.string("tariff")),
            sourceUpdatedAt = f.string("updateDate")?.let(::parseSourceTimestamp),
            fetchedAtMillis = fetchedAtMillis,
        )
        return DetailParseResult.Parsed(ParsedDetail(detail, f.string("areaPolygon")))
    }

    fun tariffLines(raw: String?): List<String> =
        raw?.split(';', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun parseSourceTimestamp(raw: String): SourceTimestamp {
        val text = raw.trim()
        return SourceTimestamp(raw = text, epochMillis = toEpochMillis(text))
    }

    private fun toEpochMillis(text: String): Long? {
        msDateRegex.matchEntire(text)?.let { return it.groupValues[1].toLongOrNull() }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        for (pattern in localPatterns) {
            try {
                return LocalDateTime.parse(text, pattern).atZone(istanbul).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun parsePark(obj: JsonObject): Park? {
        val f = JsonFields(obj)
        val id = parkId(f) ?: return null
        return Park(
            id = id,
            name = f.string("parkName"),
            district = f.string("district"),
            location = location(f),
            openState = f.openState("isOpen"),
            capacity = f.int("capacity"),
            emptyCapacity = f.int("emptyCapacity"),
            workHours = f.string("workHours"),
            parkType = f.string("parkType"),
            freeTime = f.string("freeTime"),
            fee = f.string("fee"),
            monthlyFee = f.string("monthlyFee"),
        )
    }

    private fun parkId(f: JsonFields): Int? = f.int("parkID", "parkId", "id")?.takeIf { it > 0 }

    private fun location(f: JsonFields): GeoPoint? =
        GeoPoint.validOrNull(f.double("lat", "latitude"), f.double("lng", "lon", "longitude"))
}
