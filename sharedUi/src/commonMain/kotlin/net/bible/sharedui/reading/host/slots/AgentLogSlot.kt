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

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.agentPanelHeight
import net.bible.sharedui.ai.reading.AgentLogPanel

/**
 * Host seam supplying the agent log's status glyph (Android: `R.drawable.icon_robot`), the same
 * pattern as [net.bible.sharedui.reading.LocalPinIcon]. Defaults to `error(...)` so a render without
 * a provider fails loudly instead of drawing no icon.
 */
@kotlin.native.HiddenFromObjC
val LocalAgentStatusIcon = staticCompositionLocalOf<@Composable () -> Painter> {
    error("No LocalAgentStatusIcon provided — wrap content in ProvideAppLocals (or the golden harness).")
}

/**
 * The agent-log panel, pre-built over the live [agentLog] controller. `collapsedDp` is
 * `ReadingViewScreen`'s measurement, handed down rather than remembered here: this lambda leaves the
 * composition on every hide, and a panel that auto-shows while still `expanded` never reports a
 * collapsed height, so a local copy would stay 0 for that whole showing.
 */
@kotlin.native.HiddenFromObjC
@Composable
internal fun AgentLogSlotContent(
    agentLog: AgentLogController,
    disableAnimations: Boolean,
    applyNavBarInset: Boolean,
    maxHeightDp: Float,
    collapsedDp: Float,
    onCollapsedHeightMeasured: (Float) -> Unit,
) {
    val agentLogUiState by agentLog.state.collectAsState()
    AgentLogPanel(
        agentLogUiState,
        animateStatus = !disableAnimations,
        statusIcon = LocalAgentStatusIcon.current(),
        applyNavBarInset = applyNavBarInset,
        panelHeightDp = if (agentLogUiState.expanded) {
            agentPanelHeight(agentLogUiState, collapsedDp, maxHeightDp)
        } else null,   // collapsed lays out intrinsically, exactly as before round 12b
        onHeightDragStarted = agentLog::onHeightDragStarted,
        onHeightDrag = { dragUpDp -> agentLog.onHeightDrag(dragUpDp, collapsedDp, maxHeightDp) },
        onCollapsedHeightMeasured = onCollapsedHeightMeasured,
        onToggleExpanded = agentLog::toggleExpanded,
        onStop = agentLog::stop,
        onClose = agentLog::hide,
        onModelSelectorClick = agentLog::onModelSelectorClick,
        onModelChosen = agentLog::onModelChosen,
        onModelPickerDismiss = agentLog::onModelPickerDismiss,
        onRawLogClick = agentLog::onRawLogClick,
    )
}
