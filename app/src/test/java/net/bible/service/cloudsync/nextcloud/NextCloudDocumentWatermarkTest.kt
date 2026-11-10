package net.bible.service.cloudsync.nextcloud

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.documents.*
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
@LooperMode(LooperMode.Mode.LEGACY)
class NextCloudDocumentWatermarkTest {
    class SignInActivity : ActivityBase()
    @After fun reset() = DatabaseResetter.resetDatabase()

    private data class Fixture(val meta: DocumentSyncMeta, val modified: Long, val invalid: Boolean = false, val brokenJson: Boolean = false)
    private fun fixture(id: String, time: Long, version: String = "1", deleted: Boolean = false) = Fixture(
        DocumentSyncMeta(id, id, DocumentType.SWORD, version, 1, "en", sourceDevice = "test", timestamp = time, deleted = deleted), time
    )
    private val a = 1_704_164_600_000L
    private val b = 1_704_164_645_000L
    private val c = 1_704_164_660_000L

    @Test fun manyDocumentListingUsesOneSearchPerPass() = withServer { dav, store ->
        repeat(96) { index -> dav.items["DOC$index"] = fixture("DOC$index", a + index * 1_000L) }
        val started = System.nanoTime()
        val saved = refresh(store, MergeResult(emptyList(), 0))
        assertEquals(96, saved.cache.size)
        assertEquals("Cold document listing SEARCH count", 1, dav.bounds.size)
        assertEquals(96, dav.gets.size)
        dav.bounds.clear(); dav.gets.clear()
        val repeated = refresh(store, saved)
        assertEquals(saved, repeated)
        assertEquals("Unchanged document listing SEARCH count", 1, dav.bounds.size)
        assertEquals(5, dav.gets.size)
        println("96-document fixture cold + unchanged elapsed ms: ${(System.nanoTime() - started) / 1_000_000}")
    }

    @Test fun olderMetadataDropsOutOfOverlapAndSamePathEditIsDiscovered() = withServer { dav, store ->
        dav.items["A"] = fixture("A", a); dav.items["B"] = fixture("B", b)
        var saved = refresh(store, MergeResult(emptyList(), 0))
        assertEquals(setOf("A", "B"), saved.cache.map { it.initials }.toSet()); assertEquals(b, saved.watermark)
        dav.gets.clear(); dav.bounds.clear()
        saved = refresh(store, saved)
        assertEquals(setOf(b - 5_000), dav.bounds.toSet())
        assertEquals(setOf("/remote.php/dav/files/me/sync-documents/B/meta.json"), dav.gets.toSet()); assertEquals("1", saved.cache.single { it.initials == "A" }.version)
        dav.items["A"] = fixture("A", c, "2"); dav.gets.clear()
        saved = refresh(store, saved)
        assertTrue(dav.gets.contains("/remote.php/dav/files/me/sync-documents/A/meta.json")); assertEquals("2", saved.cache.single { it.initials == "A" }.version); assertEquals(c, saved.watermark)
    }

    @Test fun equalTimestampMetadataRemainsEligible() = withServer { dav, store ->
        dav.items["A"] = fixture("A", b); dav.items["B"] = fixture("B", b)
        val saved = refresh(store, MergeResult(emptyList(), 0)); dav.gets.clear()
        val repeated = refresh(store, saved)
        assertEquals(setOf("/remote.php/dav/files/me/sync-documents/A/meta.json", "/remote.php/dav/files/me/sync-documents/B/meta.json"), dav.gets.toSet()); assertEquals(2, repeated.cache.size); assertEquals(b, repeated.watermark)
    }

    @Test fun failedMetaReadIsRetriedBeforeWatermarkCanPassIt() = withServer { dav, store ->
        dav.items["A"] = fixture("A", a).copy(brokenJson = true); dav.items["B"] = fixture("B", b)
        val saved = refresh(store, MergeResult(emptyList(), 0))
        assertEquals(a - 1, saved.watermark); assertEquals(listOf("B"), saved.cache.map { it.initials })
        dav.items["A"] = fixture("A", a); dav.gets.clear()
        val recovered = refresh(store, saved)
        assertTrue(dav.gets.contains("/remote.php/dav/files/me/sync-documents/A/meta.json")); assertEquals(setOf("A", "B"), recovered.cache.map { it.initials }.toSet())
    }

    @Test fun samePathTombstoneReplacesLiveMeta() = withServer { dav, store ->
        dav.items["A"] = fixture("A", a)
        val saved = refresh(store, MergeResult(emptyList(), 0))
        dav.items["A"] = fixture("A", c, deleted = true)
        val result = refresh(store, saved)
        assertTrue(result.cache.single().deleted); assertEquals(c, result.watermark)
    }

