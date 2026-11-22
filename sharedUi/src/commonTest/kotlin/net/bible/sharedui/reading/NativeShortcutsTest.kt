package net.bible.sharedui.reading

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #4177: the native (non-JS) iOS shortcuts. */
class NativeShortcutsTest {
    private fun chord(key: Key, cp: Int, shift: Boolean = false) =
        nativeDomKey(key, cp)?.let { nativeShortcut(it, ctrl = false, alt = false, shift = shift, meta = true, editing = false) }

    /** What a Finnish/Swedish keyboard reports: "+" on the US "-" position, "-" on the US "/" position. */
    @Test fun fontKeysFollowThePrintedCharacterOnANordicLayout() {
        assertEquals(NativeShortcut.FontLarger, chord(Key.Minus, '+'.code))
        assertEquals(NativeShortcut.FontSmaller, chord(Key.Slash, '-'.code))
        assertNull(chord(Key.Equals, '\u00B4'.code), "the acute accent key is no shortcut")
        assertNull(chord(Key.Minus, '\u00DF'.code), "German sharp s sits on the US minus position")
        assertEquals(NativeShortcut.OpenSettings, chord(Key.M, ','.code))
        assertEquals(NativeShortcut.FontReset, chord(Key.Zero, '0'.code))
    }

    @Test fun usLayoutAndUnknownCharacterStillWork() {
        assertEquals(NativeShortcut.FontLarger, chord(Key.Equals, '='.code))
        assertEquals(NativeShortcut.FontLarger, chord(Key.Equals, '+'.code, shift = true))
        assertEquals(NativeShortcut.FontSmaller, chord(Key.Minus, '-'.code))
        assertEquals(NativeShortcut.FontSmaller, chord(Key.NumPadSubtract, 0))
        assertEquals(NativeShortcut.FontLarger, chord(Key.NumPadAdd, 0))
        assertEquals(NativeShortcut.FontReset, chord(Key.NumPad0, 0))
        assertEquals(NativeShortcut.SyncNow, chord(Key.S, 's'.code))
        assertEquals(NativeShortcut.SyncNow, chord(Key.S, 'S'.code))
    }

    /** Compose may report no character with Cmd held; position must not guess (Nordic "+" is on Key.Minus). */
    @Test fun punctuationAndDigitRowAreNeverMappedFromPositionWithoutACharacter() {
        for (key in listOf(Key.Minus, Key.Equals, Key.Plus, Key.Comma, Key.Zero, Key.Slash)) {
            assertNull(chord(key, 0), "$key with code point 0")
            assertNull(chord(key, 1), "$key with a control code point")
        }
    }

    @Test fun lettersFollowThePrintedCharacterAndNonLatinFallsBackToTheKey() {
        assertEquals(NativeShortcut.SyncNow, chord(Key.S, 0))
        assertEquals(NativeShortcut.SyncNow, chord(Key.S, '\u044B'.code), "Cyrillic layout keeps Cmd+S on the S key")
        assertNull(chord(Key.S, 'o'.code), "Dvorak: the S position prints o")
        assertEquals(NativeShortcut.BookmarkSelection, chord(Key.X, 'b'.code), "Dvorak: B is printed elsewhere")
        assertEquals("q", nativeDomKeyOfCharacter("Q", onKeyS = false, onKeyB = false))
        assertNull(nativeDomKeyOfCharacter("ab", false, false))
        assertNull(nativeDomKeyOfCharacter("\uF700", false, false))
    }

    @Test fun tabAndF11AreByKey() {
        assertEquals("Tab", nativeDomKey(Key.Tab, 9))
        assertEquals("F11", nativeDomKey(Key.F11, 0))
    }

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
        assertEquals(NativeShortcut.ShowHistory, cmd("y"))
        assertEquals(NativeShortcut.ShowHistory, cmd("Y"))
    }

    /** #4204: Cmd+Y opens History (Safari's key); Cmd+Shift+Y, Ctrl+Y and Cmd+H (the system's Hide) are not claimed. */
    @Test fun commandYShowsHistoryAndNothingElseIsTaken() {
        assertEquals(NativeShortcut.ShowHistory, chord(Key.Y, 'y'.code))
        assertEquals(NativeShortcut.ShowHistory, chord(Key.Z, 'y'.code), "German layout: Y is printed on the US Z key")
        assertNull(cmd("y", shift = true))
        assertNull(cmd("h"))
        assertNull(nativeShortcut("y", ctrl = true, alt = false, shift = false, meta = false, editing = false))
        assertNull(nativeShortcut("h", ctrl = true, alt = false, shift = false, meta = false, editing = false))
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
        for (k in listOf("s", ",", "=", "-", "0", "b", "y")) assertNull(cmd(k, editing = true), k)
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
