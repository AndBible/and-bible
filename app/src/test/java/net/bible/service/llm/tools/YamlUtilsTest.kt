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

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class YamlUtilsTest {

    @Test
    fun simpleKeyValue() {
        val result = yamlToJson("name: John\nage: 30")
        assertEquals("John", result["name"]!!.jsonPrimitive.content)
        assertEquals(30, result["age"]!!.jsonPrimitive.int)
    }

    @Test
    fun nestedMap() {
        val yaml = """
            outer:
              inner: value
              count: 5
        """.trimIndent()
        val result = yamlToJson(yaml)
        val outer = result["outer"]!!.jsonObject
        assertEquals("value", outer["inner"]!!.jsonPrimitive.content)
        assertEquals(5, outer["count"]!!.jsonPrimitive.int)
    }

    @Test
    fun listValues() {
        val yaml = """
            items:
              - alpha
              - beta
              - gamma
        """.trimIndent()
        val result = yamlToJson(yaml)
        val items = result["items"]!!.jsonArray
        assertEquals(3, items.size)
        assertEquals("alpha", items[0].jsonPrimitive.content)
        assertEquals("beta", items[1].jsonPrimitive.content)
        assertEquals("gamma", items[2].jsonPrimitive.content)
    }

    @Test
    fun booleanAndNumberTypes() {
        val yaml = "enabled: true\ncount: 42\nprice: 9.99"
        val result = yamlToJson(yaml)
        assertEquals(true, result["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(42, result["count"]!!.jsonPrimitive.int)
        assertEquals(9.99, result["price"]!!.jsonPrimitive.double, 0.001)
    }

    @Test
    fun nullValue() {
        val yaml = "key: null"
        val result = yamlToJson(yaml)
        assertEquals(JsonNull, result["key"])
    }

    @Test
    fun stringWithSpecialCharacters() {
        val yaml = """key: "value with: colon and 'quotes'" """
        val result = yamlToJson(yaml)
        assertEquals("value with: colon and 'quotes'", result["key"]!!.jsonPrimitive.content)
    }

    @Test(expected = Exception::class)
    fun invalidYamlThrows() {
        // A YAML that loads as a non-Map (plain string) should fail the cast
        yamlToJson("just a plain string")
    }

    @Test
    fun nestedListOfMaps() {
        val yaml = """
            people:
              - name: Alice
                age: 25
              - name: Bob
                age: 30
        """.trimIndent()
        val result = yamlToJson(yaml)
        val people = result["people"]!!.jsonArray
        assertEquals(2, people.size)
        assertEquals("Alice", people[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(30, people[1].jsonObject["age"]!!.jsonPrimitive.int)
    }
}
