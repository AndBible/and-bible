package net.bible.service.llm

import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.llm.http.LlmHttpResponse
import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.llm.http.LlmTimeouts
import net.bible.sharedcore.platform.AppSettings
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class OpenCodeSessionHeadersTest {
    private fun config(endpoint: String) = LlmProviderConfig(
        providerType = "CUSTOM", displayName = "OpenCode", endpoint = endpoint,
    )

    @Test
    fun openCodeRequestsKeepTheConversationIdAcrossIterationsAndRetries() = runTest {
        val requests = mutableListOf<Map<String, String>>()
        val transport = object : LlmHttpTransport {
            override suspend fun postJson(url: String, headers: Map<String, String>, jsonBody: String, timeouts: LlmTimeouts): LlmHttpResponse {
                assertEquals("https://opencode.ai/zen/go/v1/chat/completions", url)
                requests += headers.toMap()
                // Exercise the service's automatic retry as well as the next agent iteration.
                return if (requests.size == 1) {
                    LlmHttpResponse(400, "Unsupported parameter: temperature", emptyMap())
                } else {
                    LlmHttpResponse(200, """{"choices":[{"message":{"content":"ok"}}]}""", emptyMap())
                }
            }

            override suspend fun get(url: String, headers: Map<String, String>, timeouts: LlmTimeouts): LlmHttpResponse =
                error("Unexpected GET: $url")
        }
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, _ ->
            when (method.name) {
                "getStringSet" -> emptySet<String>()
                "setStringSet" -> Unit
                else -> error("Unexpected setting: ${method.name}")
            }
        } as AppSettings
        startKoin { modules(module {
            single<LlmHttpTransport> { transport }
            single<AppSettings> { settings }
        }) }
        try {
            val provider = config("https://opencode.ai/zen/go/v1")
            val resolved = LlmProcessingService.ResolvedProvider(
                provider, provider.resolveAdapter(), "test-model", "test-key", provider.resolveEndpoint(),
            )
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
            // AgentExecutor creates these once before its loop and reuses them for every iteration.
            val conversationHeaders = LlmProcessingService.buildProviderExtraHeaders(provider)
            repeat(2) {
                LlmProcessingService.callLlmApiWithTools(messages, emptyList(), extraHeaders = conversationHeaders, preResolved = resolved)
            }
            val nextConversationHeaders = LlmProcessingService.buildProviderExtraHeaders(provider)
            LlmProcessingService.callLlmApiWithTools(messages, emptyList(), extraHeaders = nextConversationHeaders, preResolved = resolved)

            assertEquals(4, requests.size)
            val sessionId = assertNotNull(requests[0]["x-opencode-session"])
            Uuid.parse(sessionId)
            assertTrue(requests.take(3).all { it["x-opencode-session"] == sessionId })
            val nextSessionId = assertNotNull(requests[3]["x-opencode-session"])
            assertNotEquals(sessionId, nextSessionId)
            assertEquals("Bearer test-key", requests[0]["Authorization"])
        } finally {
            stopKoin()
        }
    }

    @Test
    fun openCodeHostMatchingUsesTheParsedHostNotASubstring() {
        for (endpoint in listOf(
            "https://opencode.ai/zen/go/v1",
            "https://opencode.ai/zen/v1/",
            "https://OPENCODE.AI:443/zen/go/v1",
            "https://api.opencode.ai/zen/v1",
            "https://API.OpenCode.ai/zen/v1",
        )) {
            val headers = LlmProcessingService.buildProviderExtraHeaders(config(endpoint))
            assertFalse(headers["x-opencode-session"].isNullOrBlank(), endpoint)
        }
        for (endpoint in listOf(
            "https://opencode.ai.example.com/v1",
            "https://evilopencode.ai/v1",
            "https://api.evilopencode.ai/v1",
            "https://example.com/opencode.ai/v1",
            "https://opencode.ai@example.com/v1",
            "https://api.openai.com/v1",
            "http://localhost:8080/v1",
            "not a URL",
        )) {
            assertEquals(emptyMap(), LlmProcessingService.buildProviderExtraHeaders(config(endpoint)), endpoint)
        }
    }

    @Test
    fun xaiConversationHeaderIsStillGeneratedSeparately() {
        val provider = LlmProviderConfig(providerType = "XAI", displayName = "xAI")
        val first = LlmProcessingService.buildProviderExtraHeaders(provider)
        val second = LlmProcessingService.buildProviderExtraHeaders(provider)
        assertNotNull(first["x-grok-conv-id"])
        assertNotEquals(first["x-grok-conv-id"], second["x-grok-conv-id"])
        assertFalse("x-opencode-session" in first)
    }
}