    @Test fun invalidListingDoesNotCommitSavedDocumentWatermark() = withServer { dav, store ->
        dav.items["A"] = fixture("A", a)
        var saved = refresh(store, MergeResult(emptyList(), 0))
        val before = saved
        dav.items["B"] = fixture("B", b).copy(invalid = true)
        dav.items["B/nested"] = fixture("C", c)
        dav.searchResults.clear()
        assertThrows(IOException::class.java) { saved = refresh(store, saved) }
        assertTrue(dav.searchResults.any { it.containsAll(setOf("B", "B/nested")) })
        assertEquals(before, saved)
        dav.items["B"] = fixture("B", b)
        saved = refresh(store, saved)
        assertEquals(setOf("A", "B"), saved.cache.map { it.initials }.toSet()); assertEquals(b, saved.watermark)
    }

    // Test-local save boundary mirrors refreshCache's list-then-merge order, not database persistence.
    private fun refresh(store: DocumentStore, saved: MergeResult): MergeResult {
        val listing = awaitOperation { store.listChangedDocuments(saved.watermark) }
        return mergeCloudListing(saved.cache, listing.changedMetas, listing.currentInitials, saved.watermark, listing.matchedCreatedTimes, listing.failedCreatedTimes)
    }

    private inner class Dav : Dispatcher() {
        val items = java.util.concurrent.ConcurrentHashMap<String, Fixture>()
        val gets = CopyOnWriteArrayList<String>()
        val bounds = CopyOnWriteArrayList<Long>()
        val searchResults = CopyOnWriteArrayList<Set<String>>()
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            return when (request.method) {
                "PROPFIND" -> {
                    val folders = if (path.contains("sync-documents")) items.keys.map { it.substringBefore('/') }.distinct().sorted().joinToString("") { response("$it/", null, true) } else ""
                    multistatus(response("", null, true) + folders)
                }
                "SEARCH" -> {
                    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(request.body!!.utf8().byteInputStream())
                    val bound = Instant.parse(doc.getElementsByTagNameNS("DAV:", "literal").item(0).textContent).toEpochMilli()
                    bounds.add(bound)
                    val href = doc.getElementsByTagNameNS("DAV:", "href").item(0).textContent.trimEnd('/')
                    val scope = if (href.endsWith("/sync-documents")) "" else href.substringAfter("/sync-documents/")
                    val matched = items.entries.filter { (scope.isEmpty() || it.key == scope || it.key.startsWith("$scope/")) && it.value.modified > bound }
                    searchResults.add(matched.map { it.key }.toSet())
                    multistatus(matched.joinToString("") { response("${it.key}/meta.json", it.value, false) })
                }
                "GET" -> {
                    val id = path.substringAfter("/sync-documents/").substringBeforeLast('/'); gets.add(path)
                    MockResponse.Builder().code(200).body(items[id]?.let { if (it.brokenJson) "{" else it.meta.toJson() } ?: "{}").build()
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        private fun response(path: String, fixture: Fixture?, folder: Boolean): String {
            val modified = fixture?.takeUnless { it.invalid }?.let { DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(it.modified).atZone(ZoneOffset.UTC)) }
            return """<d:response><d:href>/remote.php/dav/files/me/sync-documents/$path</d:href><d:propstat><d:prop><oc:fileid>42</oc:fileid><d:displayname>${path.trimEnd('/').substringAfterLast('/')}</d:displayname><d:getcontenttype>${if (folder) "httpd/unix-directory" else "application/json"}</d:getcontenttype><d:resourcetype>${if (folder) "<d:collection/>" else ""}</d:resourcetype>${modified?.let { "<d:getlastmodified>$it</d:getlastmodified>" } ?: ""}<nc:creation_time>1577836800</nc:creation_time></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
        }
        private fun multistatus(body: String) = MockResponse.Builder().code(207).addHeader("Content-Type", "application/xml").body("""<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">$body</d:multistatus>""").build()
    }

    private fun withServer(block: (Dav, DocumentStore) -> Unit) {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                val dav = Dav(); server.dispatcher = dav; server.start()
                val adapter = NextCloudAdapter(server.url("/").toString(), "me", "pw", httpsOnly = false)
                assertTrue(awaitOperation { adapter.signIn() })
                block(dav, DocumentStore(adapter, "/sync-documents", get(AppEnvironment::class.java)))
            }
        } finally { host.pause().stop().destroy() }
    }
}
