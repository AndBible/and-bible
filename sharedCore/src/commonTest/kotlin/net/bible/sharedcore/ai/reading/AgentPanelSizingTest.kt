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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentPanelSizingTest {

    @Test fun clamp_confinesToTheCollapsedToMaxRange() {
        assertEquals(200f, clampAgentPanelHeight(requestedDp = 200f, collapsedDp = 48f, maxDp = 600f))
        assertEquals(48f, clampAgentPanelHeight(requestedDp = 10f, collapsedDp = 48f, maxDp = 600f))
        assertEquals(600f, clampAgentPanelHeight(requestedDp = 9000f, collapsedDp = 48f, maxDp = 600f))
    }

    /** A short landscape reading area can be smaller than the collapsed panel; that must not render. */
    @Test fun clamp_aMaxBelowTheCollapsedHeightCannotProduceAnImpossiblePanel() {
        assertEquals(48f, clampAgentPanelHeight(requestedDp = 300f, collapsedDp = 48f, maxDp = 20f))
    }

    @Test fun snap_draggingIntoTheThresholdCollapsesInsteadOfShrinking() {
        assertTrue(shouldCollapseAfterDrag(requestedDp = 60f, collapsedDp = 48f))
        assertTrue(shouldCollapseAfterDrag(requestedDp = 10f, collapsedDp = 48f))
        assertFalse(shouldCollapseAfterDrag(requestedDp = 72f, collapsedDp = 48f))
        assertFalse(shouldCollapseAfterDrag(requestedDp = 400f, collapsedDp = 48f))
    }

    @Test fun defaultHeight_isHandlePlusHeaderPlusClassicListHeight() {
        assertEquals(20f + 48f + 240f, AGENT_PANEL_DEFAULT_EXPANDED_DP)
    }

    @Test fun initialHeight_isTheDefaultClampedIntoTheAvailableSpace() {
        assertEquals(AGENT_PANEL_DEFAULT_EXPANDED_DP, initialAgentPanelHeight(collapsedDp = 48f, maxDp = 600f))
        assertEquals(150f, initialAgentPanelHeight(collapsedDp = 48f, maxDp = 150f))
    }

    @Test fun height_collapsedIsTheCollapsedHeightWhateverWasDragged() {
        val state = AgentLogUiState(visible = true, expanded = false, heightDp = 400f)
        assertEquals(48f, agentPanelHeight(state, collapsedDp = 48f, maxDp = 600f))
    }

    @Test fun height_expandedWithNoDraggedHeightUsesTheDefault() {
        val state = AgentLogUiState(visible = true, expanded = true, heightDp = null)
        assertEquals(AGENT_PANEL_DEFAULT_EXPANDED_DP, agentPanelHeight(state, collapsedDp = 48f, maxDp = 600f))
    }

    /** Rotating to landscape shrinks the reading area under a height dragged in portrait. */
    @Test fun height_expandedReClampsARememberedHeightAgainstTheCurrentMaximum() {
        val state = AgentLogUiState(visible = true, expanded = true, heightDp = 500f)
        assertEquals(300f, agentPanelHeight(state, collapsedDp = 48f, maxDp = 300f))
    }

    /**
     * The ceiling is the reading area plus the collapsed panel's own reservation, which together are
     * the distance from the bottom of the toolbar to the top of the bottom bars — see
     * [agentPanelDragCeiling]'s kdoc for why that identity holds.
     */
    @Test fun dragCeiling_isTheReadingAreaPlusTheCollapsedReservation() {
        assertEquals(648f, agentPanelDragCeiling(splitDp = 600f, collapsedDp = 48f))
    }

    /**
     * The reservation is measured, not assumed, so it is 0 for the first frame of a showing (and for
     * the whole of a showing that starts expanded, since the collapsed height is only reported while
     * collapsed). The ceiling must still be the reading area rather than nothing.
     */
    @Test fun dragCeiling_beforeTheReservationHasBeenMeasuredIsJustTheReadingArea() {
        assertEquals(600f, agentPanelDragCeiling(splitDp = 600f, collapsedDp = 0f))
    }
}
