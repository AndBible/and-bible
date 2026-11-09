package net.bible.service.cloudsync.nextcloud

import android.os.Looper
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.documents.mergeCloudListing
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
@LooperMode(LooperMode.Mode.LEGACY)
class NextCloudListingWatermarkTest {
    class SignInActivity : ActivityBase()

    @After fun reset() { DatabaseResetter.resetDatabase() }

    @Test
    fun searchWithoutCreationTimeAdvancesDocumentWatermarkFromModificationTime() {
        assertSearchWatermark(creationProperty = "")
    }

    @Test
    fun searchWithOldCreationTimeStillUsesNewModificationTime() {
        // Library 2.20.0 reads Nextcloud's creation_time property, not DAV creationdate.
        assertSearchWatermark(creationProperty = "<nc:creation_time>1577836800</nc:creation_time>")
    }

    @Test
    fun patchSearchReturnsModificationTimesInsteadOfReversedCreationTimes() {
        val responses = fileResponse("device/2.1.sqlite3.gz", "Wed, 03 Jan 2024 03:04:05 GMT", creation = "1577836800") +
            fileResponse("device/1.1.sqlite3.gz", "Tue, 02 Jan 2024 03:04:05 GMT", creation = "1609459200")
        withListingServer(responses) { adapter ->
            val files = awaitOperation { adapter.listFiles(listOf("/sync-documents"), null, null, 0L) }
            assertEquals(
                mapOf("1.1.sqlite3.gz" to 1_704_164_645_000L, "2.1.sqlite3.gz" to 1_704_251_045_000L),
                files.associate { it.name to it.createdTime },
            )
        }
    }

