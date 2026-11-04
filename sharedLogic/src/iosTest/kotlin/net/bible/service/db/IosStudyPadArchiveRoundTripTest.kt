package net.bible.service.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.cloudsync.ios.StubCoreStrings
import net.bible.service.installzip.DefaultInstallCommitter
import net.bible.service.common.BackupType
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
import okio.Buffer
import okio.buffer
import okio.use
import okio.ByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import org.crosswire.common.compress.ZipReader
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Exercises the real StudyPad ZIP exporter and importer with Native SQLite databases. */
class IosStudyPadArchiveRoundTripTest {
    private var previousHost: DatabaseHost? = null
    private var previousReady = false
    private var previousFactory: (() -> DatabaseContainer)? = null
    private var previousInstance: DatabaseContainer? = null
    private val testDirs = mutableListOf<Path>()
    private lateinit var bytes: ByteArray

    @BeforeTest
    fun setUp() {
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        previousReady = DatabaseContainer.ready
        previousFactory = DatabaseContainer.containerFactory
        previousInstance = DatabaseContainer.detachInstanceForTest()
        startKoin { modules(module { single<DateTimeFormats> { FixedDateTimeFormats } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        previousHost?.let(DatabaseContainer::installHost)
        previousInstance?.let { instance ->
            DatabaseContainer.containerFactory = { instance }
            DatabaseContainer.ready = true
            DatabaseContainer.instance
        }
        previousFactory?.let { DatabaseContainer.containerFactory = it }
        DatabaseContainer.ready = previousReady
        testDirs.forEach { FileSystem.SYSTEM.deleteRecursively(it, mustExist = false) }
    }

    @Test
    fun exportedStudyPadArchiveImportsNotesLinksAndOrderedEntries() = runTest(timeout = 10.minutes) {
        val sourceDir = newTestDir()
        DatabaseContainer.installHost(FakeDatabaseHost(sourceDir))
        val source = DatabaseContainer.create()
        DatabaseContainer.containerFactory = { source }
        DatabaseContainer.ready = true
        testDirs += sourceDir.toPath()
        try {
            val sourceDao = source.bookmarkDb.bookmarkDao()
            val label = BookmarkEntities.Label(name = "Archive round trip pad")
            val bookmark = BookmarkEntities.BibleBookmark(
                kjvOrdinalStart = 4321, kjvOrdinalEnd = 4322, ordinalStart = 4321, ordinalEnd = 4322,
                v11n = KJVA, playbackSettings = null, startOffset = null, endOffset = null, customIcon = null,
            )
            sourceDao.insert(label)
            sourceDao.insert(bookmark)
            sourceDao.insert(BookmarkEntities.BibleBookmarkNotes(bookmark.id, "Bookmark note"))
            sourceDao.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.id, label.id, orderNumber = 3, indentLevel = 2))
            val entries = listOf(
                BookmarkEntities.StudyPadTextEntry(labelId = label.id, orderNumber = 7, indentLevel = 1),
                BookmarkEntities.StudyPadTextEntry(labelId = label.id, orderNumber = 2, indentLevel = 3),
            )
            entries.forEachIndexed { index, entry ->
                sourceDao.insert(entry)
                sourceDao.insert(BookmarkEntities.StudyPadTextEntryText(entry.id, "Entry ${index + 1}"))
            }
            val expectedEntries = sourceDao.studyPadTextEntriesByLabelId(label.id).map {
                Triple(it.text, it.orderNumber, it.indentLevel)
            }
            val expectedLink = assertNotNull(sourceDao.getBibleBookmarkToLabel(bookmark.id, label.id))

            val filesDir = trackTestDir()
            FileSystem.SYSTEM.createDirectories(sourceDir.toPath() / "tmp")
            val environment = TestEnvironment(sourceDir)
            lateinit var backupHost: CapturingBackupHost
            val backup = BackupControl(
                AppDialogController(), StubCoreStrings(), environment, TestSettings(), WindowControl(),
                testCloudSync(environment), TestNotifier(), testDirectories(filesDir),
            )
            backupHost = CapturingBackupHost { bytes = it }
            assertTrue(
                net.bible.service.db.exportStudyPads(
                    listOf(label), backupHost, StubCoreStrings(), environment, backup,
                    chooseDestination = { SaveOrShare.SAVE },
                ),
            )

            // Read the ZIP independently of the importer and validate its real embedded database payload.
            val archive = filesDir / "export.abdb.zip"
            FileSystem.SYSTEM.write(archive) { write(bytes) }
            val payloads = readZip(archive)
            assertEquals(
                setOf(ANDBIBLE_BACKUP_MANIFEST_FILENAME, "db/${BookmarkDatabase.dbFileName}"),
                payloads.keys,
            )
            assertEquals(
                BackupType.STUDYPAD_EXPORT,
                AndBibleBackupManifest.fromJson(payloads.getValue(ANDBIBLE_BACKUP_MANIFEST_FILENAME).utf8()).backupType,
            )

            val unpackDir = trackTestDir()
            ArchiveUtils.unzip(archive, unpackDir)
            val payloadDbFile = unpackDir / "db/${BookmarkDatabase.dbFileName}"
            val payloadDb = buildAppDatabase<BookmarkDatabase>(payloadDbFile.toString(), *bookmarkMigrations)
            try {
                val payloadDao = payloadDb.bookmarkDao()
                assertEquals(label, payloadDao.labelById(label.id))
                val payloadBookmark = assertNotNull(payloadDao.bibleBookmarkById(bookmark.id))
                assertEquals("Bookmark note", payloadBookmark.notes)
                assertEquals(expectedLink, payloadDao.getBibleBookmarkToLabel(bookmark.id, label.id))
                assertEquals(
                    expectedEntries,
                    payloadDao.studyPadTextEntriesByLabelId(label.id).map { Triple(it.text, it.orderNumber, it.indentLevel) },
                )
            } finally {
                payloadDb.close()
            }

            // Import the extracted payload through the production install committer into a separate empty database.
            val importDir = newTestDir()
            testDirs += importDir.toPath()
            DatabaseContainer.installHost(FakeDatabaseHost(importDir))
            val imported = DatabaseContainer.create()
            DatabaseContainer.detachInstanceForTest()
            DatabaseContainer.containerFactory = { imported }
            DatabaseContainer.ready = true
            try {
                DefaultInstallCommitter(
                    backup, StubCoreStrings(), environment, clearAddonCaches = {},
                ).commitStudyPad(unpackDir)
                val importedDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
                assertEquals(label, importedDao.labelById(label.id))
                assertEquals("Bookmark note", assertNotNull(importedDao.bibleBookmarkById(bookmark.id)).notes)
                assertEquals(expectedLink, importedDao.getBibleBookmarkToLabel(bookmark.id, label.id))
                assertEquals(
                    expectedEntries,
                    importedDao.studyPadTextEntriesByLabelId(label.id).map { Triple(it.text, it.orderNumber, it.indentLevel) },
                )
            } finally {
                imported.closeOwned()
            }
        } finally {
            source.closeOwned()
        }
    }

    private fun trackTestDir(): Path = newTestDir().toPath().also(testDirs::add)

    private suspend fun DatabaseContainer.closeOwned() {
        DatabaseContainer.aiLifetime.replace { closeAllOwned(it) }
        DatabaseContainer.ready = false
    }

    private fun readZip(path: Path): Map<String, ByteString> {
        val result = linkedMapOf<String, ByteString>()
        ZipReader(FileSystem.SYSTEM.source(path)).use { reader ->
            while (true) {
                val entry = reader.nextEntry() ?: break
                result[entry.name] = reader.entry.buffer().readByteString()
            }
        }
        return result
    }

    private class CapturingBackupHost(private val capture: (ByteArray) -> Unit) : BackupHost {
        override suspend fun openInput(source: PickedSource): Source = error("unused")
        override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
        override suspend fun deliver(
            file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
            subject: String?, message: String?, chooserTitle: String,
        ): DeliveryResult {
            assertEquals("Archive round trip pad$DATABASE_BACKUP_SUFFIX", fileName)
            capture(FileSystem.SYSTEM.read(file.toPath()) { readByteArray() })
            return DeliveryResult.DELIVERED
        }
        override suspend fun restoreModules() = Unit
        override suspend fun showErrorReport(message: String, cause: Throwable?) = Unit
    }

    private class TestEnvironment(private val dir: String) : AppEnvironment {
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

    private fun testDirectories(filesDir: Path) = object : AppDirectories {
        override val internalFilesDir = filesDir
        override val modulesDir = filesDir / "modules"
        override val manualInstallDir = filesDir / "jsword"
        override val manualInstallDir2 = filesDir / "sword"
        override val manualReadingPlanDir = filesDir / "readingplans"
    }

    private fun testCloudSync(env: AppEnvironment) = CloudSync(
        AppDialogController(), StubCoreStrings(), env, TestSettings(),
        object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("not used")
        },
        SyncScheduler { error("not used") },
        object : DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) = error("not used")
            override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) = error("not used")
            override fun stop() = error("not used")
        },
        DocumentSyncProvider { error("not used") },
        DocumentSyncSettings({ error("not used") }, { error("not used") }, { error("not used") }, OrderedLauncher(CoroutineScope(Job())), env),
    )

    private class TestNotifier : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) = Unit
    }

    private class TestSettings : AppSettings {
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = 0L
        override fun getInt(key: String, default: Int) = 0
        override fun getBoolean(key: String, default: Boolean) = false
        override fun getDouble(key: String, default: Double) = 0.0
        override fun getFloat(key: String, default: Float) = 0f
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private object FixedDateTimeFormats : DateTimeFormats {
        override fun shortDate(epochMs: Long) = "2026-01-01"
        override fun shortTime(epochMs: Long) = "00:00"
        override fun relativeTimeSpan(epochMs: Long, nowMs: Long) = "now"
        override fun pattern(pattern: String, epochMs: Long) = "20260101000000"
    }
}
