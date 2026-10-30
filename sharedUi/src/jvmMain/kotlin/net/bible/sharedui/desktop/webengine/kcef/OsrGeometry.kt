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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Size and placement of an off-screen browser view. Compose works in view pixels, CEF in DIP (pixels / [scale]).
 *
 * @param widthPx view width in physical pixels
 * @param heightPx view height in physical pixels
 * @param scale device scale factor (pixels per DIP)
 * @param screenOrigin screen position of the view's top-left corner, in screen coordinates
 */
internal data class OsrGeometry(val widthPx: Int, val heightPx: Int, val scale: Double, val screenOrigin: Point) {
    /** View width in DIP, at least 1 (CEF rejects an empty view rect). */
    val widthDip: Int get() = ceil(widthPx / scale).toInt().coerceAtLeast(1)

    /** View height in DIP, at least 1. */
    val heightDip: Int get() = ceil(heightPx / scale).toInt().coerceAtLeast(1)

    /**
     * Pixel size CEF actually paints for the DIP view rect, \`ceil(dip * scale)\`. It can exceed [widthPx] at fractional
     * scales (1001 px at 1.5 gives 668 DIP and a 1002 px paint). [OsrFrameBuffer.resize] must use these, not [widthPx].
     */
    val bufferWidthPx: Int get() = ceil(widthDip * scale).toInt()

    /** Pixel height CEF paints, see [bufferWidthPx]. */
    val bufferHeightPx: Int get() = ceil(heightDip * scale).toInt()

    /** Maps a Compose pixel offset inside the view to the view DIP that contains it (floored, as AWT does). */
    fun toViewDip(xPx: Float, yPx: Float): Point = Point(floor(xPx / scale).toInt(), floor(yPx / scale).toInt())

    /** Maps a view DIP coordinate to view pixels. */
    fun toViewPx(xDip: Int, yDip: Int): Point = Point((xDip * scale).roundToInt(), (yDip * scale).roundToInt())

    /** Maps a view DIP point to screen coordinates (adds the window/view origin). */
    fun toScreen(viewDip: Point): Point = Point(screenOrigin.x + viewDip.x, screenOrigin.y + viewDip.y)
}
