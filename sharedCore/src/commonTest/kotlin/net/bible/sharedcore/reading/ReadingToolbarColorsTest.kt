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

package net.bible.sharedcore.reading

import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingToolbarColorsTest {

    private val lightSurface = 0xFFFFFBFE.toInt()
    private val darkSurface = 0xFF1C1B1F.toInt()
    private val orange = 0xFFFF6D00.toInt()

    @Test
    fun nullIsNotSet() = assertFalse(isWorkspaceColorSet(null))

    @Test
    fun theClassicDefaultIsNotSet() = assertFalse(isWorkspaceColorSet(DEFAULT_WORKSPACE_COLOR_ARGB))

    @Test
    fun anyOtherColourIsSet() = assertTrue(isWorkspaceColorSet(orange))

    @Test
    fun notSetKeepsTheSurfaceInEveryMode() {
        for (mode in DisplayColorMode.entries) {
            for (night in listOf(false, true)) {
                val surface = if (night) darkSurface else lightSurface
                assertEquals(
                    surface,
                    readingToolbarContainerArgb(null, surface, night, mode),
                    "not-set must be a no-op (night=$night, mode=$mode)",
                )
                assertEquals(
                    surface,
                    readingToolbarContainerArgb(DEFAULT_WORKSPACE_COLOR_ARGB, surface, night, mode),
                    "the default sentinel must be a no-op (night=$night, mode=$mode)",
                )
            }
        }
    }

    @Test
    fun setInDayIsTheColourItself() {
        assertEquals(
            orange,
            readingToolbarContainerArgb(orange, lightSurface, nightMode = false, colorMode = DisplayColorMode.NORMAL),
        )
    }

    @Test
    fun setInDayIsGreyscaledInBw() {
        val result = readingToolbarContainerArgb(orange, lightSurface, false, DisplayColorMode.BW)
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        assertTrue(r == g && g == b, "BW must be grey, got #${result.toUInt().toString(16)}")
    }

    @Test
    fun setInDayStaysColouredInColorEink() {
        assertEquals(orange, readingToolbarContainerArgb(orange, lightSurface, false, DisplayColorMode.COLOR_EINK))
    }

    @Test
    fun setInNightIsBlendedTowardsTheSurface() {
        val result = readingToolbarContainerArgb(orange, darkSurface, nightMode = true, colorMode = DisplayColorMode.NORMAL)
        // Exactly 30% of the way from the dark surface to the accent, per channel.
        assertEquals(lerpArgb(darkSurface, orange, NIGHT_WORKSPACE_TINT_FRACTION), result)
        // And it must stay much closer to the surface than to the raw colour.
        val red = (result shr 16) and 0xFF
        assertTrue(red < 0x80, "night tint must not be glaring, red=$red")
    }

    @Test
    fun setInNightAndBwIsGreyscaledThenBlended() {
        val result = readingToolbarContainerArgb(orange, darkSurface, nightMode = true, colorMode = DisplayColorMode.BW)
        // The orange is greyscaled first by accentArgbFor, then blended with the dark surface.
        // The greyscaled accent (grey) blended with the dark surface (near-grey) should be very close to grey —
        // all channels should differ by at most 2-3 due to the non-perfectly-grey surface.
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        val maxDiff = maxOf(kotlin.math.abs(r - g), kotlin.math.abs(g - b), kotlin.math.abs(r - b))
        assertTrue(maxDiff <= 3, "set + night + BW must be nearly grey (channels differ by at most 3), got #${result.toUInt().toString(16)} (r=$r, g=$g, b=$b, maxDiff=$maxDiff)")
    }
    // Note: COLOR_EINK is not tested in night mode because accentArgbFor only special-cases BW,
    // so COLOR_EINK takes the identical code path as NORMAL.

    @Test
    fun lerpEndpointsAreExact() {
        assertEquals(darkSurface, lerpArgb(darkSurface, orange, 0f))
        assertEquals(orange, lerpArgb(darkSurface, orange, 1f))
    }

    @Test
    fun lerpKeepsFullAlpha() {
        val result = lerpArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF, (result ushr 24) and 0xFF)
    }
}
