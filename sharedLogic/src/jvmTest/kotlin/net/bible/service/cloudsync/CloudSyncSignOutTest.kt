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
        var onUpload: suspend () -> Unit = {}
        override val signedIn get() = loggedIn
        var onSignIn: suspend () -> Boolean = { true }
        override suspend fun signIn() = onSignIn()
        override suspend fun signOut() { loggedIn = false }
        /** Like WebDavCloudAdapter.client: a call before sign-in has completed throws. */
        var listingsBeforeSignIn = 0
        var listings = 0
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            if (!loggedIn) { listingsBeforeSignIn++; throw IllegalStateException("WebDAV adapter not signed in") }
            listings++
            return emptyList()
        }
        var createdFolders = 0
        var allowCreate = false
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
            check(allowCreate) { "already initialized" }
            createdFolders++
            return CloudFile("new-folder-$createdFolders", name, 0L, 1L, parentId ?: "root")
        }
        override suspend fun get(id: String): CloudFile = error("unused")
        var onGetFolders: suspend () -> Unit = {}
        override suspend fun getFolders(parentId: String): List<CloudFile> {
            onGetFolders()
            downloaded = true
            return emptyList()
        }
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile {
            uploading.complete(Unit)
            onUpload()
            finishUpload.await()
            return CloudFile("patch", name, FileSystem.SYSTEM.metadata(file).size!!, 1L, parentId)
        }
        override suspend fun delete(id: String) = error("unused")
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private data class Fixture(
        val cloud: CloudSync,
        val adapter: Adapter,
        val db: SyncableDatabaseAccessor<*>,
        val settings: DatabaseAppSettings,
        val container: DatabaseContainer,
        val stopping: () -> Boolean,
        val documents: DocumentSync,
        val documentSettings: DocumentSyncSettings,
    )

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
            block(Fixture(cloud, adapter, db, settings, container, { stopping }, documents, docSettings))
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

    @Test fun signOutDuringUploadLetsPatchDownloadFinishBeforeClearingSyncState() = withFixture { f ->
        val (cloud, adapter, db, settings, container) = f
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
            if (f.stopping()) signOut.await()
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
    }

    /**
     * #4031: every failing step of every category raised its own modal "Error occurred in Cloud synchronization" (one
     * per category for the download, another for the upload), so one pass on the Mac stacked about ten dialogs. One
     * pass shows at most one error, with the first failure as its cause; the next non-interactive pass failing the
     * same way is recorded quietly (repeated) instead of popping the dialog again.
     */
    @Test fun failingPassShowsOneErrorAndARepeatedFailureIsQuiet() = withFixture { f ->
        val (cloud, adapter, _, settings, _) = f
        val workspaces = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.WORKSPACES)
        workspaces.category.setSyncEnabled(settings, true)
        workspaces.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder-w")
        workspaces.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder-w")
        val errors = mutableListOf<UserMessage.ErrorNotification>()
        val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
        try {
            adapter.finishUpload.complete(Unit)
            adapter.onUpload = { throw IllegalStateException("upload refused") }
            adapter.onGetFolders = { throw IllegalStateException("listing refused") }
            cloud.synchronize(allowInteraction = false)
            assertEquals(1, errors.size, "One failing pass must show one error: $errors")
            assertTrue(errors.single().cause is IllegalStateException, "The first failure travels as the cause")
            assertFalse(errors.single().repeated)

            errors.clear()
            cloud.synchronize(allowInteraction = false)
            assertEquals(1, errors.size, "$errors")
            assertTrue(errors.single().repeated, "The same failure in the next pass is recorded quietly")
            assertTrue(errors.single().background)
        } finally {
            subscription.cancel()
        }
    }

    /**
     * A lost Google sign-in (revoked refresh token) reaches CloudSync as CloudSyncUserFacingException(requiresReconnect):
     * the pass reports it once, the adapter is dropped (signedIn false), and the next automatic pass does not retry.
     */
    @Test fun lostSignInIsReportedDropsTheAdapterAndIsNotRetried() = withFixture { f ->
        val (cloud, adapter, _, _, _) = f
        val errors = mutableListOf<UserMessage.ErrorNotification>()
        val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }
        try {
            adapter.finishUpload.complete(Unit)
            adapter.onUpload = { throw CloudSyncUserFacingException("Sign in again", true, net.bible.service.cloudsync.drive.GoogleAuthRequiredException("revoked")) }
            cloud.synchronize(allowInteraction = false)
            assertEquals(1, errors.size, "$errors")
            assertEquals("Sign in again", errors.single().text)
            assertTrue(errors.single().cause is CloudSyncUserFacingException)
            assertFalse(cloud.signedIn, "The adapter is dropped until the user signs in again")

            errors.clear()
            val before = adapter.listings
            cloud.synchronize(allowInteraction = false)
            assertEquals(before, adapter.listings, "A dropped adapter must not be retried by automatic passes")
            assertTrue(errors.isEmpty(), "$errors")
        } finally {
            subscription.cancel()
        }
    }

    @Test fun missingFolderIdsAreSkippedWithoutGenericError() = withFixture { f ->
        val (cloud, adapter, db, _, _) = f
        val errors = mutableListOf<UserMessage>()
        val subscription = UserMessages.messages.subscribe { errors += it }
        try {
            adapter.finishUpload.complete(Unit)
            // Whatever clears the ids (a reset, an interrupted join) must not surface as an empty error.
            adapter.onUpload = {
                db.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
                db.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
            }
            cloud.synchronize()
            assertTrue(errors.isEmpty(), "Missing folder ids must not emit the generic error notification: $errors")
            assertFalse(adapter.downloaded, "Patch download must be skipped when the folder ids are missing")
        } finally {
            subscription.cancel()
        }
    }

    @Test fun halfFinishedJoinWithoutInteractionIsCancelledQuietlyAndResetsSyncState() = withFixture { f ->
        val (cloud, adapter, db, _, _) = f
        val errors = mutableListOf<UserMessage>()
        val subscription = UserMessages.messages.subscribe { errors += it }
        try {
            db.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
            db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 12345L)
            db.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 12345L)
            assertFailsWith<CancelStartedSync> { cloud.initializeSync(db, allowInteraction = false) }
            assertNull(db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
            assertNull(db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
            assertNull(db.dao.getLong(LAST_SYNCHRONIZED_KEY), "A stale watermark must not survive into a fresh join")
            assertNull(db.dao.getLong(LAST_PATCH_WRITTEN_KEY))
            assertEquals(0, adapter.createdFolders)
            assertTrue(errors.isEmpty(), "Cancelled start must not emit an error notification: $errors")
        } finally {
            subscription.cancel()
        }
    }

    @Test fun halfFinishedJoinIsRejoinedAfresh() = withFixture { f ->
        val (cloud, adapter, db, _, _) = f
        adapter.allowCreate = true
        adapter.finishUpload.complete(Unit)
        db.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
        db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 12345L)
        cloud.initializeSync(db)
        assertEquals("new-folder-1", db.dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertEquals("new-folder-2", db.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY))
        assertTrue(db.dao.getLong(LAST_SYNCHRONIZED_KEY) != 12345L, "Stale watermark replaced by the fresh join")
    }

    @Test fun adapterIsNotPublishedAsSignedInUntilAttemptCompletes() = withFixture { f ->
        f.adapter.loggedIn = true
        val release = CompletableDeferred<Unit>()
        f.adapter.onSignIn = { release.await(); true }
        val existing = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        assertFalse(f.cloud.signedIn)
        release.complete(Unit)
        assertEquals(true, existing.await())
        assertTrue(f.cloud.signedIn)
    }

    /**
     * #4031 (document sync before sign-in): at startup the background sync pass signs in while BookInstallWatcher
     * queues auto-upload ops for every MyBible/EPUB book it registers. The drain reached the adapter CloudSync had
     * already published but not yet signed in, so every document failed with "WebDAV adapter not signed in" and a
     * sync error. Document sync waits for the running sign-in and then uses the signed-in adapter.
     */
    @Test fun documentSyncDuringSignInWaitsForItAndUsesTheSignedInAdapter() = withFixture { f ->
        f.adapter.allowCreate = true
        f.adapter.loggedIn = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.adapter.onSignIn = { entered.complete(Unit); release.await(); f.adapter.loggedIn = true; true }
        val signIn = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        entered.await()
        // Loaded up front so the refresh reaches the adapter before the sign-in is released (UNDISPATCHED).
        f.documentSettings.ensureLoaded()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { runCatching { f.documents.refreshCache() } }
        release.complete(Unit)
        assertEquals(true, signIn.await())
        val result = refresh.await()
        assertTrue(result.isSuccess, "Document sync must not fail while the sign-in is still running: ${result.exceptionOrNull()}")
        assertEquals(0, f.adapter.listingsBeforeSignIn, "Document sync used the adapter before its sign-in completed")
        assertTrue(f.adapter.listings > 0, "Document sync must run against the signed-in adapter once the sign-in completes")
    }

    /** A sign-in that fails leaves document sync a quiet no-op, as when never signed in: no exception, no adapter use. */
    @Test fun documentSyncDuringFailingSignInIsAQuietNoOp() = withFixture { f ->
        f.adapter.loggedIn = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.adapter.onSignIn = { entered.complete(Unit); release.await(); false }
        val signIn = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        entered.await()
        // Loaded up front so the refresh reaches the adapter before the sign-in is released (UNDISPATCHED).
        f.documentSettings.ensureLoaded()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { runCatching { f.documents.refreshCache() } }
        release.complete(Unit)
        assertEquals(false, signIn.await())
        val result = refresh.await()
        assertTrue(result.isSuccess, "A failed sign-in must not surface as a document-sync error: ${result.exceptionOrNull()}")
        assertEquals(0, f.adapter.listingsBeforeSignIn)
        assertEquals(0, f.adapter.listings)
    }

    @Test fun manualSignInWaitsForExistingSuccessfulSignInWithoutAnotherAttempt() = withFixture { f ->
        f.adapter.loggedIn = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        f.adapter.onSignIn = {
            attempts++
            entered.complete(Unit)
            release.await()
            f.adapter.loggedIn = true
            true
        }
        val existing = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        entered.await()
        val manual = async(start = CoroutineStart.UNDISPATCHED) { CloudSyncPort.of(f.cloud).signIn() }
        assertFalse(manual.isCompleted)
        release.complete(Unit)
        assertEquals(true, existing.await())
        assertEquals(true, manual.await())
        assertEquals(1, attempts)
    }

    @Test fun manualSignInWaitsForExistingFailedSignInWithoutRetry() = withFixture { f ->
        f.adapter.loggedIn = false
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        f.adapter.onSignIn = { attempts++; release.await(); false }
        val existing = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        val manual = async(start = CoroutineStart.UNDISPATCHED) { CloudSyncPort.of(f.cloud).signIn() }
        assertFalse(manual.isCompleted)
        release.complete(Unit)
        assertEquals(false, existing.await())
        assertFalse(manual.await() == true)
        assertEquals(1, attempts)
    }

    @Test fun cancellingManualSignInWaitDoesNotCancelExistingSignIn() = withFixture { f ->
        f.adapter.loggedIn = false
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        f.adapter.onSignIn = { attempts++; release.await(); f.adapter.loggedIn = true; true }
        val existing = async(start = CoroutineStart.UNDISPATCHED) { f.cloud.signIn(background = true) }
        val manual = async(start = CoroutineStart.UNDISPATCHED) { CloudSyncPort.of(f.cloud).signIn() }
        assertFalse(manual.isCompleted)
        manual.cancel()
        manual.join()
        release.complete(Unit)
        assertEquals(true, existing.await())
        assertEquals(1, attempts)
    }

    private suspend fun syncFailureMessages(f: Fixture, allowInteraction: Boolean): List<UserMessage.ErrorNotification> {
        val errors = mutableListOf<UserMessage>()
        val subscription = UserMessages.messages.subscribe { errors += it }
        try {
            f.adapter.finishUpload.complete(Unit)
            f.adapter.onUpload = { throw IllegalStateException("boom") }
            f.cloud.synchronize(allowInteraction)
        } finally {
            subscription.cancel()
        }
        return errors.filterIsInstance<UserMessage.ErrorNotification>()
    }

    @Test fun failureInASyncWithoutInteractionIsABackgroundSyncError() = withFixture { f ->
        val errors = syncFailureMessages(f, allowInteraction = false)
        assertTrue(errors.isNotEmpty(), "The failing upload must be reported")
        assertTrue(errors.all { it.background && it.kind == net.bible.android.control.activity.ActivityKind.SYNC }, "$errors")
    }

    @Test fun interactiveAutomaticSyncReportsNonTransportErrorsAsBackground() = withFixture { f ->
        val errors = mutableListOf<UserMessage>()
        val subscription = UserMessages.messages.subscribe { errors += it }
        try {
            f.adapter.finishUpload.complete(Unit)
            f.adapter.onUpload = { throw IllegalStateException("boom") }
            f.cloud.synchronize(allowInteraction = true, backgroundErrors = true, reportTransportErrors = false)
            val notifications = errors.filterIsInstance<UserMessage.ErrorNotification>()
            assertTrue(notifications.isNotEmpty())
            assertTrue(notifications.all { it.background && it.kind == net.bible.android.control.activity.ActivityKind.SYNC })
        } finally { subscription.cancel() }
    }

    @Test fun failureInAnInteractiveSyncIsAForegroundSyncError() = withFixture { f ->
        val errors = syncFailureMessages(f, allowInteraction = true)
        assertTrue(errors.isNotEmpty(), "The failing upload must be reported")
        assertTrue(errors.all { !it.background && it.kind == net.bible.android.control.activity.ActivityKind.SYNC }, "$errors")
    }
}
