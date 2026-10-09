package net.bible.android.control.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F115 (fix batch 6 §1.3): every restore exit -- success, failure, cancellation -- releases the save
 * freeze that [DatabaseContainer.replacingDatabases] set, and a failed replace leaves no closed Room
 * instance behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RestoreReleaseTest {
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

    @Test fun aReplaceThatThrowsLeavesNoClosedInstanceBehind() {
        runCatching { runBlocking { DatabaseContainer.replacingDatabases {
            DatabaseContainer.instance.workspaceDb.close() // what the zip path does before its copy
            error("copy failed")
        } } }
        // The next access must reopen, not hit the closed Room instance.
        assertNotNull(runBlocking { DatabaseContainer.instance.workspaceDb.workspaceDao().workspace(repo.id) })
    }

    /** The old monolithic restore: reset, delete, reopen (migrate) -- and the migration throws. */
    @Test fun aReplaceWhoseReopenThrowsStillReleasesTheFreezeAndMovesTheEpoch() {
        val epoch = DatabaseContainer.replaceEpoch
        val realFactory = DatabaseContainer.containerFactory
        try {
            runCatching { runBlocking { DatabaseContainer.replacingDatabases {
                DatabaseContainer.reset()
                DatabaseContainer.containerFactory = { error("migration failed") }
                DatabaseContainer.instance
            } } }
        } finally { DatabaseContainer.containerFactory = realFactory }
        assertFalse("replacing must be released", DatabaseContainer.replacing)
        assertEquals(epoch + 1, DatabaseContainer.replaceEpoch)
    }

    private fun countReloads(block: () -> Unit): Int {
        var n = 0
        val streamSub = DatabaseContainer.databaseRestored.subscribe { n++ }
        try { block() } finally { streamSub.cancel() }
        return n
    }

    @Test fun aReplaceThatThrowsStillPostsTheReload() {
        val n = countReloads {
            runCatching { runBlocking { BackupControl.reloadingAfterReplace { DatabaseContainer.replacingDatabases { error("boom") } } } }
        }
        assertEquals(1, n)
    }

    /** Review Focus 3. */
    @Test fun noReplaceMeansNoReloadIsPosted() {
        assertEquals(0, countReloads { runBlocking { BackupControl.reloadingAfterReplace { /* user cancelled */ } } })
    }

    @Test fun afterAThrowingRestoreAndItsReloadSavingWorksAgain() {
        runCatching { runBlocking { BackupControl.reloadingAfterReplace { DatabaseContainer.replacingDatabases { error("boom") } } } }
        repo.loadFromDb(repo.id) // what databaseRestored triggers on the host
        repo.name = "D"; repo.saveIntoDb(false)
        assertEquals("D", runBlocking { dao.workspace(repo.id) }!!.name)
    }
}
