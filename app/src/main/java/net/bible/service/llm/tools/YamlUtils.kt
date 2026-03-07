/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

/**
 * Convert YAML string to JSONObject.
 * Useful for defining tool parameter schemas in a more readable format.
 */
fun yamlToJson(yaml: String): JSONObject {
    val yamlParser = Yaml()
    val map: Map<String, Any> = yamlParser.load(yaml)
    return mapToJsonObject(map)
}

private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
    val json = JSONObject()
    for ((key, value) in map) {
        json.put(key, convertValue(value))
    }
    return json
}

private fun convertValue(value: Any?): Any? {
    return when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val stringMap = value.entries.associate { (k, v) -> k.toString() to v }
            mapToJsonObject(stringMap)
        }
        is List<*> -> {
            val array = JSONArray()
            for (item in value) {
                array.put(convertValue(item))
            }
            array
        }
        else -> value
    }
}
