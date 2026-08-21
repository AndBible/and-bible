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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

/**
 * The live, collapsible bottom agent-log panel (mirrors classic `AgentLogWidget` +
 * `AgentLogAdapter`). Self-hiding: renders nothing when [state]`.visible` is false, so a host may
 * always slot it into its layout.
 *
 * **Header**: a pulsing (while running) robot status icon — host-supplied as [statusIcon] since
 * this module cannot reference `R.drawable`; the caller resolves `icon_robot`, the JS side's
 * `faRobot`, already shared with the Compose drawer/overflow/pane menus — the latest status
 * message (or the idle label), an optional cumulative session cost, an expand/collapse toggle,
 * and a trailing stop-while-running / close-while-idle button.
 *
 * **Body** (only while [state]`.expanded`): a scrollable list filling whatever height is left below
 * the handle and header (round 12b §4 replaced its own 240dp cap with [panelHeightDp]) — a
 * model-selector row first, then one row per log entry (leading kind icon, message/details/cost,
 * trailing status icon, an optional "view raw" link).
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
    statusIcon: Painter,
    /**
     * Round 12b §3: consume the bottom navigation-bar inset because this panel is the bottom-most
     * visible bar (`agentLogOwnsNavBarInset`). Applied to the inner `Column`, not the `Surface`, so
     * the panel's own `surfaceColorAtElevation(3.dp)` and its rounded top corners extend flat into
     * the navigation-bar strip while the content clears it. `ime` is excluded for the same reason
     * documented on `SpeakTransportBar.applyNavBarInset`.
     */
    applyNavBarInset: Boolean = false,
    /**
     * The panel's rendered height in dp, or `null` to lay out intrinsically (which is what a
     * collapsed panel does, exactly as before round 12b). The caller computes it with
     * `agentPanelHeight(state, collapsedDp, maxDp)`.
     */
    panelHeightDp: Float?,
    /** One drag step on the handle, positive upward. Wired to `AgentLogController.onHeightDrag`. */
    onHeightDrag: (dragUpDp: Float) -> Unit,
    /**
     * Reports this panel's height, in dp, WHILE COLLAPSED. The host reserves exactly that much
     * in-flow space so the collapsed panel covers nothing, and keeps reserving it while the panel is
     * expanded so the expanded panel overlays instead of reflowing the panes (round 12b §4).
     *
     * Measured rather than assumed: the header is `heightIn(min = 48.dp)` and its status text can
     * wrap, so 48dp is a floor and not the height, and a hard-coded reservation would either clip a
     * two-line status or let the collapsed panel cover content.
     */
    onCollapsedHeightMeasured: (Float) -> Unit,
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

    val density = LocalDensity.current
    Surface(
        modifier = Modifier.fillMaxWidth()
            .then(if (panelHeightDp != null) Modifier.height(panelHeightDp.dp) else Modifier)
            .onSizeChanged { size ->
                // Only while collapsed: an expanded panel's height is the dragged value, and
                // reporting it would make the in-flow reservation grow with the drag -- reflowing
                // the panes, which is the whole thing the overlay exists to avoid.
                if (!state.expanded) onCollapsedHeightMeasured(with(density) { size.height.toDp() }.value)
            },
        // Rounded top corners + a shadow read as an M3 bottom surface rising over the panes.
        // Classic's equivalents are a 1dp top divider plus android:elevation="8dp" on the root
        // (agent_log_widget.xml:25-32); the divider is redundant next to corners and a shadow and
        // is deliberately not ported. This is NOT a real bottom sheet -- see the round 6 spec
        // section 4.4 for the four reasons it cannot be one.
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(
                    if (applyNavBarInset) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                    } else Modifier
                )
        ) {
            if (state.expanded) {
                AgentLogDragHandle(onClick = onToggleExpanded, onDrag = onHeightDrag)
            }
            AgentLogHeader(
                snapshot = state.snapshot,
                statusIcon = statusIcon,
                expanded = state.expanded,
                animateStatus = animateStatus,
                onToggleExpanded = onToggleExpanded,
                onStop = onStop,
                onClose = onClose,
            )
            if (state.expanded) {
                AgentLogBody(
                    snapshot = state.snapshot,
                    statusIcon = statusIcon,
                    onModelSelectorClick = onModelSelectorClick,
                    onRawLogClick = onRawLogClick,
                    // The panel's height is now the user's, so the log takes what is left after the
                    // handle and header rather than capping itself at a fixed 240dp.
                    modifier = Modifier.weight(1f),
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
    statusIcon: Painter,
    expanded: Boolean,
    animateStatus: Boolean,
    onToggleExpanded: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
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
        // Classic puts the expand toggle FIRST, in the drag-handle position
        // (agent_log_widget.xml:47-60), and maps collapsed -> ic_expand_less (UP): the panel is
        // bottom-anchored and expanding grows it upward. The five downward-expanding Compose
        // surfaces keep the opposite mapping -- see the plan's global constraints.
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = strings.agentLogExpand,
            )
        }
        // Classic makes the robot, the status text AND the cost text expand/collapse toggles
        // (AgentLogWidget.kt: statusIcon/statusText/headerCostText all bind `toggleListener`) --
        // the XML comments at agent_log_widget.xml:44-47 and :94-96 exist to stop those neighbours
        // stealing the caret's and the close button's taps. The port originally had only the caret
        // button and, after that was fixed, still left the cost text as a dead strip between the
        // toggle band and the stop/close button (whole-branch review, Minor 6) -- it is now part of
        // the same band, still its own un-weighted `Text` so it never joins the status text's
        // ellipsis.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = statusIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
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

/**
 * An M3-spec drag handle (32x4dp, `onSurfaceVariant` at 40%), shown ONLY while the panel is
 * expanded: collapsed, the header is already `heightIn(min = 48.dp)` and a handle would add its own
 * ~20dp (4dp pill + 8dp top/bottom padding) for no gain, while expanded that same ~20dp is what
 * makes the surface read as a sheet.
 *
 * It is **draggable** (resizing the panel, [onDrag], positive upward) and **tappable** (collapsing
 * it, [onClick]). Round 12b §4 added the drag: round 6 had decided against it, and the maintainer's
 * objection is exactly right — a handle that renders the universal drag affordance and cannot be
 * dragged lies about the surface.
 *
 * The panel is still NOT an M3 bottom sheet, and every reason round 6 gave still holds: the reading
 * view's one `BottomSheetScaffold` is taken by the F6 search results, a second sheet would have to
 * be a `ModalBottomSheet` (a `Popup`, invisible to Roborazzi -- this repo's only UI regression
 * gate), modality would drop an unrequested scrim every time the panel auto-shows on a run start,
 * and it would nest the model-picker dialog inside a sheet. A hand-rolled gesture avoids all four.
 *
 * Drawn by hand rather than with `BottomSheetDefaults.DragHandle` so this file opts into no
 * experimental Material 3 API. Labelled via `clickable`'s own `onClickLabel` (whole-branch review,
 * Minor 5) rather than `clearAndSetSemantics {}` with no label: `clearAndSetSemantics` is documented
 * to clear DESCENDANT semantics, and it is not established that it also clears semantics
 * contributed by another modifier on the SAME node (here `clickable`'s own click action) -- if it
 * doesn't, an unlabelled clear leaves this announcing as an unlabelled button, worse than the
 * labelled caret it was meant to defer to. Reusing [strings]`.agentLogExpand` (the caret's own
 * description) is correct either way: same action, same label.
 */
@Composable
private fun AgentLogDragHandle(onClick: () -> Unit, onDrag: (dragUpDp: Float) -> Unit) {
    val strings = LocalStrings.current
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { deltaPx ->
                    // Compose's vertical delta is positive DOWNWARD; the reducer reads positive as
                    // "grow", so the sign is flipped once, here, at the boundary.
                    onDrag(-with(density) { deltaPx.toDp() }.value)
                },
                orientation = Orientation.Vertical,
            )
            .clickable(onClickLabel = strings.agentLogExpand, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(2.dp),
                )
        )
    }
}

@Composable
private fun AgentLogBody(
    snapshot: AgentLogSnapshot,
    statusIcon: Painter,
    onModelSelectorClick: () -> Unit,
    onRawLogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onModelSelectorClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Classic's AgentLogAdapter draws icon_robot on this row (AgentLogAdapter.kt:110).
                Icon(
                    painter = statusIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = strings.agentLogModelSelector(snapshot.defaultModelText ?: strings.agentLogModelNotConfigured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
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
