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

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Collects CEF `onPaint` buffers (BGRA, premultiplied, 4 bytes per pixel) into a view-sized pixel array and publishes the
 * result as [frame]. `paint` runs on CEF's UI thread; the copy happens under a lock and the publish to the Compose state is
 * done through [publisher] (the AWT thread by default), coalesced so at most one publish is pending. The pixel arrays are
 * only reallocated when the view size changes.
 *
 * A `<select>` popup is painted by CEF separately; it is kept in its own array and composited at [popupRect] when a frame
 * is published, so hiding it just republishes the main view.
 *
 * @param publisher runs a publish on the thread that owns the Compose state
 */
internal class OsrFrameBuffer(private val publisher: (Runnable) -> Unit = { SwingUtilities.invokeLater(it) }) {
    private val lock = Any()
    private var width = 0
    private var height = 0
    private var view = ByteArray(0)
    private var hasPainted = false
    private var popup: ByteArray? = null
    private var popupW = 0
    private var popupH = 0
    private var popupAt: Rectangle? = null
    private val pending = AtomicBoolean(false)
    private val state = mutableStateOf<ImageBitmap?>(null)

    /** The latest composed frame, or null before the first paint of the current size. */
    val frame: State<ImageBitmap?> get() = state

    /** Sets the view size in pixels; clears the frame. Paints of any other size are dropped until CEF catches up. */
    fun resize(widthPx: Int, heightPx: Int) {
        synchronized(lock) {
            if (widthPx == width && heightPx == height) return
            width = widthPx
            height = heightPx
            view = ByteArray(maxOf(0, widthPx) * maxOf(0, heightPx) * 4)
            hasPainted = false
        }
        schedulePublish()
    }

    /** Sets (view pixels) or clears (null) the visible popup rectangle. */
    fun popupRect(rect: Rectangle?) {
        synchronized(lock) {
            popupAt = rect?.let { Rectangle(it) }
            if (rect == null) popup = null
        }
        schedulePublish()
    }

    /**
     * Copies [dirty] areas of [buffer] (a [width] x [height] pixel BGRA buffer) into the main view, or the whole buffer into
     * the popup layer when [popup] is true. A main-view buffer whose size differs from the last [resize] is ignored.
     */
    fun paint(popup: Boolean, dirty: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        synchronized(lock) {
            if (popup) {
                val need = width * height * 4
                if (buffer.capacity() < need) return
                var arr = this.popup
                if (arr == null || arr.size != need) arr = ByteArray(need).also { this.popup = it }
                buffer.get(0, arr, 0, need)
                popupW = width
                popupH = height
            } else {
                if (width != this.width || height != this.height) return
                val src = buffer.duplicate()
                for (d in dirty) {
                    val x0 = maxOf(0, d.x)
                    val y0 = maxOf(0, d.y)
                    val x1 = minOf(width, d.x + d.width)
                    val y1 = minOf(height, d.y + d.height)
                    if (x1 <= x0 || y1 <= y0) continue
                    val len = (x1 - x0) * 4
                    for (y in y0 until y1) {
                        val off = (y * width + x0) * 4
                        if (off + len > src.capacity()) break
                        src.get(off, view, off, len)
                    }
                }
                hasPainted = true
            }
        }
        schedulePublish()
    }

    private fun schedulePublish() {
        if (pending.compareAndSet(false, true)) publisher(Runnable { publishNow() })
    }

    private fun publishNow() {
        pending.set(false)
        val image: ImageBitmap? = synchronized(lock) {
            if (!hasPainted || width <= 0 || height <= 0) return@synchronized null
            var pixels = view
            val p = popup
            val at = popupAt
            if (p != null && at != null) {
                pixels = view.copyOf()
                for (row in 0 until popupH) {
                    val dy = at.y + row
                    if (dy < 0 || dy >= height) continue
                    val x0 = maxOf(0, at.x)
                    val x1 = minOf(width, at.x + popupW)
                    if (x1 <= x0) continue
                    System.arraycopy(p, (row * popupW + (x0 - at.x)) * 4, pixels, (dy * width + x0) * 4, (x1 - x0) * 4)
                }
            }
            Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL), pixels, width * 4)
                .toComposeImageBitmap()
        }
        state.value = image
    }
}
