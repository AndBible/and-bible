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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.AgentTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Proves [yamlToJson] produces the same JSON as the snakeyaml implementation it replaced
 * (copied below as [snakeYamlOracle]) for every real tool parameter schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class YamlToJsonParityTest {

    /** The pre-L1b implementation, verbatim, kept as the oracle. */
    private fun snakeYamlOracle(yaml: String): JsonObject {
        val map: Map<String, Any> = Yaml().load(yaml)
        return oracleMap(map)
    }

    private fun oracleMap(map: Map<String, Any?>): JsonObject =
        JsonObject(map.entries.associate { (key, value) -> key to oracleValue(value) })

    private fun oracleValue(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> oracleMap(value.entries.associate { (k, v) -> k.toString() to v })
        is List<*> -> JsonArray(value.map { oracleValue(it) })
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun sourceFileOf(tool: Any): File {
        val rel = tool::class.java.name.substringBefore('$').replace('.', '/') + ".kt"
        return net.bible.test.ProductionSources.resolve(rel).also { check(it.isFile) { "tool source not found: $rel" } }
    }

    /** Raw YAML source of each registered tool's `yamlToJson("""...""")` call, keyed by tool. */
    private fun schemaSources(): Map<AgentTool, Pair<String, JsonObject>> {
        val re = Regex("yamlToJson\\(\"\"\"(.*?)\"\"\"\\)", RegexOption.DOT_MATCHES_ALL)
        return AgentTool.entries.associateWith { agentTool ->
            val tool = ToolRegistry.get(agentTool)!!
            val matches = re.findAll(sourceFileOf(tool).readText()).toList()
            assertEquals("exactly one schema literal in ${tool::class.java.name}", 1, matches.size)
            matches.single().groupValues[1] to tool.parametersSchema
        }
    }

    @Test
    fun everyToolSchemaMatchesSnakeYaml() {
        val sources = schemaSources()
        println("YamlToJsonParityTest: ${sources.size} tool schemas")
        assertTrue("schema count must be > 0", sources.isNotEmpty())
        for ((tool, pair) in sources) {
            val (yaml, registered) = pair
            val oracle = snakeYamlOracle(yaml)
            assertEquals("yamlToJson vs snakeyaml for $tool", oracle, yamlToJson(yaml))
            assertEquals("registered schema vs snakeyaml for $tool", oracle, registered)
        }
    }

    @Test
    fun yesIsHandledLikeSnakeYaml() {
        // snakeyaml is a YAML 1.1 parser: bare yes/no/on/off are booleans. A YAML 1.2 parser keeps
        // them as strings. If yamlToJson cannot match, no real schema may depend on the difference.
        val yaml = "type: boolean\ndefault: yes"
        val oracle = snakeYamlOracle(yaml)
        if (yamlToJson(yaml) != oracle) {
            val yes11 = Regex(":\\s*(yes|no|on|off|y|n)\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            val offenders = schemaSources().filter { (_, p) -> yes11.containsMatchIn(p.first) }.keys
            assertTrue("schemas using YAML-1.1-only scalars: $offenders", offenders.isEmpty())
        }
    }

    @Test
    fun yaml11ScalarDifferenceIsDocumented() {
        // kaml is YAML 1.2: `yes` stays a string, snakeyaml (1.1) yields a boolean. Pin both so a parser
        // change that alters this is noticed; the schema scan in yesIsHandledLikeSnakeYaml covers real data.
        assertEquals(JsonPrimitive("yes"), yamlToJson("default: yes")["default"])
        assertEquals(JsonPrimitive(true), snakeYamlOracle("default: yes")["default"])
    }
}
