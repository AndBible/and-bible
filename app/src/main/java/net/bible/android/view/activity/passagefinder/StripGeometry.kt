/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.passagefinder

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scroll and layout geometry for one horizontal strip of the passage finder.
 *
 * Both implementations are pure, frame-stateless functions of [scroll]: given a scroll
 * position they produce item positions directly, with no dependency on the previous
 * frame's layout. That is the property the Compose implementation lacked — there, item
 * *layout* widths grew with proximity, so position depended on width which depended on
 * position, and centering needed an iterative convergence loop that took up to eight
 * frames to settle. Here the same visual result falls out of a single O(n) pass.
 *
 * Coordinates: [scroll] is a position in *base space* — the un-magnified coordinate
 * system in which every item occupies a fixed span. Snapping, clamping and index lookup
 * all happen in base space, where they are exact. Only rendering maps base space onto
 * the screen.
 */
interface StripLane {
    /** Number of selectable items in the lane. */
    val itemCount: Int

    /** Current scroll position, in base space, of the point sitting at the viewport centre. */
    var scroll: Float

    /** Base-space scroll position at which [index] is exactly centred. */
    fun snapPointFor(index: Int): Float

    /** The item whose snap point is nearest [at] (defaults to the current [scroll]). */
    fun nearestIndex(at: Float = scroll): Int

    /**
     * Screen pixels travelled per unit of base-space scroll at the current focus.
     *
     * Drag deltas arrive in screen pixels and must be divided by this to keep the
     * content tracking the finger: where the lens magnifies the strip, a screen
     * pixel covers less base space.
     */
    val localScale: Float

    /** Lowest legal [scroll] — the first item centred. */
    val minScroll: Float get() = snapPointFor(0)

    /** Highest legal [scroll] — the last item centred. */
    val maxScroll: Float get() = snapPointFor((itemCount - 1).coerceAtLeast(0))

    /** Clamps [value] to the scrollable range. */
    fun clampScroll(value: Float): Float = value.coerceIn(minScroll, maxScroll)
}

/**
 * Fixed-pitch lane used by the chapter and verse strips.
 *
 * Every cell occupies [pitchPx] regardless of magnification: the lens scales cells
 * about their own centres at draw time only, exactly as the Compose version did with
 * `graphicsLayer { scaleX/scaleY }`, which likewise left layout untouched. Base space
 * and screen space therefore coincide and [localScale] is always 1.
 */
class UniformLane : StripLane {
    override var itemCount: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            scroll = clampScroll(scroll)
        }

    /** Layout pitch of one cell, in pixels. */
    var pitchPx: Float = 1f
        set(value) {
            field = if (value > 0f) value else 1f
        }

    override var scroll: Float = 0f

    override fun snapPointFor(index: Int): Float = index * pitchPx

    override fun nearestIndex(at: Float): Int =
        if (itemCount <= 0) 0
        else (at / pitchPx).roundToInt().coerceIn(0, itemCount - 1)

    override val localScale: Float get() = 1f

    /** Signed horizontal offset of [index] from the viewport centre, in pixels. */
    fun offsetFromCentre(index: Int): Float = snapPointFor(index) - scroll

    /**
     * Lens proximity of [index]: 1 at the viewport centre, falling linearly to 0 at
     * [radiusPx] away. Matches the Compose `computeProximityMap` falloff.
     */
    fun proximity(index: Int, radiusPx: Float): Float =
        (1f - abs(offsetFromCentre(index)) / radiusPx).coerceIn(0f, 1f)

    /**
     * Index range that can be visible in a viewport of [viewportWidth] pixels, inclusive.
     *
     * Measured symmetrically about the viewport centre, which is where [scroll] sits by
     * definition, so this needs the strip's own width and not the host view's — passing
     * the latter for a strip narrower than its view just returns cells the clip discards.
     */
    fun visibleRange(viewportWidth: Float, marginPx: Float): IntRange {
        if (itemCount <= 0) return IntRange.EMPTY
        val half = viewportWidth / 2f + marginPx
        val first = ((scroll - half) / pitchPx).toInt().coerceIn(0, itemCount - 1)
        val last = ((scroll + half) / pitchPx).toInt().coerceIn(0, itemCount - 1)
        return first..last
    }
}

