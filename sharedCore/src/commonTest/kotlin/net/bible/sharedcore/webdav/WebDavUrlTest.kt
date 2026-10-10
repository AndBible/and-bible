package net.bible.sharedcore.webdav

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebDavUrlTest {
    @Test fun acceptsHttps() {
        assertTrue(WebDavUrl.validate("https://nas.local/dav/"))
        assertTrue(WebDavUrl.validate("https://cloud.example.com/remote.php/dav/files/me/"))
        assertTrue(WebDavUrl.validate("https://192.168.1.10:8443"))
        assertTrue(WebDavUrl.validate("HTTPS://Example.com/dav"))
    }

    @Test fun rejectsHttpAndOthers() {
        assertFalse(WebDavUrl.validate("http://nas.local/dav/"))
        assertFalse(WebDavUrl.validate("ftp://nas.local/"))
        assertFalse(WebDavUrl.validate("nas.local/dav"))
        assertFalse(WebDavUrl.validate(""))
    }

    @Test fun rejectsMissingHostOrSpaces() {
        assertFalse(WebDavUrl.validate("https://"))
        assertFalse(WebDavUrl.validate("https:///dav"))
        assertFalse(WebDavUrl.validate("https://nas local/dav"))
    }
}
