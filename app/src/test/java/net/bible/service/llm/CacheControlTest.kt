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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.tools.ToolDefinition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CacheControlTest {

    private fun toolDef(tool: AgentTool) = ToolDefinition(
        tool = tool,
        description = "Test tool",
        parametersSchema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(emptyMap())
        ))
    )

    private fun parseRequestJson(json: String): JsonObject =
        llmJson.parseToJsonElement(json).jsonObject

    // ===================== Anthropic Adapter =====================

    @Test
    fun anthropicSystemBlockHasCacheControl() {
        val adapter = AnthropicApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "You are helpful"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val json = parseRequestJson(adapter.buildRequestBody("model", messages, emptyList(), null))
        val system = json["system"]!!.jsonArray[0].jsonObject
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicLastToolHasCacheControl() {
        val adapter = AnthropicApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val tools = listOf(toolDef(AgentTool.GET_VERSE_CONTENT), toolDef(AgentTool.SEARCH_BIBLE))
        val json = parseRequestJson(adapter.buildRequestBody("model", messages, tools, null))
        val wireTools = json["tools"]!!.jsonArray
        assertNull(wireTools[0].jsonObject["cache_control"])
        assertEquals("ephemeral", wireTools[1].jsonObject["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicNoToolsNoCrash() {
        val adapter = AnthropicApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val json = parseRequestJson(adapter.buildRequestBody("model", messages, emptyList(), null))
        assertNull(json["tools"])
    }

    @Test
    fun anthropicLastUserMessageGetsCacheControl() {
        val adapter = AnthropicApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val json = parseRequestJson(adapter.buildRequestBody("model", messages, emptyList(), null))
        val msgs = json["messages"]!!.jsonArray
        // Last (and only) user message should have cache_control
        val lastUserContent = msgs.last().jsonObject["content"]!!
        // String content should be converted to content block array
        assertTrue("Expected JsonArray but got ${lastUserContent::class.simpleName}", lastUserContent is JsonArray)
        val block = lastUserContent.jsonArray[0].jsonObject
        assertEquals("text", block["type"]!!.jsonPrimitive.content)
        assertEquals("Hello", block["text"]!!.jsonPrimitive.content)
        assertNotNull(block["cache_control"])
    }

    @Test
    fun anthropicToolResultsGetCacheControl() {
        val adapter = AnthropicApiAdapter()
        // Simulate iteration 2: system, user, assistant+tool_call, tool_results
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            adapter.createAssistantToolCallMessage(
                listOf(ToolCall("tc1", AgentTool.GET_VERSE_CONTENT, """{"ref":"Gen.1.1"}""")),
                null
            ),
        ) + adapter.createToolResultMessages(
            listOf(ToolResultBlock("tc1", "In the beginning God created..."))
        )

        val json = parseRequestJson(adapter.buildRequestBody("model", messages, emptyList(), null))
        val msgs = json["messages"]!!.jsonArray
        // Last user message (tool results) should have cache_control on last block
        val lastMsg = msgs.last().jsonObject
        assertEquals("user", lastMsg["role"]!!.jsonPrimitive.content)
        val content = lastMsg["content"]!!.jsonArray
        val lastBlock = content.last().jsonObject
        assertEquals("ephemeral", lastBlock["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicMaxThreeBreakpoints() {
        val adapter = AnthropicApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            adapter.createAssistantToolCallMessage(
                listOf(ToolCall("tc1", AgentTool.GET_VERSE_CONTENT, """{"ref":"Gen.1.1"}""")),
                null
            ),
        ) + adapter.createToolResultMessages(
            listOf(ToolResultBlock("tc1", "Result"))
        )
        val tools = listOf(toolDef(AgentTool.GET_VERSE_CONTENT))
        val body = adapter.buildRequestBody("model", messages, tools, null)
        val count = "\"cache_control\"".toRegex().findAll(body).count()
        assertEquals("Expected exactly 3 cache_control breakpoints (system + tools + conversation)", 3, count)
    }

    // ===================== OpenAI Adapter (cache control disabled) =====================

    @Test
    fun openAiNoCacheControlByDefault() {
        val adapter = OpenAiApiAdapter()
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val tools = listOf(toolDef(AgentTool.GET_VERSE_CONTENT))
        val body = adapter.buildRequestBody("model", messages, tools, null)
        assertFalse("Should not contain cache_control when disabled", body.contains("cache_control"))
    }

    // ===================== OpenAI Adapter (cache control enabled / OpenRouter) =====================

    /** Default model used for tests where caching SHOULD be enabled. */
    private val cachingModel = "anthropic/claude-sonnet-4"

    @Test
    fun openAiNoTopLevelCacheControl() {
        // Top-level cache_control must NOT be sent: OpenRouter rejects it with 404
        // ("No endpoints found that support Anthropic automatic caching") for models
        // whose endpoints don't support automatic caching, e.g. anthropic/claude-3-haiku.
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, emptyList(), null))
        assertNull("Top-level cache_control must not be present", json["cache_control"])
    }

    @Test
    fun openAiLastToolHasCacheControl() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
        val tools = listOf(toolDef(AgentTool.GET_VERSE_CONTENT), toolDef(AgentTool.SEARCH_BIBLE))
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, tools, null))
        val wireTools = json["tools"]!!.jsonArray
        assertNull(wireTools[0].jsonObject["function"]?.jsonObject?.get("cache_control"))
        assertNull(wireTools[0].jsonObject["cache_control"])
        assertEquals("ephemeral", wireTools[1].jsonObject["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiLastUserMessageConvertedToContentBlocks() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello world")
        )
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, emptyList(), null))
        val msgs = json["messages"]!!.jsonArray
        val lastMsg = msgs.last().jsonObject
        val content = lastMsg["content"]!!
        // Should be converted from string to content block array
        assertTrue("Expected JsonArray", content is JsonArray)
        val block = content.jsonArray[0].jsonObject
        assertEquals("text", block["type"]!!.jsonPrimitive.content)
        assertEquals("Hello world", block["text"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", block["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiToolResultMessageGetsCacheControl() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            adapter.createAssistantToolCallMessage(
                listOf(ToolCall("tc1", AgentTool.GET_VERSE_CONTENT, """{"ref":"Gen.1.1"}""")),
                null
            ),
        ) + adapter.createToolResultMessages(
            listOf(ToolResultBlock("tc1", "In the beginning..."))
        )
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, emptyList(), null))
        val msgs = json["messages"]!!.jsonArray
        // Last message should be the tool result with cache_control
        val lastMsg = msgs.last().jsonObject
        assertEquals("tool", lastMsg["role"]!!.jsonPrimitive.content)
        val content = lastMsg["content"]!!
        assertTrue("Tool result content should be converted to array", content is JsonArray)
        val block = content.jsonArray[0].jsonObject
        assertNotNull(block["cache_control"])
    }

    @Test
    fun openAiNoToolsNoCrash() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, emptyList(), null))
        assertNull(json["tools"])
    }

    @Test
    fun openAiEmptyMessagesNoCrash() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        // Edge case: no user or tool messages
        val messages = listOf(ChatMessage(ChatMessage.Role.SYSTEM, "sys"))
        val body = adapter.buildRequestBody(cachingModel, messages, emptyList(), null)
        assertNotNull(body)
    }

    @Test
    fun openAiSystemMessageNotTargetedForConversationBreakpoint() {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(ChatMessage(ChatMessage.Role.SYSTEM, "sys"))
        val json = parseRequestJson(adapter.buildRequestBody(cachingModel, messages, emptyList(), null))
        val msgs = json["messages"]!!.jsonArray
        // System message content should remain a string, not converted to content blocks
        val sysContent = msgs[0].jsonObject["content"]!!
        assertTrue("System message should stay as string", sysContent is JsonPrimitive)
    }

    // ===================== Per-model cache_control gating =====================

    /** Build a minimal request and return whether it contains any "cache_control" string. */
    private fun bodyHasCacheControl(model: String): Boolean {
        val adapter = OpenAiApiAdapter(supportsCacheControl = true)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "sys"),
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val tools = listOf(toolDef(AgentTool.GET_VERSE_CONTENT))
        return adapter.buildRequestBody(model, messages, tools, null).contains("cache_control")
    }

    @Test
    fun cacheControlEnabledForSupportedClaudeModels() {
        // Modern Claude variants support inline cache_control on OpenRouter
        for (model in listOf(
            "anthropic/claude-sonnet-4",
            "anthropic/claude-sonnet-4-5",
            "anthropic/claude-opus-4",
            "anthropic/claude-opus-4-1",
            "anthropic/claude-haiku-4-5",
        )) {
            assertTrue("Expected cache_control for $model", bodyHasCacheControl(model))
            assertTrue("modelSupportsCacheControl should be true for $model", modelSupportsCacheControl(model))
        }
    }

    @Test
    fun cacheControlDisabledForBlacklistedClaudeModels() {
        // These older Claude variants do NOT support prompt caching on OpenRouter
        // (server returns 400 "unsupported model or your request did not allow prompt caching")
        for (model in listOf(
            "anthropic/claude-3-haiku",
            "anthropic/claude-3-haiku:beta",
            "anthropic/claude-3.5-haiku",
            "anthropic/claude-3-5-haiku",
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3-5-sonnet",
            "anthropic/claude-3.7-sonnet",
            "anthropic/claude-3.7-sonnet:thinking",
        )) {
            assertFalse("Expected NO cache_control for $model", bodyHasCacheControl(model))
            assertFalse("modelSupportsCacheControl should be false for $model", modelSupportsCacheControl(model))
        }
    }

    @Test
    fun cacheControlDisabledForNonAnthropicModels() {
        // OpenRouter routes many providers; only Anthropic Claude understands cache_control
        for (model in listOf(
            "google/gemini-3-flash",
            "google/gemini-2.5-pro",
            "openai/gpt-5.4-mini",
            "openai/gpt-4o",
            "meta-llama/llama-3.3-70b",
            "mistralai/mistral-large",
        )) {
            assertFalse("Expected NO cache_control for $model", bodyHasCacheControl(model))
            assertFalse("modelSupportsCacheControl should be false for $model", modelSupportsCacheControl(model))
        }
    }
}
