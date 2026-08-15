package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchModeControllerTest {
    @Test
    fun openActivatesSearchMode() {
        var cleared = 0
        val c = SearchModeController(onClearQuery = { cleared++ })
        assertFalse(c.active.value)
        c.open()
        assertTrue(c.active.value)
        assertEquals(0, cleared, "opening must never touch the query")
    }

    @Test
    fun closeDeactivatesAndClearsByDefault() {
        var cleared = 0
        val c = SearchModeController(onClearQuery = { cleared++ })
        c.open()
        c.close()
        assertFalse(c.active.value)
        assertEquals(1, cleared)
    }

    @Test
    fun closeDoesNotClearWhenPolicySaysSo() {
        var cleared = 0
        val c = SearchModeController(onClearQuery = { cleared++ }, clearOnClose = false)
        c.open()
        c.close()
        assertFalse(c.active.value)
        assertEquals(0, cleared, "clearOnClose = false must leave the query alone")
    }

    @Test
    fun resetNeverClearsEvenWhenClearOnCloseIsTrue() {
        var cleared = 0
        val c = SearchModeController(onClearQuery = { cleared++ })
        c.open()
        c.reset()
        assertFalse(c.active.value)
        assertEquals(0, cleared, "reset is for load/refresh paths that must not wipe a seeded query")
    }

    @Test
    fun closeClearsEvenWhenAlreadyInactive() {
        var cleared = 0
        val c = SearchModeController(onClearQuery = { cleared++ })
        c.close()
        assertEquals(1, cleared, "close is unconditional; callers that must not clear use reset()")
    }
}
