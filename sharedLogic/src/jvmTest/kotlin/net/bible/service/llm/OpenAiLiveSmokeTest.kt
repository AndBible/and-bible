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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bible.service.llm.tools.ToolDefinition
import net.bible.sharedcore.http.DesktopHttpClients
import net.bible.sharedcore.llm.http.KtorLlmHttpTransport
import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.platform.AppSettings
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Manual smoke test against the real OpenAI API through the app's own request path
 * ([LlmProcessingService.callLlmApiWithTools] + [OpenAiApiAdapter]). It does nothing unless
 * `ANDBIBLE_LIVE_OPENAI_KEY_FILE` names a file holding an API key, so CI never calls the API.
 * Optional `ANDBIBLE_LIVE_OPENAI_MODEL` (default `gpt-6-luna`).
 *
 * Run: `ANDBIBLE_LIVE_OPENAI_KEY_FILE=~/.config/openai/api-key ./gradlew :sharedLogic:jvmTest --tests "*OpenAiLiveSmokeTest"`.
 * It prints only response kinds, tool names and token counts (never the key or full responses).
 */
class OpenAiLiveSmokeTest {
    private val keyFile = System.getenv("ANDBIBLE_LIVE_OPENAI_KEY_FILE")?.let(::File)
    private val model = System.getenv("ANDBIBLE_LIVE_OPENAI_MODEL") ?: "gpt-6-luna"

    private val verseTool = ToolDefinition(
        AgentTool.GET_VERSE_CONTENT,
        "Get the text of a Bible verse.",
        JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf("reference" to JsonObject(mapOf("type" to JsonPrimitive("string"))))),
            "required" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("reference"))),
        )),
    )

    private fun <T> withLiveService(block: suspend (LlmProcessingService.ResolvedProvider) -> T): T? {
        val file = keyFile ?: run { println("LIVE: skipped (ANDBIBLE_LIVE_OPENAI_KEY_FILE not set)"); return null }
        val apiKey = file.readText().trim()
        val clients = DesktopHttpClients()
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, _ ->
            when (method.name) {
                "getStringSet" -> emptySet<String>()
                "setStringSet" -> Unit
                else -> error("Unexpected setting: ${method.name}")
            }
        } as AppSettings
        val strings = Proxy.newProxyInstance(LlmStrings::class.java.classLoader, arrayOf(LlmStrings::class.java)) { _, method, args ->
            "${method.name}(${args?.joinToString() ?: ""})"
        } as LlmStrings
        startKoin { modules(module {
            single<LlmHttpTransport> { KtorLlmHttpTransport(clients::clientFor) }
            single<AppSettings> { settings }
            single<LlmStrings> { strings }
        }) }
        try {
            val provider = LlmProviderConfig(providerType = LlmProvider.OPENAI.name, displayName = LlmProvider.OPENAI.displayName)
            val resolved = LlmProcessingService.ResolvedProvider(
                provider, provider.resolveAdapter(), model, apiKey, provider.resolveEndpoint(),
            )
            return runBlocking { block(resolved) }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun plainChatAndToolLoopWork() {
        withLiveService { resolved ->
            val adapter = resolved.adapter
            // 1. Plain chat, as the Quick Setup connection test sends it.
            val chat = LlmProcessingService.callLlmApiWithTools(
                listOf(ChatMessage(ChatMessage.Role.USER, "Reply with the single word: ok")), emptyList(), preResolved = resolved,
            )
            val chatParsed = adapter.parseResponse(chat.responseBody)
            println("LIVE chat: model=$model kind=${chatParsed::class.simpleName} " +
                "chars=${(chatParsed as? ParsedResponse.TextResponse)?.content?.length} usage=${chat.usage}")
            assertIs<ParsedResponse.TextResponse>(chatParsed)

            // 2. Tool call: the model must call the tool.
            val messages = mutableListOf(
                ChatMessage(ChatMessage.Role.SYSTEM, "You answer Bible questions. Always fetch verse text with the tool."),
                ChatMessage(ChatMessage.Role.USER, "Quote John 3:16 in one sentence."),
            )
            val first = LlmProcessingService.callLlmApiWithTools(messages, listOf(verseTool), preResolved = resolved)
            val toolCalls = assertIs<ParsedResponse.ToolCalls>(adapter.parseResponse(first.responseBody))
            println("LIVE tool call: tools=${toolCalls.toolCalls.map { it.tool.camelCaseName }} " +
                "args=${toolCalls.toolCalls.map { it.parseArguments().keys }} usage=${first.usage}")

            // 3. Tool result back: the model must answer with text.
            messages += adapter.createAssistantToolCallMessage(toolCalls.toolCalls, toolCalls.content)
            messages += adapter.createToolResultMessages(toolCalls.toolCalls.map {
                ToolResultBlock(it.id, "John 3:16 (KJV): For God so loved the world, that he gave his only begotten Son.")
            })
            val second = LlmProcessingService.callLlmApiWithTools(messages, listOf(verseTool), preResolved = resolved)
            val answer = assertIs<ParsedResponse.TextResponse>(adapter.parseResponse(second.responseBody))
            println("LIVE tool answer: chars=${answer.content.length} mentionsGod=${"God" in answer.content} usage=${second.usage}")
            assertTrue(answer.content.isNotBlank())
        }
    }
}
