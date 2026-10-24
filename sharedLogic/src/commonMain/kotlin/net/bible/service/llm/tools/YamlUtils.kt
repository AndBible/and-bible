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

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Convert YAML string to kotlinx.serialization JsonObject.
 * Useful for defining tool parameter schemas in a more readable format.
 *
 * Implementation choice (L1b phase 0, spec S9): kaml 0.104.0, a Kotlin Multiplatform YAML library
 * (published for jvm and iosArm64/iosSimulatorArm64, so this file has no JVM-only imports and can move
 * to commonMain). It replaced snakeyaml, which is JVM-only; snakeyaml is kept as the test oracle in
 * `YamlToJsonParityTest`, which proves identical output for every registered tool schema.
 *
 * Known differences from snakeyaml, none of which any real schema depends on (the parity test checks):
 * - kaml is YAML 1.2: bare `yes`/`no`/`on`/`off`/`y`/`n` stay strings, where snakeyaml (YAML 1.1) makes
 *   them booleans. Write `true`/`false` in schemas.
 * - kaml's node tree does not record whether a scalar was quoted, so a quoted `"123"` or `"true"`
 *   becomes a number or boolean here, where snakeyaml keeps a string. Do not quote such values.
 * - Only decimal integers, floats and `true`/`false` (any case) are typed; hex/octal/underscored
 *   numbers stay strings.
 *
 * @throws Exception if the document is not a mapping or is malformed.
 */
fun yamlToJson(yaml: String): JsonObject {
    val node = Yaml.default.parseToYamlNode(yaml)
    return nodeToJson(node) as? JsonObject
        ?: throw IllegalArgumentException("YAML document must be a mapping")
}

private fun nodeToJson(node: YamlNode): JsonElement = when (node) {
    is YamlNull -> JsonNull
    is YamlMap -> JsonObject(node.entries.entries.associate { (k, v) -> k.content to nodeToJson(v) })
    is YamlList -> JsonArray(node.items.map { nodeToJson(it) })
    is YamlTaggedNode -> nodeToJson(node.innerNode)
    is YamlScalar -> scalarToJson(node.content)
}

private val INT_RE = Regex("[-+]?(0|[1-9][0-9]*)")
private val FLOAT_RE = Regex("[-+]?([0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+)([eE][-+]?[0-9]+)?")

private fun scalarToJson(text: String): JsonElement = when {
    text == "true" || text == "True" || text == "TRUE" -> JsonPrimitive(true)
    text == "false" || text == "False" || text == "FALSE" -> JsonPrimitive(false)
    INT_RE.matches(text) -> text.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(text)
    FLOAT_RE.matches(text) -> JsonPrimitive(text.toDouble())
    else -> JsonPrimitive(text)
}
