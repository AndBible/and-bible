package net.bible.service.cloudsync

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.documents.DOCUMENT_META_FILENAME
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncMeta
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.DocumentType
import net.bible.service.cloudsync.ios.StubCoreStrings
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.settings.DatabaseAppSettings
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #4103: the "Sync now" preview ([DocumentSync.computeSyncPlan]) waited for a chain of sequential cloud round
 * trips. Pins the request shape of one preview over an unchanged cloud: one documents-folder lookup and no
 * meta.json download for metas already read.
 */
class DocumentSyncPreviewRequestsTest {
    private class Environment(private val dir: String) : AppEnvironment {
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

    /** A documents root folder "docs" with one folder + meta.json per document, all from one upload burst. */
    private class Adapter(private val documentsFolderName: () -> String, private val initials: List<String>) : CloudAdapter {
        val folderLookups = java.util.concurrent.CopyOnWriteArrayList<String>()
        val downloads = java.util.concurrent.CopyOnWriteArrayList<String>()
        override val signedIn = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            if (parentsIds == null) {
                folderLookups += name.orEmpty()
                return if (name == documentsFolderName()) listOf(CloudFile("docs", name, 0L, 1_000L, "")) else emptyList()
            }
            return parentsIds.filter { it.startsWith("docs/") }.map {
                CloudFile("$it/meta.json", DOCUMENT_META_FILENAME, 100L, 5_000L, it, etag = "\"$it-1\"")
            }.filter { it.createdTime >= (createdTimeAtLeast ?: 0L) }
        }
        override suspend fun getFolders(parentId: String) = initials.map { CloudFile("docs/$it", it, 0L, 5_000L, "docs") }
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
            downloads += id
            val i = id.removePrefix("docs/").substringBefore('/')
            val meta = DocumentSyncMeta(initials = i, name = i, documentType = DocumentType.SWORD, version = "1.0",
                size = 10L, language = "en", sourceDevice = "other", timestamp = 5_000L)
            sink.buffer().use { it.writeUtf8(meta.toJson()) }
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = error("folder exists")
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = error("unused")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private class Fixture(val documents: DocumentSync, val adapter: Adapter)

    private fun withFixture(block: suspend (Fixture) -> Unit) = runTest {
        val hostField = DatabaseContainer::class.java.getDeclaredField("_host").apply { isAccessible = true }
        val instanceField = DatabaseContainer::class.java.getDeclaredField("_instance").apply { isAccessible = true }
        val previousHost = hostField.get(null)
        val previousInstance = instanceField.get(null)
        val previousReady = DatabaseContainer.ready
        val previousFactory = DatabaseContainer.containerFactory
        val dir = newTestDir()
        var opened: DatabaseContainer? = null
        try {
            DatabaseContainer.detachInstanceForTest()
            DatabaseContainer.installHost(FakeDatabaseHost(dir))
            val container = DatabaseContainer.create().also { opened = it }
            DatabaseContainer.containerFactory = { container }
            DatabaseContainer.ready = true
            val env = Environment(dir)
            val settings = DatabaseAppSettings({ container.settingsStore }, { true })
            val launcher = OrderedLauncher(backgroundScope)
            val docSettings = DocumentSyncSettings({ container.documentSyncDb.documentSyncPreferencesDao() },
                { container.documentSyncDb.cloudListingStateDao() }, { container.documentSyncDb.cloudDocumentSyncTimestampDao() }, launcher, env)
            val starter = object : DocumentSyncStarter {
                override fun pushDocuments(initials: List<String>) {}
                override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {}
                override fun stop() {}
            }
            lateinit var cloud: CloudSync
            val adapter = Adapter({ cloud.syncFolderName(cloud.DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX) },
                listOf("BSB", "KJV", "NASB", "MHC", "TSK", "StrongsRealGreek"))
            lateinit var documents: DocumentSync
            val provider = DocumentSyncProvider { documents }
            cloud = CloudSync(AppDialogController(), StubCoreStrings(), env, settings, object : CloudAdapterFactory {
                override val available = listOf("test")
                override fun displayName(id: String) = id
                override fun create(id: String) = adapter
            }, SyncScheduler { error("not scheduled") }, starter, provider, docSettings)
            val watcher = BookInstallWatcher(launcher, starter, provider, docSettings, object : AddonCatalog {
                override fun clearCaches() {}
                override val readingPlans = emptyMap<String, net.bible.sharedlogic.platform.AddonReadingPlan>()
            })
            documents = DocumentSync(env, CloudSyncProvider { cloud }, starter, watcher, docSettings, { error("no backup") })
            assertEquals(true, cloud.signIn())
            block(Fixture(documents, adapter))
        } finally {
            try {
                DatabaseContainer.detachInstanceForTest()
                withContext(NonCancellable) {
                    opened?.let { container -> DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) } }
                }
            } finally {
                instanceField.set(null, previousInstance)
                hostField.set(null, previousHost)
                DatabaseContainer.containerFactory = previousFactory
                DatabaseContainer.ready = previousReady
                FileSystem.SYSTEM.deleteRecursively(dir.toPath())
            }
        }
    }

    @Test fun previewLooksUpTheDocumentsFolderOnce() = withFixture { f ->
        f.adapter.folderLookups.clear()
        f.documents.computeSyncPlan(download = true, upload = true, delete = true)
        assertEquals(1, f.adapter.folderLookups.size, "documents folder lookups: ${f.adapter.folderLookups}")
    }

    @Test fun previewAfterTheScreenScanDownloadsNoUnchangedMeta() = withFixture { f ->
        f.documents.scan()  // the Synced documents screen refreshes on open
        assertEquals(6, f.adapter.downloads.size)
        f.adapter.downloads.clear()

        f.documents.computeSyncPlan(download = true, upload = true, delete = true)

        assertEquals(emptyList(), f.adapter.downloads)
        assertEquals(6, f.documents.scanCached().count { it.cloudVersion == "1.0" }, "the cache still holds every cloud meta")
    }
}
