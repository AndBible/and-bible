package net.bible.sharedcore.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DownloadHttpTypesTest {
    @Test fun httpsIsAcceptedCaseInsensitively() {
        requireHttps("https://example.org/a")
        requireHttps("HTTPS://example.org/a")
    }

    @Test fun otherSchemesAreRejectedWithTheUrl() {
        for (url in listOf("http://example.org/a", "ftp://example.org/a", "file:///tmp/a", "example.org/a")) {
            val error = assertFailsWith<InsecureUrlException> { requireHttps(url) }
            assertEquals(url, error.url)
        }
    }

    @Test fun timeoutIsATransportFailure() {
        assertIs<DownloadTransportException>(DownloadTimeoutException(null))
        assertIs<okio.IOException>(HttpStatusException(404, "Not Found"))
    }
}
