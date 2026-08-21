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
}
