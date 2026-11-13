package net.bible.service.cloudsync.desktop.drive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoopbackOAuthBrowserTest {
    private val LOOP = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val page = "<html><body>Signed in</body></html>"

    private fun httpGet(url: String): Pair<Int, String> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString().orEmpty()
        return code to body
    }

    private fun portOf(redirect: String) = URI(redirect).port

    private fun refuses(port: Int): Boolean = try {
        Socket(LOOP, port).close(); false
    } catch (_: java.io.IOException) { true }

    @Test
    fun returnsRedirectWithCode() = runBlocking {
        var redirect = ""
        var served: Pair<Int, String>? = null
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            val uri = URI(url)
            redirect = uri.query.substringAfter("redirect_uri=")
            served = withContext(Dispatchers.IO) { httpGet("$redirect?code=c&state=s") }
            true
        }, successPage = page)
        val result = browser.authorize { "https://accounts.example/auth?redirect_uri=$it" }
        assertEquals(redirect.trimEnd('/') + "/?code=c&state=s", result)
        assertTrue(redirect.startsWith("http://127.0.0.1:"))
        assertEquals(200, served!!.first)
        assertEquals(page, served!!.second)
        assertTrue(refuses(portOf(redirect)))
    }

    @Test
    fun errorRedirectGetsErrorPageAndCodeRedirectGetsSuccessPage() = runBlocking {
        var errorServed: Pair<Int, String>? = null
        val denied = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=")
            errorServed = withContext(Dispatchers.IO) { httpGet("$base?error=access_denied&state=s") }
            true
        }, successPage = "<ok/>", errorPage = "<fail/>")
        denied.authorize { "https://x/auth?redirect_uri=$it" }
        assertEquals(200, errorServed!!.first)
        assertEquals("<fail/>", errorServed!!.second)

        var okServed: Pair<Int, String>? = null
        val granted = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=")
            okServed = withContext(Dispatchers.IO) { httpGet("$base?code=c&state=s") }
            true
        }, successPage = "<ok/>", errorPage = "<fail/>")
        granted.authorize { "https://x/auth?redirect_uri=$it" }
        assertEquals("<ok/>", okServed!!.second)
    }

    @Test
    fun faviconRequestIsIgnored() = runBlocking {
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=").trimEnd('/')
            withContext(Dispatchers.IO) {
                httpGet("$base/favicon.ico")
                httpGet("$base/?code=abc")
            }
            true
        }, successPage = page)
        val result = browser.authorize { "https://x/auth?redirect_uri=$it" }
        assertTrue(result.endsWith("/?code=abc"), result)
    }

    @Test
    fun errorRedirectIsReturned() = runBlocking {
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=").trimEnd('/')
            withContext(Dispatchers.IO) { httpGet("$base/?error=access_denied") }
            true
        }, successPage = page)
        assertTrue(browser.authorize { "https://x/auth?redirect_uri=$it" }.endsWith("/?error=access_denied"))
    }

    @Test
    fun timeoutCancels() = runBlocking {
        var port = 0
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            port = portOf(URI(url).query.substringAfter("redirect_uri="))
            true
        }, timeoutMs = 200, successPage = page)
        assertFailsWith<CloudSyncUserCancelledException> { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        assertTrue(refuses(port))
    }

    @Test
    fun cancellationClosesSocket() = runBlocking {
        val opened = CompletableDeferred<Int>()
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            opened.complete(portOf(URI(url).query.substringAfter("redirect_uri=")))
            true
        }, successPage = page)
        val job = async(Dispatchers.Default) { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        val port = withTimeout(5000) { opened.await() }
        assertFalse(refuses(port))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFailsWith<CancellationException> { job.await() }
        assertTrue(refuses(port))
    }

    @Test
    fun browserOpenFailureCancels() = runBlocking {
        var port = 0
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            port = portOf(URI(url).query.substringAfter("redirect_uri="))
            false
        }, successPage = page)
        assertFailsWith<CloudSyncUserCancelledException> { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        assertTrue(refuses(port))
    }

    @Test
    fun secondAuthorizeCancelsFirst() = runBlocking {
        val firstOpened = CompletableDeferred<Int>()
        var calls = 0
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            if (++calls == 1) firstOpened.complete(portOf(URI(url).query.substringAfter("redirect_uri=")))
            else {
                val base = URI(url).query.substringAfter("redirect_uri=").trimEnd('/')
                withContext(Dispatchers.IO) { httpGet("$base/?code=second") }
            }
            true
        }, successPage = page)
        val first = async(Dispatchers.Default) {
            runCatching { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        }
        val firstPort = withTimeout(5000) { firstOpened.await() }
        val second = browser.authorize { "https://x/auth?redirect_uri=$it" }
        assertTrue(second.endsWith("/?code=second"))
        assertTrue(withTimeout(5000) { first.await() }.exceptionOrNull() is CloudSyncUserCancelledException)
        assertTrue(refuses(firstPort))
    }

    @Test
    fun secondInstanceSupersedesFirst() = runBlocking {
        val firstOpened = CompletableDeferred<Int>()
        val first = LoopbackOAuthBrowser(openBrowser = { url ->
            firstOpened.complete(portOf(URI(url).query.substringAfter("redirect_uri="))); true
        }, successPage = page)
        val second = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=").trimEnd('/')
            withContext(Dispatchers.IO) { httpGet("$base/?code=2") }
            true
        }, successPage = page)
        val a = async(Dispatchers.Default) { runCatching { first.authorize { "https://x/auth?redirect_uri=$it" } } }
        val port = withTimeout(5000) { firstOpened.await() }
        assertTrue(second.authorize { "https://x/auth?redirect_uri=$it" }.endsWith("/?code=2"))
        assertTrue(withTimeout(5000) { a.await() }.exceptionOrNull() is CloudSyncUserCancelledException)
        assertTrue(refuses(port))
    }

    @Test
    fun concurrentSocketsGetDifferentOsChosenPorts() = runBlocking {
        val ports = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val both = CompletableDeferred<Unit>()
        // Instances do not supersede each other here because the second only binds after the first opened;
        // the first is superseded, but both ports were observed while bound.
        val b1 = LoopbackOAuthBrowser(openBrowser = { u -> ports += portOf(URI(u).query.substringAfter("redirect_uri=")); both.await(); true }, successPage = page)
        val j1 = async(Dispatchers.Default) { runCatching { b1.authorize { "https://x/auth?redirect_uri=$it" } } }
        withTimeout(5000) { while (ports.size < 1) kotlinx.coroutines.yield() }
        val b2 = LoopbackOAuthBrowser(openBrowser = { u -> ports += portOf(URI(u).query.substringAfter("redirect_uri=")); false }, successPage = page)
        runCatching { b2.authorize { "https://x/auth?redirect_uri=$it" } }
        both.complete(Unit)
        j1.await()
        assertEquals(2, ports.toSet().size)
        assertTrue(ports.all { it > 0 && it != 80 && it != 8080 })
    }

    private fun stalledClient(port: Int): Socket = Socket(LOOP, port).also {
        it.getOutputStream().apply { write("GET /?co".toByteArray()); flush() }
    }

    @Test
    fun timeoutWhileConnectionStalledMidRequest() = runBlocking {
        var port = 0
        var stalled: Socket? = null
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            port = portOf(URI(url).query.substringAfter("redirect_uri="))
            stalled = stalledClient(port); true
        }, timeoutMs = 300, successPage = page)
        val started = System.nanoTime()
        assertFailsWith<CloudSyncUserCancelledException> { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3000, "authorize must end promptly")
        assertTrue(refuses(port))
        assertEquals(-1, stalled!!.also { it.soTimeout = 2000 }.getInputStream().read(), "client socket closed")
        stalled!!.close()
    }

    @Test
    fun cancelWhileConnectionStalledMidRequest() = runBlocking {
        val connected = CompletableDeferred<Int>()
        var stalled: Socket? = null
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            val port = portOf(URI(url).query.substringAfter("redirect_uri="))
            stalled = stalledClient(port); connected.complete(port); true
        }, successPage = page)
        val job = async(Dispatchers.Default) { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        val port = withTimeout(5000) { connected.await() }
        val started = System.nanoTime()
        withTimeout(3000) { job.cancelAndJoin() }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3000)
        assertTrue(refuses(port))
        stalled!!.close()
    }

    @Test
    fun idleConnectionDoesNotBlockRealRedirect() = runBlocking {
        val idle = java.util.concurrent.atomic.AtomicReference<Socket?>(null)
        val browser = LoopbackOAuthBrowser(openBrowser = { url ->
            val base = URI(url).query.substringAfter("redirect_uri=").trimEnd('/')
            idle.set(Socket(LOOP, portOf(base)))
            withContext(Dispatchers.IO) { httpGet("$base/?code=real") }
            true
        }, successPage = page)
        val result = withTimeout(4000) { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        assertTrue(result.endsWith("/?code=real"))
        idle.get()!!.close()
    }
}
