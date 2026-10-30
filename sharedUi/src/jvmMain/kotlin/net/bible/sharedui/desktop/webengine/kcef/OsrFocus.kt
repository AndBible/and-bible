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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import net.bible.sharedui.desktop.webengine.FocusRestorable

/**
 * Compose focus of one off-screen pane and the keys that follow it. Gaining focus gives CEF its keyboard focus and claims
 * the window's keys ([DesktopWebPaneKeys]); losing it (or [release]) undoes both. Keys reach [onKey] only through the
 * focus chain, so none arrive while something else (the drawer, a dialog) has focus.
 */
internal class OsrFocus(private val sink: OsrKeyboardSink, private val onFocusChange: (Boolean) -> Unit = {}) : FocusRestorable {
    val requester = FocusRequester()

    @Volatile
    var isFocused = false
        private set

    fun onFocusChanged(focused: Boolean) {
        if (focused == isFocused) return
        isFocused = focused
        if (focused) DesktopWebPaneKeys.focusGained(this) else DesktopWebPaneKeys.focusLost(this)
        sink.setFocus(focused)
        onFocusChange(focused)
    }

    /** Moves Compose focus into the pane (on the EDT); a pane that has no focus target in the composition yet ignores it. */
    override fun requestFocus() {
        if (isFocused) sink.setFocus(true) else runCatching { requester.requestFocus() }
    }

    /** Drops Compose focus from the pane; set by the pane's composition (`LocalFocusManager.clearFocus(force = true)`). */
    @Volatile
    var clearFocus: () -> Unit = {}

    /**
     * A primary press somewhere in the pane's window ([inside] the pane or not). A press outside while the pane has focus
     * takes the focus away, as the windowed pane lost X focus to such a click: otherwise a drawer or toolbar opened over the
     * pane would leave the keys going to the page behind it.
     */
    fun onWindowPress(inside: Boolean) {
        if (!inside && isFocused) clearFocus()
    }

    /** The pane leaves the composition or is disposed: give the keys back. */
    fun release() {
        onFocusChanged(false)
        DesktopWebPaneKeys.forget(this)
    }

    /** Forwards a key to the page and consumes it, Tab included, so Compose never moves focus out of the pane. */
    fun onKey(e: KeyEvent): Boolean = isFocused && OsrKeys.forward(e, sink)
}

/**
 * Makes a node the off-screen pane's keyboard target: focusable, reporting focus changes to [focus], forwarding every key
 * in the preview pass (before ancestors' bubbling handlers and Compose's own Tab traversal) and feeding the IME bridge.
 */
internal fun Modifier.osrKeyboard(focus: OsrFocus, ime: OsrIme? = null): Modifier =
    focusRequester(focus.requester)
        .onFocusChanged { focus.onFocusChanged(it.isFocused) }
        .onPreviewKeyEvent { focus.onKey(it) }
        .let { if (ime != null) it.then(OsrImeElement(ime)) else it }
        .focusable()
