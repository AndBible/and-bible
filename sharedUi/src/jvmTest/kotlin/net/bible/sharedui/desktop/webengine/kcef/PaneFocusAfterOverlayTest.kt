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
package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.OverlayPresence
import net.bible.sharedui.components.TrackOverlay
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PaneFocusAfterOverlayTest {
    private class Sink : OsrKeyboardSink {
        val focus = mutableListOf<Boolean>()
        override fun setFocus(focused: Boolean) { focus += focused }
        override fun sendKey(e: java.awt.event.KeyEvent) {}
    }

    private inline fun withRestoreListener(body: () -> Unit) {
        DesktopWebPaneKeys.ownsKeys // initialises the object, which installs its own (EDT-posted) listener
        val saved = OverlayPresence.onAllClosed
        OverlayPresence.onAllClosed = { DesktopWebPaneKeys.restoreAfterOverlay() }
        try { body() } finally { OverlayPresence.onAllClosed = saved }
    }

    @Test fun paneRegainsFocusAfterOverlayCloses() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        val other = FocusRequester()
        var overlay by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        Box(Modifier.size(20.dp).focusRequester(other).focusable())
                        if (overlay) TrackOverlay()
                    }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { overlay = true }
                // The overlay takes the focus away from the pane.
                runOnIdle { other.requestFocus() }
                runOnIdle { assertFalse(DesktopWebPaneKeys.ownsKeys) }
                runOnIdle { overlay = false }
                runOnIdle {
                    assertEquals(listOf(true, false, true), sink.focus)
                    assertTrue(DesktopWebPaneKeys.ownsKeys)
                }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun focusIsNotRestoredWhileAnotherOverlayIsOpen() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        val other = FocusRequester()
        var first by mutableStateOf(false)
        var second by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        Box(Modifier.size(20.dp).focusRequester(other).focusable())
                        if (first) TrackOverlay()
                        if (second) TrackOverlay()
                    }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { first = true; second = true }
                runOnIdle { other.requestFocus() }
                runOnIdle { second = false }
                runOnIdle { assertEquals(listOf(true, false), sink.focus) }
                runOnIdle { first = false }
                runOnIdle { assertEquals(listOf(true, false, true), sink.focus) }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun aReleasedPaneIsNotRestored() {
        val sink = Sink()
        val pane = OsrFocus(sink)
        pane.onFocusChanged(true)
        pane.release()
        assertFalse(DesktopWebPaneKeys.restoreAfterOverlay())
        assertEquals(listOf(true, false), sink.focus)
    }
}
