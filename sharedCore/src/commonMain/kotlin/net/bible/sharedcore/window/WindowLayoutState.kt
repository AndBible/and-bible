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

package net.bible.sharedcore.window

/** Window lifecycle state, mirrors the classic `WindowLayout.WindowState`. */
enum class WindowStateValue { VISIBLE, MINIMISED, CLOSED }

/**
 * Immutable, platform-neutral description of one reading window. Identity is an opaque
 * string (the androidMain layer maps `IdType` ↔ this). Carries only layout/display
 * state — no live domain object, no `BibleView`.
 */
data class WindowSnapshot(
    val id: String,
    val state: WindowStateValue,
    val weight: Float,
    val isVisible: Boolean,
    val isPinMode: Boolean,
    val isSynchronised: Boolean,
    val syncGroup: Int,
    val isLinksWindow: Boolean,
)

/**
 * Immutable snapshot of the whole window layout for one workspace, in display order
 * (classic `WindowRepository.sortedWindows`).
 *
 * Split *orientation* is deliberately absent: classically it is `enableReverseSplitMode
 * XOR isPortrait`, i.e. orientation-derived (a config concern). Only the pure workspace
 * flag `reverseSplitMode` is carried here; the Compose chrome derives the orientation in
 * `SplitContent` via [splitIsHorizontal], from the window's
 * shape (`splitIsHorizontal`). It does NOT read `LocalConfiguration` — nothing in this repo does.
 */
data class WindowLayoutState(
    val windows: List<WindowSnapshot>,
    val activeWindowId: String,
    val maximizedWindowId: String?,
    val reverseSplitMode: Boolean,
    val restoreButtonsVisible: Boolean,
    /**
     * The workspace's auto-pin setting (`workspaceSettings.autoPin`). Carried here, beside
     * [reverseSplitMode], because it is a pure workspace flag — and it is needed at the drawing
     * site: see [shouldShowPinIndicator] for why a pin indicator must be suppressed while it is on.
     */
    val autoPin: Boolean = false,
) {
    companion object {
        val EMPTY = WindowLayoutState(
            windows = emptyList(),
            activeWindowId = "",
            maximizedWindowId = null,
            reverseSplitMode = false,
            restoreButtonsVisible = true,
            autoPin = false,
        )
    }
}
