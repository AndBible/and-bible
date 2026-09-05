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

package net.bible.android.view.activity.page.screen

import net.bible.service.sword.BookAndKey

/**
 * The app-wide "copied Bible reference" slot: set by `MainBibleActivity`'s copy-reference pane-menu
 * action and by `BibleView`'s and-bible-URL handler, read back by `WindowPaneMenuStateBuilder` to
 * decide whether the pane menu's "go to copied reference" row is shown and what it is labelled.
 *
 * Split out of `SplitBibleArea.kt` by Batch Z-late's epilogue (spec 10.3) for the same reason
 * [RestoreButtonsVisibilityChanged] was: it is a top-level declaration that merely happened to live
 * in the classic file, it is LIVE on the Compose path (three surviving files import it by name),
 * and its old host is classic-only and is deleted in the next commit. Same package as before, so no
 * consumer needed an import change.
 *
 * Deliberately a plain top-level `var` rather than snapshot state, exactly as before: every reader
 * samples it at menu-build time, so nothing observes it reactively and making it observable would
 * be a behaviour change, not a port.
 */
var clipboardKey: BookAndKey? = null
