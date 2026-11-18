package net.bible.android.control.page.window

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowLayout.WindowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4141: a maximised window with its links window open beside it is restored the same way after a reload (restart,
 * workspace switch, sync or restore): both the maximised window id and the links window's open state are saved, so the
 * two-pane view comes back. A maximised window without an open links window comes back alone.
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

    @Test fun aMaximisedWindowWithItsLinksWindowOpenIsRestoredWithBothPanes() {
        val window = f.repo.activeWindow
        val links = window.targetLinksWindow
        f.repo.maximizedWindowId = window.id
        links.windowState = WindowState.VISIBLE
        assertEquals("premise", listOf(window.id, links.id), f.repo.visibleWindows.map { it.id })

        saveAndReload()

        assertEquals(window.id, f.repo.maximizedWindowId)
        assertEquals("the two-pane view is restored", listOf(window.id, links.id), f.repo.visibleWindows.map { it.id })
    }

    @Test fun aMaximisedWindowWithoutAnOpenLinksWindowIsRestoredAlone() {
        val window = f.repo.activeWindow
        window.targetLinksWindow // exists, but minimised
        f.repo.maximizedWindowId = window.id

        saveAndReload()

        assertEquals(window.id, f.repo.maximizedWindowId)
        assertEquals(listOf(window.id), f.repo.visibleWindows.map { it.id })
    }

    @Test fun aMaximisedLinksWindowStaysMaximisedAfterALoad() {
        val links = f.repo.addNewLinksWindow()
        links.windowState = WindowState.VISIBLE
        f.repo.maximizedWindowId = links.id

        saveAndReload()

        assertEquals("a links window the user maximised stays maximised", links.id, f.repo.maximizedWindowId)
        assertTrue(f.repo.visibleWindows.any { it.id == links.id })
    }
}
