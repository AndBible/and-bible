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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityState
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.isPureMonochrome

/** Most rows the overflow menu shows; the rest are behind "Show all". */
internal const val MENU_ACTIVITY_ROWS = 3

/**
 * The activity section at the top of the overflow menu (#4007): up to [MENU_ACTIVITY_ROWS] rows (running first,
 * then failures; newest first within each; finished successes only appear in the full list), each a title with a slim progress bar or the
 * failure message, then "Show all (N)" (N = what the full list shows) when the list holds more than the rows shown or any failure exists, then a divider.
 * Renders nothing when there is nothing to show, so the menu is then exactly the normal one.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun ActivityMenuSection(activity: ActivityMenuState, onSyncClick: (() -> Unit)? = null) {
    // Stable sort: the incoming order (newest first) is kept within each state.
    // Same filter as the full list (ActivityListContent): cancelled items are never shown anywhere.
    val all = activity.items.value.filter { it.state != ActivityState.CANCELLED }
    val shown = all
        .filter { it.state == ActivityState.RUNNING || it.state == ActivityState.FAILED }
        .sortedBy { it.state.menuRank() }
    val sync = activity.syncNow?.takeIf { it.available.value }
    if (shown.isEmpty() && sync == null) return
    val strings = LocalStrings.current
    Column(Modifier.testTag(ACTIVITY_SECTION_TAG)) {
        if (sync != null) SyncNowRow(sync, onSyncClick ?: sync.onClick)
        val rows = shown.take(MENU_ACTIVITY_ROWS)
        rows.forEach { ActivityRow(it) }
        // Whenever the list holds more than these rows (finished successes, overflow) or anything failed.
        if (shown.isNotEmpty() && (all.size > rows.size || shown.any { it.state == ActivityState.FAILED })) {
            AbMenuItem(text = strings.activityShowAll(all.size), onClick = activity.onShowAll)
        }
        HorizontalDivider()
    }
}

private fun ActivityState.menuRank(): Int = when (this) {
    ActivityState.RUNNING -> 0
    else -> 1
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    val mono = isPureMonochrome()
    val progress = item.progress
    val message = item.message
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(ACTIVITY_ROW_TAG),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            item.state == ActivityState.RUNNING -> {
                val barColor = LocalContentColor.current
                val trackColor = barColor.copy(alpha = 0.2f)
                val barModifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = barModifier, color = barColor, trackColor = trackColor,
                    )
                } else {
                    AbLoadingIndicator(modifier = barModifier, color = barColor, trackColor = trackColor)
                }
            }
            message != null -> Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.state == ActivityState.FAILED && !mono) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
