package net.bible.desktop.webview2

import java.awt.Frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #4067: WebView2's own top-level input window stayed on screen (just above the desktop) after the frame was
 * minimized, so the desktop under it took no clicks. The pane must tell WebView2 it is hidden while its frame is minimized.
 */
class PaneVisibilityTest {
    private val applied = ArrayList<Boolean>()
    private val visibility = PaneVisibility { applied += it }

    @Test
    fun minimizingTheFrameHidesAnAttachedPaneAndRestoringShowsIt() {
        visibility.setAttached(true)
        visibility.setFrameState(Frame.MAXIMIZED_BOTH)
        visibility.setFrameState(Frame.MAXIMIZED_BOTH or Frame.ICONIFIED)
        assertFalse(visibility.visible)
        visibility.setFrameState(Frame.MAXIMIZED_BOTH)
        assertTrue(visibility.visible)
        assertEquals(listOf(true, false, true), applied)
    }

    @Test
    fun minimizingANormalFrameHidesThePaneToo() {
        visibility.setAttached(true)
        visibility.setFrameState(Frame.ICONIFIED)
        assertEquals(listOf(true, false), applied)
    }

    @Test
    fun restoringTheFrameDoesNotShowAParkedPane() {
        visibility.setAttached(true)
        visibility.setFrameState(Frame.ICONIFIED)
        visibility.setAttached(false)
        visibility.setFrameState(Frame.NORMAL)
        assertFalse(visibility.visible)
        assertEquals(listOf(true, false), applied)
    }

    @Test
    fun attachingIntoAMinimizedFrameStaysHiddenUntilRestored() {
        visibility.setFrameState(Frame.ICONIFIED)
        visibility.setAttached(true)
        assertFalse(visibility.visible)
        visibility.setFrameState(Frame.NORMAL)
        assertEquals(listOf(true), applied)
    }

    @Test
    fun startsHiddenAndAppliesOnlyChanges() {
        assertFalse(visibility.visible)
        visibility.setFrameState(Frame.NORMAL)
        visibility.setAttached(false)
        visibility.setAttached(true)
        visibility.setAttached(true)
        visibility.setFrameState(Frame.MAXIMIZED_BOTH)
        assertEquals(listOf(true), applied)
    }
}
