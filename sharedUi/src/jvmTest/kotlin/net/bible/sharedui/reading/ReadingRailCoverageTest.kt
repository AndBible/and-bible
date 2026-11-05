package net.bible.sharedui.reading

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingRailCoverageTest {
    private val sideBySide = mapOf(
        "left" to Rect(0f, 0f, 399f, 600f),
        "right" to Rect(401f, 0f, 800f, 600f),
    )

    @Test fun narrowRailReservesOnlyTheEndPane() {
        assertEquals(setOf("right"), railCoveredWindows(sideBySide, Rect(650f, 536f, 800f, 600f)))
    }

    @Test fun wideRailReservesBothBottomPanes() {
        assertEquals(setOf("left", "right"), railCoveredWindows(sideBySide, Rect(300f, 536f, 800f, 600f)))
    }

    @Test fun hiddenRailReservesNothing() {
        assertEquals(emptySet(), railCoveredWindows(sideBySide, null))
    }

    @Test fun stackedLayoutReservesOnlyTheBottomPane() {
        val panes = mapOf("top" to Rect(0f, 0f, 800f, 299f), "bottom" to Rect(0f, 301f, 800f, 600f))
        assertEquals(setOf("bottom"), railCoveredWindows(panes, Rect(300f, 536f, 800f, 600f)))
    }

    @Test fun singleOrMaximizedPaneOwnsTheRail() {
        assertEquals(setOf("only"), railCoveredWindows(mapOf("only" to Rect(0f, 0f, 800f, 600f)), Rect(650f, 536f, 800f, 600f)))
    }

    @Test fun measuredCoverageOnlyRefreshesWhenItChangesAndPreservesSystemInsets() {
        var changes = 0
        val coverage = ReadingRailCoverage { changes++ }
        coverage.update(setOf("right"))
        coverage.update(setOf("right"))
        assertEquals(1, changes, "identical measurements must not trigger inset feedback")
        assertEquals(24f, coverage.bottomOffset("left", true, 24f, 40f))
        assertEquals(64f, coverage.bottomOffset("right", true, 24f, 40f))
        assertEquals(0f, coverage.bottomOffset("right", false, 24f, 40f))
        coverage.update(setOf("left", "right"))
        assertEquals(64f, coverage.bottomOffset("left", true, 24f, 40f))
        coverage.update(emptySet())
        assertEquals(24f, coverage.bottomOffset("right", true, 24f, 40f))
        assertEquals(3, changes)
    }

    @Test fun edgeContactIsNotOverlapAndRtlUsesPhysicalBounds() {
        assertEquals(setOf("right"), railCoveredWindows(sideBySide, Rect(399f, 536f, 800f, 600f)))
        assertEquals(setOf("left"), railCoveredWindows(sideBySide, Rect(0f, 536f, 150f, 600f)))
    }
}
