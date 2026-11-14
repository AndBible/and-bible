package net.bible.service.cloudsync.documents

import kotlinx.coroutines.runBlocking
import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.db.newTestDir
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #4103: the listing overlap (watermark margin) re-matches every meta.json uploaded in the same burst as the
 * newest one, so each refresh re-downloaded all of them although nothing changed. A meta file whose version
 * token (etag) is unchanged since this store's owner last read it is reused instead of downloaded again.
 */
class DocumentStoreMetaReuseTest {
    private val dir = newTestDir()

    @AfterTest fun cleanUp() { FileSystem.SYSTEM.deleteRecursively(dir.toPath()) }

    private class Env(private val dir: String) : AppEnvironment {
        override val isInitialized = true
        override val deviceIdentifier = "test-device"
        override val isDiscrete = false
        override val isBeta = false
        override val isDebugMode = true
        override val applicationNameMedium = "Test"
        override val packageName = "net.bible.android.activity"
        override val versionNumber = 1L
        override val isMeteredNetwork = false
        override val megabytesFree = 1000L
        override val tmpDir = dir
        override val cacheDir = dir
        override val dbBackupPath = dir
        override val lastCrashTime = 0L
        override val apkPath: String? = null
        override fun databasePath(name: String) = "$dir/$name"
        override fun deleteDatabase(name: String) = false
    }

    /** One folder per document under "root", each holding a meta.json with [etags] as its version token. */
    private class Adapter(initials: List<String>) : CloudAdapter {
        val etags = initials.associateWith<String, String?> { "\"$it-1\"" }.toMutableMap()
        val versions = initials.associateWith { "1.0" }.toMutableMap()
        val sizes = initials.associateWith { 120L }.toMutableMap()
        /** Server-clock modification times as listed; the device-clock createdTime equals them (no offset). */
        val mtimes = initials.associateWith<String, Long?> { META_MTIME }.toMutableMap()
        val downloads = java.util.concurrent.CopyOnWriteArrayList<String>()
        /** Runs inside each download, after the listing: a concurrent sign-out or reset lands here. */
        var duringDownload: () -> Unit = {}
        override val signedIn = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun getFolders(parentId: String) =
            etags.keys.map { CloudFile("root/$it", it, 0L, 1_000L, "root") }
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?) =
            parentsIds.orEmpty().map { folder ->
                val initials = folder.substringAfter("root/")
                // Every meta sits inside the listing overlap: it is matched again on every listing.
                CloudFile("$folder/meta.json", DOCUMENT_META_FILENAME, sizes.getValue(initials), mtimes[initials] ?: META_MTIME, folder,
                    etag = etags[initials], serverModifiedTime = mtimes[initials])
            }
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
            downloads += id
            duringDownload()
            val initials = id.substringAfter("root/").substringBefore('/')
            val meta = DocumentSyncMeta(initials = initials, name = initials, documentType = DocumentType.SWORD,
                version = versions.getValue(initials), size = 10L, language = "en", sourceDevice = "other", timestamp = 2_000L)
            sink.buffer().use { it.writeUtf8(meta.toJson()) }
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = error("unused")
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = error("unused")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private val docs = listOf("BSB", "KJV", "NASB", "MHC", "TSK", "StrongsRealGreek")

    /** The device clock, long after every meta was written: reads are settled and may be reused. */
    private var now = META_MTIME + 60_000L
    private fun reads() = DocumentMetaReads { now }

    @Test fun unchangedMetasAreNotDownloadedAgain() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        val first = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(watermark = 1_000L)
        assertEquals(6, adapter.downloads.size)

        // A later store (each refresh builds a new one) over the same unchanged cloud.
        val second = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(watermark = 1_000L)

