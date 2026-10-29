/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One test per row of the D3a shortcut table, plus the text-input rules. */
class DesktopShortcutsTest {
    private class Fake : DesktopShortcutActions {
        val log = mutableListOf<String>()
        var reading = true
        var escapeAllowed = true
        override fun isOnReadingScreen() = reading
        override fun activeWindowId() = "active"
        override fun search(windowId: String) { log += "search:$windowId" }
        override fun goToReference(windowId: String) { log += "goto:$windowId" }
        override fun back(historyFallback: Boolean) { log += "back:$historyFallback" }
        override fun bookmarkSelection(windowId: String, selectionJson: String) { log += "bookmark:$windowId:$selectionJson" }
        override fun changeFontSize(change: FontSizeChange) { log += "font:$change" }
        override fun focusNextWindow() { log += "next" }
        override fun focusPreviousWindow() { log += "previous" }
        override fun toggleFullScreen() { log += "fullscreen" }
        override fun openSettings() { log += "settings" }
        override fun escapeAllowed(windowId: String) = escapeAllowed
    }

    private val actions = Fake()
    private var rtl = false
    private val shortcuts = DesktopShortcuts(actions) { rtl }

    private fun ctrl(key: String, fromPane: Boolean = false, shift: Boolean = false, selection: String? = null) =
        Chord(key, ctrl = true, shift = shift, fromPane = fromPane, selection = selection)

    @Test fun ctrlFSearchesTheActiveWindowFromCompose() {
        assertTrue(shortcuts.onChord(ctrl("f"), null))
        assertEquals(listOf("search:active"), actions.log)
    }

    @Test fun ctrlFFromThePageIsLeftToThePage() {
        assertFalse(shortcuts.onChord(ctrl("f", fromPane = true), "w1"))
        assertEquals(emptyList(), actions.log)
    }

    @Test fun ctrlLGoesToReferenceInThePaneWindow() {
        assertTrue(shortcuts.onChord(ctrl("l", fromPane = true), "w1"))
        assertEquals(listOf("goto:w1"), actions.log)
    }

    @Test fun altLeftIsBackWithHistoryFallbackAndAltRightForwardNoop() {
        assertTrue(shortcuts.onChord(Chord("ArrowLeft", alt = true), null))
        assertTrue(shortcuts.onChord(Chord("ArrowRight", alt = true), null))
        assertEquals(listOf("back:true"), actions.log)
    }

    @Test fun altArrowsSwapInRightToLeft() {
        rtl = true
        shortcuts.onChord(Chord("ArrowRight", alt = true), null)
        shortcuts.onChord(Chord("ArrowLeft", alt = true), null)
        assertEquals(listOf("back:true"), actions.log)
    }

    @Test fun mouseBackAndForwardButtons() {
        assertTrue(shortcuts.onNavigation(DesktopBackDispatcher.Navigation.Back))
        assertTrue(shortcuts.onNavigation(DesktopBackDispatcher.Navigation.Forward))
        assertEquals(listOf("back:true"), actions.log)
    }

    @Test fun escFromThePaneIsBackWithoutHistoryFallback() {
        assertTrue(shortcuts.onChord(Chord("Escape", fromPane = true), "w1"))
        assertEquals(listOf("back:false"), actions.log)
    }

    @Test fun escIsIgnoredWhileAPageModalOrEditorIsOpen() {
        actions.escapeAllowed = false
        assertFalse(shortcuts.onChord(Chord("Escape", fromPane = true), "w1"))
        assertEquals(emptyList(), actions.log)
    }

    @Test fun escFromComposeIsNeverHandledHere() {
        assertFalse(shortcuts.onChord(Chord("Escape"), null))
        assertEquals(emptyList(), actions.log)
    }

    @Test fun ctrlCIsNeverIntercepted() {
        assertFalse(shortcuts.onChord(ctrl("c"), null))
        assertFalse(shortcuts.onChord(ctrl("c", fromPane = true), "w1"))
        assertEquals(emptyList(), actions.log)
    }

    @Test fun ctrlBWithASelectionBookmarksIt() {
        assertTrue(shortcuts.onChord(ctrl("b", fromPane = true, selection = "{\"x\":1}"), "w1"))
        assertEquals(listOf("bookmark:w1:{\"x\":1}"), actions.log)
    }

    @Test fun ctrlBWithoutASelectionStaysWithThePage() {
        assertFalse(shortcuts.onChord(ctrl("b", fromPane = true), "w1"))
        assertEquals(emptyList(), actions.log)
    }

    @Test fun ctrlBFromComposeOpensTheKeyChooser() {
        assertTrue(shortcuts.onChord(ctrl("b"), null))
        assertEquals(listOf("goto:active"), actions.log)
    }

    @Test fun ctrlPlusMinusZeroChangeFontSize() {
        for (k in listOf("+", "=", "-", "0")) assertTrue(shortcuts.onChord(ctrl(k, fromPane = true), "w1"))
        assertEquals(
            listOf("font:Larger", "font:Larger", "font:Smaller", "font:Reset"),
            actions.log,
        )
    }

    @Test fun fontSizeStepsAndClamps() {
        assertEquals(110, FontSizeChange.Larger.apply(100))
        assertEquals(90, FontSizeChange.Smaller.apply(100))
        assertEquals(100, FontSizeChange.Reset.apply(250))
        assertEquals(500, FontSizeChange.Larger.apply(500))
        assertEquals(10, FontSizeChange.Smaller.apply(10))
        assertEquals(10, FontSizeChange.Smaller.apply(15))
    }

    @Test fun ctrlTabCyclesWindows() {
        assertTrue(shortcuts.onChord(ctrl("Tab", fromPane = true), "w1"))
        assertTrue(shortcuts.onChord(ctrl("Tab", fromPane = true, shift = true), "w1"))
        assertEquals(listOf("next", "previous"), actions.log)
    }

    @Test fun f11TogglesFullScreen() {
        assertTrue(shortcuts.onChord(Chord("F11"), null))
        assertEquals(listOf("fullscreen"), actions.log)
    }

    @Test fun ctrlCommaOpensSettingsEvenOffTheReadingScreen() {
        actions.reading = false
        assertTrue(shortcuts.onChord(ctrl(","), null))
        assertEquals(listOf("settings"), actions.log)
    }

    @Test fun readingShortcutsAreIgnoredOffTheReadingScreen() {
        actions.reading = false
        assertFalse(shortcuts.onChord(ctrl("f"), null))
        assertFalse(shortcuts.onChord(ctrl("+"), null))
        assertFalse(shortcuts.onChord(Chord("F11"), null))
        assertFalse(shortcuts.onChord(ctrl("Tab"), null))
        assertEquals(emptyList(), actions.log)
        // Back still works there.
        assertTrue(shortcuts.onChord(Chord("ArrowLeft", alt = true), null))
        assertEquals(listOf("back:true"), actions.log)
    }

    @Test fun textInputInPageIsNotIntercepted() {
        val keys = listOf(
            Chord("a", fromPane = true, editable = true),
            Chord("Backspace", fromPane = true, editable = true),
            Chord("Escape", fromPane = true, editable = true),
            Chord("Escape", fromPane = true, composing = true),
            Chord("ArrowLeft", alt = true, composing = true),
            Chord("c", ctrl = true, fromPane = true, editable = true),
            Chord("v", ctrl = true, fromPane = true, editable = true),
            Chord("b", ctrl = true, fromPane = true, editable = true, selection = "{}"),
        )
        for (k in keys) assertFalse(shortcuts.onChord(k, "w1"), k.toString())
        assertEquals(emptyList(), actions.log)
    }
}
