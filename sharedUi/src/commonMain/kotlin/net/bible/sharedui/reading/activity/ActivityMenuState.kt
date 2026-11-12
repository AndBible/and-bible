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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityBadgeCounts
import net.bible.android.control.activity.ActivityState

/**
 * What the reading toolbar's overflow button and menu need to show background activity (#4007).
 *
 * The host hands this to the reading screen once (the instance is remembered per wiring and never changes
 * while the preference stays on); the changing data lives in the [items] and [unreadFailures] [State]s, which
 * only the composables that draw them read (the overflow button, the menu section, the list). A progress update
 * therefore recomposes those readers and not the reading screen. A `null` state means the preference is off and
 * the toolbar renders exactly as before.
 *
 * @param items the activity items, running first, then finished ones, each newest first; read only where drawn
 * @param unreadFailures failures the user has not seen yet (the badge count); read only where drawn
 * @param onShowAll opens the full activity list
 * @param onMenuOpened called when the overflow menu opens or a failure arrives while it is open; the host marks failures read
 */
@kotlin.native.HiddenFromObjC
@Stable
class ActivityMenuState(
    val items: State<List<ActivityItem>>,
    val unreadFailures: State<Int>,
    val onShowAll: () -> Unit,
    val onMenuOpened: () -> Unit,
    val syncNow: SyncNowMenuState? = null,
    val onOpenItem: (ActivityItem) -> Unit = {},
    val badge: State<ActivityBadgeCounts> = mutableStateOf(ActivityBadgeCounts(failures = unreadFailures.value)),
    val onDismissItem: (ActivityItem) -> Unit = {},
) {
    /** A snapshot of plain values, for tests and goldens. */
    constructor(items: List<ActivityItem>, unreadFailures: Int, onShowAll: () -> Unit, onMenuOpened: () -> Unit, syncNow: SyncNowMenuState? = null) :
        this(mutableStateOf(items), mutableStateOf(unreadFailures), onShowAll, onMenuOpened, syncNow, badge = mutableStateOf(
            ActivityBadgeCounts(
                failures = (unreadFailures - items.count { it.state == ActivityState.RUNNING && it.attention }).coerceAtLeast(0),
                attention = items.count { it.state == ActivityState.RUNNING && it.attention }.coerceAtMost(unreadFailures),
            ),
        ))

    /** The same data with another "show all" action. */
    fun withShowAll(onShowAll: () -> Unit, onOpenItem: (ActivityItem) -> Unit = this.onOpenItem) =
        ActivityMenuState(items, unreadFailures, onShowAll, onMenuOpened, syncNow, onOpenItem, badge, onDismissItem)
}

/** Semantics test tags of the activity UI. */
@kotlin.native.HiddenFromObjC
const val ACTIVITY_RING_TAG = "activityRing"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_BADGE_TAG = "activityBadge"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_SECTION_TAG = "activitySection"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_ROW_TAG = "activityRow"

@kotlin.native.HiddenFromObjC
@Stable
class SyncNowMenuState(val available: State<Boolean>, val running: State<Boolean>, val onClick: () -> Unit)

@kotlin.native.HiddenFromObjC
const val SYNC_NOW_ROW_TAG = "syncNowRow"
