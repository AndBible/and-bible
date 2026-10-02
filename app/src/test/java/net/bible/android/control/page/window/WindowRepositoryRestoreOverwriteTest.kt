package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
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

/**
 * Fix batch 5 §1.1: a save between a database restore and the live repository's reload must not write
 * the pre-restore state over the restored database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositoryRestoreOverwriteTest {
    private lateinit var repo: WindowRepository
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        CommonUtils.settings.setBoolean("first-time", false)
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
        repo.name = "A"
        repo.saveIntoDb(false)
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        repo.clear()
    }

    /** What BackupControl does: the restored file holds "C", then the container is reset. */
    private fun restoreWorkspaceNamed(name: String) = runBlocking {
        DatabaseContainer.replacingDatabases {
            dao.updateWorkspace(dao.workspace(repo.id)!!.copy(name = name))
            DatabaseContainer.reset()
        }
    }

    @Test fun aSaveAfterTheRestoreDoesNotOverwriteIt() {
        restoreWorkspaceNamed("C")
        repo.name = "B" // pre-restore in-memory state, not yet reloaded
        repo.saveIntoDb(false)
        assertEquals("C", dao.workspace(repo.id)!!.name)
    }

    @Test fun nothingIsWrittenWhileReplacing() = runBlocking {
        DatabaseContainer.replacingDatabases {
            repo.name = "B"
            repo.saveIntoDb(false)
        }
        assertEquals("A", dao.workspace(repo.id)!!.name)
    }

    @Test fun saveWorksAgainAfterTheReload() {
        restoreWorkspaceNamed("C")
        repo.loadFromDb(repo.id)
        repo.name = "D"
        repo.saveIntoDb(false)
        assertEquals("D", dao.workspace(repo.id)!!.name)
    }

    @Test fun aThrowingReplaceLeavesReplacingFalse() {
        runCatching { runBlocking { DatabaseContainer.replacingDatabases { error("boom") } } }
        assertFalse(DatabaseContainer.replacing)
    }

    /** Review Focus 2: CloudSync categories replace in parallel; a restore waits for CloudSync inside its own replace. */
    @Test fun anInnerReplaceExitDoesNotClearReplacing() = runBlocking {
        DatabaseContainer.replacingDatabases {
            DatabaseContainer.replacingDatabases { }
            assertTrue("the outer replace is still running", DatabaseContainer.replacing)
        }
        assertFalse(DatabaseContainer.replacing)
    }
}
