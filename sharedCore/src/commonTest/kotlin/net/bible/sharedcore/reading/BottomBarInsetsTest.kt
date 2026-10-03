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

package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarInsetsTest {
    @Test fun theAgentPanelOwnsTheInsetOnlyWhenItIsTheBottomMostBar() {
        assertTrue(agentLogOwnsNavBarInset(agentLogVisible = true, speakBarVisible = false))
        assertFalse(
            agentLogOwnsNavBarInset(agentLogVisible = true, speakBarVisible = true),
            "the speak bar sits below the panel, so it owns the inset when both are up",
        )
        assertFalse(agentLogOwnsNavBarInset(agentLogVisible = false, speakBarVisible = false))
        assertFalse(agentLogOwnsNavBarInset(agentLogVisible = false, speakBarVisible = true))
    }

    @Test fun theSpeakBarDrawsItsOwnTopEdgeOnlyWhenNothingSitsOnTopOfIt() {
        assertTrue(
            speakBarOwnsTopEdge(agentLogVisible = false),
            "alone at the bottom, the bar is the topmost bottom surface and keeps its corners and shadow",
        )
        assertFalse(
            speakBarOwnsTopEdge(agentLogVisible = true),
            "the agent panel sits directly on the bar, so the panel owns the edge and the bar goes square",
        )
    }

    /**
     * The two rules are about the OPPOSITE ends of the same pair and must not be conflated: the
     * navigation-bar inset belongs to the BOTTOM-most surface, the corners and shadow to the
     * TOP-most one. With both bars visible the speak bar owns the inset and does NOT own the top
     * edge — the one configuration in which a copy-pasted predicate would be silently wrong.
     */
    @Test fun theInsetOwnerAndTheEdgeOwnerAreDifferentSurfacesWhenBothBarsAreUp() {
        assertFalse(agentLogOwnsNavBarInset(agentLogVisible = true, speakBarVisible = true))
        assertFalse(speakBarOwnsTopEdge(agentLogVisible = true))
    }

    @Test fun theRailOwnsTheInsetOnlyWhenNoBottomBarIsUp() {
        assertTrue(railOwnsNavBarInset(agentLogVisible = false, speakBarVisible = false))
        assertFalse(railOwnsNavBarInset(agentLogVisible = true, speakBarVisible = false), "F67")
        assertFalse(railOwnsNavBarInset(agentLogVisible = false, speakBarVisible = true), "the Speak-bar twin of F67")
        assertFalse(railOwnsNavBarInset(agentLogVisible = true, speakBarVisible = true))
    }

    /** Exactly one surface owns the bottom inset in every combination. */
    @Test fun exactlyOneSurfaceOwnsTheBottomInset() {
        for (agent in listOf(false, true)) for (speak in listOf(false, true)) {
            val owners = listOf(
                railOwnsNavBarInset(agent, speak),
                agentLogOwnsNavBarInset(agent, speak),
                speak, // the speak bar owns it whenever visible (ReadingViewScreen passes speakBarVisible)
            ).count { it }
            assertEquals(1, owners, "agent=$agent speak=$speak")
        }
    }
}
