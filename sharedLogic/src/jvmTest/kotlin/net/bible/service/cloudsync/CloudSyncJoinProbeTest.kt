package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SyncConfiguration
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
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncJoinProbeTest {
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
        var cloudFolders: List<CloudFile> = emptyList()
        var folderKnown = true
        var writes = 0
        var listings = 0
        override val signedIn get() = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            listings++
            return cloudFolders.filter { name == null || it.name == name }
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile { writes++; error("probe must not write") }
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun getFolders(parentId: String): List<CloudFile> = emptyList()
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile { writes++; error("probe must not write") }
        override suspend fun delete(id: String) { writes++; error("probe must not write") }
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = folderKnown
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) { writes++ }
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private data class Fixture(val cloud: CloudSync, val adapter: Adapter, val db: SyncableDatabaseAccessor<*>)

    private fun withFixture(block: suspend kotlinx.coroutines.test.TestScope.(Fixture) -> Unit) = runTest {
        val hostField = DatabaseContainer::class.java.getDeclaredField("_host").apply { isAccessible = true }
        val instanceField = DatabaseContainer::class.java.getDeclaredField("_instance").apply { isAccessible = true }
        val previousHost = hostField.get(null)
        val previousInstance = instanceField.get(null)
        val previousReady = DatabaseContainer.ready
        val previousFactory = DatabaseContainer.containerFactory
        val dir = newTestDir()
        var opened: DatabaseContainer? = null
        val adapter = Adapter()
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
            block(Fixture(cloud, adapter, db))
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

    private suspend fun Fixture.join() {
        db.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder")
        db.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder")
        db.dao.setConfig(SYNC_NAMESPACE_KEY, cloud.namespace)
    }

    @Test fun probeOfUnknownCategoryWithCloudFolderNeedsDecision() = withFixture { f ->
        f.adapter.cloudFolders = listOf(CloudFile("cloud-1", f.cloud.syncFolderName(f.db.categoryName), 0L, 1L, "root"))
        val probe = f.cloud.probeJoin(f.db)
        assertEquals(JoinState.NeedsDecision("cloud-1"), probe.state)
        assertFalse(probe.dropStaleState)
    }

    @Test fun probeWithoutCloudFolderNeedsCreate() = withFixture { f ->
        assertEquals(JoinState.NeedsCreate, f.cloud.probeJoin(f.db).state)
    }

    @Test fun probeOfJoinedCategoryIsReady() = withFixture { f ->
        f.join()
        val probe = f.cloud.probeJoin(f.db)
        assertEquals(JoinState.Ready, probe.state)
        assertFalse(probe.resetNamespace)
        assertFalse(probe.dropStaleState)
    }

    @Test fun probeOfHalfJoinIsAFreshJoin() = withFixture { f ->
        f.join()
        f.db.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
        f.adapter.cloudFolders = listOf(CloudFile("cloud-1", f.cloud.syncFolderName(f.db.categoryName), 0L, 1L, "root"))
        val probe = f.cloud.probeJoin(f.db)
        assertEquals(JoinState.NeedsDecision("cloud-1"), probe.state)
        assertTrue(probe.dropStaleState)
    }

    @Test fun probeWritesNothing() = withFixture { f ->
        f.join()
        f.db.dao.setConfig(SYNC_NAMESPACE_KEY, "other")
        f.db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 777L)
        val probe = f.cloud.probeJoin(f.db)
        assertTrue(probe.resetNamespace)
        assertEquals("sync-folder", f.db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("device-folder", f.db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertEquals(777L, f.db.dao.getLong(LAST_SYNCHRONIZED_KEY))
        assertEquals("other", f.db.dao.getString(SYNC_NAMESPACE_KEY))
        assertEquals(0, f.adapter.writes)
    }

    @Test fun nonInteractiveInitializeOfUnjoinedCategoryCancelsWithoutListingTheCloud() = withFixture { f ->
        f.adapter.cloudFolders = listOf(CloudFile("cloud-1", f.cloud.syncFolderName(f.db.categoryName), 0L, 1L, "root"))
        f.db.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "stale")
        f.db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 5L)
        assertFailsWith<CancelStartedSync> { f.cloud.initializeSync(f.db, allowInteraction = false) }
        assertEquals(0, f.adapter.listings, "A background pass must not make a cloud listing call")
        assertNull(f.db.dao.getString(SYNC_FOLDER_FILE_ID_KEY), "Cleanups still run before the cancel")
        assertNull(f.db.dao.getLong(LAST_SYNCHRONIZED_KEY))
    }

    @Test fun probeOfInterruptedMergeNeedsRecovery() = withFixture { f ->
        val original = "${f.db.localDbPath}.merge-original".toPath()
        FileSystem.SYSTEM.write(original) { writeUtf8("x") }
        try {
            assertEquals(JoinState.NeedsRecovery, f.cloud.probeJoin(f.db).state)
        } finally { FileSystem.SYSTEM.delete(original, mustExist = false) }
    }
}
