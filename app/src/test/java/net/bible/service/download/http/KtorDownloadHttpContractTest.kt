package net.bible.service.download.http

import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.bible.android.platform.AndroidHttpClients
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.KtorDownloadHttp
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class KtorDownloadHttpContractTest : DownloadHttpContract() {
    private val clients = CopyOnWriteArrayList<HttpClient>()
    private val bases = CopyOnWriteArrayList<OkHttpClient>()
    override val legacy = false

    override fun http(server: TestHttpsServer, timeoutMs: Int): DownloadHttp {
        val base = OkHttpClient.Builder()
            .sslSocketFactory(server.sslContext.socketFactory, server.trustManager)
            .hostnameVerifier { host, _ -> host == "127.0.0.1" }
            .build().also { bases += it }
        val client = AndroidHttpClients(base).downloadClient.also { clients += it }
        return KtorDownloadHttp(client) { timeoutMs }
    }

    // A default OkHttp or app-specific agent would disclose the wrong identity on the wire.
    @Test fun userAgentIsExactlyTheSystemAgent() = TestHttpsServer().use { server ->
        val old = System.getProperty("http.agent")
        System.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android 14; D3 test)")
        try {
            server.route("/ua") { it.reply(200, ByteArray(0)) }
            runBlocking { http(server).headers("${server.base}/ua") }
            val ua = server.requests.single().headers["user-agent"]!!.single()
            assertEquals("Dalvik/2.1.0 (Linux; U; Android 14; D3 test)", ua)
            assertFalse(ua.contains("AndBible", ignoreCase = true) || ua.contains("okhttp", ignoreCase = true))
        } finally {
            if (old == null) System.clearProperty("http.agent") else System.setProperty("http.agent", old)
        }
    }

    // Both engines have made real calls before closing either side of the shared base.
    @Test fun closingDownloadClientKeepsLlmClientWorking() = TestHttpsServer().use { server ->
        server.route("/ok") { it.reply(200, "ok".toByteArray()) }
        val base = OkHttpClient.Builder()
            .sslSocketFactory(server.sslContext.socketFactory, server.trustManager)
            .hostnameVerifier { host, _ -> host == "127.0.0.1" }
            .build().also { bases += it }
        val factory = AndroidHttpClients(base)
        val download = factory.downloadClient.also { clients += it }
        val profile = net.bible.sharedcore.llm.http.LlmTimeouts(1_000, 5_000, null)
        factory.clientFor(profile).also { clients += it }
        val transport = net.bible.sharedcore.llm.http.KtorLlmHttpTransport(factory::clientFor)
        runBlocking {
            assertEquals(200, KtorDownloadHttp(download) { 5_000 }.headers("${server.base}/ok").status)
            assertEquals(200, transport.get("${server.base}/ok", emptyMap(), profile).code)
            download.close()
            kotlinx.coroutines.withTimeout(5_000) {
                download.engine.coroutineContext[Job]!!.join()
            }
            assertFalse(base.dispatcher.executorService.isShutdown)
            assertEquals(200, transport.get("${server.base}/ok", emptyMap(), profile).code)
        }
    }

    @After fun closeClients() {
        clients.forEach { it.close() }
        runBlocking {
            kotlinx.coroutines.withTimeout(5_000) { clients.forEach { it.engine.coroutineContext[Job]!!.join() } }
        }
        bases.forEach {
            it.dispatcher.executorService.shutdownNow()
            it.connectionPool.evictAll()
        }
    }
}
