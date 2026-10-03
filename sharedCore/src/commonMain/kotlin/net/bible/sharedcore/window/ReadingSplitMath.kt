package net.bible.sharedcore.window

import kotlin.math.max

/** Layout weight used for the split; single window fills, others keep their (positive) weight. */
fun effectiveWeights(windows: List<WindowSnapshot>): List<Float> = when {
    windows.isEmpty() -> emptyList()
    windows.size == 1 -> listOf(1f)
    else -> windows.map { max(it.weight, 0.001f) }
}

/** Fraction of the weighted extent this pane occupies. */
fun paneFraction(weights: List<Float>, index: Int): Float {
    if (index !in weights.indices) return 0f
    val sum = weights.sum()
    return if (sum <= 0f) 0f else weights[index] / sum
}

data class WeightDelta(val weight1: Float, val weight2: Float)

/** Separator drag → new weights for the two adjacent panes (ports classic Separator.kt). */
fun separatorDrag(
    translation: Float,
    averagePaneExtent: Float,
    startWeight1: Float,
    startWeight2: Float,
): WeightDelta {
    if (averagePaneExtent <= 0f) return WeightDelta(startWeight1, startWeight2)
    val variation = translation / averagePaneExtent
    return WeightDelta(
        weight1 = max(0.1f, startWeight1 + variation),
        weight2 = max(0.1f, startWeight2 - variation),
    )
}
