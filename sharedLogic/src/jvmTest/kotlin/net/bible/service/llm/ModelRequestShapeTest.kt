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

package net.bible.service.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.llm.tools.ToolDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Request shape per model: the Quick Setup models need model-specific parameters so that the
 * agent's tool calls keep working (gpt-6-luna: function calling on Chat Completions only with
 * `reasoning_effort: "none"`; claude-haiku-5-5: thinking on by default and only the default
 * temperature accepted), while every other model's request stays unchanged.
 */
class ModelRequestShapeTest {
    private val tool = ToolDefinition(
        AgentTool.GET_VERSE_CONTENT, "Get verses",
        JsonObject(mapOf("type" to JsonPrimitive("object"), "properties" to JsonObject(emptyMap()))),
    )
    private val messages = listOf(
        ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
        ChatMessage(ChatMessage.Role.USER, "Hello"),
    )

    private fun openAiBody(model: String, tools: List<ToolDefinition> = listOf(tool), temperature: Double? = 0.3) =
        llmJson.parseToJsonElement(OpenAiApiAdapter().buildRequestBody(model, messages, tools, temperature)).jsonObject

    private fun anthropicBody(model: String, temperature: Double? = 0.3) =
        llmJson.parseToJsonElement(AnthropicApiAdapter().buildRequestBody(model, messages, listOf(tool), temperature)).jsonObject

    @Test
    fun gpt6LunaSendsReasoningEffortNoneWithTools() {
        val body = openAiBody("gpt-6-luna")
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertTrue("tools" in body)
    }

    @Test
    fun gpt6LunaSendsReasoningEffortNoneForPlainChatToo() {
        // The connection test and plain chat use no tools; one shape for the model keeps cost and latency predictable.
        assertEquals("none", openAiBody("gpt-6-luna", tools = emptyList())["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun gpt6LunaDatedSnapshotGetsTheSameRule() {
        assertEquals("none", openAiBody("gpt-6-luna-2026-10-01")["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun otherOpenAiCompatibleModelsSendNoReasoningEffort() {
        for (model in listOf(
            "gpt-5.4-mini", "gpt-5.4", "gpt-4o-mini", "gpt-6-lunar", "gemini-3.8-flash",
            "gemini-3-flash-preview", "openai/gpt-6-luna", "anthropic/claude-haiku-5-5", "test-model",
        )) {
            val body = openAiBody(model)
            assertFalse("reasoning_effort" in body, "Unexpected reasoning_effort for $model")
            assertEquals(0.3, body["temperature"]?.jsonPrimitive?.content?.toDouble(), "temperature for $model")
        }
    }

    @Test
    fun claudeHaiku55DisablesThinkingAndOmitsTemperature() {
        val body = anthropicBody("claude-haiku-5-5")
        assertEquals("disabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertNull(body["temperature"])
        assertTrue("tools" in body)
    }

    @Test
    fun claudeHaiku55DatedSnapshotGetsTheSameRule() {
        val body = anthropicBody("claude-haiku-5-5-20261101")
        assertEquals("disabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertNull(body["temperature"])
    }

    @Test
    fun olderClaudeModelsKeepTemperatureAndSendNoThinking() {
        for (model in listOf("claude-haiku-4-5", "claude-sonnet-4-6", "claude-opus-4-6")) {
            val body = anthropicBody(model)
            assertNull(body["thinking"], "Unexpected thinking for $model")
            assertEquals(0.3, body["temperature"]?.jsonPrimitive?.content?.toDouble(), "temperature for $model")
        }
    }

    @Test
    fun anthropicResponseWithThinkingBlocksStillYieldsToolCalls() {
        val response = """
            {"content":[
              {"type":"thinking","thinking":"Let me look it up.","signature":"abc"},
              {"type":"redacted_thinking","data":"xyz"},
              {"type":"text","text":"Looking up."},
              {"type":"tool_use","id":"tu_1","name":"${AgentTool.GET_VERSE_CONTENT.camelCaseName}","input":{"ref":"John 3:16"}}
            ],"usage":{"input_tokens":10,"output_tokens":5}}
        """.trimIndent()
        val parsed = assertIs<ParsedResponse.ToolCalls>(AnthropicApiAdapter().parseResponse(response))
        assertEquals("Looking up.", parsed.content)
        assertEquals(listOf("tu_1"), parsed.toolCalls.map { it.id })
        assertEquals(AgentTool.GET_VERSE_CONTENT, parsed.toolCalls.single().tool)
    }

    @Test
    fun anthropicResponseWithOnlyThinkingAndTextYieldsTheText() {
        val response = """{"content":[{"type":"thinking","thinking":"hmm","signature":"s"},{"type":"text","text":"Hi"}]}"""
        assertEquals(ParsedResponse.TextResponse("Hi"), AnthropicApiAdapter().parseResponse(response))
    }
}