/**
 * Variable-width "bookshelf lens" lane used by the book strip.
 *
 * Each spine has a base width proportional to its chapter count, and swells towards
 * [lensWidthPx] as it approaches the viewport centre — pushing its neighbours aside
 * rather than merely scaling over them, which is what gives the strip its shelf feel.
 *
 * [layout] resolves the whole strip in one pass:
 *  1. proximity per item, from base-space distance to [scroll] — stable, because base
 *     positions never move;
 *  2. magnified width per item;
 *  3. a cumulative sweep to get screen positions;
 *  4. a single translation chosen so the base-space point [scroll] lands on the
 *     viewport centre.
 *
 * Step 4 is what replaces the old iterative re-centring: because base positions are
 * independent of magnification, the offset that centres an item is known exactly and
 * the very first frame is already correct.
 */
class LensLane : StripLane {
    /** Un-magnified width of each spine, in pixels, excluding the inter-item gap. */
    private var baseWidths = FloatArray(0)

    /** Base-space span of each item: base width plus the constant gap. */
    private var baseSpans = FloatArray(0)

    /** Base-space left edge of each item (exclusive prefix sum of [baseSpans]). */
    private var baseLefts = FloatArray(0)

    /** Constant gap between spines. Does not magnify, so it is excluded from the lens. */
    var gapPx: Float = 0f

    /** Distance from the viewport centre, in base-space pixels, at which the lens fades out. */
    var lensRadiusPx: Float = 1f
        set(value) {
            field = if (value > 0f) value else 1f
        }

    /** Width of a spine at the exact centre of the lens. */
    var lensWidthPx: Float = 0f

    /** Tightness of the magnification bell; see [bellFalloff]. */
    var lensFalloff: Float = 10f

    override var scroll: Float = 0f

    /** Magnified width of each item. Valid after [layout]. */
    var widths: FloatArray = FloatArray(0)
        private set

    /** Screen-space left edge of each item. Valid after [layout]. */
    var lefts: FloatArray = FloatArray(0)
        private set

    /**
     * Linear lens proximity (0..1) of each item, for fading and text weight. Kept linear
     * so spines approaching the lens stay legible well before they start to grow.
     * Valid after [layout].
     */
    var proximities: FloatArray = FloatArray(0)
        private set

    /**
     * Bell-shaped magnification factor (0..1) of each item, for width and height.
     * Separate from [proximities] so size can be concentrated at the centre while
     * legibility fades out gradually. Valid after [layout].
     */
    var sizeFactors: FloatArray = FloatArray(0)
        private set

    /**
     * How close each item is to being *the* selected one: 1 at its snap point, falling to
     * 0 one item away. Valid after [layout].
     *
     * The lens alone cannot answer this. Its radius spans many items, so neighbours sit
     * near the top of the bell and come out within a few percent of the centre's size —
     * which is exactly why the selected spine was hard to pick out. This falls off over a
     * single item instead, so only the selected one is marked, and it crossfades to its
     * neighbour as the strip scrolls rather than popping.
     */
    var focusFactors: FloatArray = FloatArray(0)
        private set

    override var localScale: Float = 1f
        private set

    override val itemCount: Int get() = baseWidths.size

    /**
     * Installs the per-item base widths, in pixels, and resets the derived base-space
     * tables. Call whenever the book list or the density changes.
     */
    fun setBaseWidths(widthsPx: FloatArray) {
        baseWidths = widthsPx
        val n = widthsPx.size
        baseSpans = FloatArray(n)
        baseLefts = FloatArray(n)
        widths = FloatArray(n)
        lefts = FloatArray(n)
        proximities = FloatArray(n)
        sizeFactors = FloatArray(n)
        focusFactors = FloatArray(n)
        var acc = 0f
        for (i in 0 until n) {
            baseSpans[i] = widthsPx[i] + gapPx
            baseLefts[i] = acc
            acc += baseSpans[i]
        }
        scroll = clampScroll(scroll)
    }

