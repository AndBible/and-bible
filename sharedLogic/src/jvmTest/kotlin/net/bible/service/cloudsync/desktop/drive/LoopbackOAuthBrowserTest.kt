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
        Socket(InetAddress.getLoopbackAddress(), port).close(); false
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
    fun portIsChosenByOs() = runBlocking {
        var p1 = 0
        var p2 = 0
        val browser = LoopbackOAuthBrowser(openBrowser = { url -> p1 = portOf(URI(url).query.substringAfter("redirect_uri=")); false }, successPage = page)
        runCatching { browser.authorize { "https://x/auth?redirect_uri=$it" } }
        val b2 = LoopbackOAuthBrowser(openBrowser = { url -> p2 = portOf(URI(url).query.substringAfter("redirect_uri=")); false }, successPage = page)
        runCatching { b2.authorize { "https://x/auth?redirect_uri=$it" } }
        assertTrue(p1 > 0 && p2 > 0)
    }
}
