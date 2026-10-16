package net.bible.service.download.http

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidHttpClients
import net.bible.android.platform.SuspendHttpTransport
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.KtorManifestFetcher
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.KtorDownloadHttp
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import okhttp3.OkHttpClient
import org.crosswire.common.util.WebResource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadConsumersTest {
    private fun downloader(http: DownloadHttp, scope: CoroutineScope) = GenericFileDownloader(
        scope = scope, settings = get(AppSettings::class.java), notifier = get(UserNotifier::class.java),
        strings = get(CoreStrings::class.java), http = http,
    )

    private fun withKtorTransport(block: suspend (TestHttpsServer, DownloadHttp, CoroutineScope) -> Unit): Unit = runTest(timeout = 60.seconds) {
        TestHttpsServer().use { server ->
            val base = OkHttpClient.Builder()
                .sslSocketFactory(server.sslContext.socketFactory, server.trustManager)
                .hostnameVerifier { host, _ -> host == "127.0.0.1" }.build()
            val client = AndroidHttpClients(base).downloadClient
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val http = KtorDownloadHttp(client) { 5_000 }
            val previous = WebResource.transport
            try {
                WebResource.transport = SuspendHttpTransport(http, scope)
                block(server, http, scope)
            } finally {
                WebResource.transport = previous
                scope.cancel()
                client.close()
                try {
                    runBlocking {
                        withTimeout(5_000) {
                            scope.coroutineContext[Job]!!.join()
                            client.engine.coroutineContext[Job]!!.join()
                        }
                    }
                } finally {
                    base.dispatcher.executorService.shutdownNow()
                    base.connectionPool.evictAll()
                }
            }
        }
    }

    @Test fun manifestFetcherKeepsItsNullContract() = withKtorTransport { server, http, _ ->
        server.route("/ok") { it.reply(200, "manifest".toByteArray()) }
        server.route("/no") { it.reply(404, "not a manifest".toByteArray()) }
        val closedPort = ServerSocket(0).use { it.localPort }
        val fetcher = KtorManifestFetcher(http)
        run {
            assertEquals(200, fetcher.status("${server.base}/ok"))
            assertEquals("manifest", fetcher.body("${server.base}/ok"))
            assertEquals(404, fetcher.status("${server.base}/no"))
            assertNull(fetcher.body("${server.base}/no"))
            for (url in listOf("not a url", "http://127.0.0.1:1/plain", "https://127.0.0.1:$closedPort/x")) {
                assertNull(fetcher.status(url))
                assertNull(fetcher.body(url))
            }
        }
        assertTrue(server.requests.all { it.method == "GET" })
    }

    @Test fun unchangedFileIsSkippedAcrossInstancesAndChangedFileIsInstalled() = withKtorTransport { server, http, scope ->
        var modified = "Sun, 13 Sep 2020 12:26:40 GMT"
        var body = "first"
        server.route("/file.json") { it.reply(200, body.toByteArray(), mapOf("Last-Modified" to modified)) }
        val dir = kotlin.io.path.createTempDirectory("d3-consumer").toFile()
        ShadowStatFs.registerStats(dir.path, 100, 20, 10)
        val url = URL("${server.base}/file.json?${System.nanoTime()}")
        val settings = get<AppSettings>(AppSettings::class.java)
        val key = "last-downloaded-$url"
        try {
            val target = File(dir, "file.json")
            assertTrue(downloader(http, scope).downloadFileWithProgress(url, target))
            assertEquals("first", target.readText())
            assertEquals(1_600_000_000_000L, settings.getLong(key, -1))
            assertEquals(2, server.requests.size)
            body = "must not replace unchanged file"
            assertTrue(downloader(http, scope).downloadFileWithProgress(url, target))
            assertEquals("first", target.readText())
            assertEquals(3, server.requests.size)
            modified = "Mon, 14 Sep 2020 12:26:40 GMT"
            body = "second"
            assertTrue(downloader(http, scope).downloadFileWithProgress(url, target))
            assertEquals("second", target.readText())
            assertEquals(1_600_086_400_000L, settings.getLong(key, -1))
            assertEquals(5, server.requests.size)
            assertTrue(server.requests.all { it.method == "GET" })
        } finally {
            settings.removeLong(key)
            ShadowStatFs.unregisterStats(dir.path)
            dir.deleteRecursively()
        }
    }

    @Test fun failedProbeDoesNotSkipExistingFile() = withKtorTransport { server, http, scope ->
        // A downgrade fails the probe; the next GET is the real download and succeeds.
        var calls = 0
        server.route("/retry") {
            if (++calls == 1) it.reply(302, ByteArray(0), mapOf("Location" to "http://127.0.0.1:1/plain"))
            else it.reply(200, "replacement".toByteArray())
        }
        val dir = kotlin.io.path.createTempDirectory("d3-probe").toFile()
        ShadowStatFs.registerStats(dir.path, 100, 20, 10)
        val url = URL("${server.base}/retry")
        val settings = get<AppSettings>(AppSettings::class.java)
        val key = "last-downloaded-$url"
        try {
            val target = File(dir, "retry").apply { writeText("old") }
            settings.setLong(key, Long.MAX_VALUE)
            assertTrue(downloader(http, scope).downloadFileWithProgress(url, target))
            assertEquals("replacement", target.readText())
            assertEquals(2, calls)
        } finally {
            settings.removeLong(key)
            ShadowStatFs.unregisterStats(dir.path)
            dir.deleteRecursively()
        }
    }

    @Test fun lastModifiedDistinguishesMissingHeadersFromTransportFailure() = withKtorTransport { server, http, scope ->
        server.route("/missing") { it.reply(200, ByteArray(0)) }
        server.route("/invalid") { it.reply(200, ByteArray(0), mapOf("Last-Modified" to "invalid")) }
        val d = downloader(http, scope)
        assertEquals(0L, d.lastModified(URL("${server.base}/missing")))
        assertEquals(0L, d.lastModified(URL("${server.base}/invalid")))
        val closedPort = ServerSocket(0).use { it.localPort }
        assertNull(d.lastModified(URL("https://127.0.0.1:$closedPort/x")))
        assertNull(d.lastModified(URL("http://127.0.0.1:1/plain")))
    }

    @Test fun manifestCancellationPropagates() = withKtorTransport { _, http, _ ->
        val cancellation = CancellationException("caller cancelled")
        val cancelled = object : DownloadHttp by http {
            override suspend fun headers(url: String): net.bible.sharedcore.http.HttpHeaders = throw cancellation
            override suspend fun text(url: String): net.bible.sharedcore.http.HttpTextResult = throw cancellation
        }
        val fetcher = KtorManifestFetcher(cancelled)
        run {
            for (call in listOf<suspend () -> Any?>({ fetcher.status("https://example.org") }, { fetcher.body("https://example.org") })) {
                try {
                    call()
                    fail("Cancellation must not become null")
                } catch (actual: CancellationException) {
                    assertSame(cancellation, actual)
                }
            }
        }
    }
}
