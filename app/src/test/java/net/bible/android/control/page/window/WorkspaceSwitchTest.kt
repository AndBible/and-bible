package net.bible.android.control.page.window

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.ChapterVerse
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Switching away from a workspace and back must show what was saved, however far behind the write queue is. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceSwitchTest {
    private val gate = GateDispatcher()
    private lateinit var f: Fixture

    @Before fun setUp() {
        f = Fixture(gate)
        runBlocking { f.repo.initialize() }
        runBlocking { f.persistence.awaitPersisted() }
    }
    @After fun tearDown() {
        gate.release()
        f.close()
    }

    /** Breaks if the loader's read does not wait for the writes queued before it (here: the held save of workspace A). */
    @Test fun switchAwayAndBackWithoutDrainingShowsTheSavedVerse() {
        val a = f.repo.id
        val b = IdType()
        runBlocking { workspaceDao.insertWorkspace(WorkspaceEntities.Workspace("B").also { it.id = b }) }
        f.repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 7)
        gate.hold()
        f.repo.saveIntoDb(false)
        assertTrue("the save must still be queued", gate.pending > 0)

        runBlocking {
            f.repo.loadFromDb(b)
            assertEquals(b, f.repo.id)
            f.repo.loadFromDb(a)
        }

        assertEquals(a, f.repo.id)
        assertEquals(7, f.repo.activeWindow.pageManager.currentBible.currentBibleVerse.verse.verse)
    }
}
