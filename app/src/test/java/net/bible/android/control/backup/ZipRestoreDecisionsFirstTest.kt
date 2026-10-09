package net.bible.android.control.backup

import androidx.room3.useReaderConnection
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.BookmarkDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.queryLong
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * D1 final review I1: the zip restore asks every question before it closes any database. Under Room 3 a closed
 * database never reopens, so a DAO call made while the prompt of file N+1 was up (file N already closed and
 * overwritten) threw. The prompts here make DAO calls on every database and check nothing was replaced yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ZipRestoreDecisionsFirstTest {
    private val app get() = BibleApplication.application
    private val unzipFolder get() = File(app.cacheDir, "zip-restore-test")
    private val selection = listOf(BookmarkDatabase.dbFileName, WorkspaceDatabase.dbFileName)
    private var wasReady = false

    private val labelNames get() = runBlocking {
        DatabaseContainer.instance.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name }.toSet()
    }
    private val workspaceNames get() = runBlocking {
        DatabaseContainer.instance.workspaceDb.workspaceDao().allWorkspaces().map { it.name }.toSet()
    }

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the restore closes the databases only when they are ready
        runBlocking {
            DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "backup-label"))
            DatabaseContainer.instance.workspaceDb.workspaceDao().insertWorkspace(WorkspaceEntities.Workspace(name = "backup-ws"))
        }
        // Snapshot the files as "the backup".
        DatabaseContainer.reset()
        File(unzipFolder, "db").mkdirs()
        for (name in selection) app.getDatabasePath(name).copyTo(File(unzipFolder, "db/$name"), overwrite = true)
        runBlocking {
            DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "live-label"))
            DatabaseContainer.instance.workspaceDb.workspaceDao().insertWorkspace(WorkspaceEntities.Workspace(name = "live-ws"))
        }
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        unzipFolder.deleteRecursively()
    }

    /** Every database of the live container answers a query, and the live rows are still there (nothing replaced). */
    private fun assertAllOpenAndUnreplaced() {
        for ((name, db) in DatabaseContainer.instance.dbByFilename) {
            runBlocking { db.useReaderConnection { it.queryLong("SELECT count(*) FROM sqlite_master") } }
                ?: throw AssertionError("$name did not answer")
        }
        assertTrue("bookmarks replaced before the last prompt", "live-label" in labelNames)
        assertTrue("workspaces replaced before the last prompt", "live-ws" in workspaceNames)
    }

    private fun prompts(
        lastRestoreOrImport: Boolean?,
        lastConfirm: Boolean = true,
    ) = object : BackupControl.RestorePrompts {
        var promptsOnLastFile = 0
        override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: File): Boolean? =
            if (category == SyncableDatabaseDefinition.WORKSPACES) {
                assertAllOpenAndUnreplaced(); promptsOnLastFile++; lastRestoreOrImport
            } else true
        override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition): Boolean =
            if (category == SyncableDatabaseDefinition.WORKSPACES) {
                assertAllOpenAndUnreplaced(); promptsOnLastFile++; lastConfirm
            } else true
    }

    @Test fun duringTheLastPromptsEveryDatabaseIsOpenAndBothFilesAreRestoredAfter() {
        val p = prompts(lastRestoreOrImport = true)
        runBlocking { BackupControl.restoreSelectedFiles(selection, unzipFolder, p) }
        assertEquals(2, p.promptsOnLastFile)
        assertTrue("backup-label" in labelNames)
        assertFalse("live-label" in labelNames)
        assertFalse("live-ws" in workspaceNames)
        assertTrue("backup-ws" in workspaceNames)
    }

    @Test fun cancellingTheLastPromptRestoresOnlyTheFirstFileAndLeavesTheLastUntouched() {
        val p = prompts(lastRestoreOrImport = null)
        runBlocking { BackupControl.restoreSelectedFiles(selection, unzipFolder, p) }
        assertEquals(1, p.promptsOnLastFile)
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
    }

    @Test fun answeringNoToTheLastOverwriteLeavesTheLastUntouched() {
        val p = prompts(lastRestoreOrImport = true, lastConfirm = false)
        runBlocking { BackupControl.restoreSelectedFiles(selection, unzipFolder, p) }
        assertEquals(2, p.promptsOnLastFile)
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
    }

    @Test fun importingTheLastFileMergesItAfterTheRestore() {
        val p = prompts(lastRestoreOrImport = false)
        runBlocking { BackupControl.restoreSelectedFiles(selection, unzipFolder, p) }
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
        assertTrue("backup-ws" in workspaceNames)
    }
}
