package net.bible.android.control.backup

import net.bible.service.db.reset
import okio.Path.Companion.toOkioPath
import androidx.room3.useReaderConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
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
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogResult
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
    private val backup by lazy { net.bible.test.testBackupControl() }
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
            DatabaseContainer.instance.bookmarkDb.syncDao().setConfig("backup-device", "must be cleared")
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
        override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: okio.Path): Boolean? =
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
        runBlocking { backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), p) }
        assertEquals(2, p.promptsOnLastFile)
        assertTrue("backup-label" in labelNames)
        assertFalse("live-label" in labelNames)
        assertFalse("live-ws" in workspaceNames)
        assertTrue("backup-ws" in workspaceNames)
    }

    @Test fun cancellingTheLastPromptRestoresOnlyTheFirstFileAndLeavesTheLastUntouched() {
        val p = prompts(lastRestoreOrImport = null)
        runBlocking { backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), p) }
        assertEquals(1, p.promptsOnLastFile)
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
    }

    @Test fun answeringNoToTheLastOverwriteLeavesTheLastUntouched() {
        val p = prompts(lastRestoreOrImport = true, lastConfirm = false)
        runBlocking { backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), p) }
        assertEquals(2, p.promptsOnLastFile)
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
    }

    @Test fun cancellationAfterReplacementStillClearsRestoredSyncConfiguration() {
        cancelWhenRestoredContainerOpens(importWorkspaces = false)
    }

    @Test fun cancellationAfterReplacementStillFinishesImports() {
        cancelWhenRestoredContainerOpens(importWorkspaces = true)
    }

    private fun cancelWhenRestoredContainerOpens(importWorkspaces: Boolean) {
        if (importWorkspaces) runBlocking {
            val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
            dao.allWorkspaces().filter { it.name == "backup-ws" }.forEach { dao.deleteWorkspace(it.id) }
        }
        val realFactory = DatabaseContainer.containerFactory
        var cancelledAfterReplace = false
        try {
            runBlocking {
                val restore = launch {
                    val job = coroutineContext[Job]!!
                    // This factory is reached only after copying the files and resetting the old container.
                    DatabaseContainer.containerFactory = {
                        realFactory().also {
                            cancelledAfterReplace = true
                            job.cancel()
                        }
                    }
                    backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), prompts(!importWorkspaces))
                }
                restore.join()
                assertTrue("the caller must still observe cancellation", restore.isCancelled)
            }
        } finally {
            DatabaseContainer.containerFactory = realFactory
        }
        assertTrue("cancellation must happen after replacement", cancelledAfterReplace)
        assertFalse("the restored file must be in place", "live-label" in labelNames)
        assertTrue("the selected workspace restore or import must finish", "backup-ws" in workspaceNames)
        assertEquals("afterRestore must clear the backup device's sync configuration", null, runBlocking {
            DatabaseContainer.instance.bookmarkDb.syncDao().getConfig("backup-device")
        })
    }

    /** #3912 review: a database reset cancelled after the file was deleted must still finish like a restore. */
    @Test fun cancellationAfterResetStillClearsSyncStateOfTheNewDatabase() {
        val dialogs = AppDialogController()
        val koin = org.koin.core.context.GlobalContext.get()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val resetter = BackupControl(
            dialogs, net.bible.android.platform.AndroidCoreStrings(context), net.bible.android.platform.AndroidAppEnvironment(context),
            net.bible.test.testAppSettings(), koin.get(), koin.get(), koin.get(),
        )
        val realFactory = DatabaseContainer.containerFactory
        var cancelledAfterReset = false
        try {
            runBlocking {
                val reset = launch {
                    val job = coroutineContext[Job]!!
                    // Reached only after the file was deleted and the old container reset; the stale row stands for
                    // what afterRestore() must clear from the new database.
                    DatabaseContainer.containerFactory = {
                        realFactory().also { container ->
                            runBlocking { container.bookmarkDb.syncDao().setConfig("stale", "must be cleared") }
                            cancelledAfterReset = true
                            job.cancel()
                        }
                    }
                    resetter.resetDatabase(BookmarkDatabase.dbFileName, SyncableDatabaseDefinition.BOOKMARKS)
                }
                while (dialogs.pending.value == null) delay(10)
                dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
                reset.join()
                assertTrue("the caller must still observe cancellation", reset.isCancelled)
            }
        } finally {
            DatabaseContainer.containerFactory = realFactory
        }
        assertTrue("cancellation must happen after the reset", cancelledAfterReset)
        assertFalse("the reset database must be in place", "live-label" in labelNames)
        assertEquals("afterRestore must clear the new database's sync configuration", null, runBlocking {
            DatabaseContainer.instance.bookmarkDb.syncDao().getConfig("stale")
        })
    }

    @Test fun cancellationInTheLastPromptLeavesAllFilesUntouched() = runBlocking {
        val restore = launch {
            val job = coroutineContext[Job]!!
            val p = object : BackupControl.RestorePrompts {
                override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: okio.Path) = true
                override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition): Boolean {
                    if (category == SyncableDatabaseDefinition.WORKSPACES) job.cancel()
                    return true
                }
            }
            backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), p)
        }
        restore.join()
        assertTrue(restore.isCancelled)
        assertTrue("bookmarks must not be replaced before commit", "live-label" in labelNames)
        assertTrue("workspaces must not be replaced before commit", "live-ws" in workspaceNames)
    }

    @Test fun importingTheLastFileMergesItAfterTheRestore() {
        val p = prompts(lastRestoreOrImport = false)
        runBlocking { backup.restoreSelectedFiles(selection, unzipFolder.toOkioPath(), p) }
        assertFalse("live-label" in labelNames)
        assertTrue("live-ws" in workspaceNames)
        assertTrue("backup-ws" in workspaceNames)
    }
}
