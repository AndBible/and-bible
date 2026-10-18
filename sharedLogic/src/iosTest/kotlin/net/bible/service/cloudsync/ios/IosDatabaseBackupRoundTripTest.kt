package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.backup.DATABASE_BACKUP_NAME
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import org.crosswire.common.compress.ZipReader
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * I2 Stream D Task 10: the database backup round trip on the iOS simulator, through the real [BackupControl]
 * (the code Android runs), real files, real Room databases (bundled SQLite driver) and the real dialog controller.
 *
 * 1. A container is opened in directory A, one label and one bookmark (with a note) are written through the DAOs,
 *    [BackupControl.zipDatabaseFiles] makes the `.abdb.zip`, and the zip is checked entry by entry. The zip is then
 *    restored through [BackupControl.restoreAppDatabaseFromSourceWithUI] into the empty directory B; a container
 *    opened there must hold the same label and bookmark.
 * 2. The database backup that the released Android build 1117 wrote ([AndroidDbBackupFixture]) is restored into the
 *    empty directory C the way the Android `RestorePathsTest.oldReleaseDbBackupRestoresItsLabelsAndBookmarks` does
 *    (bookmarks section only), and its bookmark rows are read back: the same numbers the Android test asserts.
 *
 * The restore runs with `DatabaseContainer.ready == false` (no live container to close, as on a fresh install), so
 * the post-restore sync-state cleanup (`afterRestore`) is not exercised here; the Android tests cover it.
 *
 * Compiles on Linux (`:sharedLogic:compileTestKotlinIosSimulatorArm64`), runs on the Mac simulator only.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IosDatabaseBackupRoundTripTest {
    private var previousHost: DatabaseHost? = null
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        // askIfRestoreOrImport and saveOrShare switch to Main; on the simulator the real Main is the (blocked) test thread.
        Dispatchers.setMain(mainDispatcher)
        // BackupControl stamps zip entries through KoinPlatform: the one binding it reads.
        startKoin { modules(module { single<DateTimeFormats> { FixedDateTimeFormats }; }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        previousHost?.let { DatabaseContainer.installHost(it) }
    }

    @Test
    fun aBackupMadeOnTheSimulatorRestoresIntoAnEmptyDirectory() = runTest(mainDispatcher, timeout = 10.minutes) {
        // ---- A: make the data and the zip ----
        val dirA = newTestDir()
        DatabaseContainer.installHost(FakeDatabaseHost(dirA))
        val label = BookmarkEntities.Label(name = "Round trip label")
        val bookmark = BookmarkEntities.BibleBookmark(
            kjvOrdinalStart = 4321, kjvOrdinalEnd = 4322, ordinalStart = 4321, ordinalEnd = 4322, v11n = KJVA,
            playbackSettings = null, startOffset = null, endOffset = null, customIcon = null,
        )
        val container = DatabaseContainer.create()
        try {
            // Room opens lazily: touch every database so each file exists, as in a used app.
            for (name in ALL_DB_FILENAMES) container.dbByFilename.getValue(name).useWriterConnectionMarked { }
            val dao = container.bookmarkDb.bookmarkDao()
            dao.insert(label)
            dao.insert(bookmark)
            dao.insert(BookmarkEntities.BibleBookmarkNotes(bookmark.id, "Round trip note"))
            container.sync()
        } finally {
            container.closeOwned()
        }
        for (name in ALL_DB_FILENAMES) assertTrue(FileSystem.SYSTEM.exists(dirA.toPath() / name), "$name exists in A")

        val rigA = Rig(dirA)
        val zip = assertNotNull(rigA.backup.zipDatabaseFiles(), "zipDatabaseFiles returned no zip")
        assertEquals(DATABASE_BACKUP_NAME, zip.name)

        // ---- the zip: the manifest and one db/<name> entry per database file, each a SQLite file ----
        val entries = readZip(zip)
        assertEquals(
            (listOf(ANDBIBLE_BACKUP_MANIFEST_FILENAME) + ALL_DB_FILENAMES.map { "db/$it" }).sorted(),
            entries.keys.sorted(),
        )
        assertTrue("DB_BACKUP" in entries.getValue(ANDBIBLE_BACKUP_MANIFEST_FILENAME).utf8(), "manifest names the backup type")
        for (name in ALL_DB_FILENAMES) {
            assertTrue(entries.getValue("db/$name").startsWith(SQLITE_HEADER), "db/$name is a SQLite file")
        }

        // ---- B: restore it into an empty directory ----
        val dirB = newTestDir()
        assertFalse(FileSystem.SYSTEM.exists(dirB.toPath() / BookmarkDatabase.dbFileName), "B starts empty")
        DatabaseContainer.installHost(FakeDatabaseHost(dirB))
        val rigB = Rig(dirB)
        val restored = restoreFlow(rigB, FileSystem.SYSTEM.read(zip) { readByteArray() }) { true }

        assertTrue(restored, "restore reported failure; errors: ${rigB.notices.errors} / ${rigB.host.errors}")
        assertEquals(emptyList(), rigB.notices.errors, "the restore showed an error")
        assertEquals(emptyList(), rigB.host.errors)
        for (name in ALL_DB_FILENAMES) assertTrue(FileSystem.SYSTEM.exists(dirB.toPath() / name), "$name restored into B")
        // Every syncable database asked "restore or import", and the answer "restore" was confirmed.
        assertTrue(rigB.requests.any { it is AppDialogRequest.Options }, "the restore-or-import question was asked")
        assertTrue(rigB.requests.any { it is AppDialogRequest.Confirm }, "the overwrite confirmation was asked")

        val reopened = DatabaseContainer.create()
        try {
            val dao = reopened.bookmarkDb.bookmarkDao()
            assertEquals("Round trip label", assertNotNull(dao.labelById(label.id)).name)
            val back = assertNotNull(dao.bibleBookmarkById(bookmark.id))
            assertEquals(4321, back.kjvOrdinalStart)
            assertEquals("Round trip note", back.notes)
        } finally {
            reopened.closeOwned()
        }
    }

    @Test
    fun theAndroidMadeDatabaseBackupRestoresItsBookmarks() = runTest(mainDispatcher, timeout = 10.minutes) {
        assertEquals(
            "c0acf1ef083780ee13e56bae4d02311e4efadc8011cb0a1e505c1f089b5efed9",
            AndroidDbBackupFixture.bytes.sha256().hex(),
            "the embedded fixture differs from old-release/db-backup.abdb.zip",
        )
        val dirC = newTestDir()
        DatabaseContainer.installHost(FakeDatabaseHost(dirC))
        val rig = Rig(dirC)
        // Like the Android test: only the bookmarks section is selected (the stub's databaseTitle is the file name).
        val restored = restoreFlow(rig, AndroidDbBackupFixture.bytes.toByteArray()) { it.label == BookmarkDatabase.dbFileName }

        assertTrue(restored, "restore reported failure; errors: ${rig.notices.errors} / ${rig.host.errors}")
        assertEquals(emptyList(), rig.notices.errors, "the restore showed an error")
        assertTrue(FileSystem.SYSTEM.exists(dirC.toPath() / BookmarkDatabase.dbFileName))

        val container = DatabaseContainer.create()
        try {
            val dao = container.bookmarkDb.bookmarkDao()
            val labels = dao.allLabelsSortedByName().map { it.name }
            assertTrue("G2Fixture" in labels, labels.toString())
            assertTrue("Salvation" in labels, labels.toString())
            val bookmarks = dao.allBookmarks()
            assertEquals(14, bookmarks.size)
            assertEquals("G2 fixture bookmark note", bookmarks.single { it.kjvOrdinalStart == 33099 }.notes)
            assertTrue(bookmarks.any { it.kjvOrdinalStart == 33100 })
        } finally {
            container.closeOwned()
        }
    }

    // ---- the restore flow ----

    /**
     * Runs [BackupControl.restoreAppDatabaseFromSourceWithUI] over [zipBytes] and answers its dialogs: the section list
     * gets the options accepted by [selectSection], "restore" is chosen for every restore-or-import question and every
     * confirmation is Ok. Every request is recorded in [Rig.requests].
     */
    private suspend fun kotlinx.coroutines.test.TestScope.restoreFlow(
        rig: Rig,
        zipBytes: ByteArray,
        selectSection: (net.bible.sharedcore.settings.SettingsItem.Choice) -> Boolean,
    ): Boolean {
        val job: Deferred<Boolean> = async {
            rig.backup.restoreAppDatabaseFromSourceWithUI(rig.host.over(zipBytes), PickedSource("test://backup.abdb.zip", "backup.abdb.zip", null))
        }
        while (!job.isCompleted) {
            advanceUntilIdle() // the Main-dispatched parts of the flow
            rig.dialogs.pending.value?.let { shown ->
                rig.requests += shown.request
                val answer: AppDialogResult = when (val request = shown.request) {
                    is AppDialogRequest.MultiChoice ->
                        AppDialogResult.SelectedMany(request.options.filter(selectSection).map { it.value })
                    // "restore" is the id askIfRestoreOrImport gives its Restore choice.
                    is AppDialogRequest.Options -> AppDialogResult.Selected("restore")
                    else -> AppDialogResult.Ok
                }
                rig.dialogs.respond(shown.id, answer)
            }
            withContext(Dispatchers.Default) { delay(20) } // real time: the restore works on Dispatchers.IO
        }
        return job.await()
    }

    private fun readZip(zip: Path): Map<String, okio.ByteString> {
        val out = LinkedHashMap<String, okio.ByteString>()
        ZipReader(FileSystem.SYSTEM.source(zip)).use { reader ->
            while (true) {
                val entry = reader.nextEntry() ?: break
                out[entry.name] = reader.entry.buffer().readByteString()
            }
        }
        return out
    }

    /**
     * Closes a container made by `create()`. `create()` leaves `DatabaseContainer.ready` true (its pre-upgrade backup step
     * restores the flag with `withReadyCleared`), but these tests restore "into a fresh install" with nothing live, so it is
     * cleared again here: with it set, the restore would ask for the live `instance`, which the fake host cannot open.
     */
    private suspend fun DatabaseContainer.closeOwned() {
        DatabaseContainer.aiLifetime.replace { closeAllOwned(it) }
        DatabaseContainer.ready = false
    }

    // ---- BackupControl and its collaborators ----

    /** A [BackupControl] over [dir] (databases) and a second directory (app files), with recording doubles. */
    private class Rig(dir: String) {
        val dialogs = AppDialogController()
        val requests = mutableListOf<AppDialogRequest>()
        val notices = RecordingNotifier()
        val host = ByteBackupHost()
        private val filesDir = newTestDir().toPath()
        private val dirs = object : AppDirectories {
            override val internalFilesDir = filesDir
            override val modulesDir = filesDir / "modules"
            override val manualInstallDir = filesDir / "jsword"
            override val manualInstallDir2 = filesDir / "sword"
            override val manualReadingPlanDir = filesDir / "readingplans"
        }
        private val env = DirEnvironment(dir)
        private val settings = MapSettings()
        private val cloudSync = CloudSync(
            dialogs, StubCoreStrings(), env, settings,
            object : CloudAdapterFactory {
                override val available = listOf("NEXT_CLOUD")
                override fun displayName(id: String) = id
                override fun create(id: String): CloudAdapter = error("no sign-in in a backup test")
            },
            SyncScheduler { error("no sync in a backup test") },
            object : DocumentSyncStarter {
                override fun pushDocuments(initials: List<String>) = error("unused")
                override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) = error("unused")
                override fun stop() = error("unused")
            },
            DocumentSyncProvider { error("no document sync in a backup test") },
            DocumentSyncSettings({ error("unused") }, { error("unused") }, { error("unused") }, OrderedLauncher(CoroutineScope(Job())), env),
        )
        val backup = BackupControl(dialogs, StubCoreStrings(), env, settings, WindowControl(), cloudSync, notices, dirs)
    }

    private class RecordingNotifier : UserNotifier {
        val errors = mutableListOf<String>()
        override fun showInfo(message: String, long: Boolean) {}
        override fun showError(message: String, cause: Throwable?) { errors += message }
    }

    /** Serves a fresh [Buffer] over the given bytes on every [openInput], as a picked file would. */
    private class ByteBackupHost : BackupHost {
        val errors = mutableListOf<String>()
        private var bytes = ByteArray(0)
        fun over(zip: ByteArray) = apply { bytes = zip }
        override suspend fun openInput(source: PickedSource): Source = Buffer().write(bytes)
        override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
        override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
                                     subject: String?, message: String?, chooserTitle: String): DeliveryResult = error("unused")
        override suspend fun restoreModules() = Unit
        override suspend fun showErrorReport(message: String, cause: Throwable?) { errors += message }
    }

    private class DirEnvironment(private val dir: String) : AppEnvironment {
        override val isInitialized = true
        override val deviceIdentifier = "test-device"
        override val isDiscrete = false
        override val isBeta = false
        override val isDebugMode = false
        override val applicationNameMedium = "AndBible"
        override val packageName = "net.bible.test"
        override val versionNumber = 1L
        override val isMeteredNetwork = false
        override val megabytesFree = 1000L
        override val tmpDir = "$dir/tmp"
        override val cacheDir = "$dir/cache"
        override val dbBackupPath = "$dir/backups"
        override fun databasePath(name: String) = "$dir/$name"
        override fun deleteDatabase(name: String) = true
        override val lastCrashTime = 0L
        override val apkPath: String? = null
    }

    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = m[key] as String? ?: default
        override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
        override fun getInt(key: String, default: Int) = m[key] as Int? ?: default
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun getDouble(key: String, default: Double) = m[key] as Double? ?: default
        override fun getFloat(key: String, default: Float) = m[key] as Float? ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    private object FixedDateTimeFormats : DateTimeFormats {
        override fun shortDate(epochMs: Long) = "2026-01-01"
        override fun shortTime(epochMs: Long) = "00:00"
        override fun relativeTimeSpan(epochMs: Long, nowMs: Long) = "now"
        override fun pattern(pattern: String, epochMs: Long) = "20260101000000"
    }

    private companion object {
        val SQLITE_HEADER = okio.ByteString.of(*"SQLite format 3\u0000".encodeToByteArray())
    }
}
