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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolResultTest {

    // --- Success.toJson ---

    @Test
    fun successWithJsonObject() {
        val data = JSONObject().apply {
            put("key", "value")
            put("count", 42)
        }
        val result = ToolResult.success(data)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals("value", json.getJSONObject("data").getString("key"))
        assertEquals(42, json.getJSONObject("data").getInt("count"))
    }

    @Test
    fun successWithJsonArray() {
        val data = JSONArray().apply {
            put("item1")
            put("item2")
        }
        val result = ToolResult.success(data)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals(2, json.getJSONArray("data").length())
    }

    @Test
    fun successWithString() {
        val result = ToolResult.success("hello")
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals("hello", json.getString("data"))
    }

    @Test
    fun successWithNumber() {
        val result = ToolResult.success(42)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals(42, json.getInt("data"))
    }

    @Test
    fun successWithBoolean() {
        val result = ToolResult.success(true)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertTrue(json.getBoolean("data"))
    }

    @Test
    fun successWithMap() {
        val data = mapOf("a" to 1, "b" to "two")
        val result = ToolResult.success(data)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals(1, json.getJSONObject("data").getInt("a"))
        assertEquals("two", json.getJSONObject("data").getString("b"))
    }

    @Test
    fun successWithList() {
        val data = listOf("x", "y", "z")
        val result = ToolResult.success(data)
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals(3, json.getJSONArray("data").length())
        assertEquals("x", json.getJSONArray("data").getString(0))
    }

    @Test
    fun successWithBuilderSyntax() {
        val result = ToolResult.success {
            put("title", "Test")
            put("finished", true)
        }
        val json = JSONObject(result.toJson())

        assertEquals("success", json.getString("status"))
        assertEquals("Test", json.getJSONObject("data").getString("title"))
        assertTrue(json.getJSONObject("data").getBoolean("finished"))
    }

    // --- Error.toJson ---

    @Test
    fun errorWithMessageOnly() {
        val result = ToolResult.error("Something went wrong")
        val json = JSONObject(result.toJson())

        assertEquals("error", json.getString("status"))
        assertEquals("Something went wrong", json.getString("message"))
        assertFalse(json.has("code"))
    }

    @Test
    fun errorWithMessageAndCode() {
        val result = ToolResult.error("Book not found", "BOOK_NOT_FOUND")
        val json = JSONObject(result.toJson())

        assertEquals("error", json.getString("status"))
        assertEquals("Book not found", json.getString("message"))
        assertEquals("BOOK_NOT_FOUND", json.getString("code"))
    }

    // --- Type checks ---

    @Test
    fun successIsCorrectType() {
        val result = ToolResult.success("data")
        assertTrue(result is ToolResult.Success)
        assertFalse(result is ToolResult.Error)
    }

    @Test
    fun errorIsCorrectType() {
        val result = ToolResult.error("msg")
        assertTrue(result is ToolResult.Error)
        assertFalse(result is ToolResult.Success)
    }
}
