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

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

/** #3900: with the status bar hidden, a top cutout is cleared sideways, only where it covers an edge button. */
class TopBarCutoutSidePaddingTest {
    // pixel_6_pro: 1440 px wide, density 3.5, so the 56 dp edge button zone is 196 px.
    private val width = 1440
    private val zone = 196

    private fun pad(vararg rects: IntRect) = topBarCutoutSidePadding(width, zone, rects.toList())

    @Test fun noCutoutNoPadding() = assertEquals(CutoutSidePadding(0, 0), pad())

    // The emulator's cutout.emulation.hole: centre (84, 83), radius 44.
    @Test fun aTopLeftHoleOverTheLeadingButtonPadsTheLeftJustPastTheHole() =
        assertEquals(CutoutSidePadding(128, 0), pad(IntRect(40, 39, 128, 127)))

    @Test fun aTopRightHoleOverTheTrailingButtonPadsTheRightJustPastTheHole() =
        assertEquals(CutoutSidePadding(0, 128), pad(IntRect(1312, 39, 1400, 127)))

    @Test fun aCornerCutoutFromTheEdgePadsItsWidth() =
        assertEquals(CutoutSidePadding(150, 0), pad(IntRect(0, 0, 150, 120)))

    // Pixel 7: a centred punch hole sits over the title, not a button; it may stay there.
    @Test fun aCentredHoleIsLeftAlone() = assertEquals(CutoutSidePadding(0, 0), pad(IntRect(680, 30, 760, 110)))

    // The emulator's cutout.emulation.tall: a wide centred notch.
    @Test fun aWideCentredNotchIsLeftAlone() = assertEquals(CutoutSidePadding(0, 0), pad(IntRect(420, 0, 1020, 200)))

    // A cutout that does reach the button zone but would need more than a quarter of the bar to clear
    // is not avoided: squeezing the bar that far is worse than the overlap.
    @Test fun aCutoutTooWideToClearIsLeftAlone() = assertEquals(CutoutSidePadding(0, 0), pad(IntRect(100, 0, 700, 120)))

    @Test fun aHoleJustInsideTheButtonZoneCounts() =
        assertEquals(CutoutSidePadding(260, 0), pad(IntRect(zone - 1, 30, 260, 110)))

    @Test fun aHoleBeyondTheButtonZoneDoesNot() = assertEquals(CutoutSidePadding(0, 0), pad(IntRect(zone, 30, 260, 110)))

    @Test fun holesOnBothSidesPadBothAndTheWiderOneWinsPerSide() =
        assertEquals(
            CutoutSidePadding(128, 160),
            pad(IntRect(40, 39, 128, 127), IntRect(0, 0, 90, 80), IntRect(1280, 0, 1440, 100)),
        )
}
