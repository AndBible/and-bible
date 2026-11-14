package net.bible.service.cloudsync.webdav

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4103: the WebDAV listing hands the server's `getetag` to the sync layer, which reuses unchanged meta reads. */
class WebDavCloudAdapterEtagTest {
    private val listing = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
        <d:response><d:href>/dav/docs/KJV/</d:href><d:propstat><d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
        <d:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</d:getlastmodified>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        <d:response><d:href>/dav/docs/KJV/meta.json</d:href><d:propstat><d:prop>
        <d:resourcetype/>
        <d:getcontentlength>120</d:getcontentlength>
        <d:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</d:getlastmodified>
        <d:getetag>"abc-1"</d:getetag>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        <d:response><d:href>/dav/docs/KJV/1.0.abmd.zip</d:href><d:propstat><d:prop>
        <d:resourcetype/>
        <d:getcontentlength>2048</d:getcontentlength>
        <d:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</d:getlastmodified>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    private val rootListing = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
        <d:response><d:href>/dav/</d:href><d:propstat><d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test fun listedFilesCarryTheServerEtag() = runBlocking {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.trimEnd('/') == "/dav") rootListing else listing
            respond(body, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8"))
        }
        val state = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
        val ui = object : WebDavSignInUi {
            override suspend fun confirmCertificate(info: DavCertificateInfo) = true
            override fun message(kind: WebDavMessage) = kind.name
        }
        val adapter = WebDavCloudAdapter(WebDavConfig("https://h/dav/", "me", "pw", ""), state, ui, "device", { c, _ ->
            WebDavClient(createWebDavHttpClient(engine, c.username, c.password), c.serverUrl, c.username, c.password, { 0L })
        })
        adapter.signIn()

        val files = adapter.listFiles(parentsIds = listOf("docs/KJV"))

        assertEquals(mapOf("docs/KJV/meta.json" to "\"abc-1\"", "docs/KJV/1.0.abmd.zip" to null), files.associate { it.id to it.etag })
    }

    /**
     * Review of #4103: an etag derived from mtime(seconds) + size + inode can repeat for a rewrite in the same
     * second, so the meta reuse also keys on the server's own modification time, exactly as listed: before the
     * per-response clock offset (which jitters) is subtracted.
     */
    @Test fun listedFilesCarryTheRawServerModificationTime() = runBlocking {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.trimEnd('/') == "/dav") rootListing else listing
            // The server clock runs 60 s ahead of the device (device clock is 0 below): a non-zero offset.
            respond(body, HttpStatusCode.MultiStatus, headersOf(
                HttpHeaders.ContentType to listOf("application/xml; charset=utf-8"),
                HttpHeaders.Date to listOf("Thu, 01 Jan 1970 00:01:00 GMT"),
            ))
        }
        val state = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
        val ui = object : WebDavSignInUi {
            override suspend fun confirmCertificate(info: DavCertificateInfo) = true
            override fun message(kind: WebDavMessage) = kind.name
        }
        val adapter = WebDavCloudAdapter(WebDavConfig("https://h/dav/", "me", "pw", ""), state, ui, "device", { c, _ ->
            WebDavClient(createWebDavHttpClient(engine, c.username, c.password), c.serverUrl, c.username, c.password, { 0L })
        })
        adapter.signIn()

        val meta = adapter.listFiles(parentsIds = listOf("docs/KJV")).single { it.name == "meta.json" }

        val serverMtime = 1_790_849_100_000L // Thu, 01 Oct 2026 10:05:00 GMT
        assertEquals(serverMtime, meta.serverModifiedTime)
        assertEquals(serverMtime - 60_000L, meta.createdTime, "the device-clock time still carries the offset")
    }
}
