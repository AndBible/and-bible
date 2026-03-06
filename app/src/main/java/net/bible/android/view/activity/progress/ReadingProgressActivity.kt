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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import net.bible.android.activity.R
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.versification.Scripture
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.Calendar

class ReadingProgressActivity : ActivityBase() {

    private val kjva get() = Versifications.instance().getVersification("KJVA")
    private var currentCycle = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reading_progress)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.reading_progress_title)

        currentCycle = ProgressControl.getCurrentCycle()

        findViewById<Button>(R.id.newCycleButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reading_progress_new_cycle)
                .setMessage(R.string.reading_progress_new_cycle_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    currentCycle = ProgressControl.startNewCycle()
                    refreshAll()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refreshAll()
    }

    private fun refreshAll() {
        refreshSummary()
        refreshBibleHeatmap()
        refreshCalendarHeatmap()
        refreshCycleLabel()
    }

    private fun refreshSummary() {
        val totalRead = ProgressControl.getTotalReadChapters(currentCycle)
        val totalChapters = ProgressControl.totalBibleChapters
        val memorizedVerses = ProgressControl.getTotalMemorizedVerses()
        val activeDays = ProgressControl.getDistinctReadDays(currentCycle)

        findViewById<TextView>(R.id.chaptersReadCount).text = "$totalRead"
        findViewById<TextView>(R.id.chaptersReadLabel).text = getString(R.string.reading_progress_chapters_read)
        findViewById<TextView>(R.id.memorizedVersesCount).text = "$memorizedVerses"
        findViewById<TextView>(R.id.memorizedVersesLabel).text = getString(R.string.reading_progress_verses_memorized)
        findViewById<TextView>(R.id.activeDaysCount).text = "$activeDays"
        findViewById<TextView>(R.id.activeDaysLabel).text = getString(R.string.reading_progress_active_days)

        val progressBar = findViewById<ProgressBar>(R.id.overallProgressBar)
        progressBar.progress = if (totalChapters > 0) (totalRead * 1000 / totalChapters) else 0
        val pct = if (totalChapters > 0) totalRead * 100f / totalChapters else 0f
        findViewById<TextView>(R.id.overallProgressLabel).text =
            getString(R.string.reading_progress_overall, String.format("%.1f", pct))
    }

    private fun refreshBibleHeatmap() {
        val bookProgress = ProgressControl.getBookReadingProgress(currentCycle)

        val otGrid = findViewById<GridLayout>(R.id.otBooksGrid)
        val ntGrid = findViewById<GridLayout>(R.id.ntBooksGrid)
        otGrid.removeAllViews()
        ntGrid.removeAllViews()

        for (book in kjva.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val isNT = book.ordinal >= BibleBook.MATT.ordinal
            val grid = if (isNT) ntGrid else otGrid

            val progress = bookProgress[book] ?: 0f
            val btn = createBookButton(book, progress)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            grid.addView(btn, params)
        }
    }

    private fun createBookButton(book: BibleBook, progress: Float): TextView {
        val dp4 = 4.dp
        return TextView(this).apply {
            text = kjva.getShortName(book)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp4, dp4 * 2, dp4, dp4 * 2)
            setTextColor(if (progress >= 1f) Color.WHITE else Color.DKGRAY)

            background = GradientDrawable().apply {
                cornerRadius = 4f * resources.displayMetrics.density
                setColor(progressToColor(progress))
            }

            setOnClickListener {
                showChapterDetail(book)
            }
        }
    }

    private fun showChapterDetail(book: BibleBook) {
        val section = findViewById<LinearLayout>(R.id.chapterDetailSection)
        section.visibility = View.VISIBLE

        val titleView = findViewById<TextView>(R.id.chapterDetailTitle)
        titleView.text = kjva.getLongName(book)

        val grid = findViewById<GridLayout>(R.id.chaptersGrid)
        grid.removeAllViews()

        val totalChapters = kjva.getLastChapter(book)
        val readChapters = ProgressControl.getReadChaptersForBook(book, currentCycle).toSet()

        for (ch in 1..totalChapters) {
            val isRead = ch in readChapters
            val btn = TextView(this).apply {
                text = "$ch"
                textSize = 12f
                gravity = Gravity.CENTER
                val dp6 = 6.dp
                setPadding(dp6, dp6, dp6, dp6)
                minWidth = 36.dp
                setTextColor(if (isRead) Color.WHITE else Color.DKGRAY)
                background = GradientDrawable().apply {
                    cornerRadius = 4f * resources.displayMetrics.density
                    setColor(if (isRead) COLOR_READ else COLOR_EMPTY)
                }
                setOnClickListener {
                    navigateToChapter(book, ch)
                }
            }
            val params = GridLayout.LayoutParams().apply {
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            grid.addView(btn, params)
        }
    }

    private fun navigateToChapter(book: BibleBook, chapter: Int) {
        val verse = Verse(kjva, book, chapter, 1)
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
        resultIntent.putExtra("verse", verse.osisID)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun refreshCalendarHeatmap() {
        val cal = Calendar.getInstance()
        val endMs = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        val startMs = cal.timeInMillis

        val records = ProgressControl.getReadingCalendar(startMs, endMs)
        val dailyCounts = mutableMapOf<Long, Int>()
        for (record in records) {
            dailyCounts[record.dayTimestamp] = record.count
        }

        findViewById<CalendarHeatmapView>(R.id.calendarHeatmap).setData(dailyCounts)
    }

    private fun refreshCycleLabel() {
        findViewById<TextView>(R.id.cycleLabel).text =
            getString(R.string.reading_progress_cycle, currentCycle)
    }

    private fun progressToColor(progress: Float): Int {
        return when {
            progress <= 0f -> COLOR_EMPTY
            progress < 0.25f -> COLOR_LOW
            progress < 0.50f -> COLOR_MEDIUM
            progress < 0.75f -> COLOR_HIGH
            progress < 1.0f -> COLOR_ALMOST
            else -> COLOR_READ
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private val COLOR_EMPTY = Color.parseColor("#E8E8E8")
        private val COLOR_LOW = Color.parseColor("#C6E48B")
        private val COLOR_MEDIUM = Color.parseColor("#7BC96F")
        private val COLOR_HIGH = Color.parseColor("#239A3B")
        private val COLOR_ALMOST = Color.parseColor("#196127")
        private val COLOR_READ = Color.parseColor("#196127")
    }
}
