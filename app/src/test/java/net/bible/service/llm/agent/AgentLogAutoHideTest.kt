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

package net.bible.service.llm.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLogAutoHideTest {

    @Test
    fun settingOff_neverHides() {
        for (reason in listOf(
            AgentStopReason.COMPLETED, AgentStopReason.ERROR, AgentStopReason.CANCELLED, null
        )) {
            assertFalse(shouldAutoHideAgentLog(settingEnabled = false, reason = reason))
        }
    }

    @Test
    fun settingOn_hidesOnCompleted() {
        assertTrue(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.COMPLETED))
    }

    @Test
    fun settingOn_hidesOnCancelled() {
        assertTrue(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.CANCELLED))
    }

    @Test
    fun settingOn_doesNotHideOnError() {
        assertFalse(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.ERROR))
    }

    @Test
    fun settingOn_doesNotHideOnNullReason() {
        assertFalse(shouldAutoHideAgentLog(settingEnabled = true, reason = null))
    }
}
