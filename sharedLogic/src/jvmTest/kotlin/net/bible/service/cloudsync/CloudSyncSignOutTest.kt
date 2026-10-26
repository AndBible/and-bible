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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncSignOutTest {
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
        val uploading = CompletableDeferred<Unit>()
        val finishUpload = CompletableDeferred<Unit>()
        var loggedIn = true
        var downloaded = false
        override val signedIn get() = loggedIn
        override suspend fun signIn() = true
        override suspend fun signOut() { loggedIn = false }
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?) = emptyList<CloudFile>()
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = error("already initialized")
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun getFolders(parentId: String): List<CloudFile> {
            downloaded = true
            return emptyList()
        }
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile {
            uploading.complete(Unit)
            finishUpload.await()
            return CloudFile("patch", name, FileSystem.SYSTEM.metadata(file).size!!, 1L, parentId)
        }
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    @Test fun signOutDuringUploadLetsPatchDownloadFinishBeforeClearingSyncState() = runTest {
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
            var stopping = false
            val starter = object : DocumentSyncStarter {
                override fun pushDocuments(initials: List<String>) {}
                override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {}
                override fun stop() { stopping = true }
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
            db.category.setSyncEnabled(settings, true)
            db.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder")
            db.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder")
            container.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "upload me"))
            val errors = mutableListOf<UserMessage>()
            val subscription = UserMessages.messages.subscribe { errors += it }
            val sync = async(Dispatchers.IO) { cloud.synchronize() }
            var signOut: kotlinx.coroutines.Deferred<Unit>? = null
            try {
                adapter.uploading.await()
                // UNDISPATCHED enters signOut immediately; if it is serialized it suspends on the sync lock.
                signOut = async(start = CoroutineStart.UNDISPATCHED) { cloud.signOut() }
                runCurrent()
                // On the broken implementation, finish the reset before releasing the upload to reproduce
                // the missing folder IDs at the start of downloadAndApplyNewPatches, without a timing race.
                if (stopping) signOut.await()
                adapter.finishUpload.complete(Unit)
                sync.await()
                assertTrue(errors.isEmpty(), "Sync must not emit the generic error notification: $errors")
                assertTrue(adapter.downloaded, "Patch download must run before sign-out removes the folder IDs")
                signOut.await()
                assertFalse(cloud.signedIn)
                assertFalse(db.category.isSyncEnabled(settings))
                assertNull(db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
                assertNull(db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
                assertNull(db.dao.getLong(LAST_SYNCHRONIZED_KEY))
            } finally {
                try {
                    withContext(NonCancellable) {
                        adapter.finishUpload.complete(Unit)
                        sync.cancel()
                        signOut?.cancel()
                        sync.join()
                        signOut?.join()
                    }
                } finally {
                    subscription.cancel()
                }
            }
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
}
