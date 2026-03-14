/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.llm.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.yaml.snakeyaml.Yaml

/**
 * Convert YAML string to kotlinx.serialization JsonObject.
 * Useful for defining tool parameter schemas in a more readable format.
 */
fun yamlToJson(yaml: String): JsonObject {
    val yamlParser = Yaml()
    val map: Map<String, Any> = yamlParser.load(yaml)
    return mapToJsonObject(map)
}

private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
    val entries = map.entries.associate { (key, value) -> key to convertValue(value) }
    return JsonObject(entries)
}

private fun convertValue(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is Map<*, *> -> {
            val stringMap = value.entries.associate { (k, v) -> k.toString() to v }
            mapToJsonObject(stringMap)
        }
        is List<*> -> JsonArray(value.map { convertValue(it) })
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
