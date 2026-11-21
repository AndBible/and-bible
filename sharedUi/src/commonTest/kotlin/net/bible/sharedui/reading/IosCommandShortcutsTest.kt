package net.bible.sharedui.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosCommandShortcutsTest {
    @Test fun commandFindAndBookmarkReuseControlCommandsOnIosOnly() {
        for ((code, expected) in listOf("KeyF" to "CtrlKeyF", "KeyB" to "CtrlKeyB")) {
            assertEquals(expected, readingShortcut(code, ctrl = false, alt = false, meta = true, iosCommandKeys = true))
            assertNull(readingShortcut(code, ctrl = false, alt = false, meta = true))
            assertEquals(expected, readingShortcut(code, ctrl = true, alt = false))
        }
    }

    /** #4168: Cmd+L is "go to reference" like Ctrl+L on the desktop, which sends the page's Ctrl+B (key chooser). */
    @Test fun commandLOpensTheKeyChooserLikeDesktopControlL() {
        assertEquals("CtrlKeyB", readingShortcut("KeyL", ctrl = false, alt = false, meta = true, iosCommandKeys = true))
        assertNull(readingShortcut("KeyL", ctrl = false, alt = false, meta = true))
        assertNull(readingShortcut("KeyL", ctrl = true, alt = false, meta = true, iosCommandKeys = true))
        assertNull(readingShortcut("KeyL", ctrl = false, alt = true, meta = true, iosCommandKeys = true))
    }

    @Test fun commandDoesNotClaimOtherCommandsOrMixedModifiers() {
        for (code in listOf("KeyG", "BracketLeft", "BracketRight", "KeyD", "Comma", "KeyC", "KeyW")) {
            assertNull(readingShortcut(code, ctrl = false, alt = false, meta = true, iosCommandKeys = true))
        }
        assertNull(readingShortcut("KeyF", ctrl = true, alt = false, meta = true, iosCommandKeys = true))
        assertNull(readingShortcut("KeyF", ctrl = false, alt = true, meta = true, iosCommandKeys = true))
    }

    @Test fun commandBoldRemainsWithTheEditor() {
        assertNull(readingShortcut("KeyB", ctrl = false, alt = false, meta = true, editing = true, iosCommandKeys = true))
        assertEquals("CtrlKeyF", readingShortcut("KeyF", ctrl = false, alt = false, meta = true, editing = true, iosCommandKeys = true))
    }
}

/** #4177: the native (non-JS) iOS shortcuts. */
class NativeShortcutsTest {
    private fun cmd(key: String, shift: Boolean = false, editing: Boolean = false) =
        nativeShortcut(key, ctrl = false, alt = false, shift = shift, meta = true, editing = editing)

    @Test fun commandChordsMapToTheDesktopActions() {
        assertEquals(NativeShortcut.SyncNow, cmd("s"))
        assertEquals(NativeShortcut.OpenSettings, cmd(","))
        assertEquals(NativeShortcut.FontLarger, cmd("="))
        assertEquals(NativeShortcut.FontLarger, cmd("+"))
        assertEquals(NativeShortcut.FontSmaller, cmd("-"))
        assertEquals(NativeShortcut.FontReset, cmd("0"))
        assertEquals(NativeShortcut.BookmarkSelection, cmd("b"))
        assertEquals(NativeShortcut.SyncNow, cmd("S"))
    }

    @Test fun shiftedSyncAndBookmarkAreNotClaimed() {
        assertNull(cmd("s", shift = true))
        assertNull(cmd("b", shift = true))
    }

    @Test fun controlTabSwitchesWindowsAndF11TogglesFullScreen() {
        assertEquals(NativeShortcut.NextWindow, nativeShortcut("Tab", ctrl = true, alt = false, shift = false, meta = false, editing = false))
        assertEquals(NativeShortcut.PreviousWindow, nativeShortcut("Tab", ctrl = true, alt = false, shift = true, meta = false, editing = false))
        assertEquals(NativeShortcut.ToggleFullScreen, nativeShortcut("F11", ctrl = false, alt = false, shift = false, meta = false, editing = false))
        assertNull(nativeShortcut("Tab", ctrl = false, alt = false, shift = false, meta = false, editing = false))
        assertNull(nativeShortcut("F11", ctrl = true, alt = false, shift = false, meta = false, editing = false))
    }

    @Test fun everyShortcutIsIgnoredWhileEditing() {
        for (k in listOf("s", ",", "=", "-", "0", "b")) assertNull(cmd(k, editing = true), k)
        assertNull(nativeShortcut("Tab", ctrl = true, alt = false, shift = false, meta = false, editing = true))
        assertNull(nativeShortcut("F11", ctrl = false, alt = false, shift = false, meta = false, editing = true))
    }

    @Test fun altAndMixedModifiersAreNeverClaimed() {
        assertNull(nativeShortcut("s", ctrl = false, alt = true, shift = false, meta = true, editing = false))
        assertNull(nativeShortcut("s", ctrl = true, alt = false, shift = false, meta = true, editing = false))
        assertNull(nativeShortcut("s", ctrl = false, alt = false, shift = false, meta = false, editing = false))
    }

    @Test fun physicalControlLOpensTheKeyChooserOnIosOnly() {
        assertEquals("CtrlKeyB", readingShortcut("KeyL", ctrl = true, alt = false, iosCommandKeys = true))
        assertNull(readingShortcut("KeyL", ctrl = true, alt = false, editing = true, iosCommandKeys = true))
        assertNull(readingShortcut("KeyL", ctrl = true, alt = false))
    }
}
