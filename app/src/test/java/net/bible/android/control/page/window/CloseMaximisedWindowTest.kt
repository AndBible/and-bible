package net.bible.android.control.page.window

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression guards for #3737 ("closing a maximised window leaves a blank reading pane"): closing the
 * maximised window must leave maximised mode (no stale `maximizedWindowId`) and keep at least one
 * window visible, both in the repository and in the layout snapshot the reading host renders.
 *
 * The window menu hides Close while maximised, so these drive [WindowControl.closeWindow] directly,
 * as the AI window tool and the My Document delete path do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloseMaximisedWindowTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository

    @Before
    fun setUp() {
        repo = net.bible.test.testWindowRepository()
        windowControl = WindowControl()
        windowControl.windowRepository = repo
        repo.initializeAtEdge()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(repo.scope)
    }

    private val windowState: WindowStateServiceImpl get() = GlobalContext.get().get()

    private fun maximiseAndClose(window: Window) {
        windowControl.maximiseWindow(window)
        assertEquals(window.id, repo.maximizedWindowId)
        windowControl.closeWindow(window)
    }

    private fun assertLeftMaximisedModeWithAVisibleWindow(closed: Window) {
        assertNull(repo.maximizedWindowId)
        assertFalse(repo.isMaximized)
        assertFalse(closed in repo.windowList)
        assertTrue(repo.visibleWindows.isNotEmpty(), "no visible window in the repository")
        assertTrue(repo.activeWindow.isVisible, "active window ${repo.activeWindow} is not visible")

        val layout = windowState.layout.value
        assertNull(layout.maximizedWindowId)
        assertTrue(layout.windows.any { it.isVisible }, "no visible window in the layout snapshot: ${layout.windows}")
    }

    @Test fun closingMaximisedPinnedWindowShowsTheOtherWindow() {
        repo.workspaceSettings.autoPin = true
        val first = repo.activeWindow
        val second = windowControl.addNewWindow(first)

        maximiseAndClose(second)

        assertLeftMaximisedModeWithAVisibleWindow(second)
        assertEquals(listOf(first), repo.visibleWindows)
    }

    @Test fun closingMaximisedFirstWindowShowsTheSecond() {
        val first = repo.activeWindow
        val second = windowControl.addNewWindow(first)
        windowControl.restoreWindow(first, force = true)

        maximiseAndClose(first)

        assertLeftMaximisedModeWithAVisibleWindow(first)
        assertEquals(listOf(second), repo.visibleWindows)
    }

    /** Unpinned windows: only one is VISIBLE, the other is a minimised rail tab. */
    @Test fun closingMaximisedUnpinnedWindowRestoresTheMinimisedOne() {
        val first = repo.activeWindow
        first.isPinMode = false
        val second = windowControl.addNewWindow(first)
        second.isPinMode = false
        windowControl.restoreWindow(second, force = true)
        assertTrue(first.isMinimised)

        maximiseAndClose(second)

        assertLeftMaximisedModeWithAVisibleWindow(second)
        assertEquals(listOf(first), repo.visibleWindows)
        assertEquals(1.0f, first.weight)
    }

    @Test fun closingMaximisedWindowWithAnOpenLinksWindowKeepsTheLinksWindowVisible() {
        val first = repo.activeWindow
        val links = repo.primaryTargetLinksWindow
        links.windowState = WindowLayout.WindowState.VISIBLE

        maximiseAndClose(first)

        assertLeftMaximisedModeWithAVisibleWindow(first)
        assertEquals(listOf(links), repo.visibleWindows)
    }
}
