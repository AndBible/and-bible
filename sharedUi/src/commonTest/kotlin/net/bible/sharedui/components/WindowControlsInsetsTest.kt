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
package net.bible.sharedui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS app on a Mac reserves a top safe-area band for the window controls (traffic lights). Comparing the plain
 * safe area with the horizontally corner-adapted one tells how much of the top inset is the controls, and how far
 * sideways they reach.
 */
class WindowControlsInsetsTest {
    private fun controls(safe: Edges, adapted: Edges) = windowControlsInsets(
        safeTop = safe.top, safeLeft = safe.left, safeRight = safe.right,
        adaptedTop = adapted.top, adaptedLeft = adapted.left, adaptedRight = adapted.right,
    )

    private data class Edges(val top: Double, val left: Double = 0.0, val right: Double = 0.0)

    @Test fun controlsAtTheTopLeftMoveTheTopInsetToTheLeft() =
        assertEquals(WindowControlsInsets(top = 28.0, left = 78.0, right = 0.0), controls(Edges(28.0), Edges(0.0, left = 78.0)))

    @Test fun controlsAtTheTopRightMoveTheTopInsetToTheRight() =
        assertEquals(WindowControlsInsets(top = 28.0, left = 0.0, right = 78.0), controls(Edges(28.0), Edges(0.0, right = 78.0)))

    // A real status bar (or the band in full screen, 0) stays: corner adaptation does not move it.
    @Test fun aTopInsetThatAdaptationDoesNotMoveIsKept() =
        assertEquals(WindowControlsInsets.None, controls(Edges(24.0), Edges(24.0)))

    @Test fun noInsetsMeanNothingToMove() = assertEquals(WindowControlsInsets.None, controls(Edges(0.0), Edges(0.0)))

    // Only the controls' part of the top inset moves; what the adapted region still keeps on top stays reserved.
    @Test fun onlyTheControlsPartOfTheTopInsetIsMoved() =
        assertEquals(WindowControlsInsets(top = 8.0, left = 70.0, right = 0.0), controls(Edges(32.0), Edges(24.0, left = 70.0)))

    // Existing side insets (a landscape notch, for example) are not the controls: only the growth counts.
    @Test fun sideInsetsCountOnlyTheirGrowth() =
        assertEquals(WindowControlsInsets(top = 28.0, left = 30.0, right = 0.0), controls(Edges(28.0, left = 48.0, right = 48.0), Edges(0.0, left = 78.0, right = 48.0)))

    // If the top inset would go but nothing grows sideways, we do not know where the controls are: removing the band
    // could put the bar's buttons under them, so nothing changes.
    @Test fun aTopInsetWithoutASidewaysPlaceIsKept() =
        assertEquals(WindowControlsInsets.None, controls(Edges(28.0), Edges(0.0)))
}
