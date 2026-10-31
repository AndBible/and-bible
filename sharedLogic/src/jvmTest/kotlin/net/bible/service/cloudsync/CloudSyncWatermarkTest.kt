package net.bible.service.cloudsync

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SyncConfiguration
import net.bible.android.database.SyncStatus
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
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
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncWatermarkTest {
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

    private class Adapter : CloudAdapter {
        var folders = listOf(CloudFile("remote-folder", "remote-device", 0L, 1L, "sync-folder"))
        var files = emptyList<CloudFile>()
        var patch: Path? = null
        var failDownload = false
        var corruptPatch = false
        var onList: () -> Unit = {}
        val downloads = mutableListOf<String>()
        override val signedIn = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            val snapshot = files
            onList()
            return snapshot.filter { it.createdTime >= (createdTimeAtLeast ?: 0L) }
        }
        override suspend fun getFolders(parentId: String) = folders
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
            downloads += id
            sink.buffer().use { output ->
                if (failDownload) throw IOException("Interrupted download")
                if (corruptPatch) output.writeUtf8("not a gzip patch")
                else FileSystem.SYSTEM.source(checkNotNull(patch)).use { output.writeAll(it) }
            }
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = error("already initialized")
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = error("no local changes")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private data class Fixture(
        val cloud: CloudSync,
        val adapter: Adapter,
        val db: SyncableDatabaseAccessor<*>,
        val container: DatabaseContainer,
    )

    // Reuse the real-container setup and exact global restoration from CloudSyncSignOutTest.
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
            val adapter = Adapter()
            lateinit var documents: DocumentSync
            val provider = DocumentSyncProvider { documents }
            val cloud = CloudSync(AppDialogController(), StubCoreStrings(), env, settings, object : CloudAdapterFactory {
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
            val db = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.BOOKMARKS)
            db.category.setSyncEnabled(settings, true)
            db.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder")
            db.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder")
            db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 100L)

            // Build a genuine remote patch, then leave the local database without its row or upload log.
            val label = BookmarkEntities.Label(name = "missed patch")
            container.bookmarkDb.bookmarkDao().insert(label)
            adapter.patch = checkNotNull(createPatchForDatabase(db, env))
            container.bookmarkDb.bookmarkDao().delete(label)
            db.dao.clearLog()
            adapter.files = listOf(CloudFile("pending", "1.${db.version()}.sqlite3.gz", 1L, 200L, "remote-folder"))
            block(Fixture(cloud, adapter, db, container))
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

    private suspend fun retryInterruptedSync(f: Fixture) {
        f.cloud.synchronize()
        assertEquals(emptyList(), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName())
        assertNull(f.db.dao.syncStatus("remote-device", 1L))
        f.adapter.failDownload = false
        f.adapter.corruptPatch = false

        f.cloud.synchronize()

        assertEquals(listOf("missed patch"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
        assertEquals(listOf("pending", "pending"), f.adapter.downloads)
        assertEquals(1L, f.db.dao.lastPatchNum("remote-device"))
        assertTrue(checkNotNull(f.db.dao.getLong(LAST_SYNCHRONIZED_KEY)) > 200L)
    }

    @Test fun failedApplyDoesNotHideTheMissedPatchOnRetry() = withFixture { f ->
        f.adapter.corruptPatch = true
        retryInterruptedSync(f)
    }

    @Test fun failedDownloadDoesNotHideTheMissedPatchOnRetry() = withFixture { f ->
        f.adapter.failDownload = true
        retryInterruptedSync(f)
    }

    @Test fun retryDoesNotDownloadAlreadyLoadedPatches() = withFixture { f ->
        // Patch 1 has no individual status, but the folder's loadedCount is already 2.
        f.db.dao.addStatus(SyncStatus("remote-device", 2L, 1L, 200L))
        val version = f.db.version()
        f.adapter.files = listOf(
            CloudFile("old-1", "1.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
            CloudFile("old-2", "2.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
            CloudFile("pending", "3.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
        )
        f.adapter.failDownload = true
        f.cloud.synchronize()
        f.adapter.failDownload = false
        f.cloud.synchronize()
        assertEquals(listOf("missed patch"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
        assertEquals(listOf("pending", "pending"), f.adapter.downloads)
        assertEquals(listOf(2L, 3L), f.db.dao.allSyncStatus().map { it.patchNumber }.sorted())

        // Force the same listing again: a completed patch must not be downloaded or reapplied.
        f.db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 100L)
        f.cloud.synchronize()
        assertEquals(listOf("pending", "pending"), f.adapter.downloads)
    }

    @Test fun failedListingKeepsTheMissedPatchVisibleOnRetry() = withFixture { f ->
        f.adapter.onList = { throw IOException("Interrupted listing") }
        f.cloud.synchronize()
        assertEquals(100L, f.db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        f.adapter.onList = {}
        f.cloud.synchronize()
        assertEquals(listOf("missed patch"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
        assertEquals(1L, f.db.dao.lastPatchNum("remote-device"))
    }

    @Test fun aPatchCreatedDuringListingRemainsVisibleOnTheNextSync() = withFixture { f ->
        f.adapter.files = emptyList()
        f.adapter.onList = {
            val createdAt = System.currentTimeMillis()
            f.adapter.files = listOf(CloudFile("pending", "1.1.sqlite3.gz", 1L, createdAt, "remote-folder"))
            // End the listing in a later millisecond: storing completion time would skip the new patch.
            val deadline = System.nanoTime() + 1_000_000_000L
            while (System.currentTimeMillis() <= createdAt && System.nanoTime() < deadline) Thread.sleep(1)
            check(System.currentTimeMillis() > createdAt) { "Clock did not advance during listing" }
        }
        f.cloud.synchronize()
        assertEquals(emptyList(), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName())
        f.adapter.onList = {}
        f.cloud.synchronize()
        assertEquals(listOf("missed patch"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
        assertEquals(1L, f.db.dao.lastPatchNum("remote-device"))
    }

    @Test fun incompatiblePatchKeepsWatermarkForAnAppUpdate() = withFixture { f ->
        f.adapter.files = listOf(CloudFile("future", "1.${f.db.version() + 1}.sqlite3.gz", 1L, 200L, "remote-folder"))
        f.cloud.synchronize()
        assertEquals(100L, f.db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        assertNull(f.db.dao.syncStatus("remote-device", 1L))
        assertTrue(f.adapter.downloads.isEmpty())
    }

    @Test fun emptyPatchListingAdvancesWatermark() = withFixture { f ->
        f.adapter.files = emptyList()
        f.cloud.synchronize()
        assertTrue(checkNotNull(f.db.dao.getLong(LAST_SYNCHRONIZED_KEY)) > 200L)
    }

    @Test fun emptyFolderListingAdvancesWatermark() = withFixture { f ->
        f.adapter.folders = emptyList()
        f.cloud.synchronize()
        assertTrue(checkNotNull(f.db.dao.getLong(LAST_SYNCHRONIZED_KEY)) > 200L)
    }
}
