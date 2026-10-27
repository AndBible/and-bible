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

/**
 * Separator drag → new weights for the two adjacent panes `weights[index]` and `weights[index + 1]`
 * (ports classic Separator.kt).
 *
 * [weightedExtentPx] is the space the weights share along the split axis (the container minus the
 * separators). A pane's size is its share of the weight SUM, so a pixel is worth
 * `weights.sum() / weightedExtentPx` weight units; using that (rather than assuming the weights
 * average 1) keeps the divider under the finger whatever the stored weights add up to (#3336).
 * The minimum pane weight is a tenth of the average weight, as classic's `0.1` was for weights
 * averaging 1.
 */
fun separatorDrag(
    translation: Float,
    weightedExtentPx: Float,
    weights: List<Float>,
    index: Int,
): WeightDelta {
    val startWeight1 = weights[index]
    val startWeight2 = weights[index + 1]
    val totalWeight = weights.sum()
    if (weightedExtentPx <= 0f || totalWeight <= 0f) return WeightDelta(startWeight1, startWeight2)
    val variation = translation * totalWeight / weightedExtentPx
    val minWeight = 0.1f * totalWeight / weights.size
    return WeightDelta(
        weight1 = max(minWeight, startWeight1 + variation),
        weight2 = max(minWeight, startWeight2 - variation),
    )
}
