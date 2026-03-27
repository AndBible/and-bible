/*
 * Copyright (c) 2024-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.util.buttongrid

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Draws up to two horizontal progress bars stacked at the bottom of a button.
 * The memorization bar sits at the very bottom, with the reading bar above it
 * (separated by a small gap). If only one type has progress, only that bar is drawn.
 */
class BottomBarProgressDrawable(
    private val readingFraction: Float = 0f,
    readingColor: Int = 0,
    private val memorizationFraction: Float = 0f,
    memorizationColor: Int = 0,
    private val barHeightDp: Float = 4f,
    private val gapDp: Float = 3f,
) : Drawable() {
    private val readingPaint = Paint().apply {
        color = readingColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val memorizationPaint = Paint().apply {
        color = memorizationColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val density = canvas.density.toFloat() / 160f
        var barHeight = if (density > 0f) (barHeightDp * density).coerceAtLeast(2f) else 4f
        var gap = if (density > 0f) (gapDp * density).coerceAtLeast(1f) else 3f

        val hasReading = readingFraction > 0f
        val hasMemorization = memorizationFraction > 0f

        // If both bars are shown, ensure they fit within a reasonable portion of the button height
        if (hasReading && hasMemorization) {
            val totalNeeded = barHeight * 2 + gap
            val maxAllowed = b.height() * 0.35f
            if (totalNeeded > maxAllowed && maxAllowed > 0) {
                val scale = maxAllowed / totalNeeded
                barHeight *= scale
                gap *= scale
            }
        }

        if (hasMemorization) {
            val top = b.bottom - barHeight
            val right = b.left + (b.width() * memorizationFraction.coerceIn(0f, 1f))
            canvas.drawRect(b.left.toFloat(), top, right, b.bottom.toFloat(), memorizationPaint)
        }

        if (hasReading) {
            val bottomOfReadingBar = if (hasMemorization) b.bottom - barHeight - gap else b.bottom.toFloat()
            val top = bottomOfReadingBar - barHeight
            val right = b.left + (b.width() * readingFraction.coerceIn(0f, 1f))
            canvas.drawRect(b.left.toFloat(), top, right, bottomOfReadingBar, readingPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        readingPaint.alpha = alpha
        memorizationPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        readingPaint.colorFilter = colorFilter
        memorizationPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
