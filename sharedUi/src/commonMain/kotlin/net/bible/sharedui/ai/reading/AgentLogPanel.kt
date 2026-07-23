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

package net.bible.sharedui.ai.reading

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.reading.AgentLogEntryVd
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentLogUiState
import net.bible.sharedcore.ai.reading.LogEntryKind
import net.bible.sharedcore.ai.reading.LogEntryStatus
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.strings.LocalStrings

/** Bounded height for the expanded entry list, matching classic `AgentLogWidget`'s fixed-height
 *  RecyclerView (200dp) — the log scrolls inside the panel rather than growing it off-screen. */
private val maxBodyHeight = 240.dp

/**
 * The live, collapsible bottom agent-log panel (mirrors classic `AgentLogWidget` +
 * `AgentLogAdapter`). Self-hiding: renders nothing when [state]`.visible` is false, so a host may
 * always slot it into its layout.
 *
 * **Header**: a pulsing (while running) robot status icon, the latest status message (or the idle
 * label), an optional cumulative session cost, an expand/collapse toggle, and a trailing
 * stop-while-running / close-while-idle button.
 *
 * **Body** (only while [state]`.expanded`): a height-bounded, scrollable list — a model-selector
 * row first, then one row per log entry (leading kind icon, message/details/cost, trailing status
 * icon, an optional "view raw" link).
 *
 * **Model picker**: when [state]`.modelPicker` is non-null, an [AbListChoiceDialog] radio list of
 * the configured models is shown on top (a plain `AlertDialog` under the hood — safe for Roborazzi,
 * unlike `DropdownMenu`/`Popup`/`ModalBottomSheet`).
 *
 * All colour is via [MaterialTheme.colorScheme] roles, never a hardcoded colour, so `AbTheme`'s
 * BW/e-ink monochrome modes degrade every hue to gray for free. [animateStatus] gates the pulse
 * animation (callers pass `false` for deterministic golden/screenshot captures).
 */
@Composable
fun AgentLogPanel(
    state: AgentLogUiState,
    animateStatus: Boolean,
    onToggleExpanded: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onModelSelectorClick: () -> Unit,
    onModelChosen: (String) -> Unit,
    onModelPickerDismiss: () -> Unit,
    onRawLogClick: () -> Unit,
) {
    if (!state.visible) return
    val strings = LocalStrings.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AgentLogHeader(
                snapshot = state.snapshot,
                expanded = state.expanded,
                animateStatus = animateStatus,
                onToggleExpanded = onToggleExpanded,
                onStop = onStop,
                onClose = onClose,
            )
            if (state.expanded) {
                AgentLogBody(
                    snapshot = state.snapshot,
                    onModelSelectorClick = onModelSelectorClick,
                    onRawLogClick = onRawLogClick,
                )
            }
        }
    }

    val modelPicker = state.modelPicker
    if (modelPicker != null) {
        AbListChoiceDialog(
            title = strings.agentLogSelectModel,
            choices = modelPicker.map { SettingsItem.Choice(it.id, modelChoiceLabel(it.isDefault, it.modelId, it.providerName)) },
            selectedValue = modelPicker.firstOrNull { it.isDefault }?.id ?: "",
            onSelect = onModelChosen,
            onDismiss = onModelPickerDismiss,
        )
    }
}

private fun modelChoiceLabel(isDefault: Boolean, modelId: String, providerName: String): String =
    "${if (isDefault) "★ " else ""}$modelId — $providerName"

@Composable
private fun AgentLogHeader(
    snapshot: AgentLogSnapshot,
    expanded: Boolean,
    animateStatus: Boolean,
    onToggleExpanded: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pulseScale: Float = if (snapshot.running && animateStatus) {
            val transition = rememberInfiniteTransition(label = "agentLogStatusPulse")
            val animatedScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "agentLogStatusPulseScale",
            )
            animatedScale
        } else {
            1.0f
        }
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale),
        )
        Text(
            text = snapshot.statusText ?: strings.agentLogIdle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        val headerCost = snapshot.headerCost
        if (headerCost != null) {
            Text(
                text = headerCost,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (snapshot.running) {
            IconButton(onClick = onStop) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = strings.agentLogStop)
            }
        } else {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = strings.agentLogClose)
            }
        }
    }
}

@Composable
private fun AgentLogBody(
    snapshot: AgentLogSnapshot,
    onModelSelectorClick: () -> Unit,
    onRawLogClick: () -> Unit,
) {
    val strings = LocalStrings.current
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = maxBodyHeight)) {
        item {
            Text(
                text = strings.agentLogModelSelector(snapshot.defaultModelText ?: strings.agentLogModelNotConfigured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onModelSelectorClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(snapshot.entries, key = { it.id }) { entry ->
            AgentLogEntryRow(entry = entry, onRawLogClick = onRawLogClick)
        }
    }
}

@Composable
private fun AgentLogEntryRow(entry: AgentLogEntryVd, onRawLogClick: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = kindIcon(entry.kind),
            contentDescription = null,
            tint = kindColor(entry.kind),
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = entry.message, style = MaterialTheme.typography.bodyMedium)
            val details = entry.details
            if (details != null) {
                Text(text = details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val cost = entry.cost
            if (cost != null) {
                Text(text = cost, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.showRawLogLink) {
                Text(
                    text = strings.agentLogViewRaw,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRawLogClick).padding(top = 4.dp),
                )
            }
        }
        // Hidden for INFO+COMPLETED, matching classic AgentLogAdapter.bindLogEntry.
        if (!(entry.kind == LogEntryKind.INFO && entry.status == LogEntryStatus.COMPLETED)) {
            Icon(
                imageVector = statusIcon(entry.status),
                contentDescription = null,
                tint = statusColor(entry.status),
                modifier = Modifier.size(18.dp).padding(start = 8.dp, top = 2.dp),
            )
        }
    }
}

private fun kindIcon(kind: LogEntryKind): ImageVector = when (kind) {
    LogEntryKind.INFO -> Icons.Filled.Info
    LogEntryKind.ACTION -> Icons.Filled.Build
    LogEntryKind.PERMISSION_REQUEST -> Icons.Filled.Security
    LogEntryKind.ERROR -> Icons.Filled.Error
    LogEntryKind.LLM_COMMENT -> Icons.Filled.ChatBubbleOutline
}

@Composable
private fun kindColor(kind: LogEntryKind): Color = when (kind) {
    LogEntryKind.ERROR -> MaterialTheme.colorScheme.error
    LogEntryKind.ACTION -> MaterialTheme.colorScheme.primary
    LogEntryKind.PERMISSION_REQUEST -> MaterialTheme.colorScheme.tertiary
    LogEntryKind.LLM_COMMENT -> MaterialTheme.colorScheme.secondary
    LogEntryKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusIcon(status: LogEntryStatus): ImageVector = when (status) {
    LogEntryStatus.PENDING -> Icons.Filled.HourglassEmpty
    LogEntryStatus.APPROVED -> Icons.Filled.CheckCircle
    LogEntryStatus.DENIED -> Icons.Filled.Cancel
    LogEntryStatus.COMPLETED -> Icons.Filled.CheckCircle
    LogEntryStatus.FAILED -> Icons.Filled.Error
}

@Composable
private fun statusColor(status: LogEntryStatus): Color = when (status) {
    LogEntryStatus.FAILED, LogEntryStatus.DENIED -> MaterialTheme.colorScheme.error
    LogEntryStatus.COMPLETED, LogEntryStatus.APPROVED -> MaterialTheme.colorScheme.primary
    LogEntryStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}
