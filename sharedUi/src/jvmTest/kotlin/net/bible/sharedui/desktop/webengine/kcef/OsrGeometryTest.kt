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
package net.bible.sharedui.desktop.webengine.kcef

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #4006: on Linux a fractional display scale is split into an integer AWT scale and a Compose density factor, so the
 * pane's Compose density (CEF device scale) differs from the AWT scale. Screen coordinates for AWT (mouse events, window
 * bounds) are AWT units = screen pixels / AWT scale; CEF's screen coordinates are DIP = screen pixels / device scale.
 */
class OsrGeometryTest {
    /** 150 % on Linux: AWT at 1, Compose density 1.5. The pane is 1500 x 900 px at screen pixel (300, 150). */
    private val fractional = OsrGeometry(1500, 900, 1.5, Point(300, 150), awtScale = 1.0)

    @Test fun awtRectUsesTheAwtScaleNotTheComposeDensity() {
        assertEquals(Rectangle(300, 150, 1500, 900), fractional.awtScreenRect)
    }

    @Test fun pressNearTheBottomRightCornerIsInsideAndChromeLeftOfThePaneIsOutside() {
        val rect = fractional.awtScreenRect
        assertTrue(rect.contains(Point(1790, 1040)), "bottom-right part of the pane")
        assertFalse(rect.contains(Point(290, 160)), "toolbar just left of the pane")
        assertFalse(OsrOutsidePress.shouldClear(true, "w", "w", Point(1790, 1040), rect))
        assertTrue(OsrOutsidePress.shouldClear(true, "w", "w", Point(290, 160), rect))
    }

    @Test fun cefScreenCoordinatesStayInDip() {
        assertEquals(Point(200, 100), fractional.screenOrigin)
        assertEquals(Point(210, 120), fractional.toScreen(Point(10, 20)))
        assertEquals(1000, fractional.widthDip)
    }

    @Test fun integerScaleKeepsAwtAndDipTheSame() {
        val g = OsrGeometry(1000, 600, 2.0, Point(600, 400), awtScale = 2.0)
        assertEquals(Rectangle(300, 200, 500, 300), g.awtScreenRect)
        assertEquals(Point(300, 200), g.screenOrigin)
    }

    /** 250 %: AWT 2, Compose density 2.5. */
    @Test fun mixedScaleAboveTwo() {
        val g = OsrGeometry(1001, 501, 2.5, Point(401, 201), awtScale = 2.0)
        assertEquals(Rectangle(200, 100, 501, 251), g.awtScreenRect)
        assertEquals(Point(160, 80), g.screenOrigin)
    }
}
