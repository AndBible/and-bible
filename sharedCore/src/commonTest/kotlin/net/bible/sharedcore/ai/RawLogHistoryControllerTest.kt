package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RawLogHistoryControllerTest {

    private fun summary(id: String) = RawLogSummaryVd(
        id = id, promptName = "Prompt $id", modelInfo = "OpenAI · gpt", tokenInfo = "1 in / 2 out",
        costInfo = "$0.01", timestamp = "2026-07-17 10:00", hasError = false,
    )

    private class Fake(initial: List<RawLogSummaryVd> = emptyList()) : RawLogService {
        val flow = MutableStateFlow(initial)
        override val summaries: StateFlow<List<RawLogSummaryVd>> = flow
        var refreshCount = 0
        var lastDeletedIds: Set<String>? = null
        var lastDeleteOlderThanDays: Int? = null
        var deleteAllCount = 0
        var recordTextAnswer: String? = "text"
        var sessionEntriesAnswer: List<RawLogEntryVd> = emptyList()
        var canReportBugAnswer = false

        override fun refresh() { refreshCount++ }
        override fun deleteByIds(ids: Set<String>) { lastDeletedIds = ids }
        override fun deleteOlderThan(days: Int) { lastDeleteOlderThanDays = days }
        override fun deleteAll() { deleteAllCount++ }
        override suspend fun recordText(recordId: String): String? = recordTextAnswer
        override suspend fun sessionEntries(workspaceId: String): List<RawLogEntryVd> = sessionEntriesAnswer
        override fun canReportBug(recordId: String?): Boolean = canReportBugAnswer
    }

    private fun controller(fake: Fake, onOpenLog: (String) -> Unit = {}) =
        RawLogHistoryController(fake, CoroutineScope(UnconfinedTestDispatcher()), onOpenLog)

    @Test fun summaries_passesThroughServiceFlow() = runTest {
        val a = summary("a")
        val f = Fake(listOf(a))
        val c = controller(f)
        assertEquals(listOf(a), c.summaries.value)
        val b = summary("b")
        f.flow.value = listOf(a, b)
        assertEquals(listOf(a, b), c.summaries.value)
    }

    @Test fun toggleSelect_addsAndRemoves_andDrivesSelectionMode() = runTest {
        val f = Fake(listOf(summary("a"), summary("b")))
        val c = controller(f)
        assertEquals(emptySet(), c.selection.value)
        assertFalse(c.selectionMode.value)

        c.toggleSelect("a")
        assertEquals(setOf("a"), c.selection.value)
        assertTrue(c.selectionMode.value)

        c.toggleSelect("b")
        assertEquals(setOf("a", "b"), c.selection.value)

        c.toggleSelect("a")
        assertEquals(setOf("b"), c.selection.value)
        assertTrue(c.selectionMode.value)

        c.toggleSelect("b")
        assertEquals(emptySet(), c.selection.value)
        assertFalse(c.selectionMode.value)
    }

    @Test fun clearSelection_emptiesSelectionAndExitsSelectionMode() = runTest {
        val f = Fake(listOf(summary("a")))
        val c = controller(f)
        c.toggleSelect("a")
        assertTrue(c.selectionMode.value)
        c.clearSelection()
        assertEquals(emptySet(), c.selection.value)
        assertFalse(c.selectionMode.value)
    }

    @Test fun deleteSelected_routesSelectedIdsToServiceAndClearsSelection() = runTest {
        val f = Fake(listOf(summary("a"), summary("b")))
        val c = controller(f)
        c.toggleSelect("a")
        c.toggleSelect("b")
        c.deleteSelected()
        assertEquals(setOf("a", "b"), f.lastDeletedIds)
        assertEquals(emptySet(), c.selection.value)
        assertFalse(c.selectionMode.value)
    }

    @Test fun deleteOlderThan_routesDaysToService() = runTest {
        val f = Fake()
        val c = controller(f)
        c.deleteOlderThan(30)
        assertEquals(30, f.lastDeleteOlderThanDays)
    }

    @Test fun deleteAll_routesToService() = runTest {
        val f = Fake()
        val c = controller(f)
        c.deleteAll()
        assertEquals(1, f.deleteAllCount)
    }

    @Test fun openLog_invokesNavCallbackWithId() = runTest {
        var got: String? = null
        val f = Fake(listOf(summary("a")))
        val c = controller(f) { got = it }
        c.openLog("a")
        assertEquals("a", got)
    }
}
