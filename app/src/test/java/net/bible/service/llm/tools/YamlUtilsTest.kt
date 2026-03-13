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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class YamlUtilsTest {

    @Test
    fun simpleKeyValue() {
        val result = yamlToJson("name: John\nage: 30")
        assertEquals("John", result.getString("name"))
        assertEquals(30, result.getInt("age"))
    }

    @Test
    fun nestedMap() {
        val yaml = """
            outer:
              inner: value
              count: 5
        """.trimIndent()
        val result = yamlToJson(yaml)
        val outer = result.getJSONObject("outer")
        assertEquals("value", outer.getString("inner"))
        assertEquals(5, outer.getInt("count"))
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
        val items = result.getJSONArray("items")
        assertEquals(3, items.length())
        assertEquals("alpha", items.getString(0))
        assertEquals("beta", items.getString(1))
        assertEquals("gamma", items.getString(2))
    }

    @Test
    fun booleanAndNumberTypes() {
        val yaml = "enabled: true\ncount: 42\nprice: 9.99"
        val result = yamlToJson(yaml)
        assertEquals(true, result.getBoolean("enabled"))
        assertEquals(42, result.getInt("count"))
        assertEquals(9.99, result.getDouble("price"), 0.001)
    }

    @Test
    fun nullValue() {
        val yaml = "key: null"
        val result = yamlToJson(yaml)
        assertTrue(result.isNull("key"))
    }

    @Test
    fun stringWithSpecialCharacters() {
        val yaml = """key: "value with: colon and 'quotes'" """
        val result = yamlToJson(yaml)
        assertEquals("value with: colon and 'quotes'", result.getString("key"))
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
        val people = result.getJSONArray("people")
        assertEquals(2, people.length())
        assertEquals("Alice", people.getJSONObject(0).getString("name"))
        assertEquals(30, people.getJSONObject(1).getInt("age"))
    }
}
