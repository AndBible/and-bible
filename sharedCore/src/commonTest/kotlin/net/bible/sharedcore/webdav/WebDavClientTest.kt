package net.bible.sharedcore.webdav

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebDavClientTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<ByteArray>()
    private var now = 1_000_000L

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): WebDavClient {
        val engine = MockEngine { req ->
            requests += req
            (req.body as? OutgoingContent.WriteChannelContent)?.let { content ->
                val ch = io.ktor.utils.io.ByteChannel(autoFlush = true)
                content.writeTo(ch); ch.close()
                bodies += ch.readRemaining().readByteArray()
            }
            handler(req)
        }
        return WebDavClient(createWebDavHttpClient(engine, "me", "pw"), "https://h/dav/", "me", "pw", { now })
    }

    private fun MockRequestHandleScope.multistatus(xml: String, date: String = "Thu, 01 Oct 2026 10:05:00 GMT") =
        respond(xml, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType to listOf("application/xml"), HttpHeaders.Date to listOf(date)))

    @Test fun propfind_sendsDepthAndParses() = runTest {
        val c = client { multistatus(MultistatusFixtures.APACHE) }
        val res = c.propfind("sync", 1)
        assertEquals("PROPFIND", requests.single().method.value)
        assertEquals("1", requests.single().headers["Depth"])
        assertEquals("https://h/dav/sync/", requests.single().url.toString().let { if (it.endsWith("/")) it else "$it/" })
        assertEquals(3, res.size)
    }

    @Test fun clockOffsetFromDateHeader() = runTest {
        now = HttpDates.parseRfc1123("Thu, 01 Oct 2026 10:00:00 GMT")!!
        val c = client { multistatus(MultistatusFixtures.APACHE, date = "Thu, 01 Oct 2026 10:05:00 GMT") }
        c.propfind("sync", 0)
        assertEquals(5 * 60_000L, c.serverClockOffsetMs)
    }

    @Test fun bare401WithoutChallenge_isAuthException() = runTest {
        val c = client { respond("", HttpStatusCode.Unauthorized) }
        assertEquals(401, assertFailsWith<DavAuthException> { c.propfind("sync", 0) }.status)
    }

    @Test fun bare403WithoutChallenge_isAuthException() = runTest {
        val c = client { respond("", HttpStatusCode.Forbidden) }
        assertEquals(403, assertFailsWith<DavAuthException> { c.propfind("sync", 0) }.status)
    }

    @Test fun basicAuth_challengeThenPreemptive() = runTest {
        val c = client { req ->
            if (req.headers[HttpHeaders.Authorization] == null)
                respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"dav\""))
            else multistatus(MultistatusFixtures.APACHE)
        }
        c.propfind("sync", 0)
        c.propfind("sync", 0)
        assertEquals(3, requests.size)  // 401, retry, then preemptive
        assertEquals("Basic bWU6cHc=", requests[1].headers[HttpHeaders.Authorization])
        assertEquals("Basic bWU6cHc=", requests[2].headers[HttpHeaders.Authorization])
    }

    @Test fun put_digestRetry_resendsFullBody() = runTest {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        val c = client { req ->
            val auth = req.headers[HttpHeaders.Authorization]
            if (auth == null || !auth.startsWith("Digest"))
                respond("", HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.WWWAuthenticate, "Digest realm=\"dav\", nonce=\"abc\", qop=\"auth\", algorithm=MD5"))
            else respond("", HttpStatusCode.Created)
        }
        c.put("a/b.gz", payload.size.toLong(), "application/gzip") { ch -> ch.writeFully(payload) }
        assertTrue(requests.size >= 2)
        assertTrue(requests.last().headers[HttpHeaders.Authorization]!!.startsWith("Digest"))
        assertTrue(bodies.last().contentEquals(payload))
    }

    @Test fun statusMapping() = runTest {
        suspend fun statusOf(code: Int): Throwable {
            val c = client { respond("", HttpStatusCode.fromValue(code)) }
            return runCatching { c.propfind("x", 0) }.exceptionOrNull()!!
        }
        assertTrue(statusOf(404) is DavNotFoundException)
        assertTrue(statusOf(403) is DavAuthException)
        assertTrue(statusOf(507) is DavQuotaException)
        assertTrue(statusOf(503) is DavTransientException)
        assertTrue(statusOf(429) is DavTransientException)
        assertTrue(statusOf(408) is DavTransientException)
        assertTrue(statusOf(400) is DavProtocolException)
    }

    @Test fun unauthorizedWithoutUsableChallenge_isAuthException() = runTest {
        val c = client { respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"x\"")) }
        assertFailsWith<DavAuthException> { c.propfind("x", 0) }
    }

    @Test fun malformedMultistatus_isProtocolException() = runTest {
        val c = client { multistatus("<d:multistatus xmlns:d=\"DAV:\"><d:response>") }
        assertFailsWith<DavProtocolException> { c.propfind("x", 1) }
    }

    @Test fun nonMultistatusBody_isProtocolException() = runTest {
        val c = client { respond("<html><body>hi</body></html>", HttpStatusCode.MultiStatus) }
        assertFailsWith<DavProtocolException> { c.propfind("x", 1) }
    }

    @Test fun networkIOException_isTransient() = runTest {
        val c = client { throw kotlinx.io.IOException("connection reset") }
        assertFailsWith<DavTransientException> { c.propfind("x", 0) }
    }

    @Test fun untrustedCertificateInCauseChain_isUnwrapped() = runTest {
        val info = DavCertificateInfo("h", "AB", "CN=h", "CN=h", 0, 1)
        val c = client {
            // what the JVM does: SSLHandshakeException(IOException) caused by our CertificateException
            throw kotlinx.io.IOException("handshake", IllegalStateException("cert", DavUntrustedCertificateException(info)))
        }
        val e = assertFailsWith<DavUntrustedCertificateException> { c.propfind("x", 0) }
        assertEquals(info, e.certificate)
    }

    @Test fun platformErrorMapperWins() = runTest {
        val info = DavCertificateInfo("h", "AB", "CN=x", "CN=x", 0, 1)
        val engine = MockEngine { throw kotlinx.io.IOException("Hostname h not verified") }
        val c = WebDavClient(createWebDavHttpClient(engine, "me", "pw"), "https://h/", "me", "pw", { now },
            mapPlatformError = { DavUntrustedCertificateException(info) })
        assertFailsWith<DavUntrustedCertificateException> { c.propfind("x", 0) }
    }

    @Test fun mkcol_existing405_isOk_andMkcolsCreatesEachLevel() = runTest {
        val c = client { req -> respond("", if (req.url.encodedPath == "/dav/a/") HttpStatusCode.MethodNotAllowed else HttpStatusCode.Created) }
        c.mkcols("a/b c/d")
        assertEquals(listOf("/dav/a/", "/dav/a/b%20c/", "/dav/a/b%20c/d/"), requests.map { it.url.encodedPath })
        assertTrue(requests.all { it.method.value == "MKCOL" })
    }

    @Test fun delete_404_isOk() = runTest {
        val c = client { respond("", HttpStatusCode.NotFound) }
        c.delete("gone")
    }

    @Test fun get_streamsChunks() = runTest {
        val payload = ByteArray(200_000) { it.toByte() }
        val c = client { respond(ByteReadChannel(payload), HttpStatusCode.OK) }
        var total = 0
        val out = mutableListOf<Byte>()
        c.get("f") { buf, n -> total += n; for (i in 0 until n) out += buf[i] }
        assertEquals(payload.size, total)
        assertTrue(out.toByteArray().contentEquals(payload))
    }
}
