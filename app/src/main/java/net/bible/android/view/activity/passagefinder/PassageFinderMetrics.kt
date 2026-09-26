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

import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.floor

/**
 * Every dimension of the passage finder, resolved to pixels once per density change.
 *
 * The values are ported unchanged from the Compose implementation's `dp`/`sp` constants
 * so the widget keeps its exact proportions. The three lens radii are the one exception:
 * they were already expressed in raw pixels there (they were compared against `LazyRow`
 * layout offsets, which are pixels), and are kept in raw pixels here for the same reason —
 * converting them to dp would widen the lens on high-density screens and change the look.
 *
 * A handful of the vertical dimensions are retuned by [configure] for short viewports;
 * see there for why. Everything downstream reads them by name, so nothing else has to
 * know which tier is in force.
 */
class PassageFinderMetrics(private val displayMetrics: DisplayMetrics) {

    /** Converts [value] density-independent pixels to pixels. */
    fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, displayMetrics)

    /**
     * Converts [value] scale-independent pixels to pixels, honouring the font scale.
     *
     * Uses [TypedValue.applyDimension] rather than multiplying by `scaledDensity`, which
     * is deprecated and, from API 34, wrong: font scaling above 130% is non-linear there,
     * so a single density factor over-scales large text.
     */
    fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, displayMetrics)

    // ---- Book strip ----------------------------------------------------------------

    /** Spine width of a one-chapter book, before magnification. */
    val spineBaseMinWidth = dp(7f)

    /** Spine width of the book with the most chapters, before magnification. */
    val spineBaseMaxWidth = dp(14f)

    /** Spine width at the exact centre of the lens. */
    val spineLensWidth = dp(36f)

    /** Spine height at the centre of the lens, and the fixed height of the strip. */
    var spineMaxHeight = dp(REGULAR_SPINE_MAX_HEIGHT_DP)
        private set

    /** Spine height at the far edges of the lens. */
    var spineMinHeight = dp(REGULAR_SPINE_MIN_HEIGHT_DP)
        private set

    /** Constant gap between spines; does not magnify. */
    val spineGap = dp(2f)

    val spineCornerRadius = dp(2f)

    /**
     * How far the selected spine rises above the rest of the shelf.
     *
     * The lens is wide enough that a spine one place off centre is within a few percent
     * of the selected one's height, so magnification alone does not say which is picked.
     * Raising only the selected spine clear of its neighbours does, the way a book pulled
     * half out of a shelf stands proud of the row.
     */
    val spineFocusOvershoot = dp(12f)

    /** Height of the bar marking the book currently open in the reader. */
    val openBookMarkerHeight = dp(4f)

    /** Side inset of that bar, so it reads as sitting inside the spine rather than under it. */
    val openBookMarkerInset = dp(1f)

    /**
     * Stroke width of the divider that marks the start of a new book category.
     *
     * A hairline by intent: it separates groups on a shelf whose spines are only 7-14dp
     * wide with a 2dp gap between them, so anything heavier reads as a spine of its own.
     */
    val groupDividerWidth = dp(1f)

    val spineMinTextSize = sp(8f)
    val spineMaxTextSize = sp(16f)

    /** Lens reach for the book strip. Raw pixels — see the class note. */
    val bookLensRadius = 320f

    /**
     * Tightness of the book strip's magnification bell; see [bellFalloff].
     *
     * Raising this narrows the group of enlarged spines and so fits more books on screen;
     * lowering it spreads the magnification across the whole lens, which flattens the
     * curve towards a straight ramp.
     */
    val bookLensFalloff = 10f

    // ---- Chapter strip -------------------------------------------------------------

    /** Layout pitch of a chapter cell. Cells scale about their centre, so this is constant. */
    val chapterCellSize = dp(24f)
    val chapterMinScale = 20f / 24f
    val chapterMaxScale = 56f / 24f
    val chapterTextSize = sp(18f)

    /** Lens reach for the chapter strip. Raw pixels — see the class note. */
    val chapterLensRadius = 290f

    // ---- Verse strip ---------------------------------------------------------------

    /** Layout pitch of a verse cell. */
    val verseCellSize = dp(20f)
    val verseMinScale = 18f / 20f
    val verseMaxScale = 56f / 20f

    /**
     * 15sp rather than 16: the verse strip magnifies more than the chapter strip
     * (2.8x against 2.33x), so equal source sizes rendered unequally — 45sp against 42sp
     * in identically sized boxes. Scaling from 15sp puts both centred cells at 42sp, so
     * the two strips finally read as one control, and gives two-digit verses the same
     * breathing room chapters already had.
     */
    val verseTextSize = sp(15f)

    /** Lens reach for the verse strip. Raw pixels — see the class note. */
    val verseLensRadius = 260f

    // ---- Shared cell chrome --------------------------------------------------------

    val cellCornerRadius = dp(6f)
    val cellBorderHaloWidth = dp(4f)
    val cellBorderCoreWidth = dp(1.5f)

    /** Horizontal breathing room each side of a cell's digits, as a fraction of its height. */
    val cellTextPaddingRatio = 0.06f

    /**
     * Widest a cell may grow, as a multiple of its height, before its text shrinks instead.
     *
     * Cells widen to fit their digits rather than squeezing the glyphs, since shrinking
     * text is the worst thing to do to the number the eye is actually fixated on. The cap
     * exists only so an extreme system font scale cannot produce a cell wide enough to
     * swallow its neighbours; past it, text scaling takes over.
     */
    val cellMaxAspect = 1.5f

    // ---- Strip stack ---------------------------------------------------------------

    /** A getter, not a copy: [spineMaxHeight] is retuned by [configure]. */
    val bookStripHeight: Float get() = spineMaxHeight

    var chapterStripHeight = dp(REGULAR_CHAPTER_STRIP_HEIGHT_DP)
        private set

    /**
     * The one vertical dimension that does not shrink on a short screen: a verse cell is
     * drawn at 56dp when it reaches the centre of the lens, so there is nothing spare in
     * the 60dp band around it to give back.
     */
    val verseStripHeight = dp(60f)

    /** Vertical gap between strip levels. */
    var stripSpacing = dp(REGULAR_STRIP_SPACING_DP)
        private set

    /** Gap below the whole stack. */
    var stackBottomPadding = dp(REGULAR_STACK_BOTTOM_PADDING_DP)
        private set

    /** Width the panel and strips are capped at on wide screens. */
    val maxContentWidth = dp(480f)

    /**
     * How far the panel's backdrop fades out along an edge the reader shows through.
     *
     * Roughly matches the depth of the fade at the top of the panel, so the backdrop
     * dissolves into the text at the same rate whichever side it is approached from.
     */
    val panelEdgeFade = dp(64f)

    /**
     * How far outside the strips' own span a touch is still treated as aimed at them.
     *
     * Invisible in portrait, where the strips span the full width. In landscape the stack
     * is narrower than the screen and the space beside it belongs to the reader, so this
     * is what keeps a thumb landing a couple of dp off the panel edge from scrolling the
     * text instead of the strip it was plainly aimed at.
     */
    val stripHitSlopHorizontal = dp(8f)

    /** Height of the gradient panel behind the strips. */
    val panelHeight = dp(420f)

    /** Cumulative vertical travel that triggers a level change. */
    val drillThreshold = dp(50f)

    // ---- Preview bubble ------------------------------------------------------------

    /**
     * Gap between the top of the verse strip and the bottom of the bubble.
     *
     * The regular value is the two strip gaps the Compose column left there. On a short
     * screen that space is exactly what the bubble needs for its own text, so the compact
     * tier hands it back and keeps only enough to read as a gap.
     */
    var bubbleGap = dp(REGULAR_BUBBLE_GAP_DP)
        private set

    val bubbleMaxWidth = dp(340f)
    val bubbleCornerRadius = dp(24f)
    val bubblePaddingHorizontal = dp(20f)
    val bubblePaddingVertical = dp(12f)
    val bubbleElevation = dp(8f)
    val bubbleReferenceTextSize = sp(18f)
    val bubbleVerseTextSize = sp(14f)

    /** Verse text is clipped past this many lines, matching the Compose `maxLines`. */
    val bubbleMaxVerseLines = 5

    /** Dots standing in for the verse text while its read is in flight. */
    val bubbleLoadingDotCount = 3

    val bubbleLoadingDotRadius = dp(2f)

    /** Centre-to-centre spacing of the placeholder dots. */
    val bubbleLoadingDotPitch = dp(8f)

    /**
     * Full width of the dot row (20dp across a 4dp-tall row).
     *
     * Derived rather than a literal because the dots are start-aligned with the verse text
     * block, and mirrored to its end edge in right-to-left layouts; both need the footprint.
     */
    val bubbleLoadingDotsWidth =
        bubbleLoadingDotPitch * (bubbleLoadingDotCount - 1) + bubbleLoadingDotRadius * 2f

    // ---- Skeleton ------------------------------------------------------------------

    /** Placeholder spine width used before the book list has loaded. */
    val skeletonSpineWidth = dp(10f)

    // ---- Tiers ---------------------------------------------------------------------

    /** True while the compact tier is in force; see [configure]. */
    var compact = false
        private set

    /**
     * Retunes the stack for a viewport [viewHeightPx] tall.
     *
     * A landscape phone is around 460dp high. The regular stack takes some 320dp of that
     * before the bubble is drawn at all, and the bubble is anchored above it, so on such a
     * screen it was pushed up over the toolbar and its verse text clipped mid-sentence.
     * The compact tier gives back roughly 55dp of padding and strip height — the parts of
     * the layout that are breathing room rather than content — so the bubble has somewhere
     * to sit.
     *
     * The threshold sits below every portrait phone, which are 640dp and taller, so no
     * device changes tier merely by being held upright. It is also low enough that the
     * regular layout still fits a full bubble everywhere above it, which is what rules out
     * a band where compact would have been the better choice of the two.
     *
     * Call from the view's `onSizeChanged` and on every open. Cheap and idempotent: it
     * returns immediately when the tier is unchanged, so nothing downstream sees a value
     * move without the viewport having crossed the threshold.
     */
    fun configure(viewHeightPx: Float) {
        val pxPerDp = dp(1f)
        val heightDp = if (pxPerDp > 0f) viewHeightPx / pxPerDp else 0f
        val nowCompact = PassageFinderLayoutRules.isCompact(heightDp)
        if (nowCompact == compact) return
        compact = nowCompact
        if (nowCompact) {
            stackBottomPadding = dp(COMPACT_STACK_BOTTOM_PADDING_DP)
            stripSpacing = dp(COMPACT_STRIP_SPACING_DP)
            chapterStripHeight = dp(COMPACT_CHAPTER_STRIP_HEIGHT_DP)
            spineMaxHeight = dp(COMPACT_SPINE_MAX_HEIGHT_DP)
            spineMinHeight = dp(COMPACT_SPINE_MIN_HEIGHT_DP)
            bubbleGap = dp(COMPACT_BUBBLE_GAP_DP)
        } else {
            stackBottomPadding = dp(REGULAR_STACK_BOTTOM_PADDING_DP)
            stripSpacing = dp(REGULAR_STRIP_SPACING_DP)
            chapterStripHeight = dp(REGULAR_CHAPTER_STRIP_HEIGHT_DP)
            spineMaxHeight = dp(REGULAR_SPINE_MAX_HEIGHT_DP)
            spineMinHeight = dp(REGULAR_SPINE_MIN_HEIGHT_DP)
            bubbleGap = dp(REGULAR_BUBBLE_GAP_DP)
        }
    }

    private companion object {
        const val REGULAR_STACK_BOTTOM_PADDING_DP = 16f
        const val REGULAR_STRIP_SPACING_DP = 23f
        const val REGULAR_CHAPTER_STRIP_HEIGHT_DP = 80f
        const val REGULAR_SPINE_MAX_HEIGHT_DP = 123f
        const val REGULAR_SPINE_MIN_HEIGHT_DP = 86f
        const val REGULAR_BUBBLE_GAP_DP = 46f

        const val COMPACT_STACK_BOTTOM_PADDING_DP = 8f

        /**
         * 16dp still clears the 12dp a selected spine rises above the shelf, so the raised
         * book cannot collide with the strip above it.
         */
        const val COMPACT_STRIP_SPACING_DP = 16f

        const val COMPACT_CHAPTER_STRIP_HEIGHT_DP = 64f

        /** The shelf at 0.85 scale — still taller than its widest spine, so it reads right. */
        const val COMPACT_SPINE_MAX_HEIGHT_DP = 104f
        const val COMPACT_SPINE_MIN_HEIGHT_DP = 72f

        const val COMPACT_BUBBLE_GAP_DP = 8f
    }
}

