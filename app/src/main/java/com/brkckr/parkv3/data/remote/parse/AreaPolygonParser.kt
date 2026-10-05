package com.brkckr.parkv3.data.remote.parse

import com.brkckr.parkv3.domain.Geo
import com.brkckr.parkv3.domain.model.AreaPolygon
import com.brkckr.parkv3.domain.model.GeoPoint

/**
 * Reads the `areaPolygon` WKT of ParkDetay (docs/adr/0012). Coordinates are taken in the WKT
 * standard order, X = longitude and Y = latitude, and are never swapped. An area is returned
 * only when every point is a valid Istanbul coordinate within [MAX_DISTANCE_FROM_PARK_M] of
 * the park's own location; anything else yields no area rather than a guess.
 */
object AreaPolygonParser {

    const val MAX_DISTANCE_FROM_PARK_M = 2_000.0
    private const val MAX_POINTS = 2_000

    fun parse(wkt: String?, parkLocation: GeoPoint?): List<AreaPolygon> {
        if (wkt.isNullOrBlank() || parkLocation == null) return emptyList()
        val text = wkt.trim()
        val keyword = text.takeWhile { it.isLetter() }.uppercase()
        val depth = when (keyword) {
            "POLYGON" -> 2
            "MULTIPOLYGON" -> 3
            else -> return emptyList()
        }
        val reader = Reader(text, start = keyword.length)
        reader.skipDimensionTags() // "POLYGON Z (...)"
        val root = reader.group(depth) ?: return emptyList()
        if (!reader.atEnd()) return emptyList()
        val polygons = if (depth == 2) listOf(root) else root.items.map { it as? Node.Group ?: return emptyList() }
        val areas = polygons.map { toArea(it) ?: return emptyList() }
        val points = areas.flatMap { it.outer + it.holes.flatten() }
        if (points.size > MAX_POINTS) return emptyList()
        if (points.any { Geo.distanceMeters(it, parkLocation) > MAX_DISTANCE_FROM_PARK_M }) return emptyList()
        return areas
    }

    private fun toArea(polygon: Node.Group): AreaPolygon? {
        val rings = polygon.items.map { ring -> toRing(ring) ?: return null }
        if (rings.isEmpty()) return null
        return AreaPolygon(outer = rings.first(), holes = rings.drop(1))
    }

    private fun toRing(node: Node): List<GeoPoint>? {
        val group = node as? Node.Group ?: return null
        val points = group.items.map { item ->
            val point = item as? Node.Point ?: return null
            GeoPoint.validOrNull(latitude = point.y, longitude = point.x) ?: return null
        }
        return points.takeIf { it.distinct().size >= 3 }
    }

    private sealed interface Node {
        data class Point(val x: Double, val y: Double) : Node
        data class Group(val items: List<Node>) : Node
    }

    /** Minimal reader for nested "(x y, x y)" groups; null on anything unexpected. */
    private class Reader(private val text: String, start: Int) {
        private var pos = start

        fun atEnd(): Boolean {
            skipSpaces()
            return pos == text.length
        }

        fun skipDimensionTags() {
            skipSpaces()
            while (pos < text.length && text[pos].isLetter()) pos++
        }

        /** A parenthesised list nested [depth] levels deep, with points at the innermost level. */
        fun group(depth: Int): Node.Group? {
            skipSpaces()
            if (!eat('(')) return null
            val items = mutableListOf<Node>()
            do {
                skipSpaces()
                items += (if (depth > 1) group(depth - 1) else point()) ?: return null
                skipSpaces()
            } while (eat(','))
            return if (eat(')')) Node.Group(items) else null
        }

        private fun point(): Node.Point? {
            val x = number() ?: return null
            val y = number() ?: return null
            while (number() != null) Unit // optional Z and M values
            return Node.Point(x, y)
        }

        private fun number(): Double? {
            skipSpaces()
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            return text.substring(start, pos).toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: run { pos = start; null }
        }

        private fun eat(char: Char): Boolean {
            if (pos < text.length && text[pos] == char) {
                pos++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
