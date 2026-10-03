package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RawLlmLogControllerTest {

    private class Fake : RawLogService {
        val flow = MutableStateFlow<List<RawLogSummaryVd>>(emptyList())
        override val summaries: StateFlow<List<RawLogSummaryVd>> = flow
        override fun refresh() {}
        override fun deleteByIds(ids: Set<String>) {}
        override fun deleteOlderThan(days: Int) {}
        override fun deleteAll() {}

        var recordTextAnswers: Map<String, String?> = emptyMap()
        var sessionEntriesAnswers: Map<String, List<RawLogEntryVd>> = emptyMap()
        var canReportBugForRecordId: MutableMap<String?, Boolean> = mutableMapOf()
        var lastRecordTextRequest: String? = null
        var lastSessionEntriesRequest: String? = null
        var lastCanReportBugRequest: String? = "unset"

        override suspend fun recordText(recordId: String): String? {
            lastRecordTextRequest = recordId
            return recordTextAnswers[recordId]
        }
        override suspend fun sessionEntries(workspaceId: String): List<RawLogEntryVd> {
            lastSessionEntriesRequest = workspaceId
            return sessionEntriesAnswers[workspaceId] ?: emptyList()
        }
        override fun canReportBug(recordId: String?): Boolean {
            lastCanReportBugRequest = recordId
            return canReportBugForRecordId[recordId] ?: false
        }
    }

    private fun controller(fake: Fake) = RawLlmLogController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    @Test fun loadRecord_setsRecordTextFromService() = runTest {
        val f = Fake().apply { recordTextAnswers = mapOf("r1" to "the formatted log text") }
        val c = controller(f)
        assertNull(c.recordText.value)
        c.loadRecord("r1")
        assertEquals("the formatted log text", c.recordText.value)
        assertEquals("r1", f.lastRecordTextRequest)
    }

    @Test fun loadRecord_missingRecord_setsNull() = runTest {
        val f = Fake()
        val c = controller(f)
        c.loadRecord("missing")
        assertNull(c.recordText.value)
    }

    @Test fun loadSession_setsEntriesFromService() = runTest {
        val entries = listOf(RawLogEntryVd("User", "10 in / 0 out", "hello"))
        val f = Fake().apply { sessionEntriesAnswers = mapOf("ws1" to entries) }
        val c = controller(f)
        assertEquals(emptyList(), c.entries.value)
        c.loadSession("ws1")
        assertEquals(entries, c.entries.value)
        assertEquals("ws1", f.lastSessionEntriesRequest)
    }

    @Test fun toggleExpanded_flipsIndexMembership() = runTest {
        val f = Fake()
        val c = controller(f)
        assertEquals(emptySet(), c.expandedIndices.value)
        c.toggleExpanded(0)
        assertEquals(setOf(0), c.expandedIndices.value)
        c.toggleExpanded(2)
        assertEquals(setOf(0, 2), c.expandedIndices.value)
        c.toggleExpanded(0)
        assertEquals(setOf(2), c.expandedIndices.value)
    }

    @Test fun canReportBug_dbMode_reflectsServiceForRecordId() = runTest {
        val f = Fake().apply {
            recordTextAnswers = mapOf("r1" to "text")
            canReportBugForRecordId = mutableMapOf("r1" to true)
        }
        val c = controller(f)
        assertFalse(c.canReportBug.value)
        c.loadRecord("r1")
        assertTrue(c.canReportBug.value)
        assertEquals("r1", f.lastCanReportBugRequest)
    }

    @Test fun canReportBug_sessionMode_queriesServiceWithNullRecordId() = runTest {
        val f = Fake().apply {
            sessionEntriesAnswers = mapOf("ws1" to emptyList())
            canReportBugForRecordId = mutableMapOf(null to true)
        }
        val c = controller(f)
        c.loadSession("ws1")
        assertTrue(c.canReportBug.value)
        assertNull(f.lastCanReportBugRequest)
    }
}
