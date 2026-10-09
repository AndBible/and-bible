package net.bible.sharedcore.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultistatusParserTest {
    private val t1004 = HttpDates.parseRfc1123("Thu, 01 Oct 2026 10:04:00 GMT")!!
    private val t1005 = HttpDates.parseRfc1123("Thu, 01 Oct 2026 10:05:00 GMT")!!

    private fun check(xml: String, base: String) {
        val res = MultistatusParser.parse(xml, DavPath(base)).associateBy { it.path }
        assertEquals(setOf("sync", "sync/device one", "sync/1.12.sqlite3.gz"), res.keys)
        val self = res.getValue("sync")
        assertTrue(self.isCollection); assertEquals(t1005, self.lastModified)
        val dev = res.getValue("sync/device one")
        assertTrue(dev.isCollection); assertEquals(t1004, dev.lastModified)
        assertEquals("device one", dev.name); assertEquals("sync", dev.parent)
        val file = res.getValue("sync/1.12.sqlite3.gz")
        assertEquals(false, file.isCollection); assertEquals(2048L, file.contentLength)
        assertEquals(t1005, file.lastModified)
    }

    @Test fun apache() = check(MultistatusFixtures.APACHE, "https://nas.local/dav/")
    @Test fun nginx() = check(MultistatusFixtures.NGINX, "https://nas.local/dav/")
    @Test fun rclone() = check(MultistatusFixtures.RCLONE, "https://nas.local/dav/")
    @Test fun nextcloud() = check(MultistatusFixtures.NEXTCLOUD, "https://cloud.example.com/remote.php/dav/files/me/")
    @Test fun synology() = check(MultistatusFixtures.SYNOLOGY, "https://nas.local:5006/")

    @Test fun creationDateParsedWhenPresent_nullWhenEmptyOr404() {
        val apache = MultistatusParser.parse(MultistatusFixtures.APACHE, DavPath("https://nas.local/dav/"))
        assertEquals(HttpDates.parseIso8601("2026-10-01T10:00:00Z"), apache.first { it.path == "sync" }.creationDate)
        val rclone = MultistatusParser.parse(MultistatusFixtures.RCLONE, DavPath("https://nas.local/dav/"))
        assertNull(rclone.first { it.path == "sync" }.creationDate)
        assertNull(rclone.first { it.path == "sync" }.etag)
    }

    @Test fun etagEntityDecoded() {
        val nc = MultistatusParser.parse(MultistatusFixtures.NEXTCLOUD, DavPath("https://cloud.example.com/remote.php/dav/files/me/"))
        assertEquals("\"abc\"", nc.first { it.path.endsWith(".gz") }.etag)
    }

    @Test fun hrefOutsideBaseIsDropped() {
        val res = MultistatusParser.parse(MultistatusFixtures.APACHE, DavPath("https://nas.local/other/"))
        assertTrue(res.isEmpty())
    }

    @Test fun malformedXmlThrows() {
        assertFailsWith<IllegalArgumentException> {
            MultistatusParser.parse("<d:multistatus xmlns:d=\"DAV:\"><d:response>", DavPath("https://h/"))
        }
    }
}
