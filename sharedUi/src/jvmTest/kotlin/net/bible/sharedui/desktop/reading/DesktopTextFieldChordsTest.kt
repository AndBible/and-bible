/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.desktop.reading

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.host.reading.FontSizeChange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window's two key handlers (`Main.kt`: `onPreviewKeyEvent` then `onKeyEvent`) around a Compose text field.
 * Compose chords carry no `editable` flag, so a text field must get the first refusal of Ctrl+H/Y/S/B itself:
 * Ctrl+Y is redo on Windows/Linux, Ctrl+H is delete-backward in macOS text fields.
 */
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopTextFieldChordsTest {
    private class Rec : DesktopShortcutActions {
        val log = mutableListOf<String>()
        override fun isOnReadingScreen() = true
        override fun activeWindowId() = "active"
        override fun search(windowId: String) { log += "search" }
        override fun goToReference(windowId: String) { log += "goto" }
        override fun back(historyFallback: Boolean) {}
        override fun navigateBack(windowId: String?) {}
        override fun bookmarkSelection(windowId: String, selectionJson: String) {}
        override fun changeFontSize(change: FontSizeChange, steps: Int) {}
        override fun focusNextWindow() {}
        override fun focusPreviousWindow() {}
        override fun toggleFullScreen() {}
        override fun openSettings() {}
        override fun syncNow() { log += "sync" }
        override fun showHistory() { log += "history" }
        override fun escapeAllowed(windowId: String) = true
    }

    private fun androidx.compose.ui.test.ComposeUiTest.window(rec: Rec, textField: Boolean) {
        val input = DesktopReadingInputImpl(
            DesktopShortcuts(rec) { false }, DesktopReadingGestures({ null }), {}, x11Toolkit = false,
        )
        val requester = FocusRequester()
        setContent {
            Box(Modifier.onPreviewKeyEvent { input.onPreviewKeyEvent(it) }.onKeyEvent { input.onKeyEvent(it) }) {
                if (textField) BasicTextField("text", {}, Modifier.testTag("field").focusRequester(requester))
                else Box(Modifier.testTag("field").focusRequester(requester).focusable())
            }
        }
        runOnIdle { requester.requestFocus() }
        onNodeWithTag("field").assertIsFocused()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.ctrl(key: Key) =
        onRoot().performKeyInput { keyDown(Key.CtrlLeft); pressKey(key); keyUp(Key.CtrlLeft) }

    @Test fun focusedTextFieldKeepsCtrlYAndCtrlH() = runComposeUiTest {
        val rec = Rec()
        window(rec, textField = true)
        ctrl(Key.Y)
        ctrl(Key.H)
        assertEquals(emptyList(), rec.log, "redo / delete-backward belong to the field, not the history sheet")
    }

    /** A text field has no use for Ctrl+S / Ctrl+B, so they still work while one has focus (no `editable` flag exists). */
    @Test fun ctrlSAndCtrlBStillWorkInATextField() = runComposeUiTest {
        val rec = Rec()
        window(rec, textField = true)
        ctrl(Key.S)
        ctrl(Key.B)
        assertEquals(listOf("sync", "goto"), rec.log)
    }

    @Test fun aFocusedNonTextElementDoesNotBlockTheChords() = runComposeUiTest {
        val rec = Rec()
        window(rec, textField = false)
        ctrl(Key.Y)
        ctrl(Key.H)
        ctrl(Key.S)
        ctrl(Key.B)
        assertEquals(listOf("history", "history", "sync", "goto"), rec.log)
    }
}
