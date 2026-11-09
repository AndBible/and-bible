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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import net.bible.android.control.activity.ActivityKind
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityState
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.reading.host.slots.LocalAgentStatusIcon
import net.bible.sharedui.theme.isPureMonochrome

/** Test tags of the full activity list. */
@kotlin.native.HiddenFromObjC
const val ACTIVITY_LIST_ROW_TAG = "activityListRow"

/**
 * Body of the full activity list (#4007), shared by the compact sheet and the wide popover (see [ActivityListHost]).
 * "Running" rows (with Cancel when the work supports it) come first, then a "Recent" section with finished rows
 * (each with Dismiss) and a "Clear finished" action. CANCELLED items are never shown. With nothing to show, a single
 * "Nothing running" line. Tapping a row calls [onOpenItem]; the host decides where it leads.
 *
 * Bodies are static in captures: indeterminate bars go through [AbLoadingIndicator], which honours the
 * no-animations setting and inspection mode.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun ActivityListContent(
    items: List<ActivityItem>,
    onDismissItem: (String) -> Unit,
    onClearFinished: () -> Unit,
    onOpenItem: (ActivityItem) -> Unit,
    modifier: Modifier = Modifier,
    onReport: ((ActivityItem) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val visible = items.filter { it.state != ActivityState.CANCELLED }
    val running = visible.filter { it.state == ActivityState.RUNNING }
    val finished = visible.filter { it.state != ActivityState.RUNNING }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        if (visible.isEmpty()) {
            Text(
                strings.activityEmpty, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        if (running.isNotEmpty()) {
            SectionHeader(strings.activityRunning)
            running.forEach { ListRow(it, onOpenItem, onDismissItem, onReport) }
        }
        if (finished.isNotEmpty()) {
            SectionHeader(strings.activityRecent) {
                TextButton(onClick = onClearFinished) { Text(strings.activityClearFinished) }
            }
            finished.forEach { ListRow(it, onOpenItem, onDismissItem, onReport) }
        }
    }
}

@Composable
private fun SectionHeader(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
private fun ListRow(item: ActivityItem, onOpen: (ActivityItem) -> Unit, onDismiss: (String) -> Unit, onReport: ((ActivityItem) -> Unit)?) {
    val strings = LocalStrings.current
    val mono = isPureMonochrome()
    val message = item.message
    val progress = item.progress
    val cancel = item.cancel
    Row(
        Modifier.fillMaxWidth().testTag(ACTIVITY_LIST_ROW_TAG).clickable { onOpen(item) }
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.kind == ActivityKind.AI_AGENT) {
            Icon(LocalAgentStatusIcon.current(), contentDescription = null, modifier = Modifier.size(24.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                item.state == ActivityState.RUNNING -> {
                    if (message != null && item.kind in setOf(ActivityKind.AI_AGENT, ActivityKind.DOCUMENT_SYNC)) Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    message, style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == ActivityState.FAILED && !mono) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.state == ActivityState.RUNNING) {
            if (cancel != null) TextButton(onClick = cancel) { Text(strings.cancel) }
        } else {
            if (item.state == ActivityState.FAILED && item.reportable && onReport != null) {
                TextButton(onClick = { onReport(item) }) { Text(strings.activityReport) }
            }
            TextButton(onClick = { onDismiss(item.id) }) { Text(strings.dismiss) }
        }
    }
}