    /** Base-space centre of [index] — the scroll value that puts it under the lens. */
    override fun snapPointFor(index: Int): Float {
        if (itemCount == 0) return 0f
        val i = index.coerceIn(0, itemCount - 1)
        return baseLefts[i] + baseWidths[i] / 2f
    }

    override fun nearestIndex(at: Float): Int {
        if (itemCount == 0) return 0
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in 0 until itemCount) {
            val d = abs(snapPointFor(i) - at)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    /**
     * Resolves magnified widths and screen positions for the current [scroll], placing
     * the base-space point [scroll] at [viewportCentreX].
     */
    fun layout(viewportCentreX: Float) {
        val n = itemCount
        if (n == 0) return

        // 1 + 2: proximity and magnified width. Size follows a bell so the few spines at
        // the centre are picked out sharply and everything else stays near base width —
        // which is what lets more books fit on screen. Fading stays on the linear ramp.
        for (i in 0 until n) {
            val centre = baseLefts[i] + baseWidths[i] / 2f
            val distance = abs(centre - scroll) / lensRadiusPx
            proximities[i] = (1f - distance).coerceIn(0f, 1f)
            val bell = bellFalloff(distance, lensFalloff)
            sizeFactors[i] = bell
            focusFactors[i] = (1f - abs(centre - scroll) / baseSpans[i]).coerceIn(0f, 1f)
            widths[i] = lerp(baseWidths[i], lensWidthPx, bell)
        }

        // 3: cumulative screen sweep, relative to an arbitrary origin.
        var acc = 0f
        for (i in 0 until n) {
            lefts[i] = acc
            acc += widths[i] + gapPx
        }

        // 4: find where `scroll` falls in base space and translate so it lands on centre.
        //
        // Base and screen space are related piecewise: an item's interior is stretched by
        // its magnification, while the gap after it keeps the same width in both spaces.
        // Mapping the gap one-to-one is what makes the two ends of each piece agree — a
        // magnified gap would make the strip jump sideways every time the focus crossed
        // between two spines.
        val focus = focusIndex()
        val baseWidth = baseWidths[focus]
        val within = scroll - baseLefts[focus]
        val scrollScreenPos = if (baseWidth > 0f && within <= baseWidth) {
            lefts[focus] + (within / baseWidth) * widths[focus]
        } else {
            lefts[focus] + widths[focus] + (within - baseWidth)
        }
        val shift = viewportCentreX - scrollScreenPos
        for (i in 0 until n) {
            lefts[i] += shift
        }

        // Screen pixels per base pixel at the focus, for converting drag deltas. Taken
        // from the item even while the focus sits in a gap: the gap is a couple of dp,
        // and holding the scale steady across it avoids a stutter in the scroll rate.
        localScale = if (baseWidth > 0f) widths[focus] / baseWidth else 1f
    }

    /** The item whose base-space span contains [scroll], clamped to the ends. */
    private fun focusIndex(): Int {
        val n = itemCount
        if (n == 0) return 0
        // Binary search over baseLefts for the last item starting at or before `scroll`.
        var lo = 0
        var hi = n - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (baseLefts[mid] <= scroll) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * Index range whose magnified boxes intersect the span [viewportLeft]..[viewportRight].
     *
     * Bounds rather than a width, because [lefts] are absolute positions in the host
     * view's coordinates: a strip narrower than its view sits at an arbitrary offset
     * within it, and testing against 0..width would then return spines lying outside the
     * strip entirely — correct only because the caller clips them away again.
     */
    fun visibleRange(viewportLeft: Float, viewportRight: Float): IntRange {
        val n = itemCount
        if (n == 0) return IntRange.EMPTY
        var first = -1
        var last = -1
        for (i in 0 until n) {
            val l = lefts[i]
            val r = l + widths[i]
            if (r >= viewportLeft && l <= viewportRight) {
                if (first < 0) first = i
                last = i
            } else if (first >= 0) {
                break
            }
        }
        return if (first < 0) IntRange.EMPTY else first..last
    }
}
