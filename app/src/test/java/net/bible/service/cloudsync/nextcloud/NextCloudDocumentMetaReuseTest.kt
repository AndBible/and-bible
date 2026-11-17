package net.bible.service.cloudsync.nextcloud

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.documents.DocumentMetaReads
import net.bible.service.cloudsync.documents.DocumentStore
import net.bible.service.cloudsync.documents.DocumentSyncMeta
import net.bible.service.cloudsync.documents.DocumentType
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * #4132: document sync on Nextcloud downloaded meta.json files again on every sync although nothing had changed.
 * The listing re-matches every meta inside its overlap window (and all of them on a cold start), and the meta-read
 * reuse of #4103 only works when the listed file carries its version token: the Nextcloud adapter dropped the
 * `getetag` and raw modification time, so every matched meta was fetched again, one GET per document.
 *
 * Drives the real [NextCloudAdapter] (SEARCH listing, GET downloads) through a [DocumentStore] against a mock
 * server that counts the requests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
@LooperMode(LooperMode.Mode.LEGACY)
class NextCloudDocumentMetaReuseTest {
    class SignInActivity : ActivityBase()
    @After fun reset() = DatabaseResetter.resetDatabase()

    private val root = "/sync-documents"
    private val docs = (1..8).map { "DOC$it" }

    @Test fun secondListingWithNoRemoteChangesDownloadsNoMeta() = withStore { dav, store ->
        val first = awaitOperation { store.listChangedDocuments(0) }
        assertEquals(docs.toSet(), first.changedMetas.map { it.initials }.toSet())
        assertEquals(docs.size, dav.metaGets())

        dav.gets.clear()
        // Watermark 0 re-matches every meta, the worst case of the listing overlap.
        val second = awaitOperation { store.listChangedDocuments(0) }
        assertEquals(0, dav.metaGets())
        // The reused reads still reach the merge exactly as a download would have.
        assertEquals(first.changedMetas.toSet(), second.changedMetas.toSet())
        assertEquals(first.matchedCreatedTimes.sorted(), second.matchedCreatedTimes.sorted())
        assertTrue(second.failedCreatedTimes.isEmpty())
    }

    @Test fun changedMetaIsDownloadedAgainAndMerged() = withStore { dav, store ->
        awaitOperation { store.listChangedDocuments(0) }
        dav.gets.clear()

        dav.etags["DOC3"] = "\"DOC3-2\""
        dav.versions["DOC3"] = "2.0"
        val listing = awaitOperation { store.listChangedDocuments(0) }
        assertEquals(listOf("$root/DOC3/meta.json"), dav.gets.toList())
        assertEquals("2.0", listing.changedMetas.single { it.initials == "DOC3" }.version)
        assertEquals(docs.size, listing.changedMetas.size)
    }

    @Test fun listingWithoutEtagsStillDownloadsEveryMatchedMeta() = withStore { dav, store ->
        dav.reportEtags = false
        awaitOperation { store.listChangedDocuments(0) }
        dav.gets.clear()
        awaitOperation { store.listChangedDocuments(0) }
        assertEquals(docs.size, dav.metaGets())
    }

    @Test fun listedFileCarriesEtagAndServerModificationTime() = withStore { dav, _ ->
        val adapter = dav.adapter
        val files = awaitOperation { adapter.listChangedFilesInFolders(root, docs.map { "$root/$it" }, "meta.json", 0) }
        val file = files.single { it.id == "$root/DOC1/meta.json" }
        assertEquals("DOC1-1", file.etag?.trim('"'))
        assertEquals(MODIFIED_MS, file.serverModifiedTime)

        dav.reportEtags = false
        val bare = awaitOperation { adapter.listChangedFilesInFolders(root, docs.map { "$root/$it" }, "meta.json", 0) }
        assertNull(bare.single { it.id == "$root/DOC1/meta.json" }.etag)
    }

    private inner class Dav : Dispatcher() {
        lateinit var adapter: NextCloudAdapter
        val gets = CopyOnWriteArrayList<String>()
        val etags = ConcurrentHashMap(docs.associateWith { "\"$it-1\"" })
        val versions = ConcurrentHashMap(docs.associateWith { "1.0" })
        @Volatile var reportEtags = true

        fun metaGets() = gets.count { it.endsWith("/meta.json") }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            if (path == "/remote.php/dav/files/me/") return multistatus(
                """<d:response><d:href>/remote.php/dav/files/me/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getcontenttype>httpd/unix-directory</d:getcontenttype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""")
            return when (request.method) {
                "PROPFIND" -> multistatus(folder(root) + docs.joinToString("") { folder("$root/$it") })
                "SEARCH" -> multistatus(docs.joinToString("") { meta(it) })
                "GET" -> {
                    val file = path.substringAfter("/files/me")
                    gets += file
                    val initials = file.removePrefix("$root/").substringBefore('/')
                    val json = DocumentSyncMeta(initials = initials, name = initials, documentType = DocumentType.SWORD,
                        version = versions.getValue(initials), size = 10L, language = "en", sourceDevice = "other",
                        timestamp = 2_000L).toJson()
                    MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(json).build()
                }
                else -> MockResponse.Builder().code(500).build()
            }
        }

        private fun folder(path: String) = """
            <d:response><d:href>/remote.php/dav/files/me$path/</d:href><d:propstat><d:prop>
            <d:resourcetype><d:collection/></d:resourcetype><d:getcontenttype>httpd/unix-directory</d:getcontenttype>
            <d:getlastmodified>$MODIFIED</d:getlastmodified>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        """.trimIndent()

        private fun meta(initials: String) = """
            <d:response><d:href>/remote.php/dav/files/me$root/$initials/meta.json</d:href><d:propstat><d:prop>
            <oc:fileid>42</oc:fileid><d:displayname>meta.json</d:displayname><d:resourcetype/>
            <d:getcontenttype>application/json</d:getcontenttype><oc:size>120</oc:size>
            <d:getlastmodified>$MODIFIED</d:getlastmodified>
            ${if (reportEtags) "<d:getetag>${etags.getValue(initials)}</d:getetag>" else ""}
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        """.trimIndent()
    }

    private fun multistatus(body: String) = MockResponse.Builder().code(207)
        .addHeader("Content-Type", "application/xml")
        .body("""<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">$body</d:multistatus>""").build()

    private fun withStore(block: (Dav, DocumentStore) -> Unit) {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                val dav = Dav(); server.dispatcher = dav; server.start()
                val adapter = NextCloudAdapter(server.url("/").toString(), "me", "pw", httpsOnly = false)
                assertTrue(awaitOperation { adapter.signIn() })
                dav.adapter = adapter
                val env = KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java)
                block(dav, DocumentStore(adapter, root, env, DocumentMetaReads()))
            }
        } finally { host.pause().stop().destroy() }
    }

    private companion object {
        const val MODIFIED = "Tue, 02 Jan 2024 03:04:05 GMT"
        const val MODIFIED_MS = 1_704_164_645_000L
    }
}
