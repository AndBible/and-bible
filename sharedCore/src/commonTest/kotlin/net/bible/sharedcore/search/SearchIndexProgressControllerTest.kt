package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchIndexProgressControllerTest {
    @Test
    fun setJobs_updates_list_and_noTasks_revealed_only_when_idle() {
        val c = SearchIndexProgressController(onHide = {})
        assertFalse(c.noTasks.value) // initial
        c.setJobs(listOf(ProgressJob("a", "Indexing", 50, false)))
        assertEquals(1, c.jobs.value.size)
        assertFalse(c.noTasks.value)
        // Empty jobs must NOT flash the no-tasks line immediately (classic parity: delayed ~4s).
        c.setJobs(emptyList())
        assertFalse(c.noTasks.value)
        // Only an explicit reveal while still idle shows the line.
        c.revealNoTasksIfIdle()
        assertTrue(c.noTasks.value)
        // A non-empty setJobs after a reveal flips it back to false.
        c.setJobs(listOf(ProgressJob("b", "Indexing", 10, false)))
        assertFalse(c.noTasks.value)
    }

    @Test
    fun show_and_dismiss_error() {
        val c = SearchIndexProgressController(onHide = {})
        c.showError()
        assertEquals(SearchIndexError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }

    @Test
    fun hide_calls_onHide() {
        var hidden = false
        val c = SearchIndexProgressController(onHide = { hidden = true })
        c.hide()
        assertTrue(hidden)
    }
}
