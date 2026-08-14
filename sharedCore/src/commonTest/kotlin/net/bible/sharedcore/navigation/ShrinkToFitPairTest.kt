/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShrinkToFitPairTest {

    @Test
    fun `both naturals fit -- neither is cut`() {
        val r = shrinkToFitPair(available = 300, gap = 8, languageNatural = 50, typeNatural = 100)
        assertEquals(ShrinkToFitWidths(languageWidth = 50, typeWidth = 100, gap = 8), r)
    }

    /**
     * "Only language is long" taken to its limit: the type chip's own natural width is zero (it
     * has nothing to give), so however the shortfall is attributed, type's cut is clamped to its
     * own natural extent and it is left exactly as it started -- the whole shortfall spills onto
     * language instead. This is the only way to reach a byte-exact "type keeps its natural width"
     * under the deployed 2/3 language-share ratio: the ratio math guarantees type is asked for
     * a nonzero slice of any real (nonzero) shortfall whenever it *has* nonzero natural width to
     * give (see the "both long" and "clamp redistributes" cases below for that ordinary
     * territory) -- it is only left alone when there is nothing left to take.
     */
    @Test
    fun `type with no natural width of its own is left untouched -- language absorbs the whole shortfall`() {
        val r = shrinkToFitPair(available = 100, gap = 8, languageNatural = 200, typeNatural = 0)
        assertEquals(0, r.typeWidth) // == typeNatural: untouched
        // Type's designated 1/3 share of the shortfall (36) can't be taken from its zero natural
        // width, so language absorbs that too, on top of its own 2/3 share (72) -- 92, not just 72.
        assertEquals(92, r.languageWidth)
        assertEquals(8, r.gap) // gap fits comfortably within `available`, so it is untouched too
        assertEquals(100, r.languageWidth + r.typeWidth + r.gap) // exactly fills `available`
    }

    @Test
    fun `both long -- the shortfall is split proportionally and each result is positive`() {
        val r = shrinkToFitPair(available = 100, gap = 0, languageNatural = 150, typeNatural = 80)
        assertEquals(64, r.languageWidth)
        assertEquals(36, r.typeWidth)
        assertTrue(r.languageWidth > 0)
        assertTrue(r.typeWidth > 0)
        // Neither chip's natural width, so both actually shrank.
        assertTrue(r.languageWidth < 150)
        assertTrue(r.typeWidth < 80)
    }

    /**
     * Language's designated 2/3 share of the shortfall (113) would drive it past zero (it only
     * has 20 to give) -- it is clamped to exactly its own natural width instead, and type absorbs
     * the leftover (150) rather than that leftover being silently dropped, even though 150 is far
     * more than type's own "fair" 1/3 share (57) would have been.
     */
    @Test
    fun `a cut that would exceed a chip's natural width is clamped -- the other chip absorbs what's left over`() {
        val r = shrinkToFitPair(available = 50, gap = 0, languageNatural = 20, typeNatural = 200)
        assertEquals(0, r.languageWidth) // clamped: fully spent, not negative
        assertEquals(50, r.typeWidth) // absorbed language's leftover shortfall (150), not just its own 1/3 share (57)
        assertEquals(50, r.languageWidth + r.typeWidth) // the full shortfall was accounted for, none silently dropped
    }

    /**
     * The property the Minor finding was about, asserted directly rather than reasoned about:
     * however the four inputs combine -- including `available` narrower than `gap`, and zero --
     * the two assigned widths (plus the ACTUAL, possibly-clamped [ShrinkToFitWidths.gap]) never
     * claim more than `available`, and neither width is ever negative. Swept across a wide range
     * of combinations, including several genuinely degenerate ones no real Compose Constraints
     * would ever produce (available narrower than the nominal gap cannot happen on a real device),
     * because the point of fixing this "by construction" is that it holds unconditionally, not
     * only for the inputs anyone thought to check by hand.
     *
     * Also sweeps [net.bible.sharedcore.navigation.shrinkToFitPair]'s `minWidth` floor (added for
     * the round-6 fix wave's Important-3 finding) across the same combinations, so the floor
     * parameter is proven not to break the base invariant for any input, including one it cannot
     * possibly honor (a floor larger than `available` itself).
     */
    @Test
    fun `invariant -- widths plus the actual gap never exceed available and neither width is negative`() {
        val availables = listOf(0, 1, 2, 3, 5, 7, 8, 9, 15, 50, 100, 500)
        val gaps = listOf(0, 1, 8, 20)
        val naturals = listOf(0, 1, 5, 30, 200, 1000)
        val shares = listOf(0f, 1f / 3f, 2f / 3f, 1f)
        val minWidths = listOf(0, 1, 40, 1000)

        for (available in availables) {
            for (gap in gaps) {
                for (languageNatural in naturals) {
                    for (typeNatural in naturals) {
                        for (share in shares) {
                            for (minWidth in minWidths) {
                                val r = shrinkToFitPair(available, gap, languageNatural, typeNatural, share, minWidth)
                                val ctx = "available=$available gap=$gap languageNatural=$languageNatural " +
                                    "typeNatural=$typeNatural share=$share minWidth=$minWidth -> $r"
                                assertTrue(r.languageWidth >= 0, "languageWidth negative for $ctx")
                                assertTrue(r.typeWidth >= 0, "typeWidth negative for $ctx")
                                assertTrue(
                                    r.languageWidth + r.typeWidth + r.gap <= available,
                                    "invariant violated for $ctx",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Important-3 fix: with room to honor it, neither chip is cut below [minWidth] -- the defect
     * being fixed was a long language name shrinking the type chip down to a single letter ("A…"),
     * which is ambiguous between "All types" and "Add-ons".
     */
    @Test
    fun `floor -- neither chip is cut below minWidth when there is room to honor it`() {
        // Both naturals are comfortably above the floor and there is exactly enough budget to
        // cut both down to the floor (40 + 40 + gap 8 = 88) with room to spare.
        val r = shrinkToFitPair(available = 100, gap = 8, languageNatural = 300, typeNatural = 300, minWidth = 40)
        assertTrue(r.languageWidth >= 40, "languageWidth $r fell below the floor")
        assertTrue(r.typeWidth >= 40, "typeWidth $r fell below the floor")
        assertEquals(92, r.languageWidth + r.typeWidth) // fully uses the budget (100 - gap 8)
    }

    /**
     * The floor is honored by taking the shortfall from the OTHER chip first: language's ordinary
     * 2/3-of-shortfall share would cut it below the floor here, so type absorbs the rest instead of
     * language being pushed under 40.
     */
    @Test
    fun `floor -- the other chip absorbs what the floor won't let one chip give up`() {
        // language's ordinary 2/3-of-shortfall share (166) would cut it from 50 down to -116; it
        // is clamped at the floor (40) instead, and type -- which can afford it -- absorbs the rest.
        val r = shrinkToFitPair(available = 100, gap = 0, languageNatural = 50, typeNatural = 300, minWidth = 40)
        assertEquals(40, r.languageWidth) // clamped at the floor, not cut further
        assertEquals(60, r.typeWidth) // absorbed the rest of the shortfall
        assertEquals(100, r.languageWidth + r.typeWidth)
    }

    /**
     * When honoring both floors together genuinely would not fit -- here 2 * 40 + gap(8) = 88 >
     * available(50) -- the floor cannot be a bound this function enforces without breaking the
     * stronger "never claim more than available" invariant, so it is relaxed back to the
     * floor-free clamp-and-redistribute behaviour (matching a `minWidth = 0` call with the same
     * other inputs) rather than silently overflowing `available`.
     */
    @Test
    fun `floor -- relaxed entirely when honoring both floors would not fit in available`() {
        val withFloor = shrinkToFitPair(available = 50, gap = 0, languageNatural = 20, typeNatural = 200, minWidth = 40)
        val withoutFloor = shrinkToFitPair(available = 50, gap = 0, languageNatural = 20, typeNatural = 200, minWidth = 0)
        assertEquals(withoutFloor, withFloor)
    }

    /** A chip whose own natural width is already under the floor is left alone -- never padded up. */
    @Test
    fun `floor -- a chip already narrower than the floor is not grown`() {
        // language is short ("All") and would never need cutting on its own; only type's bulk
        // triggers the shrink. language's floor (40) exceeds its natural width (20), so its
        // effective floor is capped to what it naturally has -- it must not be grown past 20.
        val r = shrinkToFitPair(available = 60, gap = 0, languageNatural = 20, typeNatural = 300, minWidth = 40)
        assertTrue(r.languageWidth <= 20, "a chip must never be grown past its own natural width: $r")
    }
}
