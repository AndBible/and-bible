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
 * It lives here, in pure code, as a deliberate design choice: the numbers are worth asserting
 * directly. A pure test names the height it expects, runs in milliseconds and fails pointing at the
 * rule, where a gesture test would assert the same arithmetic through pointer synthesis and layout.
 * The modifier is left a shell that turns a drag delta into one call.
 *
 * It is NOT that nothing could drive the gesture (whole-branch review, Blocker 2 — the earlier
 * wording here claimed exactly that). `compose-ui-test` IS available in `:app`'s unit-test source set
 * (`app/build.gradle.kts:508-509`, added 2026-08-14) and `AbSearchableOptionSheetContentTest` already
 * uses `createComposeRule()` under Robolectric. `AgentLogPanelDragGestureTest` (`:app`) now drives the
 * real handle's drag and tap, and it is a COMPLEMENT to these functions — it covers the wiring, which
 * arithmetic tests cannot see — not a substitute this file exists to replace.
 *
 * All values are dp as plain `Float` so this file stays free of Compose's `Dp` — `:sharedCore` has no
 * Compose dependency, and the UI converts at the boundary.
 */

/**
 * The expanded height a panel starts at before the user has dragged it: the drag handle (4dp pill +
 * 8dp top/bottom padding), the header's `heightIn(min = 48.dp)`, and the 240dp body cap that classic
 * `AgentLogWidget`'s fixed 200dp RecyclerView became. Stated as the sum so the derivation survives.
 *
 * CAVEAT (fix round 1, Minor 4): this is the panel's OUTER height, while the navigation-bar inset the
 * panel consumes when it is the bottom-most bar (`agentLogOwnsNavBarInset` — the common case, since
 * the speak bar is usually down) is padding INSIDE it. So the body's share is this sum minus that
 * inset, ~192dp rather than 240dp on a typical ~48dp navigation bar. Deliberately not compensated
 * for: what the default expanded height should be is a product decision, not arithmetic, and no
 * golden can show the shortfall because every capture runs `applyNavBarInset = false`.
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
    else state.heightDp?.let { clampAgentPanelHeight(it, collapsedDp, maxDp) }
        ?: initialAgentPanelHeight(collapsedDp, maxDp)

/**
 * The panel's drag ceiling: how tall it may become, given the measured height of the reading area
 * ([splitDp]) and the in-flow space the collapsed panel reserves below it ([collapsedDp]).
 *
 * The sum IS "the bottom of the toolbar". `ReadingViewScreen`'s in-flow `Column` is
 * `toolbar + split(weight 1f) + reservation + bottomBars`, of total height `H`, and the panel is an
 * overlay whose bottom edge is bottom-aligned then lifted by the bottom bars — so it sits at
 * `H - bottomBars`. A panel of this height therefore has its TOP at
 * `H - bottomBars - (splitDp + collapsedDp)`, which is exactly where the toolbar ends. In full-screen
 * mode there is no toolbar and the expression degenerates to the top of the reading area, which is
 * the same rule with a zero-height toolbar.
 *
 * It lives here, with the rest of the panel's arithmetic, for the same reason as everything else in
 * this file (fix round 1, Minor 6): as one inline `+` inside a composable it was the last load-bearing
 * number with no test behind it, and a number is best asserted as a number. (Not because `:sharedCore`
 * is the only testable surface — see the file kdoc's Blocker 2 correction.)
 */
fun agentPanelDragCeiling(splitDp: Float, collapsedDp: Float): Float = splitDp + collapsedDp
