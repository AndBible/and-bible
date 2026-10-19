package net.bible.service.cloudsync.ios

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.*
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.platform.*
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.platform.cloud.*
import net.bible.android.database.SyncConfiguration
import okio.Path
import okio.Sink
import kotlin.test.*

class CloudSyncTask7Test {
    private class Environment : AppEnvironment {
        override val isInitialized = true
        override val deviceIdentifier = "test-device"
        override val isDiscrete = false
        override val isBeta = false
        override val isDebugMode = true
        override val applicationNameMedium = "Test"
        override val packageName = "org.andbible.ios.dev"
        override val versionNumber = 1L
        override val isMeteredNetwork = false
        override val megabytesFree = 1000L
        override val tmpDir = "/tmp"
        override val cacheDir = "/tmp"
        override val dbBackupPath = "/tmp"
        override val lastCrashTime = 0L
        override val apkPath: String? = null
        override fun databasePath(name: String) = "/tmp/$name"
        override fun deleteDatabase(name: String) = false
    }
    private class Settings : AppSettings {
        var writes = 0
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) {}
        override fun setLong(key: String, value: Long?) {}
        override fun setInt(key: String, value: Int?) {}
        override fun setBoolean(key: String, value: Boolean?) { writes++ }
        override fun setDouble(key: String, value: Double?) {}
        override fun setFloat(key: String, value: Float?) {}
    }
    private class Adapter(private val failure: Boolean = false) : CloudAdapter {
        var loggedIn = false
        val folders = mutableListOf<String>()
        override val signedIn get() = loggedIn
        override suspend fun signIn(): Boolean { if (failure) error("offline"); loggedIn = true; return true }
        override suspend fun signOut() { loggedIn = false }
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?) = emptyList<CloudFile>()
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
            folders += name; return CloudFile(name, name, 0, 0, parentId ?: "")
        }
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun getFolders(parentId: String) = emptyList<CloudFile>()
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = error("unused")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = false
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }
    private fun rig(scope: kotlinx.coroutines.CoroutineScope, adapter: Adapter, namespace: String? = null,
        dialogs: AppDialogController = AppDialogController(), settings: Settings = Settings()): CloudSync {
        val env = Environment()
        return CloudSync(dialogs, StubCoreStrings(), env, settings, object : CloudAdapterFactory {
            override val available = listOf("WEBDAV")
            override fun displayName(id: String) = id
            override fun create(id: String) = adapter
            override fun createForBackground(id: String) = adapter
        }, SyncScheduler { CompletableDeferred(true) }, object : DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) {}
            override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {}
            override fun stop() {}
        }, DocumentSyncProvider { error("no documents") },
            DocumentSyncSettings({ error("no prefs") }, { error("no listing") }, { error("no timestamps") }, OrderedLauncher(scope), env), namespace)
    }
    @Test fun backgroundFirstSyncNeverAsksOrCreatesRemoteFolders() = runTest {
        val adapter = Adapter()
        val dialogs = AppDialogController()
        val settings = Settings()
        val sync = rig(this, adapter, dialogs = dialogs, settings = settings)
        assertTrue(sync.signIn(background = true) == true)
        val dir = net.bible.service.db.newTestDir()
        val db = net.bible.shareddata.openTestDatabase<net.bible.android.database.BookmarkDatabase>(dir, "bookmarks.sqlite3")
        try {
            val accessor = SyncableDatabaseAccessor(db, { error("no replacement") }, { error("no reset") },
                "$dir/bookmarks.sqlite3", SyncableDatabaseDefinition.BOOKMARKS, deviceId = "test-device")
            assertFailsWith<CancelStartedSync> { sync.initializeSync(accessor, allowInteraction = false) }
            assertTrue(adapter.folders.isEmpty())
            assertNull(db.syncDao().getString(SYNC_FOLDER_FILE_ID_KEY))
        } finally { db.close() }
    }

    @Test fun backgroundFailureReturnsWithoutAnyDialog() = runTest {
        val attempt = async { rig(this@runTest, Adapter(true)).signIn(background = true) }
        try {
            runCurrent()
            assertTrue(attempt.isCompleted, "Background sign-in must never await a failure dialog")
            assertFalse(attempt.await() == true)
        } finally { attempt.cancel() }
    }
    @Test fun backgroundRestoresExistingCredentialsAndDocumentsUseSelectedNamespace() = runTest {
        for ((namespace, expected) in listOf(null to "org.andbible.ios.dev-sync-documents", "net.bible.android.activity.debug" to "net.bible.android.activity.debug-sync-documents")) {
            val adapter = Adapter()
            val sync = rig(this, adapter, namespace)
            assertTrue(sync.signIn(background = true) == true)
            sync.documentsSyncFolderId()
            assertEquals(listOf(expected), adapter.folders)
            assertEquals(expected.replace("documents", "bookmarks"), sync.syncFolderName("bookmarks"))
        }
    }
}
