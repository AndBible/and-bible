package net.bible.service.cloudsync

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
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
import net.bible.service.db.exec
import net.bible.service.db.useWriterConnectionMarked
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
import kotlin.test.assertFailsWith
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
        val patches = mutableMapOf<String, Path>()
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
                else FileSystem.SYSTEM.source(checkNotNull(patches[id] ?: patch)).use { output.writeAll(it) }
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
        val documentSyncCalls: java.util.concurrent.atomic.AtomicInteger,
        val env: AppEnvironment,
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
            val documentSyncCalls = java.util.concurrent.atomic.AtomicInteger()
            val provider = DocumentSyncProvider { documentSyncCalls.incrementAndGet(); documents }
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
            block(Fixture(cloud, adapter, db, container, documentSyncCalls, env))
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

    @Test fun distinctPatchesApplyInModificationOrderAndRecordTheirTimestamps() = withFixture { f ->
        val dao = f.container.bookmarkDb.bookmarkDao()
        val label = BookmarkEntities.Label(name = "first")
        f.db.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
        dao.insert(label)
        f.db.localDb.useWriterConnectionMarked { it.exec("UPDATE LogEntry SET lastUpdated = 1000") }
        f.adapter.patches["first"] = checkNotNull(createPatchForDatabase(f.db, f.env, false))
        dao.update(label.copy(name = "second"))
        f.db.localDb.useWriterConnectionMarked { it.exec("UPDATE LogEntry SET lastUpdated = 1000") }
        f.adapter.patches["second"] = checkNotNull(createPatchForDatabase(f.db, f.env, false))
        dao.delete(label)
        f.db.dao.clearLog()
        val version = f.db.version()
        f.adapter.files = listOf(
            CloudFile("second", "2.$version.sqlite3.gz", 1L, 300L, "remote-folder"),
            CloudFile("first", "1.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
        )
        f.cloud.synchronize()
        assertEquals(listOf("first"), dao.allLabelsSortedByName().map { it.name })
        assertEquals(200L, f.db.dao.syncStatus("remote-device", 1L)?.appliedDate)
        assertEquals(300L, f.db.dao.syncStatus("remote-device", 2L)?.appliedDate)
    }

    private class LateHighlight(val bookmark: BookmarkEntities.BibleBookmarkWithNotes, val label: BookmarkEntities.Label)

    /**
     * Stages, as patch "late" (uploaded at 2000), a highlight with a note and a label that the other device made at
     * 500, while this device's last download started at 1000; the local database has neither the bookmark nor a log.
     */
    private suspend fun stageLateHighlightPatch(f: Fixture): LateHighlight {
        val dao = f.container.bookmarkDb.bookmarkDao()
        val label = BookmarkEntities.Label(name = "synced label")
        dao.insert(label)
        f.db.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            org.crosswire.jsword.passage.VerseRangeFactory.fromString(
                org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV"), "Ps.33.7",
            ), null, true, null,
        ).apply { notes = "synced note" }
        dao.insert(bookmark.bookmarkEntity)
        dao.insert(bookmark.noteEntity!!)
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.id, label.id))
        f.db.localDb.useWriterConnectionMarked { it.exec("UPDATE LogEntry SET lastUpdated = 500, sourceDevice = 'remote-device'") }
        @Suppress("UNCHECKED_CAST")
        val remote = (f.db as SyncableDatabaseAccessor<net.bible.android.database.SyncableRoomDatabase>).let {
            SyncableDatabaseAccessor(it.localDb, it.dbFactory, { error("unused") }, it.localDbPath, it.category, deviceId = "remote-device")
        }
        f.adapter.patches["late"] = checkNotNull(createPatchForDatabase(remote, f.env, false))
        dao.delete(bookmark)
        f.db.dao.clearLog()
        f.db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 1000L)
        f.adapter.files = listOf(CloudFile("late", "1.${f.db.version()}.sqlite3.gz", 1L, 2000L, "remote-folder"))
        return LateHighlight(bookmark, label)
    }

    private suspend fun reportedDuring(block: suspend () -> Unit): List<net.bible.android.database.LogEntry> {
        val reported = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.database.LogEntry>()
        val subscription = DatabaseContainer.bookmarksSynced.subscribe { reported.addAll(it) }
        try { block() } finally { subscription.cancel() }
        return reported
    }

    private fun assertHighlightReported(h: LateHighlight, reported: List<net.bible.android.database.LogEntry>) {
        assertEquals(
            setOf("BibleBookmark" to h.bookmark.id, "BibleBookmarkNotes" to h.bookmark.id, "BibleBookmarkToLabel" to h.bookmark.id),
            reported.filter { it.tableName != "Label" }.map { it.tableName to it.entityId1 }.toSet(),
            "every row the patch applied must be reported: $reported",
        )
        assertEquals(h.label.id, reported.single { it.tableName == "BibleBookmarkToLabel" }.entityId2)
    }

    /**
     * #4151: the other device edited before this device's last download started, but uploaded its patch only after
     * it (or its clock is behind). The patch is applied, so the open views must hear about every row it took over;
     * the author's edit time must not be compared with this device's watermark.
     */
    @Test fun appliedPatchIsReportedAlthoughItsEditsPredateTheWatermark() = withFixture { f ->
        val h = stageLateHighlightPatch(f)
        val reported = reportedDuring { f.cloud.synchronize() }
        val dao = f.container.bookmarkDb.bookmarkDao()
        assertEquals(h.bookmark.id, dao.bibleBookmarksByIds(listOf(h.bookmark.id)).single().id, "sanity: the patch was applied")
        assertHighlightReported(h, reported)

        // Applied again (a lost status), the patch changes nothing and reports nothing.
        f.db.dao.setConfig(LAST_SYNCHRONIZED_KEY, 1000L)
        f.db.localDb.useWriterConnectionMarked { it.exec("DELETE FROM SyncStatus") }
        assertEquals(emptyList(), reportedDuring { f.cloud.synchronize() })
    }

    /** A later patch fails: the committed earlier one is reported now, since its retry re-applies it as a no-op. */
    @Test fun committedPatchIsReportedAlthoughALaterPatchFails() = withFixture { f ->
        val h = stageLateHighlightPatch(f)
        val broken = "${f.env.tmpDir}/broken.sqlite3.gz".toPath()
        FileSystem.SYSTEM.write(broken) { writeUtf8("not a gzip patch") }
        f.adapter.patches["broken"] = broken
        f.adapter.files += CloudFile("broken", "2.${f.db.version()}.sqlite3.gz", 1L, 3000L, "remote-folder")
        val reported = reportedDuring { f.cloud.synchronize() }
        assertEquals(1, f.container.bookmarkDb.bookmarkDao().bibleBookmarksByIds(listOf(h.bookmark.id)).size, "sanity: patch 1 committed")
        assertNull(f.db.dao.syncStatus("remote-device", 2L), "sanity: patch 2 failed")
        assertHighlightReported(h, reported)
    }

    /** [db]'s rows as the other device would send them: every log entry stamped [at] by "remote-device". */
    private suspend fun remotePatch(f: Fixture, db: SyncableDatabaseAccessor<*>, at: Long): Path {
        db.localDb.useWriterConnectionMarked { it.exec("UPDATE LogEntry SET lastUpdated = $at, sourceDevice = 'remote-device'") }
        db.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
        @Suppress("UNCHECKED_CAST")
        val remote = (db as SyncableDatabaseAccessor<net.bible.android.database.SyncableRoomDatabase>).let {
            SyncableDatabaseAccessor(it.localDb, it.dbFactory, { error("unused") }, it.localDbPath, it.category, deviceId = "remote-device")
        }
        return checkNotNull(createPatchForDatabase(remote, f.env, false))
    }

    /**
     * A device that missed many patches applies them in one sync: the listeners (an Android workspace switch rebuilds
     * every window) hear once per category, with the union of the rows and the last entry of a row named twice.
     */
    @Test fun patchesOfOneSyncAreReportedOnceWithTheLastEntryPerRow() = withFixture { f ->
        val dao = f.container.bookmarkDb.bookmarkDao()
        val first = BookmarkEntities.Label(name = "first")
        val other = BookmarkEntities.Label(name = "other")
        dao.insert(first)
        f.adapter.patches["p1"] = remotePatch(f, f.db, 1000L)
        dao.update(first.copy(name = "renamed"))
        dao.insert(other)
        f.adapter.patches["p2"] = remotePatch(f, f.db, 2000L)
        dao.delete(first)
        dao.delete(other)
        f.db.dao.clearLog()
        val version = f.db.version()
        f.adapter.files = listOf(
            CloudFile("p1", "1.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
            CloudFile("p2", "2.$version.sqlite3.gz", 1L, 300L, "remote-folder"),
        )
        val calls = java.util.concurrent.CopyOnWriteArrayList<List<net.bible.android.database.LogEntry>>()
        val subscription = DatabaseContainer.bookmarksSynced.subscribe { calls.add(it) }
        try { f.cloud.synchronize() } finally { subscription.cancel() }

        assertEquals(listOf("other", "renamed"), dao.allLabelsSortedByName().map { it.name }, "sanity: both patches applied")
        assertEquals(1, calls.size, "one report per category and sync: $calls")
        val labels = calls.single().filter { it.tableName == "Label" }
        assertEquals(setOf(first.id, other.id), labels.map { it.entityId1 }.toSet())
        assertEquals(2, labels.size, "one entry per row: $labels")
        assertEquals(2000L, labels.single { it.entityId1 == first.id }.lastUpdated, "the later patch's entry wins")
    }

    /**
     * Patch 1 resets the selected reading plan, patch 2 restarts it. The selection check must see the final rows,
     * after all patches of the sync, or patch 1 alone clears the selection of a plan that is still there.
     */
    @Test fun readingPlanResetAndRestartInTwoPatchesKeepsTheSelection() = withFixture { f ->
        val settings = DatabaseAppSettings({ f.container.settingsStore }, { true })
        f.db.category.setSyncEnabled(settings, false)
        val rp = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.READINGPLANS)
        rp.category.setSyncEnabled(settings, true)
        rp.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder")
        rp.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder")
        rp.dao.setConfig(LAST_SYNCHRONIZED_KEY, 100L)
        val dao = f.container.readingPlanDb.readingPlanDao()
        val original = net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan(
            "plan", net.bible.android.database.DbDate(1_000_000_000L))
        dao.updatePlan(original)
        dao.deletePlanInfo("plan")
        f.adapter.patches["reset"] = remotePatch(f, rp, 1000L)
        val restarted = original.copy(planStartDate = net.bible.android.database.DbDate(2_000_000_000L),
            id = net.bible.android.database.IdType())
        dao.updatePlan(restarted)
        f.adapter.patches["restart"] = remotePatch(f, rp, 2000L)
        // Back to this device's state before the sync: the original plan, started and selected here.
        dao.deletePlanInfo("plan")
        dao.updatePlan(original)
        rp.dao.clearLog()
        val selection = net.bible.android.control.readingplan.CurrentReadingPlan(settings)
        selection.select("plan")
        val version = rp.version()
        f.adapter.files = listOf(
            CloudFile("reset", "1.$version.sqlite3.gz", 1L, 200L, "remote-folder"),
            CloudFile("restart", "2.$version.sqlite3.gz", 1L, 300L, "remote-folder"),
        )
        f.cloud.synchronize()

        assertEquals(restarted.id, dao.getPlan("plan")?.id, "sanity: reset and restart both applied")
        assertEquals("plan", selection.code)
    }

    /** [f]'s accessor whose schema factory runs [onOpen] with the 1-based number of the patch file being opened. */
    @Suppress("UNCHECKED_CAST")
    private fun accessorOpening(f: Fixture, onOpen: (Int) -> Unit): SyncableDatabaseAccessor<net.bible.android.database.SyncableRoomDatabase> {
        val it = f.db as SyncableDatabaseAccessor<net.bible.android.database.SyncableRoomDatabase>
        var opened = 0
        return SyncableDatabaseAccessor(
            it.localDb, { name -> onOpen(++opened); it.dbFactory(name) }, { error("unused") }, it.localDbPath, it.category,
            deviceId = it.deviceId,
        )
    }

    /** Two applicable patches with the labels "one" and "two"; the local database holds neither. */
    private suspend fun twoLabelPatches(f: Fixture): Pair<Path, Path> {
        val dao = f.container.bookmarkDb.bookmarkDao()
        val one = BookmarkEntities.Label(name = "one")
        val two = BookmarkEntities.Label(name = "two")
        dao.insert(one)
        val p1 = remotePatch(f, f.db, 1000L)
        dao.insert(two)
        val p2 = remotePatch(f, f.db, 2000L)
        dao.delete(one)
        dao.delete(two)
        f.db.dao.clearLog()
        return p1 to p2
    }

    /**
     * A sync cancelled after a committed patch (a database reset or restore under way) must not notify: listeners
     * start reads in their own scopes, on databases that are about to close.
     */
    @Test fun cancelledSyncDoesNotNotifyListeners() = withFixture { f ->
        val (p1, p2) = twoLabelPatches(f)
        lateinit var job: kotlinx.coroutines.Job
        val accessor = accessorOpening(f) { n ->
            if (n == 2) { job.cancel(); throw kotlinx.coroutines.CancellationException("restore") }
        }
        var notified: List<net.bible.android.database.LogEntry>? = null
        job = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch {
            try { applyPatchesForDatabase(accessor, f.env, p1, p2, onApplied = { notified = it }) }
            catch (_: kotlinx.coroutines.CancellationException) {}
        }
        job.join()
        assertTrue(f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().isNotEmpty(), "sanity: patch 1 committed")
        assertNull(notified, "a cancelled sync must not call the listeners")
    }

    /**
     * A timed-out cleanup statement throws a CancellationException although nothing is cancelled: the committed
     * patch must still be reported.
     */
    @Test fun timeoutCancellationAfterACommitStillReports() = withFixture { f ->
        val (p1, p2) = twoLabelPatches(f)
        val accessor = accessorOpening(f) { n ->
            if (n == 2) kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.awaitCancellation() } }
        }
        var notified: List<net.bible.android.database.LogEntry>? = null
        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
            applyPatchesForDatabase(accessor, f.env, p1, p2, onApplied = { notified = it })
        }
        assertEquals(1, notified?.size, "the committed patch is reported")
    }

    /** A cancelled job whose failure is not a CancellationException (a closing database) must not notify either. */
    @Test fun cancelledJobWithAnOrdinaryFailureDoesNotNotify() = withFixture { f ->
        val (p1, p2) = twoLabelPatches(f)
        lateinit var job: kotlinx.coroutines.Job
        val accessor = accessorOpening(f) { n -> if (n == 2) { job.cancel(); throw IOException("database closed") } }
        var notified: List<net.bible.android.database.LogEntry>? = null
        job = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch {
            try { applyPatchesForDatabase(accessor, f.env, p1, p2, onApplied = { notified = it }) } catch (_: IOException) {}
        }
        job.join()
        assertTrue(f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().isNotEmpty(), "sanity: patch 1 committed")
        assertNull(notified, "a cancelled sync must not call the listeners")
    }

    /** A failing patch still reports the committed one, and a failing listener is attached, not substituted. */
    @Test fun listenerFailureIsSuppressedUnderAPatchFailure() = withFixture { f ->
        val (p1, p2) = twoLabelPatches(f)
        val accessor = accessorOpening(f) { n -> if (n == 2) throw IOException("patch 2 broken") }
        val e = assertFailsWith<IOException> {
            applyPatchesForDatabase(accessor, f.env, p1, p2, onApplied = { throw IllegalStateException("listener") })
        }
        assertEquals("patch 2 broken", e.message)
        assertEquals(listOf("listener"), e.suppressed.map { it.message })
    }

    /** Without a patch failure the listener's own error surfaces ([CloudSync] catches it); the patches stay committed. */
    @Test fun listenerFailureAfterAllPatchesSurfacesWithPatchesCommitted() = withFixture { f ->
        val (p1, p2) = twoLabelPatches(f)
        val e = assertFailsWith<IllegalStateException> {
            applyPatchesForDatabase(f.db, f.env, p1, p2, onApplied = { throw IllegalStateException("listener") })
        }
        assertEquals("listener", e.message)
        assertEquals(listOf("one", "two"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
    }

    @Test fun foregroundTransportFailureReportsOneSyncError() = withFixture { f ->
        val messages = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val subscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages.add(it) }
        try {
            f.adapter.onList = { throw IOException("connection refused") }
            f.cloud.synchronize(allowInteraction = true)
            val errors = messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>()
            assertEquals(1, errors.size)
            assertEquals(false, errors.single().background)
            assertEquals(net.bible.android.control.activity.ActivityKind.SYNC, errors.single().kind)
        } finally { subscription.cancel() }
    }

    @Test fun interactiveAutomaticRunKeepsTransportFailureSilent() = withFixture { f ->
        val messages = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val subscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages.add(it) }
        try {
            f.adapter.onList = { throw IOException("connection refused") }
            f.cloud.synchronize(allowInteraction = true, backgroundErrors = true, reportTransportErrors = false)
            assertTrue(messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>().isEmpty())
        } finally { subscription.cancel() }
    }

    @Test fun concurrentCategoryTransportFailuresProduceOneNotification() = withFixture { f ->
        val messages = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val subscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages.add(it) }
        try {
            val settings = DatabaseAppSettings({ f.container.settingsStore }, { true })
            val other = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.WORKSPACES)
            other.category.setSyncEnabled(settings, true)
            other.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-folder")
            other.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-folder")
            other.dao.setConfig(LAST_SYNCHRONIZED_KEY, 100L)
            val attempts = java.util.concurrent.atomic.AtomicInteger()
            f.adapter.onList = { attempts.incrementAndGet(); throw IOException("connection refused") }
            f.cloud.synchronize(allowInteraction = true)
            assertTrue(attempts.get() >= 2, "both enabled categories must reach the failing listing")
            assertEquals(1, messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>().size)
        } finally { subscription.cancel() }
    }

    @Test fun backgroundTransportFailureRemainsSilent() = withFixture { f ->
        val messages = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val subscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages.add(it) }
        try {
            f.adapter.onList = { throw IOException("connection refused") }
            f.cloud.synchronize(allowInteraction = false)
            assertTrue(messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>().isEmpty())
        } finally { subscription.cancel() }
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

    @Test fun normalSynchronizeRunsTheDocumentStep() = withFixture { f ->
        f.cloud.synchronize()
        assertEquals(1, f.documentSyncCalls.get())
    }

    @Test fun synchronizeWithoutDocumentsSkipsTheDocumentStepButStillSyncsDatabases() = withFixture { f ->
        f.cloud.synchronize(includeDocuments = false)
        assertEquals(0, f.documentSyncCalls.get())
        assertEquals(listOf("missed patch"), f.container.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name })
    }
}
