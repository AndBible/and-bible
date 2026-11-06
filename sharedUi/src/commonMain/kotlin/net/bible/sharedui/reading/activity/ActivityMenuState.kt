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
data class ActivityMenuState(
    val items: List<ActivityItem>,
    val unreadFailures: Int,
    val onShowAll: () -> Unit,
    val onMenuOpened: () -> Unit,
)

/** Semantics test tags of the activity UI. */
const val ACTIVITY_RING_TAG = "activityRing"
const val ACTIVITY_BADGE_TAG = "activityBadge"
const val ACTIVITY_SECTION_TAG = "activitySection"
const val ACTIVITY_ROW_TAG = "activityRow"
