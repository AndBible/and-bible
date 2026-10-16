package net.bible.sharedcore.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DavPathTest {
    private val p = DavPath("https://nas.local/remote.php/dav/files/me")

    @Test fun baseUrlGetsTrailingSlash() =
        assertEquals("https://nas.local/remote.php/dav/files/me/", p.baseUrl)

    @Test fun urlOfRootIsBase() = assertEquals(p.baseUrl, p.url(""))

    @Test fun urlEncodesEachSegment() = assertEquals(
        "https://nas.local/remote.php/dav/files/me/My%20Folder/K%C3%A4%C3%A4nn%C3%B6s/A%2BB.abmd.zip",
        p.url("My Folder/Käännös/A+B.abmd.zip"),
    )

    @Test fun urlKeepsUnreservedCharacters() =
        assertEquals("${p.baseUrl}a-b_c.d~e/1.12.sqlite3.gz", p.url("a-b_c.d~e/1.12.sqlite3.gz"))

    @Test fun relativizeAbsolutePathHref() =
        assertEquals("My Folder/x", p.relativize("/remote.php/dav/files/me/My%20Folder/x"))

    @Test fun relativizeFullUrlHrefAndTrailingSlash() =
        assertEquals("My Folder", p.relativize("https://nas.local/remote.php/dav/files/me/My%20Folder/"))

    @Test fun relativizeBaseItself() {
        assertEquals("", p.relativize("/remote.php/dav/files/me/"))
        assertEquals("", p.relativize("/remote.php/dav/files/me"))
    }

    @Test fun relativizeDecodesPlusLiterally() =
        assertEquals("A+B", p.relativize("/remote.php/dav/files/me/A+B"))

    @Test fun relativizeLowercaseHexAndUtf8() =
        assertEquals("Käännös", p.relativize("/remote.php/dav/files/me/K%c3%a4%c3%a4nn%c3%b6s"))

    @Test fun relativizeOutsideBaseIsNull() =
        assertNull(p.relativize("/remote.php/dav/files/other/x"))

    @Test fun rootBaseUrl() {
        val r = DavPath("https://h:8443")
        assertEquals("https://h:8443/", r.baseUrl)
        assertEquals("https://h:8443/a/b", r.url("a/b"))
        assertEquals("a/b", r.relativize("/a/b"))
    }

    @Test fun baseUrlWithEncodedSegment() {
        val r = DavPath("https://h/dav/My%20Files/")
        assertEquals("x", r.relativize("/dav/My%20Files/x"))
        assertEquals("https://h/dav/My%20Files/x%20y", r.url("x y"))
    }

    @Test fun join() {
        assertEquals("a", joinDavPath("", "a"))
        assertEquals("p/a", joinDavPath("p", "a"))
    }

    @Test fun relativizeTrailingPercentIsKeptLiterally() {
        assertEquals("a%", p.relativize("/remote.php/dav/files/me/a%"))
        assertEquals("a%4", p.relativize("/remote.php/dav/files/me/a%4"))
    }

    @Test fun relativizeMalformedEscapeDoesNotThrow() =
        assertEquals("a%zzb", p.relativize("/remote.php/dav/files/me/a%zzb"))

    @Test fun relativizeEscapeAtVeryEnd() =
        assertEquals("a b", p.relativize("/remote.php/dav/files/me/a%20b"))
}
