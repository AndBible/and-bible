package net.bible.service.cloudsync.nextcloud

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
@LooperMode(LooperMode.Mode.LEGACY)
class NextCloudDocumentBatchListingTest {
    class SignInActivity : ActivityBase()
    @After fun reset() = DatabaseResetter.resetDatabase()

    private val root = "/sync-documents"
    private val modified = 1_704_164_645_000L

    @Test fun rootSearchAcceptsOnlyNamedImmediateChildrenOfRequestedFolders() = withServer { dav, adapter ->
        dav.searchBody = file("$root/A/meta.json") + file("$root/B/meta.json") +
            file("$root/A/nested/meta.json", modified = "") + file("$root/AB/meta.json", modified = "") +
            file("/elsewhere/A/meta.json", modified = "") + file("$root/meta.json", modified = "") +
            file("$root/A/archive.zip", modified = "")
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A", "$root/B/"), "meta.json", 0) }
        assertEquals(setOf("$root/A/meta.json", "$root/B/meta.json"), files.map { it.id }.toSet())
        assertEquals(setOf(modified), files.map { it.createdTime }.toSet())
        assertEquals(1, dav.methods.count { it == "SEARCH" })
        assertTrue(dav.queries.single().contains("/files/me/$root"))
    }

    @Test fun emptyFoldersDoNotSearch() = withServer { dav, adapter ->
        assertTrue(awaitOperation { adapter.listChangedFilesInFolders(root, emptyList(), "meta.json", 0) }.isEmpty())
        assertTrue(dav.methods.isEmpty())
    }

