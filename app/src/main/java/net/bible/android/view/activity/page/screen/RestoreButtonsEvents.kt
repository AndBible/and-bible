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

/**
 * Posted when the window restore-buttons strip should show or hide.
 *
 * Split out of `SplitBibleArea.kt` by Batch Z-late's epilogue (spec 10.3) because it is LIVE on the
 * Compose path -- posted by `WindowRepository.notifyRestoreButtonsChanged()` and consumed by
 * `BibleView` -- while the class that used to host it is classic-only and is deleted in the next
 * commit. Same package as before, so no consumer needed an import change.
 */
class RestoreButtonsVisibilityChanged