        assertEquals(6, adapter.downloads.size, "an unchanged meta.json must not be downloaded again")
        assertEquals(first.changedMetas.sortedBy { it.initials }, second.changedMetas.sortedBy { it.initials })
        assertEquals(first.matchedCreatedTimes, second.matchedCreatedTimes)
        assertEquals(emptyList(), second.failedCreatedTimes)
    }

    @Test fun aChangedMetaIsDownloadedAgain() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.downloads.clear()
        adapter.etags["KJV"] = "\"KJV-2\""; adapter.versions["KJV"] = "2.0"

        val listing = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(listOf("root/KJV/meta.json"), adapter.downloads)
        assertEquals("2.0", listing.changedMetas.single { it.initials == "KJV" }.version)
    }

    @Test fun aMetaWithoutVersionTokenIsAlwaysDownloaded() = runBlocking {
        val adapter = Adapter(docs)
        adapter.etags.keys.forEach { adapter.etags[it] = null }
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        assertEquals(12, adapter.downloads.size)
    }

    @Test fun clearedReadsDownloadEverythingAgain() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        reads.clear()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        assertEquals(12, adapter.downloads.size)
    }

    @Test fun aMetaWithTheSameEtagButAnotherSizeIsDownloadedAgain() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.downloads.clear()
        adapter.sizes["KJV"] = 121L; adapter.versions["KJV"] = "2.0"

        val listing = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(listOf("root/KJV/meta.json"), adapter.downloads)
        assertEquals("2.0", listing.changedMetas.single { it.initials == "KJV" }.version)
    }

    /** A server whose etag is a hash of inode, size and mtime can repeat the etag of a rewritten file; its mtime moves. */
    @Test fun aMetaWithTheSameEtagAndSizeButAnotherServerTimeIsDownloadedAgain() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.downloads.clear()
        adapter.mtimes["KJV"] = META_MTIME + 1_000L; adapter.versions["KJV"] = "2.0"

        val listing = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(listOf("root/KJV/meta.json"), adapter.downloads)
        assertEquals("2.0", listing.changedMetas.single { it.initials == "KJV" }.version)
    }

    /**
     * A meta read in the same second it was written may be rewritten within that second with the same length:
     * etag (mtime in seconds + size + a reused inode), size and mtime all repeat. Such a read is never reused.
     */
    @Test fun aMetaReadRightAfterItsServerTimeIsNotReused() = runBlocking {
        val adapter = Adapter(docs)
        now = META_MTIME + 500L
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.downloads.clear()
        // Device A rewrites KJV's meta in the same second, with the same length: nothing in the listing changes.
        adapter.versions["KJV"] = "2.0"
        now = META_MTIME + 60_000L

        val listing = DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(6, adapter.downloads.size, "reads taken within the settle window must be downloaded again")
        assertEquals("2.0", listing.changedMetas.single { it.initials == "KJV" }.version)

        // Read again long after the write: settled, so the next listing reuses it.
        adapter.downloads.clear()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        assertEquals(emptyList(), adapter.downloads)
    }

    @Test fun aMetaWithoutServerTimeIsAlwaysDownloaded() = runBlocking {
        val adapter = Adapter(docs)
        adapter.mtimes.keys.forEach { adapter.mtimes[it] = null }
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        assertEquals(12, adapter.downloads.size)
    }

    /** WebDAV ids are paths below the server base: identical on every server, account and backend (review of #4103). */
    @Test fun anotherAdapterNeverReusesAnEntry() = runBlocking {
        val old = Adapter(docs)
        val reads = reads()
        DocumentStore(old, "root", Env(dir), reads).listChangedDocuments(1_000L)
        // The same ids, etags, sizes and times, on another server (or backend, or account): different content.
        val other = Adapter(docs).apply { versions.keys.forEach { versions[it] = "9.0" } }

        val listing = DocumentStore(other, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(6, other.downloads.size)
        assertEquals(setOf("9.0"), listing.changedMetas.map { it.version }.toSet())
        // Back on the first adapter, its earlier reads are gone too: the cache follows one cloud at a time.
        old.downloads.clear()
        DocumentStore(old, "root", Env(dir), reads).listChangedDocuments(1_000L)
        assertEquals(6, old.downloads.size)
    }

    @Test fun anotherRootFolderNeverReusesAnEntry() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.downloads.clear()

        DocumentStore(adapter, "other-root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(6, adapter.downloads.size)
    }

    /** A sign-out or reset while a listing downloads must not let that listing put the old cloud's reads back. */
    @Test fun aReadFinishingAfterAClearIsNotRemembered() = runBlocking {
        val adapter = Adapter(docs)
        val reads = reads()
        adapter.duringDownload = { reads.clear() }
        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)
        adapter.duringDownload = {}
        adapter.downloads.clear()

        DocumentStore(adapter, "root", Env(dir), reads).listChangedDocuments(1_000L)

        assertEquals(6, adapter.downloads.size)
    }

    private companion object { const val META_MTIME = 2_000L }
}
