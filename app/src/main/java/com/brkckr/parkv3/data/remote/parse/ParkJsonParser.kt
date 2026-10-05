package com.brkckr.parkv3.data.remote.parse

import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.SourceTimestamp
import com.brkckr.parkv3.domain.model.TariffLine
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

/**
 * Detail parse output. [rawTariff] is stored so lines can be re-derived; [rawAreaPolygon] is
 * kept verbatim and never interpreted in v1 (ADR-0003).
 */
data class ParsedDetail(val detail: ParkDetail, val rawTariff: String?, val rawAreaPolygon: String?)

sealed interface DetailParseResult {
    data class Parsed(val value: ParsedDetail) : DetailParseResult

    /** Empty array, or the placeholder record (parkID 0) the source returns for unknown ids. */
    data object NotFound : DetailParseResult
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

    /**
     * ParkDetay is an array with one element (a bare object is accepted too). For an unknown
     * id the source answers 200 with a placeholder record whose parkID is 0 and whose
     * capacity values are 1; that must never be stored as real data.
     */
    fun parseDetail(root: JsonElement, requestedId: Int, fetchedAtMillis: Long): DetailParseResult {
        val obj = when (root) {
            is JsonArray -> {
                if (root.isEmpty()) return DetailParseResult.NotFound
                root.first() as? JsonObject ?: return DetailParseResult.Malformed
            }
            is JsonObject -> root
            else -> return DetailParseResult.Malformed
        }
        val f = JsonFields(obj)
        val id = f.int("parkID", "parkId", "id")
        if (id == null || id <= 0 || id != requestedId) return DetailParseResult.NotFound
        val tariff = f.string("tariff")
        val polygon = f.string("areaPolygon")
        val point = location(f)
        val detail = ParkDetail(
            parkId = id,
            name = f.string("parkName"),
            district = f.string("district"),
            address = f.string("address"),
            parkType = f.string("parkType"),
            workHours = f.string("workHours"),
            location = point,
            capacity = f.int("capacity"),
            emptyCapacity = f.int("emptyCapacity"),
            freeTime = f.int("freeTime"),
            monthlyFee = f.double("monthlyFee"),
            tariffLines = tariffLines(tariff),
            sourceUpdatedAt = f.string("updateDate")?.let(::parseSourceTimestamp),
            fetchedAtMillis = fetchedAtMillis,
            area = AreaPolygonParser.parse(polygon, point),
        )
        return DetailParseResult.Parsed(ParsedDetail(detail, tariff, polygon))
    }

    /** "0-1 Saat : 110,00;Tam Gün : 370,00" → lines split at ';' and then at the first ':'. */
    fun tariffLines(raw: String?): List<TariffLine> =
        raw?.split(';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    TariffLine(line, null)
                } else {
                    TariffLine(line.substring(0, colon).trim(), line.substring(colon + 1).trim().ifEmpty { null })
                }
            }
            .orEmpty()

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
            capacity = f.int("capacity"),
            emptyCapacity = f.int("emptyCapacity"),
            workHours = f.string("workHours"),
            parkType = f.string("parkType"),
            freeTime = f.int("freeTime"),
        )
    }

    private fun parkId(f: JsonFields): Int? = f.int("parkID", "parkId", "id")?.takeIf { it > 0 }

    private fun location(f: JsonFields): GeoPoint? =
        GeoPoint.validOrNull(f.double("lat", "latitude"), f.double("lng", "lon", "longitude"))
}
