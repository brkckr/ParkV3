package com.brkckr.parkv3.data.remote.parse

import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.SourceTimestamp
import com.brkckr.parkv3.domain.model.TariffLine
import com.brkckr.parkv3.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class ParkJsonParserTest {

    private fun parseList(json: String) =
        ParkJsonParser.parseList(Json.parseToJsonElement(json)) as ListParseResult.Parsed

    private val fixture by lazy { parseList(Fixtures.parkList) }
    private fun fixturePark(id: Int) = fixture.parks.single { it.id == id }

    @Test
    fun `fully populated numeric record is read as is`() {
        val park = fixturePark(101)
        assertThat(park.name).isEqualTo("Kadıköy Rıhtım Otoparkı")
        assertThat(park.district).isEqualTo("KADIKÖY")
        assertThat(park.location).isEqualTo(GeoPoint(40.9903, 29.0236))
        assertThat(park.openState).isEqualTo(OpenState.OPEN)
        assertThat(park.occupancy).isEqualTo(Occupancy.Known(capacity = 200, empty = 35))
        assertThat(park.availability).isEqualTo(Availability.AVAILABLE)
        assertThat(park.workHours).isEqualTo("24 Saat")
        assertThat(park.parkType).isEqualTo("AÇIK OTOPARK")
        assertThat(park.freeTime).isEqualTo(15)
    }

    @Test
    fun `numeric strings and a decimal comma are accepted`() {
        val park = fixturePark(102)
        assertThat(park.location).isEqualTo(GeoPoint(41.1102, 29.0581))
        assertThat(park.occupancy).isEqualTo(Occupancy.Known(capacity = 150, empty = 0))
        assertThat(park.availability).isEqualTo(Availability.FULL)
    }

    @Test
    fun `explicit isOpen 0 is closed while missing isOpen is unknown`() {
        assertThat(fixturePark(103).openState).isEqualTo(OpenState.CLOSED)
        assertThat(fixturePark(103).availability).isEqualTo(Availability.CLOSED)

        assertThat(fixturePark(104).openState).isEqualTo(OpenState.UNKNOWN)
        assertThat(fixturePark(104).availability).isEqualTo(Availability.UNKNOWN)
    }

    @Test
    fun `isOpen values other than explicit true or false are unknown`() {
        val json = """[
            {"parkID": 1, "isOpen": null},
            {"parkID": 2, "isOpen": 2},
            {"parkID": 3, "isOpen": "Açık"},
            {"parkID": 4, "isOpen": "false"},
            {"parkID": 5, "isOpen": false},
            {"parkID": 6, "isOpen": "TRUE"}
        ]"""
        val states = parseList(json).parks.associate { it.id to it.openState }
        assertThat(states).containsExactly(
            1, OpenState.UNKNOWN,
            2, OpenState.UNKNOWN,
            3, OpenState.UNKNOWN,
            4, OpenState.CLOSED,
            5, OpenState.CLOSED,
            6, OpenState.OPEN,
        )
    }

    @Test
    fun `missing capacity values stay null instead of becoming zero`() {
        val park = fixturePark(106)
        assertThat(park.capacity).isNull()
        assertThat(park.emptyCapacity).isNull()
        assertThat(park.occupancy).isEqualTo(Occupancy.Missing)
        assertThat(park.availability).isEqualTo(Availability.OPEN_OCCUPANCY_UNKNOWN)
    }

    @Test
    fun `empty capacity above total capacity is inconsistent and not available`() {
        val park = fixturePark(105)
        assertThat(park.occupancy).isEqualTo(Occupancy.Inconsistent(capacity = 100, empty = 140))
        assertThat(park.availability).isEqualTo(Availability.OPEN_OCCUPANCY_UNKNOWN)
    }

    @Test
    fun `zero and swapped coordinates are invalid and never corrected`() {
        assertThat(fixturePark(106).location).isNull()
        // lat/lng look swapped; the parser must not guess the order from magnitudes.
        assertThat(fixturePark(107).location).isNull()
    }

    @Test
    fun `non numeric and non finite coordinates are invalid`() {
        val json = """[
            {"parkID": 1, "lat": "abc", "lng": 29.0},
            {"parkID": 2, "lat": "NaN", "lng": 29.0},
            {"parkID": 3, "lat": 41.0},
            {"parkID": 4, "lat": "41.000.1", "lng": 29.0}
        ]"""
        assertThat(parseList(json).parks.map { it.location }).containsExactly(null, null, null, null)
    }

    @Test
    fun `records without a usable id are skipped and duplicates keep the first record`() {
        assertThat(fixture.parks.map { it.id }).containsExactly(101, 102, 103, 104, 105, 106, 107)
        assertThat(fixture.skippedCount).isEqualTo(1)
        assertThat(fixture.duplicateCount).isEqualTo(1)
        assertThat(fixturePark(101).capacity).isEqualTo(200)
    }

    @Test
    fun `non object elements are counted as skipped`() {
        val parsed = parseList("""[{"parkID": 1}, 42, "x", null, {"parkID": 0}, {"parkID": -3}]""")
        assertThat(parsed.parks.map { it.id }).containsExactly(1)
        assertThat(parsed.skippedCount).isEqualTo(5)
    }

    @Test
    fun `blank strings are treated as missing`() {
        val park = parseList("""[{"parkID": 1, "parkName": "  ", "district": "", "workHours": null}]""").parks.single()
        assertThat(park.name).isNull()
        assertThat(park.district).isNull()
        assertThat(park.workHours).isNull()
    }

    @Test
    fun `fractional capacity is rejected rather than truncated`() {
        val park = parseList("""[{"parkID": 1, "capacity": 12.5, "emptyCapacity": "3.0"}]""").parks.single()
        assertThat(park.capacity).isNull()
        assertThat(park.emptyCapacity).isEqualTo(3)
    }

    @Test
    fun `a top level object is not a list`() {
        assertThat(ParkJsonParser.parseList(Json.parseToJsonElement("""{"parkID": 1}""")))
            .isEqualTo(ListParseResult.NotAnArray)
    }

    private fun parseDetail(json: String, requestedId: Int, fetchedAt: Long = 0L) =
        ParkJsonParser.parseDetail(Json.parseToJsonElement(json), requestedId, fetchedAt)

    @Test
    fun `detail fixture is parsed with tariff lines and source update time`() {
        val parsed = (parseDetail(Fixtures.parkDetail, requestedId = 101, fetchedAt = 42L) as DetailParseResult.Parsed).value
        val detail = parsed.detail
        assertThat(detail.parkId).isEqualTo(101)
        assertThat(detail.address).isEqualTo("Rıhtım Cad. No:1 Kadıköy/İstanbul")
        assertThat(detail.location).isEqualTo(GeoPoint(40.9903, 29.0236))
        assertThat(detail.occupancy).isEqualTo(Occupancy.Known(capacity = 200, empty = 33))
        assertThat(detail.freeTime).isEqualTo(15)
        assertThat(detail.monthlyFee).isEqualTo(2500.0)
        assertThat(detail.tariffLines).containsExactly(
            TariffLine("0-1 Saat", "40,00"),
            TariffLine("1-2 Saat", "60,00"),
            TariffLine("Tam Gün", "200,00"),
            TariffLine("Engelli araçlar ücretsizdir", null),
        ).inOrder()
        assertThat(detail.fetchedAtMillis).isEqualTo(42L)
        val expected = ZonedDateTime.of(2026, 10, 4, 14, 2, 30, 0, ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
        assertThat(detail.sourceUpdatedAt).isEqualTo(SourceTimestamp("04.10.2026 14:02:30", expected))
        assertThat(parsed.rawTariff).startsWith("0-1 Saat : 40,00;")
        // Kept verbatim; never reinterpreted.
        assertThat(parsed.rawAreaPolygon).startsWith("POLYGON ((29.0230 40.9900")
    }

    @Test
    fun `placeholder record for an unknown id is not found rather than real data`() {
        assertThat(parseDetail(Fixtures.parkDetailUnknownId, requestedId = 999_999_999)).isEqualTo(DetailParseResult.NotFound)
    }

    @Test
    fun `a detail for another id is not accepted`() {
        assertThat(parseDetail(Fixtures.parkDetail, requestedId = 102)).isEqualTo(DetailParseResult.NotFound)
    }

    @Test
    fun `null update date and zero monthly fee are carried as reported`() {
        val json = """[{"parkID": 5, "updateDate": null, "monthlyFee": 0.0, "tariff": ""}]"""
        val detail = (parseDetail(json, requestedId = 5) as DetailParseResult.Parsed).value.detail
        assertThat(detail.sourceUpdatedAt).isNull()
        assertThat(detail.monthlyFee).isEqualTo(0.0)
        assertThat(detail.tariffLines).isEmpty()
    }

    @Test
    fun `detail accepts a bare object and reports empty arrays`() {
        val bare = parseDetail("""{"parkID": 7, "address": "X"}""", requestedId = 7)
        assertThat((bare as DetailParseResult.Parsed).value.detail.address).isEqualTo("X")
        assertThat(parseDetail("[]", requestedId = 7)).isEqualTo(DetailParseResult.NotFound)
        assertThat(parseDetail("[42]", requestedId = 7)).isEqualTo(DetailParseResult.Malformed)
        assertThat(parseDetail("\"text\"", requestedId = 7)).isEqualTo(DetailParseResult.Malformed)
    }

    @Test
    fun `source timestamps in known formats are parsed and unknown formats keep the raw text`() {
        val istanbul = ZoneId.of("Europe/Istanbul")
        fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
            ZonedDateTime.of(y, mo, d, h, mi, s, 0, istanbul).toInstant().toEpochMilli()

        assertThat(ParkJsonParser.parseSourceTimestamp("04.10.2026 14:02:30").epochMillis)
            .isEqualTo(millis(2026, 10, 4, 14, 2, 30))
        assertThat(ParkJsonParser.parseSourceTimestamp("2026-10-04 14:02").epochMillis)
            .isEqualTo(millis(2026, 10, 4, 14, 2))
        assertThat(ParkJsonParser.parseSourceTimestamp("2026-10-04T11:02:00Z").epochMillis)
            .isEqualTo(millis(2026, 10, 4, 14, 2))
        assertThat(ParkJsonParser.parseSourceTimestamp("/Date(1791111720000)/").epochMillis)
            .isEqualTo(1791111720000L)

        val unknown = ParkJsonParser.parseSourceTimestamp(" dün öğlen ")
        assertThat(unknown.epochMillis).isNull()
        assertThat(unknown.raw).isEqualTo("dün öğlen")
    }
}
