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
package net.bible.android.view.activity.ai

import android.os.Looper
import net.bible.android.database.IdType
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentPermission
import net.bible.service.llm.agent.AgentSessionChange
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.AgentStopReason
import net.bible.service.llm.agent.EntryStatus
import net.bible.sharedcore.ai.reading.AgentStopReasonVd
import net.bible.sharedcore.ai.reading.LogEntryKind
import net.bible.sharedcore.ai.reading.LogEntryStatus
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers the [mapEntry] entry mapping, the [AgentStopReason.toVd] enum bridge, settings-key
 * round trips, and workspace-filtered session status changes rebuilding the snapshot.
 * DB-backed [AgentSessionServiceImpl.configuredModels] uses the same mapping as
 * `ReadingLlmServiceImplTest.configuredModels_*`, already covered there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AgentSessionServiceImplTest {

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    // --- AgentSessionManager.changes ------------------------------------------------------------

    @Test
    fun aStopForTheCurrentWorkspaceSetsTheStopReason() {
        val service = AgentSessionServiceImpl()
        val ws = CommonUtils.windowControl.windowRepository.id
        AgentSessionManager.emitChange(AgentSessionChange.StatusChanged(ws, isRunning = false, stopReason = AgentStopReason.ERROR))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AgentStopReasonVd.ERROR, service.snapshot.value.lastStopReason)
        assertFalse(service.snapshot.value.running)
    }

    @Test
    fun aRealCurrentWorkspaceSessionRefreshesRunningThenStoppedSnapshot() {
        val ws = CommonUtils.windowControl.windowRepository.id
        // Do not overwrite a session left by another test; restore this absent state in finally.
        assertNull(AgentSessionManager.getSession(ws))
        val service = AgentSessionServiceImpl()
        val session = AgentSessionManager.getOrCreateSession(ws)
        try {
            assertTrue(session.tryStart(AgentContext(promptId = IdType())))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.snapshot.value.running)
            assertNull(service.snapshot.value.lastStopReason)

            session.stop(reason = AgentStopReason.ERROR)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(service.snapshot.value.running)
            assertEquals(AgentStopReasonVd.ERROR, service.snapshot.value.lastStopReason)
        } finally {
            AgentSessionManager.clearSession(ws)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun anotherWorkspacesStopIsIgnored() {
        val service = AgentSessionServiceImpl()
        AgentSessionManager.emitChange(AgentSessionChange.StatusChanged(IdType(), isRunning = false, stopReason = AgentStopReason.ERROR))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.snapshot.value.lastStopReason)
    }

    @Test
    fun aStartClearsTheStopReason() {
        val service = AgentSessionServiceImpl()
        val ws = CommonUtils.windowControl.windowRepository.id
        AgentSessionManager.emitChange(AgentSessionChange.StatusChanged(ws, false, AgentStopReason.COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AgentStopReasonVd.COMPLETED, service.snapshot.value.lastStopReason)

        AgentSessionManager.emitChange(AgentSessionChange.StatusChanged(ws, true))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(service.snapshot.value.lastStopReason)
    }

    // --- mapEntry: all 5 kinds ------------------------------------------------------------------

    @Test
    fun mapEntry_info() {
        val entry = AgentLogEntry.info("hello", details = "det", showRawLogLink = true)
        val vd = mapEntry(entry)
        assertEquals(entry.id.toString(), vd.id)
        assertEquals(LogEntryKind.INFO, vd.kind)
        assertEquals(LogEntryStatus.COMPLETED, vd.status)
        assertEquals("hello", vd.message)
        assertEquals("det", vd.details)
        assertTrue(vd.showRawLogLink)
        assertNull(vd.cost)
    }

    @Test
    fun mapEntry_action() {
        val entry = AgentLogEntry.action("running tool", details = "args", toolCallId = "call-1")
        val vd = mapEntry(entry)
        assertEquals(LogEntryKind.ACTION, vd.kind)
        assertEquals(LogEntryStatus.PENDING, vd.status)
        assertEquals("running tool", vd.message)
        assertEquals("args", vd.details)
        assertFalse(vd.showRawLogLink)
    }

    @Test
    fun mapEntry_permissionRequest() {
        val entry = AgentLogEntry.permissionRequest(
            "may I?", AgentPermission(toolName = "write_file", description = "Write a file")
        )
        val vd = mapEntry(entry)
        assertEquals(LogEntryKind.PERMISSION_REQUEST, vd.kind)
        assertEquals(LogEntryStatus.PENDING, vd.status)
        assertEquals("may I?", vd.message)
    }

    @Test
    fun mapEntry_error() {
        val entry = AgentLogEntry.error("boom", details = "stack", showRawLogLink = true)
        val vd = mapEntry(entry)
        assertEquals(LogEntryKind.ERROR, vd.kind)
        assertEquals(LogEntryStatus.FAILED, vd.status)
        assertEquals("boom", vd.message)
        assertEquals("stack", vd.details)
        assertTrue(vd.showRawLogLink)
    }

    @Test
    fun mapEntry_comment() {
        val entry = AgentLogEntry.comment("the LLM said something")
        val vd = mapEntry(entry)
        assertEquals(LogEntryKind.LLM_COMMENT, vd.kind)
        assertEquals(LogEntryStatus.COMPLETED, vd.status)
        assertEquals("the LLM said something", vd.message)
    }

    // --- mapEntry: all 5 statuses, via direct copies (forcing a status the factories don't produce) --

    @Test
    fun mapEntry_status_pending() {
        val entry = AgentLogEntry.action("a").copy(status = EntryStatus.PENDING)
        assertEquals(LogEntryStatus.PENDING, mapEntry(entry).status)
    }

    @Test
    fun mapEntry_status_approved() {
        val entry = AgentLogEntry.action("a").copy(status = EntryStatus.APPROVED)
        assertEquals(LogEntryStatus.APPROVED, mapEntry(entry).status)
    }

    @Test
    fun mapEntry_status_denied() {
        val entry = AgentLogEntry.action("a").copy(status = EntryStatus.DENIED)
        assertEquals(LogEntryStatus.DENIED, mapEntry(entry).status)
    }

    @Test
    fun mapEntry_status_completed() {
        val entry = AgentLogEntry.action("a").copy(status = EntryStatus.COMPLETED)
        assertEquals(LogEntryStatus.COMPLETED, mapEntry(entry).status)
    }

    @Test
    fun mapEntry_status_failed() {
        val entry = AgentLogEntry.action("a").copy(status = EntryStatus.FAILED)
        assertEquals(LogEntryStatus.FAILED, mapEntry(entry).status)
    }

    // --- mapEntry: cost/details/showRawLogLink carry through verbatim, independent of kind/status ---

    @Test
    fun mapEntry_costInfo_carriesThrough() {
        val entry = AgentLogEntry.action("a").copy(costInfo = "$0.0012")
        assertEquals("$0.0012", mapEntry(entry).cost)
    }

    @Test
    fun mapEntry_details_null_whenAbsent() {
        val entry = AgentLogEntry.action("a")
        assertNull(mapEntry(entry).details)
    }

    // --- AgentStopReason.toVd(): 1:1 enum bridge --------------------------------------------------

    @Test
    fun stopReason_toVd_allValues() {
        assertEquals(AgentStopReasonVd.COMPLETED, AgentStopReason.COMPLETED.toVd())
        assertEquals(AgentStopReasonVd.ERROR, AgentStopReason.ERROR.toVd())
        assertEquals(AgentStopReasonVd.CANCELLED, AgentStopReason.CANCELLED.toVd())
    }

    // --- logVisiblePref / setLogVisiblePref: verbatim classic key -----------------------------------

    @Test
    fun logVisiblePref_defaultsToFalse() {
        val service = AgentSessionServiceImpl()
        assertFalse(service.logVisiblePref())
    }

    @Test
    fun logVisiblePref_roundTrips_viaExactClassicKey() {
        val service = AgentSessionServiceImpl()
        service.setLogVisiblePref(true)
        assertTrue(service.logVisiblePref())
        // Exact classic key from AgentLogWidget.PREF_AGENT_LOG_VISIBLE.
        assertTrue(CommonUtils.settings.getBoolean("agent_log_widget_visible", false))

        service.setLogVisiblePref(false)
        assertFalse(service.logVisiblePref())
        assertFalse(CommonUtils.settings.getBoolean("agent_log_widget_visible", false))
    }

    // --- autoHideEnabled: reflects CommonUtils.aiSettings -------------------------------------------

    @Test
    fun autoHideEnabled_reflectsAiSettings() {
        val service = AgentSessionServiceImpl()
        AiSettings.autoHideAgentLogOnCompletion = false
        assertFalse(service.autoHideEnabled())

        AiSettings.autoHideAgentLogOnCompletion = true
        assertTrue(service.autoHideEnabled())
    }
}
