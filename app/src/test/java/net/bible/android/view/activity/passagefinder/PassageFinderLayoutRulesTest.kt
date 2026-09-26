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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the passage finder's layout arithmetic.
 *
 * These are the decisions that determine whether the widget fits on a given screen —
 * which tier applies, how much of the preview bubble is drawable, and where the stack
 * lands on a screen wider than it is. They were pulled out of the view and the metrics
 * precisely so they could be pinned here: everything around them needs a live Android
 * `View` or `DisplayMetrics` and cannot be exercised in a plain unit test.
 */
class PassageFinderLayoutRulesTest {

    // ---- Tier selection ------------------------------------------------------------

    @Test
    fun `landscape phones are compact and portrait phones are not`() {
        // The reported device: 1280x600 at ~1.3x, so about 460dp tall in landscape.
        assertTrue("a landscape phone must get the compact tier", PassageFinderLayoutRules.isCompact(460f))
        // The shortest phones are around 640dp tall upright, and tablets are taller still.
        assertFalse("a portrait phone must keep the regular tier", PassageFinderLayoutRules.isCompact(640f))
        assertFalse(PassageFinderLayoutRules.isCompact(800f))
    }

    @Test
    fun `the threshold leaves no gap between the two tiers`() {
        // Exactly at the threshold the regular layout still fits a full bubble, so the
        // boundary must fall on the regular side. A device one dp shorter is compact.
        assertFalse(PassageFinderLayoutRules.isCompact(PassageFinderLayoutRules.COMPACT_MAX_HEIGHT_DP))
        assertTrue(PassageFinderLayoutRules.isCompact(PassageFinderLayoutRules.COMPACT_MAX_HEIGHT_DP - 1f))
    }

    @Test
    fun `an unmeasured view keeps the regular tier`() {
        // Zero height means "not laid out yet", not "very short screen". Treating it as
        // compact would retune every dimension on construction and then again on the first
        // real measurement, which for a portrait phone is a tier change nothing asked for.
        assertFalse(PassageFinderLayoutRules.isCompact(0f))
    }

    // ---- Bubble line budget --------------------------------------------------------

    @Test
    fun `the reported landscape device gets three lines of preview`() {
        // 460dp tall behind an 80dp toolbar. Compact tier, so an 8dp gap; chrome is the
        // bubble's 12dp padding twice over plus a ~22dp reference line.
        val lines = PassageFinderLayoutRules.bubbleMaxLines(
            room = 112f, gap = 8f, chrome = 46f, lineHeight = 18f, maxLines = 5,
        )
        assertEquals(3, lines)
    }

    @Test
    fun `a tall screen is capped by the design ceiling, not by the room available`() {
        val lines = PassageFinderLayoutRules.bubbleMaxLines(
            room = 900f, gap = 46f, chrome = 46f, lineHeight = 18f, maxLines = 5,
        )
        assertEquals("plenty of room must still stop at the ceiling", 5, lines)
    }

    @Test
    fun `a partial line is never counted`() {
        // Rounding up here is what clips a verse mid-sentence, which is the defect this
        // whole budget exists to prevent: the last line has to fit whole or not at all.
        val exactlyTwo = PassageFinderLayoutRules.bubbleMaxLines(
            room = 100f, gap = 10f, chrome = 54f, lineHeight = 18f, maxLines = 5,
        )
        assertEquals(2, exactlyTwo)
        val justUnderThree = PassageFinderLayoutRules.bubbleMaxLines(
            room = 117f, gap = 10f, chrome = 54f, lineHeight = 18f, maxLines = 5,
        )
        assertEquals("17dp of slack is not a line", 2, justUnderThree)
    }

    @Test
    fun `no room at all yields a reference-only bubble rather than a negative count`() {
        assertEquals(
            0,
            PassageFinderLayoutRules.bubbleMaxLines(
                room = 40f, gap = 8f, chrome = 46f, lineHeight = 18f, maxLines = 5,
            ),
        )
        assertEquals(
            "a bubble anchored below the toolbar has negative room",
            0,
            PassageFinderLayoutRules.bubbleMaxLines(
                room = -20f, gap = 8f, chrome = 46f, lineHeight = 18f, maxLines = 5,
            ),
        )
    }

    @Test
    fun `a degenerate line height cannot divide by zero`() {
        assertEquals(
            0,
            PassageFinderLayoutRules.bubbleMaxLines(
                room = 400f, gap = 8f, chrome = 46f, lineHeight = 0f, maxLines = 5,
            ),
        )
    }

    @Test
    fun `a large font scale spends the same room on fewer lines`() {
        val room = 200f
        val small = PassageFinderLayoutRules.bubbleMaxLines(room, 8f, 46f, 18f, 5)
        val large = PassageFinderLayoutRules.bubbleMaxLines(room, 8f, 66f, 34f, 5)
        assertTrue(
            "scaling the font up must cost lines, not overflow the bubble",
            large < small,
        )
    }

    // ---- Stack anchoring -----------------------------------------------------------

    @Test
    fun `portrait is unaffected because the stack is as wide as the view`() {
        // The whole point of the clamp: where there is no slack, every anchor gives 0,
        // so the widget looks exactly as it always has on a phone held upright.
        for (anchor in listOf(0f, 100f, 540f, 1080f)) {
            assertEquals(
                0f,
                PassageFinderLayoutRules.anchoredContentLeft(anchor, contentWidth = 1080f, viewWidth = 1080f),
            )
        }
    }

    @Test
    fun `the stack centres on the gesture that opened it`() {
        assertEquals(
            300f,
            PassageFinderLayoutRules.anchoredContentLeft(
                anchorX = 700f, contentWidth = 800f, viewWidth = 1900f,
            ),
        )
    }

    @Test
    fun `a gesture near an edge pushes the stack flush to it, never past`() {
        assertEquals(
            "a thumb at the left edge lands the stack flush left",
            0f,
            PassageFinderLayoutRules.anchoredContentLeft(
                anchorX = 40f, contentWidth = 800f, viewWidth = 1900f,
            ),
        )
        assertEquals(
            "and at the right edge, flush right",
            1100f,
            PassageFinderLayoutRules.anchoredContentLeft(
                anchorX = 1880f, contentWidth = 800f, viewWidth = 1900f,
            ),
        )
    }

    @Test
    fun `a stack wider than the view still starts at the left edge`() {
        // Cannot happen with the current cap, but the clamp must not invert if it ever
        // does: coerceIn with a reversed range throws.
        assertEquals(
            0f,
            PassageFinderLayoutRules.anchoredContentLeft(
                anchorX = 500f, contentWidth = 1200f, viewWidth = 1000f,
            ),
        )
    }

    @Test
    fun `rescaling an anchor across a rotation keeps the stack in the same relative place`() {
        // What onSizeChanged does: the anchor is a position within the old width, so it is
        // rescaled rather than reset, and the result must still be a legal left edge.
        val oldWidth = 1080f
        val newWidth = 1900f
        val anchor = 900f
        val rescaled = anchor * newWidth / oldWidth
        val left = PassageFinderLayoutRules.anchoredContentLeft(rescaled, 800f, newWidth)
        assertTrue("stack must stay within the viewport", left >= 0f && left + 800f <= newWidth)
        assertTrue("and stay on the side the gesture was on", left > (newWidth - 800f) / 2f)
    }
}
