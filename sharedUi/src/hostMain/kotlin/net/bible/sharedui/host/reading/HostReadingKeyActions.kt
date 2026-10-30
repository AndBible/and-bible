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

package net.bible.sharedui.host.reading

import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.reading.bridge.ReadingKeyActions
import net.bible.sharedui.reading.host.ReadingHostState

/**
 * iOS [ReadingKeyActions] (hardware-keyboard shortcuts forwarded by the page: Alt+M, Alt+O, Ctrl+W, Ctrl+C, Ctrl+F),
 * the counterpart of Android's anonymous object in `BibleView`. Window workspace sheet, search and copy go through
 * the same paths as the toolbar and the selection menu.
 *
 * [openDrawer] opens the common drawer (its rows are `ReadingDrawerMenu`) and [openOverflowMenu] the toolbar's overflow
 * menu. Answered by a log line instead of a silent drop: [crashAllBibleViews] (a debug-only Android aid that loads
 * `chrome://crash`; WKWebView offers no public crash trigger).
 */
@kotlin.native.HiddenFromObjC
class HostReadingKeyActions(
    private val hostState: ReadingHostState,
    private val openSearch: () -> Unit,
    private val openOverflow: () -> Unit,
    private val copyCurrentSelection: () -> Unit,
    private val log: (String) -> Unit = { nsLog("IosReadingKeyActions: $it") },
) : ReadingKeyActions {
    override fun openDrawer() = hostState.openDrawer()

    /** Alt+O: the toolbar's overflow menu (not while the search field is showing, as on Android). */
    override fun openOverflowMenu() = openOverflow()

    override fun showWorkspaceSheetIfHosted(): Boolean {
        hostState.showWorkspaceSheet()
        return true
    }

    override fun copySelection() = copyCurrentSelection()

    override fun searchIfHosted(): Boolean {
        openSearch()
        return true
    }

    override fun crashAllBibleViews() = log("crashAllBibleViews: not available on iOS")
}
