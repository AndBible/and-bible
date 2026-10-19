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
package net.bible.sharedui.reading

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class TiltAxisTest {
    private val up = PI / 2

    private fun deg(pitch: Double, roll: Double, r: TiltScreenRotation) = tiltPitchDegrees(pitch, roll, r)

    @Test
    fun portraitUsesThePitchAndFlipsItsSign() {
        assertEquals(-90f, deg(up, 0.0, TiltScreenRotation.Portrait), 0.01f)
        assertEquals(0f, deg(0.0, 0.0, TiltScreenRotation.Portrait), 0.01f)
    }

    @Test
    fun portraitUpsideDownNegatesThePitch() {
        assertEquals(-90f, deg(-up, 0.0, TiltScreenRotation.PortraitUpsideDown), 0.01f)
    }

    /** In landscape the pitch stays ~0 while the reader tilts; the roll carries the tilt. */
    @Test
    fun landscapeReadsTheRollNotThePitch() {
        assertEquals(-90f, deg(0.0, -up, TiltScreenRotation.LandscapeRight), 0.01f)
        assertEquals(-90f, deg(0.0, up, TiltScreenRotation.LandscapeLeft), 0.01f)
        // A pitch-only sample (the old behaviour) must read as "flat" in landscape.
        assertEquals(0f, deg(up, 0.0, TiltScreenRotation.LandscapeRight), 0.01f)
        assertEquals(0f, deg(up, 0.0, TiltScreenRotation.LandscapeLeft), 0.01f)
    }

    @Test
    fun tiltingForwardInLandscapeMovesTheSameWayAsInPortrait() {
        // From upright, leaning the top away by 10 degrees lowers the upright angle by 10 in every orientation.
        val lean = 10 * PI / 180
        val portrait = deg(up - lean, 0.0, TiltScreenRotation.Portrait)
        val right = deg(0.0, -(up - lean), TiltScreenRotation.LandscapeRight)
        val left = deg(0.0, up - lean, TiltScreenRotation.LandscapeLeft)
        assertEquals(portrait, right, 0.01f)
        assertEquals(portrait, left, 0.01f)
    }
}
