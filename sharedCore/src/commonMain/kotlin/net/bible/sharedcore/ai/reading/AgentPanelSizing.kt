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

package net.bible.sharedcore.ai.reading

/**
 * Every arithmetic decision the agent panel's drag gesture makes (round 12b §4).
 *
 * It lives here, in pure code, for one hard reason: this repo has no Compose UI/gesture test harness
 * and cannot add one (`compose-ui-test` is not installable under the container's strict egress), so
 * nothing can drive the gesture in a test. Keeping the numbers out of the modifier is what makes the
 * behaviour testable at all; the modifier is a shell that turns a drag delta into one call.
 *
 * All values are dp as plain `Float` so this file stays free of Compose's `Dp` — `:sharedCore` has no
 * Compose dependency, and the UI converts at the boundary.
 */

/**
 * The expanded height a panel starts at before the user has dragged it: the drag handle (4dp pill +
 * 8dp top/bottom padding), the header's `heightIn(min = 48.dp)`, and the 240dp body cap that classic
 * `AgentLogWidget`'s fixed 200dp RecyclerView became. Stated as the sum so the derivation survives.
 */
const val AGENT_PANEL_DEFAULT_EXPANDED_DP: Float = 20f + 48f + 240f

/**
 * How close to the collapsed height a downward drag has to land before it collapses the panel rather
 * than leaving it expanded at a uselessly small height. Chosen to match M3's minimum touch slop
 * region rather than tuned: anything smaller makes the last few dp of the drag feel sticky.
 */
const val AGENT_PANEL_COLLAPSE_SNAP_DP: Float = 24f

/**
 * Confines [requestedDp] to what the layout can actually give. [maxDp] can legitimately come in
 * BELOW [collapsedDp] — a short landscape reading area — in which case the collapsed height wins,
 * because a panel shorter than its own header is not a state the UI can render.
 */
fun clampAgentPanelHeight(requestedDp: Float, collapsedDp: Float, maxDp: Float): Float =
    requestedDp.coerceIn(collapsedDp, maxOf(collapsedDp, maxDp))

/** Whether a drag that asked for [requestedDp] should collapse the panel instead. */
fun shouldCollapseAfterDrag(requestedDp: Float, collapsedDp: Float): Boolean =
    requestedDp < collapsedDp + AGENT_PANEL_COLLAPSE_SNAP_DP

/** The height an expanded panel takes when the user has not dragged one. */
fun initialAgentPanelHeight(collapsedDp: Float, maxDp: Float): Float =
    clampAgentPanelHeight(AGENT_PANEL_DEFAULT_EXPANDED_DP, collapsedDp, maxDp)

/**
 * The panel's rendered height. Collapsed, it is [collapsedDp] regardless of any remembered drag —
 * and a remembered height is re-clamped on every call, so a height dragged in portrait cannot
 * overflow a shorter landscape reading area after a rotation.
 */
fun agentPanelHeight(state: AgentLogUiState, collapsedDp: Float, maxDp: Float): Float =
    if (!state.expanded) collapsedDp
    else clampAgentPanelHeight(state.heightDp ?: AGENT_PANEL_DEFAULT_EXPANDED_DP, collapsedDp, maxDp)
