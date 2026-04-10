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

package net.bible.service.llm.agent

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LoopDetectionTest {

    private val searchBible = AgentTool.SEARCH_BIBLE
    private val getVerse = AgentTool.GET_VERSE_CONTENT

    private fun sig(tool: AgentTool, args: String): ToolCallSignature {
        // Must match the normalization logic in extractSignatures (sorted keys)
        val normalized = try {
            val json = org.json.JSONObject(args)
            org.json.JSONObject().apply {
                json.keys().asSequence().sorted().forEach { key -> put(key, json.get(key)) }
            }.toString()
        } catch (_: Exception) { args }
        return ToolCallSignature(tool, normalized.hashCode())
    }

    // === detectLoop ===

    @Test
    fun detectLoop_emptyHistory() {
        assertFalse(detectLoop(emptyList()))
    }

    @Test
    fun detectLoop_belowThreshold() {
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"love"}""")
        )
        assertFalse(detectLoop(history))
    }

    @Test
    fun detectLoop_exactlyAtThreshold() {
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"love"}""")
        )
        assertTrue(detectLoop(history))
    }

    @Test
    fun detectLoop_differentTools_noLoop() {
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.1"}"""),
            sig(searchBible, """{"query":"grace"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.2"}"""),
            sig(searchBible, """{"query":"faith"}""")
        )
        assertFalse(detectLoop(history))
    }

    @Test
    fun detectLoop_sameToolDifferentArgs_noLoop() {
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"grace"}"""),
            sig(searchBible, """{"query":"faith"}"""),
            sig(searchBible, """{"query":"hope"}"""),
            sig(searchBible, """{"query":"peace"}""")
        )
        assertFalse(detectLoop(history))
    }

    @Test
    fun detectLoop_repeatedCallsWithOthersBetween() {
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.1"}"""),
            sig(searchBible, """{"query":"love"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.2"}"""),
            sig(searchBible, """{"query":"love"}""")
        )
        assertTrue(detectLoop(history))
    }

    @Test
    fun detectLoop_windowLimitsScope() {
        // Old repeated calls outside the window shouldn't trigger
        val history = listOf(
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"love"}"""),
            sig(searchBible, """{"query":"love"}"""),
            // --- these 5 are the window ---
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.1"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.2"}"""),
            sig(getVerse, """{"book":"KJV","verseRef":"Gen.1.3"}"""),
            sig(searchBible, """{"query":"grace"}"""),
            sig(searchBible, """{"query":"faith"}""")
        )
        assertFalse(detectLoop(history))
    }

    @Test
    fun detectLoop_jsonKeyOrderNormalized() {
        // Same args with different key order should be identical
        val history = listOf(
            sig(searchBible, """{"query":"love","books":["KJV"]}"""),
            sig(searchBible, """{"books":["KJV"],"query":"love"}"""),
            sig(searchBible, """{"query":"love","books":["KJV"]}""")
        )
        assertTrue(detectLoop(history))
    }

    // === extractSignatures ===

    @Test
    fun extractSignatures_basic() {
        val toolCalls = listOf(
            ToolCall(id = "1", tool = searchBible, arguments = """{"query":"love"}"""),
            ToolCall(id = "2", tool = getVerse, arguments = """{"book":"KJV","verseRef":"Gen.1.1"}""")
        )
        val sigs = extractSignatures(toolCalls)
        assertEquals(2, sigs.size)
        assertEquals(searchBible, sigs[0].tool)
        assertEquals(getVerse, sigs[1].tool)
    }

    @Test
    fun extractSignatures_sameArgsProduceSameHash() {
        val calls1 = listOf(
            ToolCall(id = "1", tool = searchBible, arguments = """{"query":"love"}""")
        )
        val calls2 = listOf(
            ToolCall(id = "2", tool = searchBible, arguments = """{"query":"love"}""")
        )
        assertEquals(extractSignatures(calls1)[0], extractSignatures(calls2)[0])
    }

    @Test
    fun extractSignatures_differentArgsProduceDifferentHash() {
        val calls = listOf(
            ToolCall(id = "1", tool = searchBible, arguments = """{"query":"love"}"""),
            ToolCall(id = "2", tool = searchBible, arguments = """{"query":"grace"}""")
        )
        val sigs = extractSignatures(calls)
        assertTrue(sigs[0] != sigs[1])
    }
}
