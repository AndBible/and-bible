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

package net.bible.service.llm.agent

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
class ToolCallParserTest {

    // --- parseMessage: TextResponse ---

    @Test
    fun textResponseWithContent() {
        val msg = JSONObject().put("role", "assistant").put("content", "Hello world")
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("Hello world", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun textResponseNullContent() {
        val msg = JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun textResponseMissingContent() {
        val msg = JSONObject().put("role", "assistant")
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun nullToolCallsYieldsTextResponse() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", "text")
            .put("tool_calls", JSONObject.NULL)
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("text", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun emptyToolCallsArrayYieldsTextResponse() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", "fallback")
            .put("tool_calls", JSONArray())
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("fallback", (result as ParsedResponse.TextResponse).content)
    }

    // --- parseMessage: ToolCalls ---

    @Test
    fun singleToolCall() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", JSONObject.NULL)
            .put("tool_calls", JSONArray().put(
                JSONObject()
                    .put("id", "call_1")
                    .put("type", "function")
                    .put("function", JSONObject()
                        .put("name", "getVerse")
                        .put("arguments", """{"book":"KJV","ref":"Gen.1.1"}"""))
            ))
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.ToolCalls)
        val tc = (result as ParsedResponse.ToolCalls)
        assertNull(tc.content)
        assertEquals(1, tc.toolCalls.size)
        assertEquals("call_1", tc.toolCalls[0].id)
        assertEquals("getVerse", tc.toolCalls[0].name)
        assertTrue(tc.toolCalls[0].arguments.contains("KJV"))
    }

    @Test
    fun multipleToolCalls() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", JSONObject.NULL)
            .put("tool_calls", JSONArray()
                .put(JSONObject()
                    .put("id", "call_1").put("type", "function")
                    .put("function", JSONObject().put("name", "tool1").put("arguments", "{}")))
                .put(JSONObject()
                    .put("id", "call_2").put("type", "function")
                    .put("function", JSONObject().put("name", "tool2").put("arguments", "{}")))
            )
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.ToolCalls)
        assertEquals(2, (result as ParsedResponse.ToolCalls).toolCalls.size)
    }

    @Test
    fun toolCallWithContent() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", "Let me look that up")
            .put("tool_calls", JSONArray().put(
                JSONObject()
                    .put("id", "call_1").put("type", "function")
                    .put("function", JSONObject().put("name", "search").put("arguments", "{}"))
            ))
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.ToolCalls)
        assertEquals("Let me look that up", (result as ParsedResponse.ToolCalls).content)
    }

    @Test
    fun unknownTypeIsSkipped() {
        val msg = JSONObject()
            .put("role", "assistant")
            .put("content", JSONObject.NULL)
            .put("tool_calls", JSONArray().put(
                JSONObject()
                    .put("id", "call_1").put("type", "code_interpreter")
                    .put("function", JSONObject().put("name", "run").put("arguments", "{}"))
            ))
        val result = ToolCallParser.parseMessage(msg)
        // Only non-function types → empty tool list → TextResponse
        assertTrue(result is ParsedResponse.TextResponse)
    }

    // --- parseMessage: ParseError ---

    @Test
    fun invalidJsonYieldsParseError() {
        // A JSONObject with a tool_calls that is not an array should cause an exception
        val msg = JSONObject().put("tool_calls", "not_an_array")
        val result = ToolCallParser.parseMessage(msg)
        assertTrue(result is ParsedResponse.ParseError)
    }

    // --- ToolCall.parseArguments ---

    @Test
    fun parseArgumentsValidJson() {
        val tc = ToolCall("id", "name", """{"key": "value", "num": 42}""")
        val args = tc.parseArguments()
        assertEquals("value", args.getString("key"))
        assertEquals(42, args.getInt("num"))
    }

    @Test
    fun parseArgumentsEmptyString() {
        val tc = ToolCall("id", "name", "")
        val args = tc.parseArguments()
        assertEquals(0, args.length())
    }

    @Test
    fun parseArgumentsBlankString() {
        val tc = ToolCall("id", "name", "   ")
        val args = tc.parseArguments()
        assertEquals(0, args.length())
    }

    // --- createToolResultMessage ---

    @Test
    fun createToolResultMessageStructure() {
        val result = ToolCallParser.createToolResultMessage("call_42", """{"status":"ok"}""")
        assertEquals("tool", result.getString("role"))
        assertEquals("call_42", result.getString("tool_call_id"))
        assertEquals("""{"status":"ok"}""", result.getString("content"))
    }

    // --- createAssistantToolCallMessage ---

    @Test
    fun createAssistantToolCallMessageStructure() {
        val calls = listOf(
            ToolCall("c1", "search", """{"q":"test"}"""),
            ToolCall("c2", "lookup", "{}")
        )
        val msg = ToolCallParser.createAssistantToolCallMessage(calls, "thinking...")
        assertEquals("assistant", msg.getString("role"))
        assertEquals("thinking...", msg.getString("content"))
        val arr = msg.getJSONArray("tool_calls")
        assertEquals(2, arr.length())
        assertEquals("c1", arr.getJSONObject(0).getString("id"))
        assertEquals("function", arr.getJSONObject(0).getString("type"))
        assertEquals("search", arr.getJSONObject(0).getJSONObject("function").getString("name"))
    }

    @Test
    fun createAssistantToolCallMessageNullContent() {
        val msg = ToolCallParser.createAssistantToolCallMessage(
            listOf(ToolCall("c1", "tool", "{}")), null
        )
        assertTrue(msg.isNull("content"))
    }
}
