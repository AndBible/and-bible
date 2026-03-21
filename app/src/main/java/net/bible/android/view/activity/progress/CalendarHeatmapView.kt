/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.Locale

class CalendarHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val cellSize = 14f * resources.displayMetrics.density
    private val cellPadding = 2f * resources.displayMetrics.density
    private val labelWidth = 24f * resources.displayMetrics.density
    private val headerHeight = 16f * resources.displayMetrics.density

    private var dailyCounts: Map<Long, Int> = emptyMap()
    private var maxCount = 1

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EBEDF0")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 10f * resources.displayMetrics.density
    }

    private val levelColors = intArrayOf(
        Color.parseColor("#EBEDF0"),
        Color.parseColor("#9BE9A8"),
        Color.parseColor("#40C463"),
        Color.parseColor("#30A14E"),
        Color.parseColor("#216E39"),
    )

    fun setData(dailyCounts: Map<Long, Int>) {
        this.dailyCounts = dailyCounts
        this.maxCount = dailyCounts.values.maxOrNull() ?: 1
        requestLayout()
        invalidate()
    }

    private fun getColorForCount(count: Int): Int {
        if (count == 0) return levelColors[0]
        val fraction = count.toFloat() / maxCount
        val level = when {
            fraction <= 0.25f -> 1
            fraction <= 0.50f -> 2
            fraction <= 0.75f -> 3
            else -> 4
        }
        return levelColors[level]
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val weeks = 53
        val width = (labelWidth + weeks * (cellSize + cellPadding) + cellPadding).toInt()
        val height = (headerHeight + 7 * (cellSize + cellPadding) + cellPadding).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cal = Calendar.getInstance()
        val today = cal.clone() as Calendar

        // Start from 52 weeks ago, aligned to start of week
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Draw day-of-week labels
        val dayLabels = arrayOf("", "M", "", "W", "", "F", "")
        for (i in dayLabels.indices) {
            if (dayLabels[i].isNotEmpty()) {
                canvas.drawText(
                    dayLabels[i],
                    0f,
                    headerHeight + i * (cellSize + cellPadding) + cellSize * 0.8f,
                    textPaint
                )
            }
        }

        // Draw month labels and cells
        var lastMonth = -1
        val rect = RectF()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val cornerRadius = 2f * resources.displayMetrics.density

        for (week in 0 until 53) {
            val x = labelWidth + week * (cellSize + cellPadding)

            for (day in 0 until 7) {
                if (cal.after(today)) break

                val y = headerHeight + day * (cellSize + cellPadding)
                val dayStart = cal.timeInMillis
                val normalizedDay = (dayStart / 86400000L) * 86400000L
                val count = dailyCounts[normalizedDay] ?: 0

                fillPaint.color = getColorForCount(count)
                rect.set(x, y, x + cellSize, y + cellSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

                // Month label on first week row
                if (day == 0 && cal.get(Calendar.MONTH) != lastMonth) {
                    lastMonth = cal.get(Calendar.MONTH)
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
                    canvas.drawText(monthName, x, headerHeight - 2f * resources.displayMetrics.density, textPaint)
                }

                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }
}
