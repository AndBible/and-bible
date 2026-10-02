package net.bible.service.cloudsync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * F113 (batch 6 §1.2): CloudSync's initial-download swap is a database replace, like a backup restore. A
 * save between the close and the live repository's reload must not write the pre-download windows over
 * the downloaded file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudSyncInitialSwapTest {
    private lateinit var repo: WindowRepository
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()
    private val workspaces get() = DatabaseContainer.databaseAccessorsByCategory[SyncableDatabaseDefinition.WORKSPACES]!!

    @Before fun setUp() {
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

    /** The "download": the current workspaces DB with the workspace renamed [name], checkpointed and copied out. */
    private fun downloadNamed(name: String): File {
        dao.updateWorkspace(dao.workspace(repo.id)!!.copy(name = name))
        workspaces.writableDb.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val f = File.createTempFile("initial", ".sqlite3")
        workspaces.localDbFile.copyTo(f, overwrite = true)
        dao.updateWorkspace(dao.workspace(repo.id)!!.copy(name = "A"))
        return f
    }

    @Test fun aSaveAfterTheSwapDoesNotOverwriteTheDownload() {
        val downloaded = downloadNamed("C")
        runBlocking { CloudSync.swapInInitialDb(workspaces, downloaded) { } }
        repo.name = "B" // pre-download in-memory state, not yet reloaded
        repo.saveIntoDb(false)
        assertEquals("C", dao.workspace(repo.id)!!.name)
    }

    @Test fun saveWorksAgainAfterTheReload() {
        runBlocking { CloudSync.swapInInitialDb(workspaces, downloadNamed("C")) { } }
        repo.loadFromDb(repo.id)
        repo.name = "D"
        repo.saveIntoDb(false)
        assertEquals("D", dao.workspace(repo.id)!!.name)
    }

    /** Review Focus 2: one release when the last overlapping swap ends, not one per category. */
    @Test fun theRefreshIsPostedOnlyWhenTheLastSwapEnds() {
        val posts = mutableListOf<Any>()
        val sub = Any()
        ABEventBus.register(sub) { on<WorkspaceRefreshRequired> { posts += it } }
        try {
            runBlocking {
                DatabaseContainer.replacingDatabases {
                    CloudSync.swapInInitialDb(workspaces, downloadNamed("C")) { }
                    assertEquals("still inside an outer replace", 0, posts.size)
                }
            }
        } finally { ABEventBus.unregister(sub) }
    }
}
