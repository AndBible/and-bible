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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the single-row tool-call log behaviour introduced for issue #3773.
 *
 * Before: ToolCalling and ToolCompleted produced two separate log entries (hourglass + tick).
 * After: ToolCalling adds a PENDING entry tagged with toolCallId; ToolCompleted finds that
 * entry via toolCallId and updates it in place (status/message/details).
 */
class AgentSessionLogTest {

    private fun newSession() = AgentSession(IdType())

    @Test
    fun `action entry stores toolCallId for later correlation`() {
        val session = newSession()
        session.addLogEntry(AgentLogEntry.action("Tool: X", details = "args", toolCallId = "call-1"))

        assertEquals(1, session.logEntries.size)
        val entry = session.logEntries.single()
        assertEquals("call-1", entry.toolCallId)
        assertEquals(EntryStatus.PENDING, entry.status)
        assertEquals(LogEntryType.ACTION, entry.type)
    }

    @Test
    fun `updateActionEntry updates existing entry in place and keeps log size at one`() {
        val session = newSession()
        session.addLogEntry(AgentLogEntry.action("Tool: X", details = "args", toolCallId = "call-1"))

        val updated = session.updateActionEntry("call-1", "Tool: X", "result body", EntryStatus.COMPLETED)

        assertTrue(updated)
        assertEquals(1, session.logEntries.size)
        val entry = session.logEntries.single()
        assertEquals(EntryStatus.COMPLETED, entry.status)
        assertEquals("Tool: X", entry.message)
        assertEquals("result body", entry.details)
    }

    @Test
    fun `updateActionEntry with FAILED status switches message and status`() {
        val session = newSession()
        session.addLogEntry(AgentLogEntry.action("Tool: X", details = "args", toolCallId = "call-1"))

        val updated = session.updateActionEntry("call-1", "Tool X failed", "boom", EntryStatus.FAILED)

        assertTrue(updated)
        assertEquals(1, session.logEntries.size)
        val entry = session.logEntries.single()
        assertEquals(EntryStatus.FAILED, entry.status)
        assertEquals("Tool X failed", entry.message)
        assertEquals("boom", entry.details)
    }

    @Test
    fun `updateActionEntry returns false when toolCallId not found`() {
        val session = newSession()
        // Different toolCallId → no match
        session.addLogEntry(AgentLogEntry.action("Tool: X", toolCallId = "call-other"))

        val updated = session.updateActionEntry("call-missing", "msg", "details", EntryStatus.COMPLETED)

        assertFalse(updated)
        // Existing entry untouched
        assertEquals(1, session.logEntries.size)
        assertEquals(EntryStatus.PENDING, session.logEntries.single().status)
    }

    @Test
    fun `updateActionEntry ignores entries with null toolCallId`() {
        val session = newSession()
        // Non-tool entries (info etc.) have no toolCallId
        session.addLogEntry(AgentLogEntry.info("Some info"))

        val updated = session.updateActionEntry("call-1", "msg", "details", EntryStatus.COMPLETED)

        assertFalse(updated)
        assertEquals(1, session.logEntries.size)
        assertNull(session.logEntries.single().toolCallId)
    }

    @Test
    fun `multiple concurrent tool calls are tracked independently by toolCallId`() {
        val session = newSession()
        session.addLogEntry(AgentLogEntry.action("Tool: A", toolCallId = "call-a"))
        session.addLogEntry(AgentLogEntry.action("Tool: B", toolCallId = "call-b"))

        // Complete B first, then A — out-of-order completion is plausible in async flows.
        assertTrue(session.updateActionEntry("call-b", "Tool: B", "result-b", EntryStatus.COMPLETED))
        assertTrue(session.updateActionEntry("call-a", "Tool A failed", "err-a", EntryStatus.FAILED))

        assertEquals(2, session.logEntries.size)
        val byId = session.logEntries.associateBy { it.toolCallId }
        assertEquals(EntryStatus.COMPLETED, byId["call-b"]!!.status)
        assertEquals("result-b", byId["call-b"]!!.details)
        assertEquals(EntryStatus.FAILED, byId["call-a"]!!.status)
        assertEquals("err-a", byId["call-a"]!!.details)
    }

    @Test
    fun `info entry still uses default null toolCallId`() {
        val entry = AgentLogEntry.info("hello")
        assertNull(entry.toolCallId)
        assertEquals(EntryStatus.COMPLETED, entry.status)
    }

    @Test
    fun `action factory without toolCallId stays backward-compatible`() {
        val entry = AgentLogEntry.action("Tool: X")
        assertNull(entry.toolCallId)
        assertEquals(EntryStatus.PENDING, entry.status)
        assertNotNull(entry.id)
    }
}