    @Test fun requestedInvalidTimestampFailsRatherThanReturningPartialSuccess() = withServer { dav, adapter ->
        dav.searchBody = file("$root/A/meta.json", modified = "") + file("$root/B/meta.json")
        assertThrows(IOException::class.java) { awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A", "$root/B"), "meta.json", 0) } }
        assertEquals(listOf("SEARCH"), dav.methods.toList())
    }

    @Test fun refusalFallsBackToRealPropfindAndIsRememberedForSession() {
        for (status in listOf(400, 403, 405, 413, 422, 501, 507)) withServer { dav, adapter ->
            dav.searchCode = status
            dav.folderBody = file("$root/A/meta.json") + file("$root/A/old.json", modified = "") +
                file("$root/A/nested/meta.json", modified = "")
            repeat(2) {
                val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", modified - 5_000) }
                assertEquals(listOf("$root/A/meta.json"), files.map { it.id })
            }
            assertEquals("HTTP $status", listOf("SEARCH", "PROPFIND", "PROPFIND"), dav.methods.toList())
        }
    }

    @Test fun fallbackFiltersModificationBoundaryLocallyAndRejectsInvalidRelevantTimes() = withServer { dav, adapter ->
        dav.searchCode = 405
        dav.folderBody = file("$root/A/meta.json")
        assertTrue(awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", modified) }.isEmpty())
        dav.folderBody = file("$root/A/meta.json", modified = "")
        assertThrows(IOException::class.java) { awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", 0) } }
    }

    @Test fun authenticationMissingRootAndServerErrorsAreNotHiddenByFallback() {
        for (status in listOf(401, 404, 500, 503)) withServer { dav, adapter ->
            dav.searchCode = status
            assertThrows(IOException::class.java) { awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", 0) } }
            assertEquals(listOf("SEARCH"), dav.methods.toList())
        }
    }

    @Test fun partialMultistatusCannotAdvanceWatermarkFromItsSuccessfulSubset() = withServer { dav, adapter ->
        dav.searchBody = file("$root/A/meta.json") + """<d:response><d:href>/remote.php/dav/files/me$root</d:href><d:status>HTTP/1.1 507 Insufficient Storage</d:status></d:response>"""
        dav.folderBody = file("$root/A/meta.json") + file("$root/B/meta.json")
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A", "$root/B"), "meta.json", 0) }
        assertEquals(setOf("$root/A/meta.json", "$root/B/meta.json"), files.map { it.id }.toSet())
        assertEquals(1, dav.methods.count { it == "SEARCH" })
        assertEquals(2, dav.methods.count { it == "PROPFIND" })
    }

    @Test fun fullResultPageFallsBackInsteadOfTrustingSuccessfulTruncation() = withServer { dav, adapter ->
        dav.searchBody = (0 until 512).joinToString("") { file("$root/DOC$it/meta.json") }
        dav.generateFolderMeta = true
        val files = awaitOperation(timeoutMillis = 60_000, cleanupMillis = 10_000) { adapter.listChangedFilesInFolders(root, (0 until 600).map { "$root/DOC$it" }, "meta.json", 0) }
        assertEquals(600, files.size)
        assertEquals(1, dav.methods.count { it == "SEARCH" })
        assertEquals(600, dav.methods.count { it == "PROPFIND" })
        assertTrue(dav.queries.single().contains("512"))
    }

    @Test fun optionalMissingPropertiesInPropfindAreNotPartialFileListings() = withServer { dav, adapter ->
        dav.searchCode = 405
        dav.folderBody = file("$root/A/meta.json").replace("</d:response>", """<d:propstat><d:prop><oc:optional/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>""")
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", 0) }
        assertEquals(listOf("$root/A/meta.json"), files.map { it.id })
    }

    @Test fun reportedLimitInOtherwiseSuccessfulXmlTriggersCompleteFallback() = withServer { dav, adapter ->
        dav.searchBody = file("$root/A/meta.json") + """<d:error><d:number-of-matches-within-limits/></d:error>"""
        dav.folderBody = file("$root/A/meta.json") + file("$root/B/meta.json")
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A", "$root/B"), "meta.json", 0) }
        assertEquals(setOf("$root/A/meta.json", "$root/B/meta.json"), files.map { it.id }.toSet())
        assertEquals(2, dav.methods.count { it == "PROPFIND" })
    }

    @Test fun failedFallbackDoesNotReturnSearchSubset() = withServer { dav, adapter ->
        dav.searchCode = 405; dav.folderCode = 503
        assertThrows(IOException::class.java) { awaitOperation { adapter.listChangedFilesInFolders(root, listOf("$root/A"), "meta.json", 0) } }
        assertEquals(listOf("SEARCH", "PROPFIND"), dav.methods.toList())
    }

    @Test fun encodedUnicodePercentAndXmlPathCharactersKeepExactParentIdentity() = withServer { dav, adapter ->
        val folder = "$root/A & <tag> 100% ä"
        val encoded = "/sync-documents/A%20%26%20%3Ctag%3E%20100%25%20%C3%A4"
        dav.searchBody = file("$encoded/meta.json") + file("$encoded/nested/meta.json", modified = "")
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, listOf(folder), "meta.json", 0) }
        assertEquals(listOf("$folder/meta.json"), files.map { it.id })
    }

    private fun file(path: String, modified: String = "Tue, 02 Jan 2024 03:04:05 GMT") = """
        <d:response><d:href>/remote.php/dav/files/me$path</d:href><d:propstat><d:prop>
        <oc:fileid>42</oc:fileid><d:displayname>meta.json</d:displayname><d:resourcetype/>
        <d:getcontenttype>application/json</d:getcontenttype>
        ${if (modified.isEmpty()) "" else "<d:getlastmodified>$modified</d:getlastmodified>"}
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
    """.trimIndent()

    private inner class Dav : Dispatcher() {
        val methods = CopyOnWriteArrayList<String>()
        val queries = CopyOnWriteArrayList<String>()
        var searchBody = ""
        var folderBody = ""
        var searchCode = 207
        var folderCode = 207
        var generateFolderMeta = false
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.url.encodedPath == "/remote.php/dav/files/me/") return multistatus("""<d:response><d:href>/remote.php/dav/files/me/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getcontenttype>httpd/unix-directory</d:getcontenttype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""")
            methods.add(request.method)
            return when (request.method) {
                "SEARCH" -> { queries.add(request.body!!.utf8()); multistatus(searchBody, searchCode) }
                "PROPFIND" -> multistatus(if (generateFolderMeta) file("${request.url.encodedPath.substringAfter("/files/me").trimEnd('/')}/meta.json") else folderBody, folderCode)
                else -> MockResponse.Builder().code(500).build()
            }
        }
    }

    private fun multistatus(body: String, code: Int = 207) = MockResponse.Builder().code(code)
        .addHeader("Content-Type", "application/xml")
        .body("""<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">$body</d:multistatus>""").build()

    private fun withServer(block: (Dav, NextCloudAdapter) -> Unit) {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                val dav = Dav(); server.dispatcher = dav; server.start()
                val adapter = NextCloudAdapter(server.url("/").toString(), "me", "pw", httpsOnly = false)
                assertTrue(awaitOperation { adapter.signIn() })
                block(dav, adapter)
            }
        } finally { host.pause().stop().destroy() }
    }
}
