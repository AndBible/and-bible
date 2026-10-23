package net.bible.android.control.backup

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
 * [reloadAfterRestore]: the order of the steps (recording fake) and the real outcome that the restore-epoch
 * save freeze of a real `WindowRepository` is lifted. Fixtures live in `:app` tests, hence this location.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReloadAfterRestoreTest {
    private class Recorder(
        private val oldIds: List<String>,
        private val workspaceId: String,
    ) : RestoreReloadTarget {
        val log = mutableListOf<String>()
        override fun windowIds(): List<String> { log += "captureIds"; return oldIds }
        override fun setResyncRequired() { log += "setResyncRequired" }
        override suspend fun reloadFromDatabase() { log += "load" }
        override fun loadedWorkspaceId(): String = workspaceId
        override fun reloadAllWindows(force: Boolean) { log += "reloadAll($force)" }
        override fun updateAllWindowsTextDisplaySettings() { log += "textSettings" }
    }

    private lateinit var repo: net.bible.android.control.page.window.WindowRepository
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        CommonUtils.settings.setBoolean("first-time", false)
        repo = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        repo.name = "A"
        repo.saveIntoDb(false)
        runBlocking { repo.persistence.awaitPersisted() }
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        repo.clear()
    }

    private fun run(target: RestoreReloadTarget, log: MutableList<String>) = runBlocking {
        reloadAfterRestore(target, { log += "release($it)" }, { log += "setWorkspace($it)" })
    }

    @Test fun stepsRunInTheRequiredOrder() {
        val t = Recorder(listOf("w1", "w2"), "ws-restored")
        run(t, t.log)
        assertEquals(
            listOf(
                "captureIds", "setResyncRequired", "load", "setWorkspace(ws-restored)",
                "release(w1)", "release(w2)", "reloadAll(true)", "textSettings",
            ),
            t.log,
        )
    }

    @Test fun oldIdsAreReleasedEvenWhenTheRestoredWorkspaceHasOtherWindows() {
        // The fake's windowIds() is only consulted BEFORE the load; after it the new workspace holds other ids.
        val t = object : RestoreReloadTarget by Recorder(emptyList(), "ws2") {
            val log = mutableListOf<String>()
            var ids = listOf("old1")
            override fun windowIds() = ids
            override suspend fun reloadFromDatabase() { ids = listOf("brandNew"); log += "load" }
            override fun setResyncRequired() { log += "resync" }
            override fun reloadAllWindows(force: Boolean) { log += "reload" }
            override fun updateAllWindowsTextDisplaySettings() { log += "text" }
        }
        run(t, t.log)
        assertEquals(listOf("resync", "load", "setWorkspace(ws2)", "release(old1)", "reload", "text"), t.log)
        assertFalse(t.log.contains("release(brandNew)"))
    }

    @Test fun noActiveWorkspaceIdIsNotStoredButEverythingElseStillRuns() {
        val t = Recorder(listOf("w1"), "")
        run(t, t.log)
        assertEquals(
            listOf("captureIds", "setResyncRequired", "load", "release(w1)", "reloadAll(true)", "textSettings"),
            t.log,
        )
    }

    @Test fun noOldWindowsMeansNoReleasesButTheReloadStillHappens() {
        val t = Recorder(emptyList(), "ws")
        run(t, t.log)
        assertEquals(
            listOf("captureIds", "setResyncRequired", "load", "setWorkspace(ws)", "reloadAll(true)", "textSettings"),
            t.log,
        )
    }

    @Test fun realRepositoryIsUnfrozenAndSavesAgainAfterARestore() {
        val oldIds = repo.windowList.map { it.id.toString() }
        runBlocking {
            DatabaseContainer.replacingDatabases { token ->
                dao.updateWorkspace(dao.workspace(repo.id)!!.copy(name = "C"))
                DatabaseContainer.resetOwned(token)
            }
        }
        assertTrue("restore must leave the repository frozen until reloaded", repo.staleAfterRestore)

        val released = mutableListOf<String>()
        var stored: String? = null
        runBlocking { reloadAfterRestore(restoreReloadTarget(repo), { released += it }, { stored = it }) }

        assertFalse("reload must lift the epoch save freeze", repo.staleAfterRestore)
        assertEquals(repo.id.toString(), stored)
        assertEquals(oldIds, released)
        assertEquals("C", repo.name)
        repo.name = "D"
        repo.saveIntoDb(false)
        runBlocking { repo.persistence.awaitPersisted() }
        assertEquals("D", runBlocking { dao.workspace(repo.id) }!!.name)
    }
}
