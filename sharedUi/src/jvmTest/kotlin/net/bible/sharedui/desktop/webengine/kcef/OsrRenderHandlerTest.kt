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
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsrRenderHandlerTest {
    private fun handler(geo: OsrGeometry, cursor: (Int) -> Unit = {}): Pair<OsrRenderHandler, OsrFrameBuffer> {
        val fb = OsrFrameBuffer(publisher = { it.run() })
        return OsrRenderHandler({ geo }, fb, cursor) to fb
    }

    @Test fun deviceScaleFollowsDensity() {
        val (h, _) = handler(OsrGeometry(1500, 900, 1.5, Point(0, 0)))
        assertEquals(1.5, h.getDeviceScaleFactor(null))
        val r = h.getViewRect(null)
        assertEquals(Rectangle(0, 0, 1000, 600), r)
    }

    @Test fun viewRectIsNeverEmpty() {
        val (h, _) = handler(OsrGeometry(0, 0, 2.0, Point(0, 0)))
        val r = h.getViewRect(null)
        assertTrue(r.width >= 1 && r.height >= 1)
    }

    @Test fun pointerMappedToViewCoordinates() {
        val g = OsrGeometry(1500, 900, 1.5, Point(0, 0))
        assertEquals(Point(100, 200), g.toViewDip(150f, 300f))
        assertEquals(Point(150, 300), g.toViewPx(100, 200))
    }

    @Test fun screenPointAddsWindowOrigin() {
        val (h, _) = handler(OsrGeometry(1000, 600, 2.0, Point(300, 200)))
        assertEquals(Point(310, 220), h.getScreenPoint(null, Point(10, 20)))
    }

    @Test fun screenInfoCarriesScale() {
        val (h, _) = handler(OsrGeometry(1000, 600, 2.0, Point(0, 0)))
        val info = org.cef.handler.CefScreenInfo()
        assertTrue(h.getScreenInfo(null, info))
        assertEquals(2.0, info.device_scale_factor)
        assertEquals(500, info.width)
    }

    @Test fun cursorForwarded() {
        var seen = -1
        val (h, _) = handler(OsrGeometry(10, 10, 1.0, Point(0, 0))) { seen = it }
        assertTrue(h.onCursorChange(null, 12))
        assertEquals(12, seen)
    }

    @Test fun popupRectIsScaledToViewPixels() {
        val (h, fb) = handler(OsrGeometry(8, 4, 2.0, Point(0, 0)))
        fb.resize(8, 4)
        fun solid(w: Int, h2: Int, b: Int) = ByteBuffer.allocateDirect(w * h2 * 4).also {
            repeat(w * h2) { _ -> it.put(b.toByte()); it.put(0); it.put(0); it.put(0xFF.toByte()) }; it.rewind()
        }
        h.onPaint(null, false, arrayOf(Rectangle(0, 0, 8, 4)), solid(8, 4, 10), 8, 4)
        h.onPopupSize(null, Rectangle(1, 0, 2, 1)) // DIP -> px (2,0,4,2)
        h.onPopupShow(null, true)
        h.onPaint(null, true, arrayOf(Rectangle(0, 0, 4, 2)), solid(4, 2, 150), 4, 2)
        val out = IntArray(32); fb.frame.value!!.readPixels(out)
        assertEquals(150, out[0 * 8 + 2] and 0xFF)
        assertEquals(150, out[1 * 8 + 5] and 0xFF)
        assertEquals(10, out[0 * 8 + 1] and 0xFF)
        assertEquals(10, out[2 * 8 + 2] and 0xFF)
        h.onPopupShow(null, false)
        fb.frame.value!!.readPixels(out)
        assertEquals(10, out[2] and 0xFF)
    }
}