    @Test
    fun incrementalListingRejectsUnknownModificationTime() {
        val failures = mutableListOf<String>()
        for (modified in listOf("", "not-a-date", "Thu, 01 Jan 1970 00:00:00 GMT")) {
            withListingServer(fileResponse("bad/meta.json", modified) + fileResponse("good/meta.json", "Tue, 02 Jan 2024 03:04:05 GMT")) { adapter ->
                try {
                    assertThrows("Invalid modification time: $modified", IOException::class.java) {
                        awaitOperation { adapter.listFiles(listOf("/sync-documents"), "meta.json", null, 0L) }
                    }
                } catch (e: AssertionError) {
                    failures.add(e.message ?: modified)
                }
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun unfilteredFoldersDoNotRequireModificationTime() {
        withListingServer(fileResponse("folder/", "", folder = true)) { adapter ->
            val files = awaitOperation { adapter.listFiles(listOf("/sync-documents"), null, null, null) }
            assertEquals("folder", files.single().name)
        }
    }

    @Test
    fun irrelevantInvalidFileDoesNotFailMetadataListing() {
        withListingServer(fileResponse("bad/other.txt", "") + fileResponse("good/meta.json", "Tue, 02 Jan 2024 03:04:05 GMT")) { adapter ->
            val files = awaitOperation { adapter.listFiles(listOf("/sync-documents"), "meta.json", "application/json", 0L) }
            assertEquals("/sync-documents/good/meta.json", files.single().id)
        }
    }

    @Test
    fun mismatchedMimeWithRequestedNameDoesNotFailMetadataListing() {
        withListingServer(
            fileResponse("bad/meta.json", "", mimeType = "text/plain") +
                fileResponse("good/meta.json", "Tue, 02 Jan 2024 03:04:05 GMT")
        ) { adapter ->
            val files = awaitOperation { adapter.listFiles(listOf("/sync-documents"), "meta.json", "application/json", 0L) }
            assertEquals("/sync-documents/good/meta.json", files.single().id)
        }
    }

    private fun fileResponse(path: String, modified: String, folder: Boolean = false, mimeType: String = "application/json", creation: String = "1577836800"): String = """
        <d:response><d:href>/remote.php/dav/files/me/sync-documents/$path</d:href>
          <d:propstat><d:prop><oc:fileid>42</oc:fileid>
            <d:displayname>${path.trimEnd('/').substringAfterLast('/')}</d:displayname>
            <d:getcontenttype>${if (folder) "httpd/unix-directory" else mimeType}</d:getcontenttype>
            <d:resourcetype>${if (folder) "<d:collection/>" else ""}</d:resourcetype>
            ${if (modified.isEmpty()) "" else "<d:getlastmodified>$modified</d:getlastmodified>"}
            <nc:creation_time>$creation</nc:creation_time>
          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        </d:response>
    """

    private fun withListingServer(responses: String, block: (NextCloudAdapter) -> Unit) {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(multistatus("""
                    <d:response><d:href>/remote.php/dav/files/me/</d:href>
                    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>
                    <d:getcontenttype>httpd/unix-directory</d:getcontenttype></d:prop>
                    <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                """))
                server.enqueue(multistatus(responses))
                val adapter = NextCloudAdapter(server.url("/").toString(), "me", "pw", httpsOnly = false)
                assertTrue(awaitOperation { adapter.signIn() })
                block(adapter)
            }
        } finally {
            host.pause().stop().destroy()
        }
    }

    private fun assertSearchWatermark(creationProperty: String) {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(multistatus("""
                    <d:response>
                      <d:href>/remote.php/dav/files/me/</d:href>
                      <d:propstat><d:prop>
                        <d:resourcetype><d:collection/></d:resourcetype>
                        <d:getcontenttype>httpd/unix-directory</d:getcontenttype>
                      </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                    </d:response>
                """))
                server.enqueue(multistatus("""
                    <d:response>
                      <d:href>/remote.php/dav/files/me/sync-documents/A/meta.json</d:href>
                      <d:propstat><d:prop>
                        <oc:fileid>42</oc:fileid>
                        <d:displayname>meta.json</d:displayname>
                        <oc:size>120</oc:size>
                        <d:getcontentlength>120</d:getcontentlength>
                        <d:getcontenttype>application/json</d:getcontenttype>
                        <d:resourcetype/>
                        <d:getlastmodified>Tue, 02 Jan 2024 03:04:05 GMT</d:getlastmodified>
                        $creationProperty
                      </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                    </d:response>
                """))
                val adapter = NextCloudAdapter(server.url("/").toString(), "me", "pw", httpsOnly = false)
                assertTrue(awaitOperation { adapter.signIn() })
                val files = awaitOperation {
                    adapter.listFiles(
                        parentsIds = listOf("/sync-documents/A"),
                        name = "meta.json",
                        mimeType = null,
                        createdTimeAtLeast = 0L,
                    )
                }
                assertEquals("PROPFIND", server.takeRequest(5, TimeUnit.SECONDS)!!.method)
                val search = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("SEARCH", search.method)
                assertTrue(search.body!!.utf8().contains("<d:getlastmodified"))
                assertEquals("/sync-documents/A/meta.json", files.single().id)
                // These are real CloudFiles from SEARCH -> GenericRemoteOperation -> library parser -> adapter.
                val merged = mergeCloudListing(
                    oldCache = emptyList(), changed = emptyList(), currentInitials = setOf("A"),
                    oldWatermark = 0L, matchedCreatedTimes = files.map { it.createdTime },
                    failedCreatedTimes = emptyList(),
                )
                assertEquals("Document watermark must use the SEARCH modification clock", 1_704_164_645_000L, merged.watermark)
            }
        } finally {
            host.pause().stop().destroy()
        }
    }

    private fun multistatus(responses: String) = MockResponse.Builder().code(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .body("""
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              $responses
            </d:multistatus>
        """.trimIndent()).build()

    private fun <T> awaitOperation(block: suspend () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        try {
            // OwnCloud posts completion on main; keep that looper running while HTTP works off-main.
            val operation = executor.submit<T> { runBlocking { block() } }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!operation.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                try {
                    operation.get(10, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    // Service the next main callback after the bounded HTTP wait.
                }
            }
            return operation.get(1, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            executor.shutdownNow()
        }
    }
}
