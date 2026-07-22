package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingSplitMathTest {
    private fun snap(w: Float) = WindowSnapshot(
        id = "w$w", state = WindowStateValue.VISIBLE, weight = w, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    @Test fun singleWindowFillsFully() {
        assertEquals(listOf(1f), effectiveWeights(listOf(snap(0.3f))))
    }

    @Test fun emptyWindowsGivesEmpty() {
        assertEquals(emptyList(), effectiveWeights(emptyList()))
    }

    @Test fun weightsArePreservedForMultipleWindows() {
        val w = effectiveWeights(listOf(snap(2f), snap(1f), snap(1f)))
        assertEquals(3, w.size)
        assertEquals(2f, w[0]); assertEquals(1f, w[1]); assertEquals(1f, w[2])
    }

    @Test fun nonPositiveWeightsAreClampedPositive() {
        val w = effectiveWeights(listOf(snap(0f), snap(-5f)))
        assertTrue(w.all { it > 0f })
    }

    @Test fun paneFractionSumsToOne() {
        val w = listOf(2f, 1f, 1f)
        val total = paneFraction(w, 0) + paneFraction(w, 1) + paneFraction(w, 2)
        assertEquals(1f, total, absoluteTolerance = 1e-5f)
        assertEquals(0.5f, paneFraction(w, 0), absoluteTolerance = 1e-5f)
    }

    @Test fun paneFractionOutOfRangeIsZero() {
        assertEquals(0f, paneFraction(listOf(1f), 5))
        assertEquals(0f, paneFraction(emptyList(), 0))
    }

    @Test fun separatorDragShiftsWeightByVariation() {
        val d = separatorDrag(translation = 50f, averagePaneExtent = 200f, startWeight1 = 1f, startWeight2 = 1f)
        assertEquals(1.25f, d.weight1, absoluteTolerance = 1e-5f)
        assertEquals(0.75f, d.weight2, absoluteTolerance = 1e-5f)
    }

    @Test fun separatorDragClampsToMinimum() {
        val d = separatorDrag(translation = -500f, averagePaneExtent = 200f, startWeight1 = 1f, startWeight2 = 1f)
        assertEquals(0.1f, d.weight1, absoluteTolerance = 1e-5f)
    }

    @Test fun separatorDragWithZeroExtentIsNoOp() {
        val d = separatorDrag(translation = 50f, averagePaneExtent = 0f, startWeight1 = 1f, startWeight2 = 1f)
        assertEquals(1f, d.weight1); assertEquals(1f, d.weight2)
    }
}
