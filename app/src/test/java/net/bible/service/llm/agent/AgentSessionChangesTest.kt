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

import net.bible.android.database.IdType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionChangesTest {
    private val ws = IdType()
    private val seen = mutableListOf<AgentSessionChange>()
    private fun session() = AgentSession(ws) { seen += it }
    private fun ctx() = AgentContext(promptId = IdType())

    @Test fun tryStartEmitsTheStartEntryThenRunning() {
        val s = session()
        assertTrue(s.tryStart(ctx()))
        assertEquals(2, seen.size)
        val log = seen[0] as AgentSessionChange.LogUpdated
        assertEquals("Agent started", log.entry.message)
        assertEquals(AgentSessionChange.StatusChanged(ws, isRunning = true), seen[1])
    }

    @Test fun aLostTryStartEmitsNothing() {
        val s = session()
        s.tryStart(ctx())
        seen.clear()
        assertFalse(s.tryStart(ctx()))
        assertEquals(emptyList<AgentSessionChange>(), seen)
    }

    @Test fun stopWithAMessageEmitsTheEntryBeforeTheStatus() {
        val s = session()
        s.tryStart(ctx())
        seen.clear()
        s.stop("boom", AgentStopReason.ERROR)
        assertEquals(2, seen.size)
        assertEquals("boom", (seen[0] as AgentSessionChange.LogUpdated).entry.message)
        assertEquals(AgentSessionChange.StatusChanged(ws, false, AgentStopReason.ERROR), seen[1])
    }

    @Test fun stopWithoutAMessageEmitsOnlyTheStatus() {
        val s = session()
        s.tryStart(ctx())
        seen.clear()
        s.stop()
        assertEquals(listOf<AgentSessionChange>(AgentSessionChange.StatusChanged(ws, false, AgentStopReason.CANCELLED)), seen)
    }

    @Test fun eachEntryMutationEmitsOneLogUpdatedWithTheMutatedEntry() {
        val s = session()
        val entry = AgentLogEntry.info("x")
        s.addLogEntry(entry)
        assertEquals(1, seen.size)
        assertTrue((seen.single() as AgentSessionChange.LogUpdated).entry === entry)

        s.updateEntryStatus(entry.id, EntryStatus.FAILED)
        assertEquals(2, seen.size)
        assertTrue((seen.last() as AgentSessionChange.LogUpdated).entry === entry)
        assertEquals(EntryStatus.FAILED, entry.status)

        s.setLastEntryCost("0.01", isTotalCost = true)
        assertEquals(3, seen.size)
        assertTrue((seen.last() as AgentSessionChange.LogUpdated).entry === entry)
        assertEquals("0.01", entry.costInfo)
        assertTrue(entry.isTotalCost)
    }

    @Test fun updateActionEntryEmitsTheSuccessfullyMutatedEntry() {
        val s = session()
        val entry = AgentLogEntry.action("Tool: X", details = "args", toolCallId = "call-1")
        s.addLogEntry(entry)
        seen.clear()

        assertTrue(s.updateActionEntry("call-1", "Tool: X completed", "result", EntryStatus.COMPLETED))

        assertEquals(listOf<AgentSessionChange>(AgentSessionChange.LogUpdated(ws, entry)), seen)
        assertEquals("Tool: X completed", entry.message)
        assertEquals("result", entry.details)
        assertEquals(EntryStatus.COMPLETED, entry.status)
    }

    @Test fun aMissingEntryEmitsNothing() {
        val s = session()
        s.updateEntryStatus(IdType(), EntryStatus.COMPLETED)
        assertFalse(s.updateActionEntry("nope", "m", null, EntryStatus.COMPLETED))
        assertEquals(emptyList<AgentSessionChange>(), seen)
    }

    @Test fun notifyPermissionWaitingReachesManagerSubscribers() {
        val got = mutableListOf<AgentSessionChange>()
        val sub = AgentSessionManager.changes.subscribe { got += it }
        try {
            AgentSessionManager.notifyPermissionWaiting(ws, waiting = true, toolName = "t")
        } finally {
            sub.cancel()
        }
        assertEquals(listOf<AgentSessionChange>(AgentSessionChange.PermissionWaiting(ws, true, "t")), got)
    }
}
