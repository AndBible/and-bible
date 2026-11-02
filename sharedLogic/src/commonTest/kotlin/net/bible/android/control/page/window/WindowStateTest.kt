package net.bible.android.control.page.window

import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.android.control.page.window.WindowLayout.WindowState

/**
 * `WindowState.fixedValueOf` reads `Window.window_layout_state`. Besides the Android names it must accept the states the old
 * Swift iOS app wrote into its stores and its "Android format" database backups (`split`, `minimized`): a workspace
 * migrated from that app (or such a backup restored) otherwise threw while the window repository loaded, so reading never
 * opened and the app stayed on Welcome (status 2026-11-02-ios-release-onboarding-loop).
 */
class WindowStateTest {
    @Test
    fun androidNamesAndLegacySplit() {
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf("VISIBLE"))
        assertEquals(WindowState.MINIMISED, WindowState.fixedValueOf("MINIMISED"))
        assertEquals(WindowState.CLOSED, WindowState.fixedValueOf("CLOSED"))
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf("SPLIT"))
    }

    @Test
    fun swiftAppStates() {
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf("split"))
        assertEquals(WindowState.MINIMISED, WindowState.fixedValueOf("minimized"))
        assertEquals(WindowState.MINIMISED, WindowState.fixedValueOf("MINIMIZED"))
        assertEquals(WindowState.CLOSED, WindowState.fixedValueOf("closed"))
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf("maximized"))
    }

    @Test
    fun unknownStateOpensVisibleInsteadOfThrowing() {
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf(""))
        assertEquals(WindowState.VISIBLE, WindowState.fixedValueOf("bogus"))
    }
}
