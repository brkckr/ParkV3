package com.brkckr.parkv3.data.remote.parse

import com.brkckr.parkv3.domain.model.GeoPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AreaPolygonParserTest {

    // Shape of a measured record (parkID 3068): point and the first corners of its polygon.
    private val park = GeoPoint(41.0246, 29.0915)
    private val measured = "POLYGON ((29.091051461068577 41.025152250107709, 29.092017056313939 41.025142132526149, " +
        "29.092051925031132 41.023770174067572, 29.091040732232518 41.02396443540, 29.091051461068577 41.025152250107709))"

    private fun ring(vararg points: Pair<Double, Double>) = points.joinToString(", ", "(", ")") { (x, y) -> "$x $y" }

    @Test
    fun `WKT order is read as longitude then latitude`() {
        val areas = AreaPolygonParser.parse(measured, park)

        assertThat(areas).hasSize(1)
        assertThat(areas[0].outer).hasSize(5)
        assertThat(areas[0].outer[0]).isEqualTo(GeoPoint(41.025152250107709, 29.091051461068577))
        assertThat(areas[0].holes).isEmpty()
    }

    @Test
    fun `latitude first order is never swapped into place`() {
        val swapped = "POLYGON ((41.0251 29.0910, 41.0251 29.0920, 41.0237 29.0920, 41.0251 29.0910))"

        assertThat(AreaPolygonParser.parse(swapped, park)).isEmpty()
    }

    @Test
    fun `an area away from the park's own point is not drawn`() {
        // Valid Istanbul coordinates, about 9 km from the park.
        val elsewhere = "POLYGON ((28.9850 41.0369, 28.9860 41.0369, 28.9860 41.0379, 28.9850 41.0369))"

        assertThat(AreaPolygonParser.parse(elsewhere, park)).isEmpty()
        assertThat(AreaPolygonParser.parse(measured, parkLocation = null)).isEmpty()
    }

    @Test
    fun `one corner far away rejects the whole area`() {
        val stretched = "POLYGON ((29.0910 41.0251, 29.0920 41.0251, 29.2000 41.0237, 29.0910 41.0251))"

        assertThat(AreaPolygonParser.parse(stretched, park)).isEmpty()
    }

    @Test
    fun `holes and multipolygons are kept`() {
        val outer = ring(29.090 to 41.024, 29.093 to 41.024, 29.093 to 41.026, 29.090 to 41.026, 29.090 to 41.024)
        val hole = ring(29.091 to 41.0245, 29.092 to 41.0245, 29.092 to 41.0255, 29.091 to 41.0245)
        val second = ring(29.094 to 41.024, 29.095 to 41.024, 29.095 to 41.025, 29.094 to 41.024)

        val withHole = AreaPolygonParser.parse("POLYGON ($outer, $hole)", park)
        assertThat(withHole).hasSize(1)
        assertThat(withHole[0].holes).hasSize(1)

        val multi = AreaPolygonParser.parse("MULTIPOLYGON (($outer, $hole), ($second))", park)
        assertThat(multi).hasSize(2)
        assertThat(multi[1].outer[0]).isEqualTo(GeoPoint(41.024, 29.094))
    }

    @Test
    fun `third and fourth dimensions are ignored`() {
        val withZ = "POLYGON Z ((29.0910 41.0251 0, 29.0920 41.0251 0, 29.0920 41.0237 0, 29.0910 41.0251 0))"

        assertThat(AreaPolygonParser.parse(withZ, park).single().outer[1]).isEqualTo(GeoPoint(41.0251, 29.0920))
    }

    @Test
    fun `anything that is not a well formed polygon yields no area`() {
        listOf(
            null,
            "",
            "   ",
            "POLYGON EMPTY",
            "POINT (29.0915 41.0246)",
            "LINESTRING (29.0910 41.0251, 29.0920 41.0251)",
            "POLYGON ((29.0910 41.0251, 29.0920 41.0251))", // fewer than three points
            "POLYGON ((29.0910 41.0251, 29.0920 41.0251, 29.0920 41.0237, 29.0910 41.0251)", // unbalanced
            "POLYGON ((29.0910 41.0251, 29.0920 41.0251, 29.0920 41.0237, 29.0910 41.0251)) trailing",
            "POLYGON ((29,0910 41,0251, 29,0920 41,0251, 29,0920 41,0237))", // decimal commas
            "POLYGON ((29.0910 41.0251, x 41.0251, 29.0920 41.0237, 29.0910 41.0251))",
            "POLYGON ((NaN 41.0251, 29.0920 41.0251, 29.0920 41.0237, 29.0910 41.0251))",
            "MULTIPOLYGON ((29.0910 41.0251, 29.0920 41.0251, 29.0920 41.0237, 29.0910 41.0251))", // missing a level
        ).forEach { wkt ->
            assertThat(AreaPolygonParser.parse(wkt, park)).isEmpty()
        }
    }
}
