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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolUtilsTest {

    // --- stripMarkdownFromTitle ---

    @Test
    fun stripMarkdownFromTitle_plainText() {
        assertEquals("Hello World", stripMarkdownFromTitle("Hello World"))
    }

    @Test
    fun stripMarkdownFromTitle_markdownLink() {
        assertEquals("click here", stripMarkdownFromTitle("[click here](https://example.com)"))
    }

    @Test
    fun stripMarkdownFromTitle_bold() {
        assertEquals("bold text", stripMarkdownFromTitle("**bold text**"))
    }

    @Test
    fun stripMarkdownFromTitle_italic() {
        assertEquals("italic text", stripMarkdownFromTitle("*italic text*"))
    }

    @Test
    fun stripMarkdownFromTitle_underscore() {
        assertEquals("underscored", stripMarkdownFromTitle("__underscored__"))
    }

    @Test
    fun stripMarkdownFromTitle_backtick() {
        assertEquals("code", stripMarkdownFromTitle("`code`"))
    }

    @Test
    fun stripMarkdownFromTitle_heading() {
        assertEquals("Heading", stripMarkdownFromTitle("# Heading"))
    }

    @Test
    fun stripMarkdownFromTitle_mixed() {
        assertEquals(
            "Romans 8:28 - Analysis",
            stripMarkdownFromTitle("**[Romans 8:28](sword://KJV/Rom.8.28)** - _Analysis_")
        )
    }

    @Test
    fun stripMarkdownFromTitle_whitespace() {
        assertEquals("trimmed", stripMarkdownFromTitle("  trimmed  "))
    }

    // --- normalizeLlmText ---

    @Test
    fun normalizeLlmText_escapedNewline() {
        assertEquals("line1\nline2", normalizeLlmText("line1\\nline2"))
    }

    @Test
    fun normalizeLlmText_escapedTab() {
        assertEquals("col1\tcol2", normalizeLlmText("col1\\tcol2"))
    }

    @Test
    fun normalizeLlmText_multipleEscapes() {
        assertEquals("a\nb\tc", normalizeLlmText("a\\nb\\tc"))
    }

    @Test
    fun normalizeLlmText_noEscapes() {
        assertEquals("plain text", normalizeLlmText("plain text"))
    }

    // --- formatJsonForLog ---

    @Test
    fun formatJsonForLog_simpleObject() {
        val json = JSONObject().apply {
            put("status", "success")
            put("data", JSONObject().apply {
                put("book", "KJV")
                put("verseRef", "Gen.1.1")
            })
        }.toString()

        val result = formatJsonForLog(json)
        assertTrue(result.contains("book: KJV"))
        assertTrue(result.contains("verseRef: Gen.1.1"))
    }

    @Test
    fun formatJsonForLog_nestedObject() {
        val json = JSONObject().apply {
            put("status", "success")
            put("data", JSONObject().apply {
                put("nested", JSONObject())
                put("count", 5)
            })
        }.toString()

        val result = formatJsonForLog(json)
        assertTrue(result.contains("nested: {...}"))
        assertTrue(result.contains("count: 5"))
    }

    @Test
    fun formatJsonForLog_arrayData() {
        val json = JSONObject().apply {
            put("status", "success")
            put("data", JSONArray().apply {
                put("item1")
                put("item2")
            })
        }.toString()

        val result = formatJsonForLog(json)
        assertEquals("2 items", result)
    }

    @Test
    fun formatJsonForLog_longValue_truncated() {
        val longText = "A".repeat(200)
        val json = JSONObject().apply {
            put("status", "success")
            put("data", JSONObject().apply {
                put("text", longText)
            })
        }.toString()

        val result = formatJsonForLog(json)
        assertTrue(result.contains("..."))
        assertTrue(result.length < 200)
    }

    @Test
    fun formatJsonForLog_invalidJson() {
        val result = formatJsonForLog("not json at all")
        assertEquals("not json at all", result)
    }

    // --- shortId ---

    @Test
    fun shortId_longId() {
        assertEquals("12345678...", shortId("12345678-1234-1234-1234-123456789012"))
    }

    @Test
    fun shortId_shortId() {
        assertEquals("abc", shortId("abc"))
    }

    @Test
    fun shortId_exactlyEight() {
        assertEquals("12345678", shortId("12345678"))
    }
}
