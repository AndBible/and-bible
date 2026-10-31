package net.bible.android.control.backup

import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.canonical.CANONICAL_IDS_VERSION_KEY
import net.bible.service.cloudsync.canonical.canonicalMyDocumentId
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.reset
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #3906: a backup restore clears the sync configuration of the restored categories, the canonical-id version included.
 * [BackupControl.restoreSelectedFiles] heals the restored database again right after, so the window without a version
 * (sync is off then) does not stay open until the next app start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupRestoreCanonicalHealTest {
    private val backup by lazy { net.bible.test.testBackupControl() }
    private val app get() = BibleApplication.application
    private val unzipFolder get() = File(app.cacheDir, "zip-restore-canonical-test")
    private var wasReady = false
    private val docs get() = SyncableDatabaseDefinition.MYDOCUMENTS.accessor

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true
        runBlocking {
            // A backup made by an older app version: a random id, no version key.
            DatabaseContainer.instance.myDocumentDb.myDocumentDao().insert(MyDocument(name = "AI", initials = "AIDocuments"))
        }
        DatabaseContainer.reset()
        File(unzipFolder, "db").mkdirs()
        app.getDatabasePath(MyDocumentDatabase.dbFileName).copyTo(File(unzipFolder, "db/${MyDocumentDatabase.dbFileName}"), overwrite = true)
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        unzipFolder.deleteRecursively()
    }

    @Test fun restoredCanonicalCategoryIsHealedAndVersioned() {
        val prompts = object : BackupControl.RestorePrompts {
            override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: okio.Path) = true
            override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition) = true
        }

        runBlocking { backup.restoreSelectedFiles(listOf(MyDocumentDatabase.dbFileName), unzipFolder.toOkioPath(), prompts) }

        runBlocking {
            val book = DatabaseContainer.instance.myDocumentDb.myDocumentDao().allDocuments().single()
            assertEquals(canonicalMyDocumentId("AIDocuments"), book.id)
            assertEquals("the version is set again after afterRestore cleared it", 1L, docs.dao.getLong(CANONICAL_IDS_VERSION_KEY))
        }
    }
}
