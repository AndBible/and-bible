package net.bible.service.cloudsync.webdav

import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import org.junit.Assert.assertEquals
import io.ktor.utils.io.writeFully
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDavServerTest {
    private val server = FakeDavServer(nowServer = 1_700_000_000_000L)
    private fun client() = WebDavClient(createWebDavHttpClient(server.engine, "me", "pw"), "https://h/dav/", "me", "pw", { server.nowServer })

    @Test fun put_bumpsParentMtime_whenPropagates() = runBlocking {
        client().mkcol("d")
        server.advance(5_000)
        server.put("d/f", byteArrayOf(1))
        assertEquals(server.nowServer / 1000 * 1000, server.mtime("d"))
    }

    @Test fun put_doesNotBumpParent_whenNotPropagating() = runBlocking {
        server.propagates = false
        val c = client()
        c.mkcol("d")
        val before = server.mtime("d")
        server.advance(5_000)
        c.put("d/f", 1, "application/gzip") { it.writeFully(byteArrayOf(1)) }
        assertEquals(before, server.mtime("d"))
        assertEquals(server.nowServer / 1000 * 1000, server.mtime("d/f"))
    }

    @Test fun depth1_listsChildrenWithEncodedHrefs() = runBlocking {
        val c = client()
        c.mkcol("Käännös +1")
        server.put("Käännös +1/A B+C.zip", byteArrayOf(1, 2, 3))
        val res = c.propfind("Käännös +1", 1)
        assertEquals(setOf("Käännös +1", "Käännös +1/A B+C.zip"), res.map { it.path }.toSet())
        assertEquals(3L, res.first { !it.isCollection }.contentLength)
        assertTrue(server.requests.last().url.encodedPath.contains("K%C3%A4%C3%A4nn%C3%B6s%20%2B1"))
    }
}
