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
import net.bible.service.llm.AnthropicApiAdapter
import net.bible.service.llm.ChatMessage
import net.bible.service.llm.OpenAiApiAdapter
import net.bible.service.llm.ParsedResponse
import net.bible.service.llm.ToolCall
import net.bible.service.llm.ToolResultBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolCallParserTest {

    private val openAi = OpenAiApiAdapter()
    private val anthropic = AnthropicApiAdapter()

    // --- OpenAI parseResponse: TextResponse ---

    @Test
    fun openAiTextResponse() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"Hello world"}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("Hello world", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun openAiNullContentYieldsEmptyText() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun openAiMissingContentYieldsEmptyText() {
        val json = """{"choices":[{"message":{"role":"assistant"}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun openAiNullToolCallsYieldsText() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"text","tool_calls":null}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("text", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun openAiEmptyToolCallsYieldsText() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"fallback","tool_calls":[]}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("fallback", (result as ParsedResponse.TextResponse).content)
    }

    // --- OpenAI parseResponse: ToolCalls ---

    @Test
    fun openAiSingleToolCall() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"getVerseContent","arguments":"{\"book\":\"KJV\",\"ref\":\"Gen.1.1\"}"}}]}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.ToolCalls)
        val tc = result as ParsedResponse.ToolCalls
        assertNull(tc.content)
        assertEquals(1, tc.toolCalls.size)
        assertEquals("call_1", tc.toolCalls[0].id)
        assertEquals(AgentTool.GET_VERSE_CONTENT, tc.toolCalls[0].tool)
        assertTrue(tc.toolCalls[0].arguments.contains("KJV"))
    }

    @Test
    fun openAiMultipleToolCalls() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"getVerseContent","arguments":"{}"}},{"id":"call_2","type":"function","function":{"name":"searchBible","arguments":"{}"}}]}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.ToolCalls)
        assertEquals(2, (result as ParsedResponse.ToolCalls).toolCalls.size)
    }

    @Test
    fun openAiToolCallWithContent() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"Let me look that up","tool_calls":[{"id":"call_1","type":"function","function":{"name":"searchBible","arguments":"{}"}}]}}]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.ToolCalls)
        assertEquals("Let me look that up", (result as ParsedResponse.ToolCalls).content)
    }

    @Test
    fun openAiUnknownTypeIsSkipped() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"code_interpreter","function":{"name":"run","arguments":"{}"}}]}}]}"""
        val result = openAi.parseResponse(json)
        // Only non-function types → empty tool list → TextResponse
        assertTrue(result is ParsedResponse.TextResponse)
    }

    @Test
    fun openAiInvalidJsonYieldsParseError() {
        val result = openAi.parseResponse("not json")
        assertTrue(result is ParsedResponse.ParseError)
    }

    @Test
    fun openAiNoChoicesYieldsParseError() {
        val json = """{"choices":[]}"""
        val result = openAi.parseResponse(json)
        assertTrue(result is ParsedResponse.ParseError)
    }

    // --- OpenAI extractUsage ---

    @Test
    fun openAiExtractUsage() {
        val json = """{"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":50,"prompt_tokens_details":{"cached_tokens":20}}}"""
        val usage = openAi.extractUsage(json)
        assertEquals(80, usage.inputTokens)  // 100 - 20
        assertEquals(50, usage.outputTokens)
        assertEquals(20, usage.cacheReadTokens)
    }

    @Test
    fun openAiExtractUsageMissing() {
        val usage = openAi.extractUsage("""{"choices":[]}""")
        assertEquals(0, usage.totalTokens)
    }

    // --- Anthropic parseResponse ---

    @Test
    fun anthropicTextResponse() {
        val json = """{"content":[{"type":"text","text":"Hello from Claude"}]}"""
        val result = anthropic.parseResponse(json)
        assertTrue(result is ParsedResponse.TextResponse)
        assertEquals("Hello from Claude", (result as ParsedResponse.TextResponse).content)
    }

    @Test
    fun anthropicToolUse() {
        val json = """{"content":[{"type":"tool_use","id":"tu_1","name":"getVerseContent","input":{"book":"KJV","ref":"Gen.1.1"}}]}"""
        val result = anthropic.parseResponse(json)
        assertTrue(result is ParsedResponse.ToolCalls)
        val tc = (result as ParsedResponse.ToolCalls).toolCalls
        assertEquals(1, tc.size)
        assertEquals("tu_1", tc[0].id)
        assertEquals(AgentTool.GET_VERSE_CONTENT, tc[0].tool)
        assertTrue(tc[0].arguments.contains("KJV"))
    }

    @Test
    fun anthropicMixedContent() {
        val json = """{"content":[{"type":"text","text":"Let me check"},{"type":"tool_use","id":"tu_1","name":"searchBible","input":{"q":"test"}}]}"""
        val result = anthropic.parseResponse(json)
        assertTrue(result is ParsedResponse.ToolCalls)
        val tc = result as ParsedResponse.ToolCalls
        assertEquals("Let me check", tc.content)
        assertEquals(1, tc.toolCalls.size)
    }

    @Test
    fun openAiUnknownToolNameIsSkipped() {
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"unknownTool","arguments":"{}"}}]}}]}"""
        val result = openAi.parseResponse(json)
        // Unknown tool name → empty tool list → TextResponse
        assertTrue(result is ParsedResponse.TextResponse)
    }

    @Test
    fun anthropicUnknownToolNameIsSkipped() {
        val json = """{"content":[{"type":"tool_use","id":"tu_1","name":"unknownTool","input":{"q":"test"}}]}"""
        val result = anthropic.parseResponse(json)
        // Unknown tool name → empty tool list → TextResponse
        assertTrue(result is ParsedResponse.TextResponse)
    }

    @Test
    fun anthropicExtractUsage() {
        val json = """{"content":[],"usage":{"input_tokens":100,"output_tokens":50,"cache_creation_input_tokens":10,"cache_read_input_tokens":20}}"""
        val usage = anthropic.extractUsage(json)
        assertEquals(100, usage.inputTokens)
        assertEquals(50, usage.outputTokens)
        assertEquals(10, usage.cacheCreationTokens)
        assertEquals(20, usage.cacheReadTokens)
    }

    @Test
    fun anthropicInvalidJsonYieldsParseError() {
        val result = anthropic.parseResponse("not json")
        assertTrue(result is ParsedResponse.ParseError)
    }

    // --- ToolCall.parseArguments ---

    @Test
    fun parseArgumentsValidJson() {
        val tc = ToolCall("id", AgentTool.GET_VERSE_CONTENT, """{"key": "value", "num": 42}""")
        val args = tc.parseArguments()
        assertEquals("value", args.getString("key"))
        assertEquals(42, args.getInt("num"))
    }

    @Test
    fun parseArgumentsEmptyString() {
        val tc = ToolCall("id", AgentTool.GET_VERSE_CONTENT, "")
        val args = tc.parseArguments()
        assertEquals(0, args.length())
    }

    @Test
    fun parseArgumentsBlankString() {
        val tc = ToolCall("id", AgentTool.GET_VERSE_CONTENT, "   ")
        val args = tc.parseArguments()
        assertEquals(0, args.length())
    }

    // --- ChatMessage creation ---

    @Test
    fun openAiCreateAssistantToolCallMessage() {
        val calls = listOf(
            ToolCall("c1", AgentTool.SEARCH_BIBLE, """{"q":"test"}"""),
            ToolCall("c2", AgentTool.GET_DICTIONARY_ENTRY, "{}")
        )
        val msg = openAi.createAssistantToolCallMessage(calls, "thinking...")
        assertEquals(ChatMessage.Role.ASSISTANT, msg.role)
        assertEquals("thinking...", msg.content)
        assertEquals(2, msg.toolCalls!!.size)
        assertEquals("c1", msg.toolCalls!![0].id)
        assertEquals(AgentTool.SEARCH_BIBLE, msg.toolCalls!![0].tool)
    }

    @Test
    fun openAiCreateAssistantToolCallMessageNullContent() {
        val msg = openAi.createAssistantToolCallMessage(
            listOf(ToolCall("c1", AgentTool.GET_VERSE_CONTENT, "{}")), null
        )
        assertNull(msg.content)
    }

    @Test
    fun openAiCreateToolResultMessages() {
        val results = listOf(ToolResultBlock("call_42", """{"status":"ok"}"""))
        val messages = openAi.createToolResultMessages(results)
        assertEquals(1, messages.size)
        assertEquals(ChatMessage.Role.TOOL, messages[0].role)
        assertEquals("call_42", messages[0].toolCallId)
        assertEquals("""{"status":"ok"}""", messages[0].content)
    }

    @Test
    fun anthropicCreateToolResultMessagesBatched() {
        val results = listOf(
            ToolResultBlock("call_1", "result1"),
            ToolResultBlock("call_2", "result2")
        )
        val messages = anthropic.createToolResultMessages(results)
        // Anthropic batches all tool results into a single user message
        assertEquals(1, messages.size)
        assertEquals(ChatMessage.Role.USER, messages[0].role)
        assertEquals(2, messages[0].toolResultBlocks!!.size)
        assertEquals("call_1", messages[0].toolResultBlocks!![0].toolCallId)
        assertEquals("result1", messages[0].toolResultBlocks!![0].content)
    }
}
