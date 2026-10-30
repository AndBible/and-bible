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
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Collects CEF `onPaint` buffers (BGRA, premultiplied, 4 bytes per pixel) into a view-sized pixel array and publishes the
 * result as [frame]. `paint` runs on CEF's UI thread; the copy happens under a lock and the publish to the Compose state is
 * done through [publisher] (the AWT thread by default), coalesced so at most one publish is pending. Pixel arrays and the
 * front Skia bitmap are only reallocated when the view size changes; a publish copies into the existing front bitmap and
 * hands Compose a fresh lightweight `ImageBitmap` wrapper over it so the state changes and redraws. Publish and drawing both
 * run on the AWT thread, so the front bitmap is never written while drawn.
 *
 * [resize] must be given [OsrGeometry.bufferWidthPx] x [OsrGeometry.bufferHeightPx], the size CEF paints at. After a resize
 * the previous frame stays in [frame] until the first paint of the new size; the pane must draw a frame at its own pixel
 * size (clipped), never stretched.
 *
 * A `<select>` popup is painted by CEF separately; it is kept in its own array and composited at [popupRect] when a frame
 * is published, so hiding it just republishes the main view.
 *
 * A main-view paint at most 1 px off the last [resize] size in each dimension (CEF rounding differently from us) is adopted:
 * the storage is reallocated once to that size and the frame is drawn at its own size, top-left, clipped, never stretched.
 * Larger mismatches (stale frames during a resize storm) are dropped. A streak of [MISMATCH_REPORT_AFTER] of them in a
 * row (CEF never reaching the requested size, e.g. a float/double rounding difference) is reported once through
 * [onPersistentMismatch]; a matching paint ends the streak.
 *
 * @param publisher runs a publish on the thread that owns the Compose state
 * @param onPersistentMismatch receives a one-line description of a size CEF keeps painting that is not the requested one
 */
internal class OsrFrameBuffer(
    private val publisher: (Runnable) -> Unit = { SwingUtilities.invokeLater(it) },
    private val onPersistentMismatch: (String) -> Unit = {},
) {
    private val lock = Any()
    private var requestedW = 0
    private var requestedH = 0
    private var width = 0
    private var height = 0
    private var view = ByteArray(0)
    private var front: Bitmap? = null
    private var frontAddr = 0L
    private var hasPainted = false
    private var popup: ByteArray? = null
    private var popupW = 0
    private var popupH = 0
    private var popupAt: Rectangle? = null
    private var mismatchStreak = 0
    private var mismatchReported = false
    private val pending = AtomicBoolean(false)
    private val state = mutableStateOf<ImageBitmap?>(null)

    /** The latest composed frame, or null before the first paint. Kept across [resize] until the next size is painted. */
    val frame: State<ImageBitmap?> get() = state

    companion object {
        /** Consecutive wrong-sized main-view paints after which the mismatch is reported (about half a second at 60 fps). */
        const val MISMATCH_REPORT_AFTER = 30
    }

    /** Sets the view size in pixels. Paints of a clearly other size are dropped until CEF catches up; the shown frame is kept. */
    fun resize(widthPx: Int, heightPx: Int) {
        synchronized(lock) {
            if (widthPx == requestedW && heightPx == requestedH) return
            requestedW = widthPx
            requestedH = heightPx
            allocate(widthPx, heightPx)
        }
    }

    /** Reallocates the pixel storage for a [widthPx] x [heightPx] view; the caller holds [lock]. */
    private fun allocate(widthPx: Int, heightPx: Int) {
        width = widthPx
        height = heightPx
        hasPainted = false
        mismatchStreak = 0
        if (widthPx > 0 && heightPx > 0) {
            view = ByteArray(widthPx * heightPx * 4)
            val bmp = Bitmap()
            bmp.allocPixels(ImageInfo(widthPx, heightPx, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            front = bmp
            frontAddr = bmp.peekPixels()?.takeIf { it.rowBytes == widthPx * 4 }?.addr ?: 0L
        } else {
            view = ByteArray(0)
            front = null
            frontAddr = 0L
        }
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
                if (width != this.width || height != this.height) {
                    if (this.width > 0 && kotlin.math.abs(width - this.width) <= 1 && kotlin.math.abs(height - this.height) <= 1) {
                        allocate(width, height) // CEF rounds the size differently from us: follow it
                    } else if (++mismatchStreak >= MISMATCH_REPORT_AFTER && !mismatchReported) {
                        mismatchReported = true
                        onPersistentMismatch("CEF keeps painting ${width}x$height while the view is ${this.width}x${this.height} px")
                    }
                    if (this.width != width || this.height != height) return
                }
                mismatchStreak = 0
                for (d in dirty) {
                    val x0 = maxOf(0, d.x)
                    val y0 = maxOf(0, d.y)
                    val x1 = minOf(width, d.x + d.width)
                    val y1 = minOf(height, d.y + d.height)
                    if (x1 <= x0 || y1 <= y0) continue
                    val len = (x1 - x0) * 4
                    for (y in y0 until y1) {
                        val off = (y * width + x0) * 4
                        if (off + len > buffer.capacity()) break
                        buffer.get(off, view, off, len)
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
        var bmp: Bitmap? = null
        synchronized(lock) {
            val addr = frontAddr
            val f = front
            if (!hasPainted || addr == 0L || f == null) return
            NativeCopy.copy(view, 0, addr, 0, view.size)
            val p = popup
            val at = popupAt
            if (p != null && at != null) {
                for (row in 0 until popupH) {
                    val dy = at.y + row
                    if (dy < 0 || dy >= height) continue
                    val x0 = maxOf(0, at.x)
                    val x1 = minOf(width, at.x + popupW)
                    if (x1 <= x0) continue
                    NativeCopy.copy(p, (row * popupW + (x0 - at.x)) * 4, addr, (dy * width + x0) * 4, (x1 - x0) * 4)
                }
            }
            f.notifyPixelsChanged()
            bmp = f
        }
        state.value = bmp?.asComposeImageBitmap()
    }
}

/**
 * Copies a byte array into the native pixel storage of a Skia bitmap without allocating. Skia exposes the storage only as a
 * raw address, so this goes through `sun.misc.Unsafe`; callers pass an address from a live, tightly packed pixmap and
 * ranges inside it.
 */
private object NativeCopy {
    private val unsafe: sun.misc.Unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        .apply { isAccessible = true }.get(null) as sun.misc.Unsafe
    private val base = unsafe.arrayBaseOffset(ByteArray::class.java).toLong()

    fun copy(src: ByteArray, srcOff: Int, dstAddr: Long, dstOff: Int, len: Int) {
        require(srcOff >= 0 && len >= 0 && srcOff + len <= src.size)
        unsafe.copyMemory(src, base + srcOff, null, dstAddr + dstOff, len.toLong())
    }
}
