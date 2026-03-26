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

package net.bible.android.view.activity.progress

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ReadingProgressBinding
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.versification.Scripture
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.Calendar

class ReadingProgressActivity : ActivityBase() {

    private lateinit var binding: ReadingProgressBinding
    private val kjva get() = Versifications.instance().getVersification("KJVA")
    private var currentCycle = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ReadingProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.reading_progress_title)

        currentCycle = ProgressControl.getCurrentCycle()

        setupTabs()
        setupReadingTab()

        val initialTab = intent.getIntExtra(EXTRA_TAB, 0)
        if (initialTab == 1) {
            binding.tabLayout.getTabAt(1)?.select()
        }

        refreshAll()
    }

    private fun setupTabs() {
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.memorize_tab_reading))
            addTab(newTab().setText(R.string.memorize_tab_memorization))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> showReadingTab()
                        1 -> showMemorizeTab()
                    }
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
    }

    private fun showReadingTab() {
        binding.readingContent.visibility = View.VISIBLE
        binding.memorizeContent.visibility = View.GONE
    }

    private fun showMemorizeTab() {
        binding.readingContent.visibility = View.GONE
        binding.memorizeContent.visibility = View.VISIBLE
        refreshMemorizeTab()
    }

    private fun setupReadingTab() {
        binding.newCycleButton.setOnClickListener {
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reading_progress_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, ReadingProgressSettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

        binding.apply {
            chaptersReadCount.text = "$totalRead"
            chaptersReadLabel.text = getString(R.string.reading_progress_chapters_read)
            memorizedVersesCount.text = "$memorizedVerses"
            memorizedVersesLabel.text = getString(R.string.reading_progress_verses_memorized)
            activeDaysCount.text = "$activeDays"
            activeDaysLabel.text = getString(R.string.reading_progress_active_days)

            overallProgressBar.progress = if (totalChapters > 0) (totalRead * 1000 / totalChapters) else 0
            val pct = if (totalChapters > 0) totalRead * 100f / totalChapters else 0f
            overallProgressLabel.text = getString(R.string.reading_progress_overall, String.format("%.1f", pct))
        }
    }

    private fun refreshBibleHeatmap() {
        val bookProgress = ProgressControl.getBookReadingProgress(currentCycle)

        binding.otBooksGrid.removeAllViews()
        binding.ntBooksGrid.removeAllViews()

        for (book in kjva.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val isNT = book.ordinal >= BibleBook.MATT.ordinal
            val grid = if (isNT) binding.ntBooksGrid else binding.otBooksGrid

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
        binding.chapterDetailSection.visibility = View.VISIBLE
        binding.chapterDetailTitle.text = kjva.getLongName(book)
        binding.chaptersGrid.removeAllViews()

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
            binding.chaptersGrid.addView(btn, params)
        }
    }

    private fun navigateToChapter(book: BibleBook, chapter: Int) {
        val verse = Verse(kjva, book, chapter, 1)
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
        resultIntent.putExtra("verse", verse.osisID)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun navigateToMemorize(range: VerseRange) {
        val resultIntent = Intent(this, ReadingProgressActivity::class.java)
        resultIntent.putExtra("action", "memorize")
        resultIntent.putExtra("startOrdinal", range.start.ordinal)
        resultIntent.putExtra("endOrdinal", range.end.ordinal)
        setResult(RESULT_OK, resultIntent)
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

        binding.calendarHeatmap.setData(dailyCounts)
    }

    private fun refreshCycleLabel() {
        binding.cycleLabel.text = getString(R.string.reading_progress_cycle, currentCycle)
    }

    // --- Memorize tab ---

    private fun refreshMemorizeTab() {
        refreshMemorizeSummary()
        refreshMemorizedPassages()
        refreshMemorizeTargets()
    }

    private fun refreshMemorizeSummary() {
        val totalMemorized = ProgressControl.getTotalMemorizedVerses()
        val (memorizedInTargets, totalTarget) = ProgressControl.getMemorizationTargetProgress()

        binding.apply {
            memSummaryMemorizedCount.text = "$totalMemorized"
            memSummaryMemorizedLabel.text = getString(R.string.memorize_verses_memorized)

            if (totalTarget > 0) {
                memSummaryTargetCount.text = "$totalTarget"
                memSummaryTargetLabel.text = getString(R.string.memorize_verses_target)

                memTargetProgressBar.apply {
                    visibility = View.VISIBLE
                    progress = memorizedInTargets * 1000 / totalTarget
                }
                val pct = memorizedInTargets * 100f / totalTarget
                memTargetProgressLabel.apply {
                    visibility = View.VISIBLE
                    text = String.format("%.0f%% (%d/%d)", pct, memorizedInTargets, totalTarget)
                }
            } else {
                memSummaryTargetCount.text = "-"
                memSummaryTargetLabel.text = getString(R.string.memorize_verses_target)
                memTargetProgressBar.visibility = View.GONE
                memTargetProgressLabel.visibility = View.GONE
            }
        }
    }

    private fun refreshMemorizedPassages() {
        binding.memorizedPassagesList.removeAllViews()

        val ranges = ProgressControl.getMemorizedVerseRanges()

        if (ranges.isEmpty()) {
            binding.noMemorizedPassages.visibility = View.VISIBLE
            return
        }
        binding.noMemorizedPassages.visibility = View.GONE

        for (range in ranges) {
            val item = createPassageItem(range.name, range)
            binding.memorizedPassagesList.addView(item)
        }
    }

    private fun refreshMemorizeTargets() {
        binding.memorizeTargetsList.removeAllViews()

        val targets = ProgressControl.getAllMemorizationTargets()

        if (targets.isEmpty()) {
            binding.noMemorizeTargets.visibility = View.VISIBLE
            return
        }
        binding.noMemorizeTargets.visibility = View.GONE

        for (target in targets) {
            val range = target.verseRange
            val memorizedCount = ProgressControl.getMemorizedOrdinalsInRange(
                target.kjvOrdinalStart, target.kjvOrdinalEnd
            ).size
            val item = createTargetItem(range.name, memorizedCount, target.verseCount, target.id, range)
            binding.memorizeTargetsList.addView(item)
        }
    }

    private fun createPassageItem(text: String, range: VerseRange): LinearLayout {
        val dp8 = 8.dp
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp8, dp8, dp8, dp8)
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.attr.selectableItemBackground.let {
                val attrs = intArrayOf(it)
                val ta = context.obtainStyledAttributes(attrs)
                val resId = ta.getResourceId(0, 0)
                ta.recycle()
                resId
            })
            setOnClickListener { navigateToMemorize(range) }

            addView(TextView(context).apply {
                this.text = text
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(context).apply {
                this.text = "\u00D7"
                textSize = 18f
                setPadding(dp8, 0, dp8, 0)
                setOnClickListener {
                    AlertDialog.Builder(this@ReadingProgressActivity)
                        .setMessage(getString(R.string.memorize_confirm_unmark, text))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            ProgressControl.unmarkVerseMemorized(range)
                            refreshMemorizeTab()
                            refreshSummary()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            })
        }
    }

    private fun createTargetItem(text: String, memorized: Int, total: Int, targetId: IdType, range: VerseRange): LinearLayout {
        val dp4 = 4.dp
        val dp8 = 8.dp
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp8, dp8, dp8)
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.attr.selectableItemBackground.let {
                val attrs = intArrayOf(it)
                val ta = context.obtainStyledAttributes(attrs)
                val resId = ta.getResourceId(0, 0)
                ta.recycle()
                resId
            })
            setOnClickListener { navigateToMemorize(range) }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    this.text = "$text ($memorized/$total)"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                addView(TextView(context).apply {
                    this.text = "\u00D7"
                    textSize = 18f
                    setPadding(dp8, 0, dp8, 0)
                    setOnClickListener {
                        AlertDialog.Builder(this@ReadingProgressActivity)
                            .setMessage(getString(R.string.memorize_confirm_remove_target, text))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                ProgressControl.removeMemorizationTarget(targetId)
                                refreshMemorizeTab()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                })
            })

            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = if (total > 0) (memorized * 1000 / total) else 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp4 }
            })
        }
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
        const val EXTRA_TAB = "tab"
    }
}
