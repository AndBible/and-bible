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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.OverlayPresence
import net.bible.sharedui.components.TrackOverlay
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import net.bible.sharedui.reading.host.ReadingOverlayTracking
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

    /** Runs [body] with the restore running synchronously (production posts it to the event thread). */
    private inline fun withRestoreListener(body: () -> Unit) {
        DesktopWebPaneKeys.resetForTests()
        OverlayPresence.onAllClosed = { DesktopWebPaneKeys.restoreAfterOverlay() }
        try { body() } finally { DesktopWebPaneKeys.resetForTests() }
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

    @Test fun aComposeFieldThatHadFocusBeforeTheOverlayIsNotOverridden() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        val field = FocusRequester()
        var overlay by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        Box(Modifier.size(20.dp).focusRequester(field).focusable())
                        if (overlay) TrackOverlay()
                    }
                }
                // The pane had the focus earlier; the user then went to a Compose text field.
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { field.requestFocus() }
                runOnIdle { overlay = true }
                runOnIdle { overlay = false }
                runOnIdle {
                    assertEquals(listOf(true, false), sink.focus)
                    assertFalse(DesktopWebPaneKeys.ownsKeys)
                }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun aPressThatTookTheFocusFromThePaneBeforeTheOverlayOpenedStillRestores() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        var overlay by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    val fm = LocalFocusManager.current
                    pane.clearFocus = { fm.clearFocus(force = true) }
                    Column { Box(Modifier.size(20.dp).osrKeyboard(pane)); if (overlay) TrackOverlay() }
                }
                runOnIdle { pane.requester.requestFocus() }
                // The press on the toolbar button that opens the overlay clears the pane's focus first.
                runOnIdle { pane.onWindowPress(inside = false) }
                runOnIdle { overlay = true }
                runOnIdle { overlay = false }
                runOnIdle { assertEquals(listOf(true, false, true), sink.focus) }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun aLaterPressElsewhereDropsThePendingRestore() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        var overlay by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    val fm = LocalFocusManager.current
                    pane.clearFocus = { fm.clearFocus(force = true) }
                    Column { Box(Modifier.size(20.dp).osrKeyboard(pane)); if (overlay) TrackOverlay() }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { pane.onWindowPress(inside = false) } // clears the pane
                runOnIdle { pane.onWindowPress(inside = false) } // e.g. a click into a Compose text field
                runOnIdle { overlay = true }
                runOnIdle { overlay = false }
                runOnIdle { assertEquals(listOf(true, false), sink.focus) }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun theHostCanVetoTheRestore() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        val other = FocusRequester()
        var overlay by mutableStateOf(false)
        withRestoreListener {
            try {
                OverlayPresence.restoreAllowed = { false } // e.g. the search field is open and keeps the focus
                setContent {
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        Box(Modifier.size(20.dp).focusRequester(other).focusable())
                        if (overlay) TrackOverlay()
                    }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { overlay = true }
                runOnIdle { other.requestFocus() }
                runOnIdle { overlay = false }
                runOnIdle { assertEquals(listOf(true, false), sink.focus) }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun paneRegainsFocusWhenTheSearchBarCloses() = runComposeUiTest {
        // Ctrl+F in the pane opens the search bar, whose field takes the focus; closing the bar leaves the focus nowhere.
        val sink = Sink()
        val pane = OsrFocus(sink)
        val field = FocusRequester()
        var bar by mutableStateOf(false)
        withRestoreListener {
            try {
                setContent {
                    ReadingOverlayTracking(drawerActive = false, searchBarOpen = bar)
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        if (bar) Box(Modifier.size(20.dp).focusRequester(field).focusable())
                    }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { bar = true }
                runOnIdle { field.requestFocus() }
                runOnIdle { assertFalse(DesktopWebPaneKeys.ownsKeys) }
                runOnIdle { bar = false }
                runOnIdle {
                    assertEquals(listOf(true, false, true), sink.focus)
                    assertTrue(DesktopWebPaneKeys.ownsKeys)
                }
            } finally {
                pane.release()
            }
        }
    }

    @Test fun aMenuItemThatOpensASheetDoesNotRestoreBetweenThem() = runComposeUiTest {
        val sink = Sink()
        val pane = OsrFocus(sink)
        val other = FocusRequester()
        var menu by mutableStateOf(false)
        var sheet by mutableStateOf(false)
        withRestoreListener {
            // Production posts the restore to the event thread; run it after the composition has settled, as it does.
            var pending = false
            OverlayPresence.onAllClosed = { pending = true }
            try {
                setContent {
                    Column {
                        Box(Modifier.size(20.dp).osrKeyboard(pane))
                        Box(Modifier.size(20.dp).focusRequester(other).focusable())
                        TrackOverlay(active = menu)
                        TrackOverlay(active = sheet)
                    }
                }
                runOnIdle { pane.requester.requestFocus() }
                runOnIdle { menu = true }
                runOnIdle { other.requestFocus() }
                // The menu closes as its item opens a sheet: the count never settles at zero for the restore.
                runOnIdle { sheet = true; menu = false }
                runOnIdle { if (pending) DesktopWebPaneKeys.restoreAfterOverlay(); pending = false }
                runOnIdle { assertEquals(listOf(true, false), sink.focus) }
                // Closing the sheet finally settles at zero: the pane must get the keys back.
                runOnIdle { sheet = false }
                runOnIdle { if (pending) DesktopWebPaneKeys.restoreAfterOverlay() }
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
