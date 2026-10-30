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
package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Counts the Compose overlays (modal sheets, dialogs, the navigation drawer) that are in the composition, so a platform
 * can react when the last one is gone. Desktop uses it to give the keyboard back to the web pane that had it
 * (`DesktopWebPaneKeys.restoreAfterOverlay`): an overlay takes Compose focus away, and nothing hands it back.
 *
 * Main (composition) thread only; the counter is not synchronized.
 */
object OverlayPresence {
    /** Called when the count rises from zero to one (the first overlay opens). */
    var onFirstOpened: (() -> Unit)? = null

    /** Called when the count drops to zero and [restoreAllowed] agrees. */
    var onAllClosed: (() -> Unit)? = null

    /** Asked before [onAllClosed]; false vetoes the platform's focus restore (e.g. while the search field keeps the focus). */
    var restoreAllowed: () -> Boolean = { true }

    var count: Int = 0
        private set

    fun opened() {
        if (count == 0) onFirstOpened?.invoke()
        count++
    }

    /** Test hook: back to the initial state (no overlays, no listener). */
    fun resetForTests() {
        count = 0
        onAllClosed = null
        onFirstOpened = null
        restoreAllowed = { true }
    }

    fun closed() {
        if (count == 0) return
        if (--count == 0 && restoreAllowed()) onAllClosed?.invoke()
    }
}

/** Registers an overlay with [OverlayPresence] while this call is in the composition (and [active]). */
@Composable
fun TrackOverlay(active: Boolean = true) {
    DisposableEffect(active) {
        if (active) OverlayPresence.opened()
        onDispose { if (active) OverlayPresence.closed() }
    }
}
