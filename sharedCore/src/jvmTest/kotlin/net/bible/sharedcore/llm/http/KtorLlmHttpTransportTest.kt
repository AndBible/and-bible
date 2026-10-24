package net.bible.sharedcore.llm.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

class KtorLlmHttpTransportTest {
    // Catches dropped/rewritten provider headers, JSON encoding, or an unsafe Content-Type header.
    @Test fun postPreservesHeadersAndExactJson() = runTest {
        val body = "{\"message\": \"hello\"}"
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer fixture", request.headers["Authorization"])
            assertEquals("fixture-conversation", request.headers["x-grok-conv-id"])
            assertEquals(body, (request.body as TextContent).text)
            assertEquals(ContentType.Application.Json, request.body.contentType)
            respond("ok")
        })
        try {
            val transport = KtorLlmHttpTransport { profile ->
                assertEquals(LlmTimeouts.CHAT, profile)
                client
            }
            assertEquals("ok", transport.postJson("https://fixture.test/chat", mapOf(
                "Authorization" to "Bearer fixture", "x-grok-conv-id" to "fixture-conversation",
                "cOnTeNt-TyPe" to "application/json"
            ), body, LlmTimeouts.CHAT).body)
        } finally { client.close() }
    }

    // Catches expectSuccess or lost Retry-After/body information on returned error responses.
    @Test fun rateLimitIsReturnedWithCaseInsensitiveHeader() = runTest {
        val client = HttpClient(MockEngine {
            respond("rate limited", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "3"))
        })
        try {
            val response = KtorLlmHttpTransport { client }.postJson(
                "https://fixture.test/chat", emptyMap(), "{}", LlmTimeouts.CHAT)
            assertEquals(429, response.code)
            assertFalse(response.isSuccessful)
            assertEquals("3", response.header("retry-after"))
            assertEquals("rate limited", response.body)
        } finally { client.close() }
    }

    @Test fun emptyServerErrorBodyIsReturnedUnchanged() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.InternalServerError) })
        try {
            val response = KtorLlmHttpTransport { client }.get("https://fixture.test/models", emptyMap(), LlmTimeouts.MODELS)
            assertEquals(500, response.code)
            assertEquals("", response.body)
        } finally { client.close() }
    }

    @Test fun getSendsHeadersWithoutBody() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("GET", request.method.value)
            assertEquals("Bearer fixture", request.headers["Authorization"])
            assertTrue(request.body is OutgoingContent.NoContent)
            respond("models")
        })
        try {
            assertEquals("models", KtorLlmHttpTransport { client }.get("https://fixture.test/models",
                mapOf("Authorization" to "Bearer fixture"), LlmTimeouts.MODELS).body)
        } finally { client.close() }
    }

    // Catches swallowing cancellation or leaving the engine handler running after caller cancellation.
    @Test fun callerCancellationCancelsRequest() = runTest {
        val entered = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            entered.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        })
        try {
            val call = async { KtorLlmHttpTransport { client }.get("https://fixture.test/models", emptyMap(), LlmTimeouts.MODELS) }
            entered.await()
            call.cancel()
            assertFailsWith<CancellationException> { call.await() }
            stopped.await()
        } finally { client.close() }
    }
}
