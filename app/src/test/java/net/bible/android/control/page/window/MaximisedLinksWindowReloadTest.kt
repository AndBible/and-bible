package net.bible.android.control.page.window

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowLayout.WindowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4141: the way back from a maximised links window lives in memory only, but the maximised window id is saved. A
 * workspace loaded with its links window maximised (restart, workspace switch, sync or restore reload) has no way back,
 * so it is shown un-maximised; a window the user maximised stays maximised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MaximisedLinksWindowReloadTest {
    private lateinit var f: Fixture

    @Before fun setUp() {
        f = Fixture()
        runBlocking { f.repo.initialize() }
        runBlocking { f.persistence.awaitPersisted() }
    }
    @After fun tearDown() { f.close() }

    private fun saveAndReload() {
        f.repo.saveIntoDb(false)
        runBlocking {
            f.persistence.awaitPersisted()
            f.repo.loadFromDb(f.repo.id)
        }
    }

    @Test fun aMaximisedLinksWindowIsLoadedUnMaximised() {
        val links = f.repo.addNewLinksWindow()
        links.windowState = WindowState.VISIBLE
        f.repo.maximizedWindowId = links.id

        saveAndReload()

        assertNull("no way back after a load: the links window is not maximised", f.repo.maximizedWindowId)
        assertTrue("the links window is still shown in the split", f.repo.visibleWindows.any { it.id == links.id })
    }

    @Test fun aMaximisedOrdinaryWindowStaysMaximisedAfterALoad() {
        val window = f.repo.activeWindow
        f.repo.maximizedWindowId = window.id

        saveAndReload()

        assertEquals(window.id, f.repo.maximizedWindowId)
    }
}
