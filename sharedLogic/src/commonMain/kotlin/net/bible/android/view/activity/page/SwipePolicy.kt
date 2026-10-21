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

package net.bible.android.view.activity.page

import net.bible.service.common.BibleViewSwipeMode

/** What a horizontal swipe on the reading view should do. */
enum class SwipeAction { NextChapter, PreviousChapter, PageDown, PageUp, None }

/**
 * The swipe decision shared by Android's `BibleGestureListener.onFling` and iOS's reading gestures.
 *
 * @param towardsNext true for the swipe that reads on (the finger moves right to left in a left-to-right UI)
 * @param isRtl the UI language is right to left: the physical direction is mirrored, so a swipe that was "next" becomes
 *   "previous" (Android's `CommonUtils.isRtl`)
 * @param mode the `bible_view_swipe_mode` setting: chapter navigation, a page scroll (what the volume keys do), or nothing
 */
fun swipeAction(towardsNext: Boolean, isRtl: Boolean, mode: BibleViewSwipeMode): SwipeAction {
    val goNext = if (isRtl) !towardsNext else towardsNext
    return when (mode) {
        BibleViewSwipeMode.CHAPTER -> if (goNext) SwipeAction.NextChapter else SwipeAction.PreviousChapter
        BibleViewSwipeMode.PAGE -> if (goNext) SwipeAction.PageDown else SwipeAction.PageUp
        BibleViewSwipeMode.NONE -> SwipeAction.None
    }
}
