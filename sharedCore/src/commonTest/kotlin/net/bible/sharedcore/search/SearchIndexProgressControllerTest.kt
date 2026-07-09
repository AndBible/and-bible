package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchIndexProgressControllerTest {
    @Test
    fun setJobs_updates_list_and_derives_noTasks() {
        val c = SearchIndexProgressController(onHide = {})
        assertFalse(c.noTasks.value) // initial
        c.setJobs(listOf(ProgressJob("a", "Indexing", 50, false)))
        assertEquals(1, c.jobs.value.size)
        assertFalse(c.noTasks.value)
        c.setJobs(emptyList())
        assertTrue(c.noTasks.value)
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
