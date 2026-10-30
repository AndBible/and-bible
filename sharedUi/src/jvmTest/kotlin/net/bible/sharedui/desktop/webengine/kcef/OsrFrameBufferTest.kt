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

import androidx.compose.ui.graphics.ImageBitmap
import java.awt.Rectangle
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsrFrameBufferTest {
    private fun newBuffer() = OsrFrameBuffer(publisher = { it.run() })

    /** A BGRA premultiplied buffer where every pixel is opaque with the given blue value. */
    private fun solid(w: Int, h: Int, blue: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(w * h * 4)
        for (i in 0 until w * h) { b.put(blue.toByte()); b.put(0); b.put(0); b.put(0xFF.toByte()) }
        b.rewind()
        return b
    }

    private fun pixels(img: ImageBitmap): IntArray {
        val out = IntArray(img.width * img.height)
        img.readPixels(out)
        return out
    }

    private fun blue(argb: Int) = argb and 0xFF

    @Test fun fullFrameCopied() {
        val fb = newBuffer()
        fb.resize(4, 3)
        val src = ByteBuffer.allocateDirect(4 * 3 * 4)
        for (i in 0 until 12) { src.put((i * 10).toByte()); src.put(0); src.put(0); src.put(0xFF.toByte()) }
        src.rewind()
        fb.paint(false, arrayOf(Rectangle(0, 0, 4, 3)), src, 4, 3)
        val img = fb.frame.value!!
        assertEquals(4, img.width); assertEquals(3, img.height)
        assertEquals((0 until 12).map { it * 10 }, pixels(img).map { blue(it) })
    }

    @Test fun dirtyRectsOnlyUpdateTheirArea() {
        val fb = newBuffer()
        fb.resize(4, 3)
        fb.paint(false, arrayOf(Rectangle(0, 0, 4, 3)), solid(4, 3, 10), 4, 3)
        fb.paint(false, arrayOf(Rectangle(1, 1, 2, 1)), solid(4, 3, 200), 4, 3)
        val p = pixels(fb.frame.value!!).map { blue(it) }
        for (y in 0 until 3) for (x in 0 until 4) {
            val expected = if (y == 1 && x in 1..2) 200 else 10
            assertEquals(expected, p[y * 4 + x], "pixel $x,$y")
        }
    }

    @Test fun staleSizedFrameIsDropped() {
        val fb = newBuffer()
        fb.resize(4, 3)
        fb.paint(false, arrayOf(Rectangle(0, 0, 4, 3)), solid(4, 3, 10), 4, 3)
        fb.paint(false, arrayOf(Rectangle(0, 0, 2, 2)), solid(2, 2, 99), 2, 2)
        assertEquals(setOf(10), pixels(fb.frame.value!!).map { blue(it) }.toSet())
    }

    @Test fun dirtyRectOutsideBufferIgnored() {
        val fb = newBuffer()
        fb.resize(4, 3)
        fb.paint(false, arrayOf(Rectangle(0, 0, 4, 3)), solid(4, 3, 10), 4, 3)
        fb.paint(false, arrayOf(Rectangle(10, 10, 5, 5), Rectangle(3, 2, 9, 9)), solid(4, 3, 77), 4, 3)
        val p = pixels(fb.frame.value!!).map { blue(it) }
        assertEquals(77, p[2 * 4 + 3])
        assertEquals(11, p.count { it == 10 })
    }

    @Test fun noFrameBeforeFirstPaint() {
        val fb = newBuffer()
        fb.resize(4, 3)
        assertNull(fb.frame.value)
    }

    @Test fun popupPaintedAtPopupRect() {
        val fb = newBuffer()
        fb.resize(6, 4)
        fb.paint(false, arrayOf(Rectangle(0, 0, 6, 4)), solid(6, 4, 10), 6, 4)
        fb.popupRect(Rectangle(2, 1, 3, 2))
        fb.paint(true, arrayOf(Rectangle(0, 0, 3, 2)), solid(3, 2, 150), 3, 2)
        val p = pixels(fb.frame.value!!).map { blue(it) }
        for (y in 0 until 4) for (x in 0 until 6) {
            val inPopup = x in 2..4 && y in 1..2
            assertEquals(if (inPopup) 150 else 10, p[y * 6 + x], "pixel $x,$y")
        }
    }

    @Test fun popupHiddenClearsIt() {
        val fb = newBuffer()
        fb.resize(6, 4)
        fb.paint(false, arrayOf(Rectangle(0, 0, 6, 4)), solid(6, 4, 10), 6, 4)
        fb.popupRect(Rectangle(2, 1, 3, 2))
        fb.paint(true, arrayOf(Rectangle(0, 0, 3, 2)), solid(3, 2, 150), 3, 2)
        fb.popupRect(null)
        assertEquals(setOf(10), pixels(fb.frame.value!!).map { blue(it) }.toSet())
    }

    @Test fun publishesAreCoalescedToOnePending() {
        val queue = mutableListOf<Runnable>()
        val fb = OsrFrameBuffer(publisher = { queue += it })
        fb.resize(2, 2)
        repeat(5) { fb.paint(false, arrayOf(Rectangle(0, 0, 2, 2)), solid(2, 2, it), 2, 2) }
        assertEquals(1, queue.size)
        queue.single().run()
        assertEquals(4, pixels(fb.frame.value!!).size)
        assertEquals(4, blue(pixels(fb.frame.value!!)[0]))
    }

    @Test fun defaultPublisherUsesTheEdt() {
        val fb = OsrFrameBuffer()
        fb.resize(2, 2)
        fb.paint(false, arrayOf(Rectangle(0, 0, 2, 2)), solid(2, 2, 5), 2, 2)
        SwingUtilities.invokeAndWait {}
        assertEquals(2, fb.frame.value!!.width)
    }
}
