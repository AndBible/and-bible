/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.view.MotionEvent
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
    var onDayClick: ((dayTimestamp: Long, count: Int) -> Unit)? = null

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
                // cal is at local midnight (HOUR/MIN/SEC/MS zeroed at start, advanced day-by-day),
                // so timeInMillis is the local-day key matching ProgressControl.bucketByLocalDay.
                val count = dailyCounts[cal.timeInMillis] ?: 0

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            return findDayAt(event.x, event.y)?.second?.let { it > 0 } == true
        }

        if (event.action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event)
        }

        val hit = findDayAt(event.x, event.y) ?: return super.onTouchEvent(event)
        val (dayTimestamp, count) = hit
        if (count <= 0) return super.onTouchEvent(event)

        performClick()
        onDayClick?.invoke(dayTimestamp, count)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findDayAt(x: Float, y: Float): Pair<Long, Int>? {
        if (x < labelWidth || y < headerHeight) return null

        val cal = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, -52)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance()

        for (week in 0 until 53) {
            val left = labelWidth + week * (cellSize + cellPadding)
            val right = left + cellSize

            for (day in 0 until 7) {
                if (cal.after(today)) return null

                val top = headerHeight + day * (cellSize + cellPadding)
                val bottom = top + cellSize
                // cal is at local midnight here — see comment in onDraw.
                val dayTimestamp = cal.timeInMillis
                val count = dailyCounts[dayTimestamp] ?: 0

                if (x in left..right && y in top..bottom) {
                    return dayTimestamp to count
                }

                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return null
    }
}
