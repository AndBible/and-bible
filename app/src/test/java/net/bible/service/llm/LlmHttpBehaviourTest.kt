package net.bible.service.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmHttpBehaviourTest {
    private data class ScriptedResponse(
        val code: Int = 200,
        val body: String = OPEN_AI_RESPONSE,
        val headers: Map<String, String> = emptyMap(),
        val block: CountDownLatch? = null,
    )

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    ) {
        fun header(name: String): String? = headers[name]?.singleOrNull()
        fun hasTemperature(): Boolean = "temperature" in Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
    }

    private lateinit var server: HttpServer
    private val executor = Executors.newCachedThreadPool()
    private val responses = LinkedBlockingQueue<ScriptedResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val received = CountDownLatch(1)
    private val blockedHandlers = mutableListOf<CountDownLatch>()
    private val providerId = "http-pin-${UUID.randomUUID()}"
    private val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello, ä"))
    private val endpoint: String get() = "http://127.0.0.1:${server.address.port}/v1"

    @Before
    fun setUp() {
        CommonUtils.settings.setStringSet(NO_TEMPERATURE_MODELS, emptySet())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                requests += RecordedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.path,
                    exchange.requestHeaders.entries.associate { (key, values) ->
                        key.lowercase(Locale.ROOT) to values.toList()
                    },
                    exchange.requestBody.readBytes(),
                )
                received.countDown()
                val response = responses.poll(5, TimeUnit.SECONDS)
                    ?: ScriptedResponse(500, "Unscripted request")
                response.block?.await()
                response.headers.forEach { (key, value) -> exchange.responseHeaders.add(key, value) }
                val bytes = response.body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(response.code, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
    }

    private var callWorkerQuiesced = true

    @After
    fun tearDown() {
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        cleanup {
            blockedHandlers.forEach { it.countDown() }
            server.stop(0)
        }
        cleanup {
            executor.shutdownNow()
            assertTrue("HTTP handlers must stop", executor.awaitTermination(2, TimeUnit.SECONDS))
        }
        // Never reset a DB that a non-cooperative call worker could still write to.
        if (!callWorkerQuiesced) {
            val error = AssertionError("Call worker did not quiesce; database reset is unsafe")
            failure?.let { error.addSuppressed(it) }
            throw error
        }
        cleanup { File(BibleApplication.application.cacheDir, "models_cache_${providerId}.json").delete() }
        cleanup {
            // The service has no public eviction API; remove only this test's unique cache entry.
            val field = DynamicModelService::class.java.getDeclaredField("cache").apply { isAccessible = true }
            (field.get(DynamicModelService) as MutableMap<*, *>).remove(providerId)
        }
        cleanup { CommonUtils.settings.setStringSet(NO_TEMPERATURE_MODELS, emptySet()) }
        cleanup { resetDatabase() }
        failure?.let { throw it }
    }

    private fun requireQuiescence(done: CountDownLatch, timeoutMs: Long = 2_000) {
        assertTrue("Worker must quiesce within ${timeoutMs}ms", done.await(timeoutMs, TimeUnit.MILLISECONDS))
    }

    private fun resolved(type: String = "CUSTOM", baseEndpoint: String = endpoint): LlmProcessingService.ResolvedProvider {
        val config = LlmProviderConfig(providerType = type, displayName = "t")
        return LlmProcessingService.ResolvedProvider(
            providerConfig = config,
            adapter = config.resolveAdapter(),
            model = "gpt-x",
            apiKey = "k-123",
            endpoint = baseEndpoint,
            configuredModelId = null,
        )
    }

    private suspend fun call(
        provider: LlmProcessingService.ResolvedProvider = resolved(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): LlmApiResponse = withTimeout(15_000) {
        LlmProcessingService.callLlmApiWithTools(messages, emptyList(), extraHeaders = extraHeaders, preResolved = provider)
    }

    @Test
    fun openAiFormatPostsTheAdapterBodyWithBearerAuth() = runBlocking {
        responses += ScriptedResponse()
        val provider = resolved()
        val result = call(provider)
        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals(URI(provider.adapter.buildEndpointUrl(endpoint)).path, request.path)
        assertEquals("Bearer k-123", request.header("authorization"))
        val expected = provider.adapter.buildRequestBody(provider.model, messages, emptyList(), 0.3)
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), request.body)
        assertEquals(expected, request.body.toString(Charsets.UTF_8))
        // Current okhttp appends charset=utf-8; only the media type is a transport contract.
        assertEquals("application/json", request.header("content-type")!!.split(';')[0].trim().lowercase(Locale.ROOT))
        assertEquals(7, result.usage.totalTokens)
    }

    @Test
    fun anthropicFormatSendsApiKeyAndVersionHeaders() = runBlocking {
        responses += ScriptedResponse(body = """{"content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":4,"output_tokens":3}}""")
        call(resolved("ANTHROPIC"))
        assertEquals(1, requests.size)
        assertEquals("/v1/messages", requests.single().path)
        assertEquals("k-123", requests.single().header("x-api-key"))
        // AnthropicApiAdapter's private ANTHROPIC_VERSION at the baseline HEAD.
        assertEquals("2023-06-01", requests.single().header("anthropic-version"))
        assertFalse(requests.single().headers.containsKey("authorization"))
    }

    @Test
    fun extraHeadersAreSent() = runBlocking {
        responses += ScriptedResponse()
        call(resolved("XAI"), mapOf("x-grok-conv-id" to "c-1"))
        assertEquals(1, requests.size)
        assertEquals("c-1", requests.single().header("x-grok-conv-id"))
    }

    @Test
    fun rateLimitWithRetryAfterIsRetriedThenSucceeds() = runBlocking {
        responses += ScriptedResponse(429, "rate limited", mapOf("retry-after" to "1"))
        responses += ScriptedResponse()
        val start = System.nanoTime()
        assertEquals(7, call().usage.totalTokens)
        assertTrue("Retry-After must delay at least 700 ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 700)
        assertEquals(2, requests.size)
    }

    @Test
    fun temperatureRejectionRetriesWithoutTemperatureAndRemembersTheModel() = runBlocking {
        responses += ScriptedResponse(400, """{"error":{"message":"Unsupported parameter: 'temperature'"}}""")
        responses += ScriptedResponse()
        val provider = resolved()
        assertEquals(7, call(provider).usage.totalTokens)
        assertEquals(2, requests.size)
        assertTrue(requests[0].hasTemperature())
        assertFalse(requests[1].hasTemperature())
        assertTrue(provider.model in CommonUtils.settings.getStringSet(NO_TEMPERATURE_MODELS))
        responses += ScriptedResponse()
        assertEquals(7, call(provider).usage.totalTokens)
        assertEquals(3, requests.size)
        assertFalse(requests[2].hasTemperature())
    }

    @Test
    fun nonRetryableErrorThrowsWithCodeBodyAndUrl() = runBlocking {
        val body = """{"error":"bad key"}"""
        responses += ScriptedResponse(401, body)
        val provider = resolved()
        val error = runCatching { call(provider) }.exceptionOrNull()
        assertTrue(error is LlmProcessingError)
        assertEquals("LLM API error: 401 - $body (URL: ${provider.adapter.buildEndpointUrl(endpoint)})", error!!.message)
        assertEquals(1, requests.size)
    }

    @Test
    fun retriesStopAfterMaxRetries() = runBlocking {
        repeat(4) { responses += ScriptedResponse(503, "unavailable", mapOf("retry-after" to "1")) }
        val error = runCatching { call() }.exceptionOrNull()
        assertTrue(error is LlmProcessingError)
        assertTrue(error!!.message!!.startsWith("LLM API error: 503"))
        assertEquals(4, requests.size)
        assertEquals(LlmRetryPolicy.MAX_RETRIES + 1, requests.size)
    }

    @Test
    fun cancellingTheCallerCancelsTheRequestPromptly() = runBlocking {
        // Raw HTTP/1.1 socket: observe peer EOF/reset without sending any response.
        // HttpServer hides its socket, so cannot distinguish cancellation from teardown.
        val listener = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val accepted = AtomicReference<Socket>()
        val requestRead = CountDownLatch(1)
        val peerClosed = CountDownLatch(1)
        val handlerDone = CountDownLatch(1)
        val handlerFailure = AtomicReference<Throwable>()
        val handler = Thread {
            try {
                listener.accept().use { socket ->
                    accepted.set(socket)
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val byte = input.read()
                        check(byte >= 0) { "EOF before request headers" }
                        header.append(byte.toChar())
                    }
                    val length = header.lines().first { it.startsWith("Content-Length:", true) }
                        .substringAfter(':').trim().toInt()
                    repeat(length) { check(input.read() >= 0) { "EOF before request body" } }
                    requestRead.countDown()
                    socket.soTimeout = 10_000
                    try {
                        if (input.read() == -1) peerClosed.countDown()
                    } catch (error: SocketException) {
                        if (!socket.isClosed) peerClosed.countDown() else throw error
                    } catch (_: SocketTimeoutException) {
                        // No client close: assertion on peerClosed must fail before fixture shutdown.
                    }
                }
            } catch (error: Throwable) {
                handlerFailure.set(error)
            } finally {
                handlerDone.countDown()
            }
        }.apply { isDaemon = true; start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = resolved(baseEndpoint = "http://127.0.0.1:${listener.localPort}/v1")
        val callDone = CountDownLatch(1)
        val completion = AtomicReference<Throwable>()
        callWorkerQuiesced = false
        val job = scope.launch { call(provider) }
        job.invokeOnCompletion { completion.set(it); callDone.countDown() }
        var originalFailure: Throwable? = null
        try {
            assertTrue("Server must read the pending request", requestRead.await(5, TimeUnit.SECONDS))
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue("Caller completes with cancellation, not a network failure", completion.get() is CancellationException)
            assertTrue("Cancellation must close the HTTP socket before server shutdown", peerClosed.await(2, TimeUnit.SECONDS))
            handlerFailure.get()?.let { throw it }
            Unit
        } catch (error: Throwable) {
            originalFailure = error
            throw error
        } finally {
            // Force I/O to unblock first; waits below are real latch deadlines, not cooperative timeouts.
            scope.cancel()
            val socketCloseFailure = runCatching { accepted.get()?.close() }.exceptionOrNull()
            val listenerCloseFailure = runCatching { listener.close() }.exceptionOrNull()
            try {
                requireQuiescence(callDone)
                callWorkerQuiesced = true
                requireQuiescence(handlerDone)
                handler.join(100)
                socketCloseFailure?.let { throw it }
                listenerCloseFailure?.let { throw it }
            } catch (cleanupFailure: Throwable) {
                if (originalFailure != null) originalFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    @Test
    fun nonCooperativeWorkerCleanupHasABoundedFailurePath() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        val worker = Thread {
            try {
                started.countDown()
                while (release.count > 0) {
                    try { release.await() } catch (_: InterruptedException) { /* Deliberately ignore cancellation. */ }
                }
            } finally {
                done.countDown()
            }
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            worker.interrupt()
            val start = System.nanoTime()
            val error = runCatching { requireQuiescence(done, 100) }.exceptionOrNull()
            assertTrue(error is AssertionError)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_000)
            assertEquals(1L, done.count)
        } finally {
            release.countDown()
            requireQuiescence(done)
            worker.join(100)
        }
    }

    @Test
    fun deadServerThrowsANonCancellationException() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val error = runCatching { call(resolved(baseEndpoint = "http://127.0.0.1:$port/v1")) }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error is CancellationException)
        assertEquals(0, requests.size)
    }

    @Test
    fun fetchModelsGetsModelsWithBearerAndFiltersNonChat() = runBlocking {
        responses += ScriptedResponse(body = """{"data":[{"id":"gpt-x"},{"id":"whisper-1"},{"id":"text-embedding-3"}]}""")
        val models = withTimeout(15_000) { DynamicModelService.fetchModels(endpoint, "k-123", providerId) }
        assertEquals(listOf("gpt-x"), models!!.map { it.id })
        assertEquals(1, requests.size)
        assertEquals("GET", requests[0].method)
        assertEquals("/v1/models", requests[0].path)
        assertEquals("Bearer k-123", requests[0].header("authorization"))
        assertTrue(requests[0].body.isEmpty())
        responses += ScriptedResponse(500, "server error")
        assertNull(withTimeout(15_000) { DynamicModelService.fetchModels(endpoint, "k-123", providerId) })
        assertEquals(2, requests.size)
    }

    companion object {
        private const val NO_TEMPERATURE_MODELS = "llm_no_temperature_models"
        private const val OPEN_AI_RESPONSE = """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":4,"completion_tokens":3,"total_tokens":7}}"""
    }
}
