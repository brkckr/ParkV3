package com.brkckr.parkv3.data.remote.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Type-tolerant field readers. The source schema is undocumented, so a value may arrive as a
 * number, a numeric string or null. Anything that cannot be read unambiguously becomes null;
 * nothing defaults to 0.
 */
internal class JsonFields(private val obj: JsonObject) {

    private val lowerCaseKeys: Map<String, String> by lazy {
        obj.keys.associateBy { it.lowercase() }
    }

    /** Finds the first present key, matching case-insensitively. */
    private fun primitive(vararg names: String): JsonPrimitive? {
        for (name in names) {
            val element = obj[name] ?: lowerCaseKeys[name.lowercase()]?.let { obj[it] }
            when (element) {
                null -> continue
                is JsonNull -> return null
                is JsonPrimitive -> return element
                is JsonObject, is JsonArray -> return null
            }
        }
        return null
    }

    fun string(vararg names: String): String? {
        val p = primitive(*names) ?: return null
        return p.content.trim().takeIf { it.isNotEmpty() }
    }

    fun int(vararg names: String): Int? {
        val p = primitive(*names) ?: return null
        if (p.isString || p.booleanOrNull == null) {
            val text = p.content.trim()
            text.toIntOrNull()?.let { return it }
            val asDouble = parseDecimal(text) ?: return null
            return asDouble.takeIf { it % 1.0 == 0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }
                ?.toInt()
        }
        return null
    }

    fun double(vararg names: String): Double? {
        val p = primitive(*names) ?: return null
        if (!p.isString && p.booleanOrNull != null) return null
        return parseDecimal(p.content.trim())?.takeIf { it.isFinite() }
    }

    private fun parseDecimal(text: String): Double? {
        if (text.isEmpty()) return null
        text.toDoubleOrNull()?.let { return it }
        // Accept a single decimal comma ("41,0123") but never guess thousands separators.
        if (text.count { it == ',' } == 1 && '.' !in text) {
            return text.replace(',', '.').toDoubleOrNull()
        }
        return null
    }
}
