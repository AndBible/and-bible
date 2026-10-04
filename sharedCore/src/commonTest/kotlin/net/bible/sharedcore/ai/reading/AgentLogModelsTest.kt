package net.bible.sharedcore.ai.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentLogModelsTest {
    @Test fun autoHide_disabled_neverHides() {
        assertFalse(shouldAutoHideAgentLog(false, AgentStopReasonVd.COMPLETED))
        assertFalse(shouldAutoHideAgentLog(false, AgentStopReasonVd.CANCELLED))
    }
    @Test fun autoHide_enabled_hidesOnCompletedAndCancelled() {
        assertTrue(shouldAutoHideAgentLog(true, AgentStopReasonVd.COMPLETED))
        assertTrue(shouldAutoHideAgentLog(true, AgentStopReasonVd.CANCELLED))
    }
    @Test fun autoHide_enabled_keepsVisibleOnError() {
        assertFalse(shouldAutoHideAgentLog(true, AgentStopReasonVd.ERROR))
    }
    @Test fun autoHide_ignoresStart_nullReason() {
        assertFalse(shouldAutoHideAgentLog(true, null))
    }
    @Test fun uiState_defaults() {
        val s = AgentLogUiState()
        assertFalse(s.visible); assertFalse(s.expanded)
        assertEquals(AgentLogSnapshot(), s.snapshot)
        assertEquals(null, s.modelPicker)
    }
    @Test fun entryVd_carriesFields() {
        val e = AgentLogEntryVd("e1", LogEntryKind.ACTION, LogEntryStatus.PENDING, "Calling tool", details = "args", cost = "$0.01", showRawLogLink = true)
        assertEquals(LogEntryKind.ACTION, e.kind); assertTrue(e.showRawLogLink)
    }
}