/**
 * The passage finder's layout decisions that are pure arithmetic.
 *
 * Split out from [PassageFinderMetrics] and [PassageFinderView] purely so they can be
 * tested: both of those need a live `DisplayMetrics` or a real Android `View`, whereas
 * these are the rules that actually decide whether the widget fits on a given screen.
 */
object PassageFinderLayoutRules {

    /**
     * Below this the compact tier applies. See [PassageFinderMetrics.configure] for why
     * this particular value, and why nothing sits between the two tiers.
     */
    const val COMPACT_MAX_HEIGHT_DP = 600f

    /**
     * Whether a viewport [viewHeightDp] tall needs the compact tier.
     *
     * A viewport of zero height is a view that has not been measured yet, not a very short
     * one, so it keeps the regular tier until a real size arrives.
     */
    fun isCompact(viewHeightDp: Float): Boolean =
        viewHeightDp > 0f && viewHeightDp < COMPACT_MAX_HEIGHT_DP

    /**
     * How many lines of verse text the preview bubble may show.
     *
     * @param room vertical space between the bottom of the toolbar and the top of the
     *   verse strip at full reveal.
     * @param gap gap the bubble leaves above the verse strip.
     * @param chrome the bubble's own fixed height — its vertical padding plus the
     *   reference line, which is always drawn.
     * @param lineHeight height of one laid-out line of verse text, measured from the font.
     * @param maxLines ceiling from the design; more than a few lines of preview stops
     *   being a glance and starts being reading.
     *
     * Returns 0 when not even one line fits, which the bubble draws as a reference line on
     * its own rather than as a clipped word.
     */
    fun bubbleMaxLines(
        room: Float,
        gap: Float,
        chrome: Float,
        lineHeight: Float,
        maxLines: Int,
    ): Int {
        if (lineHeight <= 0f) return 0
        val forText = room - gap - chrome
        if (forText <= 0f) return 0
        return floor(forText / lineHeight).toInt().coerceIn(0, maxLines)
    }

    /**
     * Left edge of the strip stack for a widget opened by a gesture at [anchorX].
     *
     * The stack is capped at [PassageFinderMetrics.maxContentWidth], so on a wide screen
     * it has to be put somewhere. Centring it is the obvious choice and the wrong one: the
     * finder is a one-thumb control, and a centred stack sits under neither thumb. Placing
     * it under the gesture that summoned it lands it under whichever thumb the user
     * actually reached with, so it needs no handedness setting, and clamping to the
     * viewport keeps it flush to an edge exactly the way portrait already looks.
     *
     * In portrait the stack is as wide as the view, so the clamp collapses to 0 and this
     * changes nothing.
     */
    fun anchoredContentLeft(anchorX: Float, contentWidth: Float, viewWidth: Float): Float {
        val slack = (viewWidth - contentWidth).coerceAtLeast(0f)
        return (anchorX - contentWidth / 2f).coerceIn(0f, slack)
    }
}
