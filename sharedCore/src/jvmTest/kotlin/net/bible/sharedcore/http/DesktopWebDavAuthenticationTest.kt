package net.bible.sharedcore.http

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.webdav.DavAuthException
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Real desktop engine checks, rather than MockEngine assertions about constructed headers. */
class DesktopWebDavAuthenticationTest {
    @Test fun basicChallengePreservesUtf8PasswordAndDoesNotSendSessionCookies() = runBlocking {
        val username = "fixture@example.invalid"
        val password = "  test-äöü-€-§-\"-:-%- spaces  "
        val expected = "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        val authorizations = CopyOnWriteArrayList<String?>()
        val cookies = CopyOnWriteArrayList<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/dav/") { exchange ->
            val auth = exchange.requestHeaders.getFirst("Authorization")
            authorizations += auth
            cookies += exchange.requestHeaders.getFirst("Cookie")
            exchange.responseHeaders.add("Set-Cookie", "browser-session=fixture; Path=/")
            if (auth != expected) {
                exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"Nextcloud\", charset=\"UTF-8\"")
                exchange.sendResponseHeaders(401, -1)
            } else {
                val body = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""".toByteArray()
                exchange.sendResponseHeaders(207, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        val http = createWebDavHttpClient(OkHttp.create(), username, password)
        try {
            val client = WebDavClient(http, "http://127.0.0.1:${server.address.port}/dav/", username, password, { 0 })
            repeat(2) { assertEquals(true, client.propfind("", 0).single().isCollection) }
            assertEquals(listOf(null, expected, expected), authorizations.toList())
            cookies.forEach { assertNull(it) }
        } finally {
            http.close()
            server.stop(0)
        }
    }

    @Test fun missingChallengeDoesNotSendCredentialsToAnUnidentified401Responder() = runBlocking {
        val authorizations = CopyOnWriteArrayList<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/dav/") { exchange ->
            authorizations += exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        server.start()
        val http = createWebDavHttpClient(OkHttp.create(), "fixture", "password")
        try {
            val client = WebDavClient(http, "http://127.0.0.1:${server.address.port}/dav/", "fixture", "password", { 0 })
            assertFailsWith<DavAuthException> { client.propfind("", 0) }
            assertEquals(listOf(null), authorizations.toList())
        } finally {
            http.close()
            server.stop(0)
        }
    }
}
