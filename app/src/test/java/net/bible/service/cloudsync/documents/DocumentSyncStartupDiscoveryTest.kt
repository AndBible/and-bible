package net.bible.service.cloudsync.documents

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.CachedCloudDocument
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.filetree.FileTreeCloudAdapter
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.cloud.FileTreeCloudStore
import net.bible.test.DatabaseResetter.resetDatabase
import okio.FileSystem
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/** Exercises the real planner and persisted cache against the iCloud file-tree transport, not a fake resolver. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncStartupDiscoveryTest {
    private val prefix = "Discovery4036_"
    private val books = mutableListOf<Book>()
    private val root = Files.createTempDirectory("document-discovery-4036").toFile()
    private val scope = AppCoroutineScope()
    private val launcher = OrderedLauncher(scope)
    private val db get() = DatabaseContainer.instance.documentSyncDb
    private val env get() = get<AppEnvironment>(AppEnvironment::class.java)
    private val cloud get() = get<CloudSync>(CloudSync::class.java)
    private val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
    private val originalAdapter = adapterField.get(cloud)
    private val settings by lazy {
        DocumentSyncSettings({ db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher, env)
    }
    private val pushes = mutableListOf<String>()
    private val downloads = mutableListOf<String>()
    private val starter = object : DocumentSyncStarter {
        override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {
            pushes.addAll(push.filter { it.startsWith(prefix) })
            downloads.addAll(download.filter { it.startsWith(prefix) })
        }
        override fun pushDocuments(initials: List<String>) { pushes.addAll(initials.filter { it.startsWith(prefix) }) }
        override fun stop() = Unit
    }
    private val watcher get() = get<BookInstallWatcher>(BookInstallWatcher::class.java)
    private val sync by lazy { DocumentSync(env, CloudSyncProvider { cloud }, starter, watcher, settings,
        { get<BackupControl>(BackupControl::class.java) }) }

    @After fun tearDown() = runBlocking {
        launcher.join("document-sync-settings")
        scope.coroutineContext[Job]!!.cancel()
        adapterField.set(cloud, originalAdapter)
        books.forEach { if (Books.installed().getBook(it.initials) === it) Books.installed().removeBook(it) }
        watcher.awaitPendingInstalls()
        root.deleteRecursively()
        resetDatabase()
    }

    private suspend fun connect(account: String = "account"): DocumentStore {
        val accountRoot = root.resolve(account).apply { mkdirs() }.toPath().toString().toPath()
        val adapter = FileTreeCloudAdapter(FileTreeCloudStore(FileSystem.SYSTEM, { accountRoot },
            now = { 100_000L }), "discovery-test", env.tmpDir.toPath())
        adapterField.set(cloud, adapter)
        settings.ensureLoaded()
        settings.enabled = true
        settings.autoUpload = true
        settings.wifiOnly = false
        return DocumentStore(adapter, cloud.documentsSyncFolderId()!!, env)
    }

    private fun install(name: String, version: String, type: String = "MyBible"): Book {
        val initials = prefix + name
        val metadata = SwordBookMetaData("""
            [$initials]
            Description=$initials
            ModDrv=RawText
            Lang=en
            Version=$version
        """.trimIndent().encodeToByteArray(), "$initials.conf").apply {
            setProperty(if (type == "EPUB") "AndBibleEpubModule" else "AndBibleMyBibleModule", "true")
        }
        return SwordBook(metadata, NullBackend()).also {
            books += it
            net.bible.service.sword.registerBook(it, startupDiscovery = true)
        }
    }

    private fun meta(name: String, version: String, deleted: Boolean = false) = DocumentSyncMeta(
        initials = prefix + name, name = name, documentType = DocumentType.MYBIBLE,
        version = version, size = 0, language = "en", category = "BIBLE", sourceDevice = "other",
        timestamp = 100_000L, deleted = deleted,
    )

    private suspend fun DocumentStore.publish(meta: DocumentSyncMeta) {
        val archive = root.resolve("archive.zip").apply { writeText("test payload") }.toPath().toString().toPath()
        if (meta.deleted) writeTombstone(meta) else uploadDocument(meta, archive)
    }

    @Test fun repeatedDiscoveryLeavesUnchangedBooksOutOfTheNormalSyncQueue() = runBlocking {
        val store = connect()
        install("epub", "4.0", "EPUB")
        install("mybible", "0.0")
        store.publish(meta("epub", "4.0"))
        store.publish(meta("mybible", "0.0"))
        repeat(2) {
            books.forEach { net.bible.service.sword.registerBook(it, startupDiscovery = true) }
            sync.runSync(download = true, upload = true, delete = true, manual = false)
        }
        assertEquals(emptyList<String>(), pushes)
        assertEquals(emptyList<String>(), downloads)
    }

    @Test fun refreshedListingKeepsLocalOnlyChangedAndPendingCandidatesUploadable() = runBlocking {
        val store = connect()
        install("localOnly", "0.0", "EPUB")
        install("updated", "2.0")
        install("pending", "3.0", "EPUB")
        install("failed", "2.0")
        install("unchanged", "1.0")
        for (name in listOf("updated", "pending", "failed", "unchanged")) store.publish(meta(name, "1.0"))
        store.publish(meta("cloudOnly", "1.0"))
        // Old sync timestamps are not proof the currently installed version was ever uploaded.
        settings.setSyncTimestamp(prefix + "pending", 1L)
        settings.setSyncTimestamp(prefix + "failed", 1L)
        // A stale cache incorrectly claims the cloud already has the local revisions.
        db.cloudDocumentCacheDao().replaceAll(listOf("updated", "pending", "failed").map {
            CachedCloudDocument(prefix + it, it, "MYBIBLE", "99.0", 0, "en", "BIBLE", "old", 1, null, false)
        })
        sync.runSync(download = true, upload = true, delete = true, manual = false)
        assertEquals(setOf(prefix + "localOnly", prefix + "updated", prefix + "pending", prefix + "failed"), pushes.toSet())
        assertEquals(listOf(prefix + "cloudOnly"), downloads)
        // No successful transfer has been acknowledged: the next cycle must retain the pending work.
        pushes.clear()
        downloads.clear()
        sync.runSync(download = true, upload = true, delete = true, manual = false)
        assertEquals(setOf(prefix + "localOnly", prefix + "updated", prefix + "pending", prefix + "failed"), pushes.toSet())
    }

    @Test fun accountResetDoesNotCarryThePreviousAccountsUnchangedDecision() = runBlocking {
        val first = connect("first")
        install("book", "1.0", "EPUB")
        first.publish(meta("book", "1.0"))
        sync.runSync(download = true, upload = true, delete = true, manual = false)
        assertTrue(pushes.isEmpty())
        sync.onSignOut()
        connect("second")
        sync.runSync(download = true, upload = true, delete = true, manual = false)
        assertEquals(listOf(prefix + "book"), pushes)
    }

    @Test fun discoveryDoesNotResurrectCloudTombstones() = runBlocking {
        val store = connect()
        install("removed", "2.0", "EPUB")
        store.publish(meta("removed", "1.0", deleted = true))
        sync.runSync(download = false, upload = true, delete = false, manual = false)
        assertEquals(emptyList<String>(), pushes)
    }
}
