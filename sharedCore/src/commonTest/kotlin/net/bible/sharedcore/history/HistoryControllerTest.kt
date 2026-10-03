package net.bible.sharedcore.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryControllerTest {
    private val sample = listOf(
        HistoryEntry(0, "Genesis 1:1", "9:00 AM, Mon 1 Jan "),
        HistoryEntry(1, "John 3:16", "9:05 AM, Mon 1 Jan "),
    )

    @Test
    fun load_populates_entries() {
        val c = HistoryController(loadEntries = { sample }, onRevert = {})
        assertEquals(sample, c.entries.value)
    }

    @Test
    fun select_forwards_id_to_revert() {
        var reverted = -1
        val c = HistoryController(loadEntries = { sample }, onRevert = { reverted = it })
        c.onSelect(1)
        assertEquals(1, reverted)
    }

    @Test
    fun revert_failure_sets_error() {
        val c = HistoryController(loadEntries = { sample }, onRevert = { throw RuntimeException("boom") })
        c.onSelect(0)
        assertEquals(HistoryError.REVERT_FAILED, c.error.value)
    }

    @Test
    fun dismiss_error_clears() {
        val c = HistoryController(loadEntries = { sample }, onRevert = { throw RuntimeException() })
        c.onSelect(0)
        c.dismissError()
        assertNull(c.error.value)
    }
}
