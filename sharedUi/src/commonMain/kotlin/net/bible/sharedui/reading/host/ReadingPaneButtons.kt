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

import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.window.WindowLayoutState

/**
 * Whether the floating window-button rail is shown at all (the hard gate, not the 2 s idle fade): removed while a window
 * is maximised, and while the user has hidden the window buttons (`hide_window_buttons`). Android's `SplitBibleArea`
 * and `BibleFrame.addWindowButton` apply the same two early-outs.
 */
@kotlin.native.HiddenFromObjC
fun paneButtonsShown(settings: AppSettings, maximised: Boolean): Boolean =
    !maximised && !settings.getBoolean("hide_window_buttons", false)

/**
 * Whether the pane of window [windowId] shows its floating window button. Not maximised, every pane follows
 * [paneButtonsShown]. Maximised, only the links window shown beside the maximised window has one (#4141), so it can be
 * minimised or closed; like links windows in the classic split it ignores `hide_window_buttons`.
 */
@kotlin.native.HiddenFromObjC
fun paneButtonShown(settings: AppSettings, layout: WindowLayoutState, windowId: String): Boolean {
    val maximisedId = layout.maximizedWindowId ?: return paneButtonsShown(settings, maximised = false)
    if (windowId == maximisedId) return false
    return layout.windows.any { it.id == windowId && it.isLinksWindow && it.isVisible }
}
