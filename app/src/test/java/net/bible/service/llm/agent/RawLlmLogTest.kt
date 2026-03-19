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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLlmLogTest {

    @Test
    fun `isEmpty returns true for new log`() {
        val log = RawLlmLog()
        assertTrue(log.isEmpty())
    }

    @Test
    fun `isEmpty returns false after adding entries`() {
        val log = RawLlmLog()
        log.addMessage("SYSTEM", "You are helpful.")
        assertFalse(log.isEmpty())
    }

    @Test
    fun `format includes all message types`() {
        val log = RawLlmLog()
        log.addMessage("SYSTEM", "System prompt")
        log.addMessage("USER", "User message")
        log.addRawApiResponse(1, """{"choices":[]}""")
        log.addToolCall("searchVerses", "call_1", """{"query":"love"}""")
        log.addToolResult("call_1", """{"result":"found"}""")
        log.addMessage("ASSISTANT", "Final response")

        val formatted = log.format()

        assertTrue(formatted.contains("=== SYSTEM ==="))
        assertTrue(formatted.contains("System prompt"))
        assertTrue(formatted.contains("=== USER ==="))
        assertTrue(formatted.contains("User message"))
        assertTrue(formatted.contains("=== RAW API RESPONSE (iteration 1) ==="))
        assertTrue(formatted.contains("""{"choices":[]}"""))
        assertTrue(formatted.contains("=== TOOL_CALL: searchVerses [call_1] ==="))
        assertTrue(formatted.contains("""{"query":"love"}"""))
        assertTrue(formatted.contains("=== TOOL_RESULT [call_1] ==="))
        assertTrue(formatted.contains("""{"result":"found"}"""))
        assertTrue(formatted.contains("=== ASSISTANT ==="))
        assertTrue(formatted.contains("Final response"))
    }

    @Test
    fun `format preserves entry order`() {
        val log = RawLlmLog()
        log.addMessage("SYSTEM", "first")
        log.addMessage("USER", "second")
        log.addRawApiResponse(1, "third")

        val formatted = log.format()
        val systemIdx = formatted.indexOf("first")
        val userIdx = formatted.indexOf("second")
        val responseIdx = formatted.indexOf("third")

        assertTrue(systemIdx < userIdx)
        assertTrue(userIdx < responseIdx)
    }

    @Test
    fun `format handles null content`() {
        val log = RawLlmLog()
        log.addMessage("ASSISTANT", null)

        val formatted = log.format()
        assertTrue(formatted.contains("=== ASSISTANT ==="))
        assertTrue(formatted.contains("(empty)"))
    }

    @Test
    fun `multiple iterations are distinguished`() {
        val log = RawLlmLog()
        log.addRawApiResponse(1, "response1")
        log.addRawApiResponse(2, "response2")

        val formatted = log.format()
        assertTrue(formatted.contains("=== RAW API RESPONSE (iteration 1) ==="))
        assertTrue(formatted.contains("=== RAW API RESPONSE (iteration 2) ==="))
    }
}
