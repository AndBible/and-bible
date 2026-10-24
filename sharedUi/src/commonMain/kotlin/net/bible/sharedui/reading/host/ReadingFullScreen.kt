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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * The reading view's full-screen state, the common counterpart of Android's `SharedActivityState.isFullScreen` plus the
 * gesture listener's `lastFullScreenByDoubleTap`. A host owns one and passes [state] to the reading view
 * (`ReadingHostDeps.fullScreen`); the status bar and the system chrome follow it in the platform's own code.
 *
 * Not thread safe: all calls come from the main thread, like the Compose state it wraps.
 */
@kotlin.native.HiddenFromObjC
class ReadingFullScreen {
    private val current = mutableStateOf(false)

    /** Whether the reading view is in full screen. */
    val state: State<Boolean> get() = current

    /**
     * True while the current full-screen stay was entered by a double tap. The auto-full-screen scroll rule does not leave
     * such a stay (Android's `lastFullScreenByDoubleTap`); leaving full screen by any route clears it.
     */
    var lockedByDoubleTap: Boolean = false
        private set

    /**
     * Enters or leaves full screen. [byDoubleTap] only matters when this call enters full screen. Setting the state it
     * already has changes nothing, so a lock is not taken over or dropped by a repeated call.
     */
    fun set(value: Boolean, byDoubleTap: Boolean = false) {
        if (current.value == value) return
        current.value = value
        // Android's `fullScreenChanged` subscription: leaving clears the lock; entering sets it only for a double tap.
        lockedByDoubleTap = value && byDoubleTap
    }

    /** Flips the state (the overflow menu's "Fullscreen" row), not by a double tap. */
    fun toggle() = set(!current.value)
}
