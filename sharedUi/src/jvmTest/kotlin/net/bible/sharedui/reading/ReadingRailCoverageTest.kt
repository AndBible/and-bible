package net.bible.sharedui.reading

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingRailCoverageTest {
    @Test fun coverageChangesRefreshDocumentsAndPreserveSystemInsets() {
        var changes = 0
        val coverage = ReadingRailCoverage { changes++ }
        coverage.update(setOf("left", "middle", "right"))
        coverage.update(setOf("left", "middle", "right"))
        assertEquals(1, changes, "unchanged layout must not trigger inset feedback")
        for (id in listOf("left", "middle", "right")) {
            assertEquals(74f, coverage.bottomOffset(id, true, 34f, 40f))
            assertEquals(0f, coverage.bottomOffset(id, false, 34f, 40f))
        }
        coverage.update(setOf("right"))
        assertEquals(34f, coverage.bottomOffset("left", true, 34f, 40f))
        assertEquals(74f, coverage.bottomOffset("right", true, 34f, 40f))
        coverage.update(emptySet())
        assertEquals(34f, coverage.bottomOffset("right", true, 34f, 40f))
        assertEquals(3, changes)
    }
}
