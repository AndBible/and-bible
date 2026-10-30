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
    /** Called when the count drops to zero. */
    var onAllClosed: (() -> Unit)? = null

    var count: Int = 0
        private set

    fun opened() {
        count++
    }

    fun closed() {
        if (count == 0) return
        if (--count == 0) onAllClosed?.invoke()
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
