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

    /**
     * The reading-view host reuses one controller for every build, so a build must not start on the
     * previous one's leftovers — an error the user never dismissed would otherwise raise the failure
     * dialog over a brand-new index prompt.
     */
    @Test
    fun reset_clears_error_jobs_and_the_no_tasks_line() {
        val c = SearchIndexProgressController(onHide = {})
        c.setJobs(listOf(ProgressJob("a", "Indexing", 50, false)))
        c.showError()
        c.setJobs(emptyList())
        c.revealNoTasksIfIdle()
        assertTrue(c.noTasks.value, "sanity: all three are set")

        c.reset()

        assertNull(c.error.value)
        assertEquals(emptyList(), c.jobs.value)
        assertFalse(c.noTasks.value)
    }

    @Test
    fun hide_calls_onHide() {
        var hidden = false
        val c = SearchIndexProgressController(onHide = { hidden = true })
        c.hide()
        assertTrue(hidden)
    }
}
