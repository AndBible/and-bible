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

package net.bible.sharedui.reading.activity

import net.bible.android.control.activity.ActivityItem

/**
 * What the reading toolbar's overflow button and menu need to show background activity (#4007).
 *
 * The host collects [net.bible.android.control.activity.ActivityCenter]'s flows into this value; a `null`
 * state means the preference is off and the toolbar renders exactly as before.
 *
 * @param items the activity items, running first, then finished ones, each newest first
 * @param unreadFailures failures the user has not seen yet (the badge count)
 * @param onShowAll opens the full activity list
 * @param onMenuOpened called when the overflow menu opens; the host marks failures read
 */
@kotlin.native.HiddenFromObjC
data class ActivityMenuState(
    val items: List<ActivityItem>,
    val unreadFailures: Int,
    val onShowAll: () -> Unit,
    val onMenuOpened: () -> Unit,
)

/** Semantics test tags of the activity UI. */
@kotlin.native.HiddenFromObjC
const val ACTIVITY_RING_TAG = "activityRing"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_BADGE_TAG = "activityBadge"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_SECTION_TAG = "activitySection"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_ROW_TAG = "activityRow"
