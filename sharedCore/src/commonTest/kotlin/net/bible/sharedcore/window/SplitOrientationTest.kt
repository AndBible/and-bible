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

package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F69 / F64 / A/B F6-B1. The split follows the WINDOW's shape; the measured box is only a fallback.
 * The keyboard shrinks the box, never the window, so every "keyboard up" case below is expressed as
 * a window/box mismatch.
 */
class SplitOrientationTest {
    @Test fun aLandscapeWindowSplitsSideBySide() =
        assertTrue(splitIsHorizontal(2000, 1000, 2000f, 900f, reverseSplitMode = false))

    @Test fun aPortraitWindowStacks() =
        assertFalse(splitIsHorizontal(1000, 2000, 1000f, 1800f, reverseSplitMode = false))

    // F69 and A/B F6-B1 in one case: portrait window, keyboard up, the box is now wider than tall.
    @Test fun aPortraitWindowStacksEvenWhenTheKeyboardMadeTheBoxWiderThanTall() =
        assertFalse(splitIsHorizontal(1000, 2000, 1000f, 400f, reverseSplitMode = false))

    // The mirror: landscape window whose box became taller than wide (e.g. a tall agent panel).
    @Test fun aLandscapeWindowStaysSideBySideWhateverTheBoxShape() =
        assertTrue(splitIsHorizontal(2000, 1000, 500f, 900f, reverseSplitMode = false))

    @Test fun reverseSplitModeInvertsBothOrientations() {
        assertFalse(splitIsHorizontal(2000, 1000, 2000f, 900f, reverseSplitMode = true))
        assertTrue(splitIsHorizontal(1000, 2000, 1000f, 400f, reverseSplitMode = true))
    }

    // Review Focus 2: an unknown window (0 on either side) falls back to the box, not to "stacked".
    @Test fun anUnknownWindowSizeFallsBackToTheBox() {
        assertTrue(splitIsHorizontal(0, 0, 1000f, 500f, reverseSplitMode = false))
        assertFalse(splitIsHorizontal(0, 0, 500f, 1000f, reverseSplitMode = false))
        assertTrue(splitIsHorizontal(1080, 0, 1000f, 500f, reverseSplitMode = false))
    }

    // Strict comparison: a square window is not "wider than tall".
    @Test fun anExactlySquareWindowStacks() =
        assertFalse(splitIsHorizontal(800, 800, 900f, 700f, reverseSplitMode = false))
}
