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
        val d = separatorDrag(translation = 50f, weightedExtentPx = 400f, weights = listOf(1f, 1f), index = 0)
        assertEquals(1.25f, d.weight1, absoluteTolerance = 1e-5f)
        assertEquals(0.75f, d.weight2, absoluteTolerance = 1e-5f)
    }

    @Test fun separatorDragClampsToMinimum() {
        val d = separatorDrag(translation = -500f, weightedExtentPx = 400f, weights = listOf(1f, 1f), index = 0)
        assertEquals(0.1f, d.weight1, absoluteTolerance = 1e-5f)
    }

    @Test fun separatorDragWithZeroExtentIsNoOp() {
        val d = separatorDrag(translation = 50f, weightedExtentPx = 0f, weights = listOf(1f, 1f), index = 0)
        assertEquals(1f, d.weight1); assertEquals(1f, d.weight2)
    }

    /** #3336: how many px pane [index] grows when its separator is dragged by [dragPx]. */
    private fun paneGrowthPx(weights: List<Float>, index: Int, dragPx: Float, extentPx: Float): Float {
        val d = separatorDrag(dragPx, extentPx, weights, index)
        val after = weights.toMutableList().also { it[index] = d.weight1; it[index + 1] = d.weight2 }
        return after[index] / after.sum() * extentPx - weights[index] / weights.sum() * extentPx
    }

    @Test fun separatorDragTracksFingerWhenWeightsSumToOne() {
        assertEquals(50f, paneGrowthPx(listOf(0.5f, 0.5f), 0, dragPx = 50f, extentPx = 1000f), absoluteTolerance = 0.01f)
    }

    @Test fun separatorDragTracksFingerWhenWeightsSumToFour() {
        assertEquals(50f, paneGrowthPx(listOf(2f, 2f), 0, dragPx = 50f, extentPx = 1000f), absoluteTolerance = 0.01f)
    }

    @Test fun separatorDragTracksFingerWithUnevenWeightsAndThreePanes() {
        assertEquals(-80f, paneGrowthPx(listOf(0.3f, 1.7f, 0.6f), 1, dragPx = -80f, extentPx = 1200f), absoluteTolerance = 0.01f)
    }

    @Test fun separatorDragMinimumScalesWithWeightSum() {
        // Weights summing to 0.2: classic's absolute 0.1 floor would be half the split.
        val d = separatorDrag(translation = -1000f, weightedExtentPx = 1000f, weights = listOf(0.1f, 0.1f), index = 0)
        assertEquals(0.01f, d.weight1, absoluteTolerance = 1e-5f)
    }
}
