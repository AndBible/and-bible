package net.bible.sharedcore.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingProgressScaleTest {
    @Test fun bookPercentScaleMax_clamps_to_100_below_100() {
        assertEquals(1.0f, ReadingProgressScale.resolveBookPercentScaleMax(null))
        assertEquals(1.0f, ReadingProgressScale.resolveBookPercentScaleMax(0.4f))
        assertEquals(1.0f, ReadingProgressScale.resolveBookPercentScaleMax(1.0f))
    }
    @Test fun bookPercentScaleMax_rounds_up_to_next_quarter_above_100() {
        assertEquals(1.5f, ReadingProgressScale.resolveBookPercentScaleMax(1.33f))
        assertEquals(2.0f, ReadingProgressScale.resolveBookPercentScaleMax(1.75f))
    }
    @Test fun bookPercentScaleSteps_are_25pct_increments_min_100() {
        assertEquals(listOf(25, 50, 75, 100), ReadingProgressScale.buildBookPercentScaleSteps(1.0f))
        assertEquals(listOf(25, 50, 75, 100, 125, 150), ReadingProgressScale.buildBookPercentScaleSteps(1.5f))
    }
    @Test fun countScaleSteps_small_max_lists_each() {
        assertEquals((1..10).toList(), ReadingProgressScale.countScaleSteps(3)) // effectiveMax coerced to 10
    }
    @Test fun countScaleSteps_large_max_includes_anchors_1_5_max() {
        val steps = ReadingProgressScale.countScaleSteps(40)
        assertEquals(1, steps.first())
        assertEquals(40, steps.last())
        assertEquals(true, steps.contains(5))
        assertEquals(true, steps.size <= 10)
    }
    @Test fun heatLevel_buckets_by_fraction() {
        assertEquals(0, ReadingProgressScale.heatLevel(0, 10))
        assertEquals(1, ReadingProgressScale.heatLevel(2, 10))   // 0.2 -> 1
        assertEquals(2, ReadingProgressScale.heatLevel(5, 10))   // 0.5 -> 2
        assertEquals(3, ReadingProgressScale.heatLevel(7, 10))   // 0.7 -> 3
        assertEquals(4, ReadingProgressScale.heatLevel(10, 10))  // 1.0 -> 4
    }
    @Test fun memorizationLevel_buckets() {
        assertEquals(0, ReadingProgressScale.memorizationLevel(0f))
        assertEquals(1, ReadingProgressScale.memorizationLevel(0.1f))
        assertEquals(2, ReadingProgressScale.memorizationLevel(0.3f))
        assertEquals(3, ReadingProgressScale.memorizationLevel(0.6f))
        assertEquals(4, ReadingProgressScale.memorizationLevel(1.0f))
    }
}
