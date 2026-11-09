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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
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
        } finally {
            executor.shutdownNow()
        }
    }
}
