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

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spannable
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ReadingProgressBinding
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.progress.ProgressControl.ChapterReadEntry
import net.bible.android.control.versification.Scripture
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.service.common.CommonUtils
import net.bible.service.common.ReadingProgressSettings
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.Calendar
import java.util.Date
import kotlin.math.ceil
import kotlin.math.roundToInt

class ReadingProgressActivity : ActivityBase() {

    private lateinit var binding: ReadingProgressBinding
    private val kjva get() = Versifications.instance().getVersification("KJVA")
    private var currentCycle = 1

    private var memorizedPassagesShown = PAGE_SIZE
    private var memorizeTargetsShown = PAGE_SIZE
    private var memOverviewActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        binding = ReadingProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.reading_progress_title)

        currentCycle = ProgressControl.getCurrentCycle()
        memOverviewActive = CommonUtils.settings.getBoolean(PREF_MEM_OVERVIEW, true)

        setupTabs()
        setupReadingTab()
        setupMemorizeToggle()

        val initialTab = intent.getIntExtra(EXTRA_TAB,
            CommonUtils.settings.getInt(PREF_LAST_TAB, 0)
        )
        if (initialTab == 1) {
            binding.tabLayout.getTabAt(1)?.select()
        }

        refreshAll()
        openInitialHistoryDialogIfRequested()
    }

    private fun openInitialHistoryDialogIfRequested() {
        val bookOrdinal = intent.getIntExtra(EXTRA_HISTORY_BOOK_ORDINAL, -1)
        if (bookOrdinal < 0) return
        val book = BibleBook.entries.getOrNull(bookOrdinal) ?: return
        val chapter = intent.getIntExtra(EXTRA_HISTORY_CHAPTER, -1).takeIf { it > 0 }
        val finishOnClose = intent.getBooleanExtra(EXTRA_HISTORY_ONLY, false)
        binding.root.post {
            showReadHistoryDialog(
                title = historyDialogTitle(book, chapter),
                book = book,
                chapter = chapter,
                onClosed = { if (finishOnClose) finish() },
            )
        }
    }

    private fun historyDialogTitle(book: BibleBook, chapter: Int?): String =
        if (chapter == null) kjva.getLongName(book) else "${kjva.getLongName(book)} $chapter"

    private fun historyDialogTitle(dayTimestamp: Long): String =
        getString(R.string.reading_progress_history_day_title, DateFormat.getDateFormat(this).format(Date(dayTimestamp)))

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
        memorizedPassagesShown = PAGE_SIZE
        memorizeTargetsShown = PAGE_SIZE
        CommonUtils.settings.setInt(PREF_LAST_TAB, 0)
    }

    private fun showMemorizeTab() {
        binding.readingContent.visibility = View.GONE
        binding.memorizeContent.visibility = View.VISIBLE
        CommonUtils.settings.setInt(PREF_LAST_TAB, 1)
        refreshMemorizeSummary()
        if (memOverviewActive) {
            refreshMemorizationHeatmap()
        } else {
            refreshMemorizeListView()
        }
    }

    private fun setupReadingTab() {
        binding.calendarHeatmap.onDayClick = { dayTimestamp, _ ->
            showDayReadHistoryDialog(dayTimestamp)
        }

        binding.cyclePrevButton.setOnClickListener {
            if (currentCycle > 1) {
                currentCycle--
                ProgressControl.setActiveCycle(currentCycle)
                refreshAll()
            }
        }

        binding.cycleNextButton.setOnClickListener {
            val latest = ProgressControl.getLatestCycle()
            if (currentCycle < latest) {
                currentCycle++
                ProgressControl.setActiveCycle(currentCycle)
                refreshAll()
            }
        }

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

    private fun setupMemorizeToggle() {
        binding.memViewOverviewButton.apply {
            setTypeface(null, if (memOverviewActive) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener { setMemorizeView(overview = true) }
        }
        binding.memViewListButton.apply {
            setTypeface(null, if (!memOverviewActive) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener { setMemorizeView(overview = false) }
        }
        binding.memListView.visibility = if (memOverviewActive) View.GONE else View.VISIBLE
        binding.memOverviewView.visibility = if (memOverviewActive) View.VISIBLE else View.GONE
    }

    private fun setMemorizeView(overview: Boolean) {
        memOverviewActive = overview
        CommonUtils.settings.setBoolean(PREF_MEM_OVERVIEW, overview)
        binding.memListView.visibility = if (overview) View.GONE else View.VISIBLE
        binding.memOverviewView.visibility = if (overview) View.VISIBLE else View.GONE

        binding.memViewOverviewButton.setTypeface(null, if (overview) Typeface.BOLD else Typeface.NORMAL)
        binding.memViewListButton.setTypeface(null, if (!overview) Typeface.BOLD else Typeface.NORMAL)

        if (overview) {
            refreshMemorizationHeatmap()
        } else {
            refreshMemorizeListView()
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
        val totalChapters = ProgressControl.totalBibleChapters

        val totalRead = ProgressControl.getTotalReadChapters(currentCycle)
        val activeDays = ProgressControl.getDistinctReadDays(currentCycle)

        binding.apply {
            chaptersReadCount.text = "$totalRead"
            chaptersReadLabel.text = getString(R.string.reading_progress_chapters_read)
            activeDaysCount.text = "$activeDays"
            activeDaysLabel.text = getString(R.string.reading_progress_active_days)

            overallProgressBar.progress = if (totalChapters > 0) (totalRead * 1000 / totalChapters) else 0
            val pct = if (totalChapters > 0) totalRead * 100f / totalChapters else 0f
            overallProgressLabel.text = getString(R.string.reading_progress_overall, String.format("%.1f", pct))
        }
    }

    private fun refreshBibleHeatmap() {
        binding.otBooksGrid.removeAllViews()
        binding.ntBooksGrid.removeAllViews()

        val bookCountProgress = ProgressControl.getBookCountProgress(currentCycle)
        val effectiveMaxPercent = resolveBookPercentScaleMax(bookCountProgress.values.maxOfOrNull { it.readPercent })
        showBookPercentScale(effectiveMaxPercent)
        for (book in kjva.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val isNT = book.ordinal >= BibleBook.MATT.ordinal
            val grid = if (isNT) binding.ntBooksGrid else binding.otBooksGrid
            val cp = bookCountProgress[book]
            val color = if (cp == null) COLOR_EMPTY
                        else countBookProgressToColor(cp.readPercent, effectiveMaxPercent)
            val totalChapters = kjva.getLastChapter(book)
            val isComplete = ProgressControl.getDistinctReadChaptersCountForBook(book, currentCycle) >= totalChapters
            val btn = createBookButton(
                book, cp?.readPercent ?: 0f, { _ -> color },
                onClick = { showChapterDetail(book) },
                onLongClick = { showReadHistoryDialog(kjva.getLongName(book), book, chapter = null) },
                isComplete = isComplete,
            )
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            grid.addView(btn, params)
        }
    }

    private fun createBookButton(
        book: BibleBook,
        progress: Float,
        colorMapper: (Float) -> Int,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        hasTarget: Boolean = false,
        isComplete: Boolean = false,
    ): View {
        val dp4 = 4.dp
        val density = resources.displayMetrics.density
        val bookName = kjva.getShortName(book)
        val displayText: CharSequence = if (isComplete) {
            SpannableString("$bookName ✓").apply {
                val tickStart = bookName.length + 1
                setSpan(SuperscriptSpan(), tickStart, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.6f), tickStart, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            bookName
        }
        val textView = TextView(this).apply {
            text = displayText
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp4, dp4 * 2, dp4, dp4 * 2)
            setTextColor(if (progress >= 1f) Color.WHITE else Color.DKGRAY)
            background = GradientDrawable().apply {
                cornerRadius = 4f * density
                setColor(colorMapper(progress))
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }

        if (!hasTarget) return textView

        // Wrap in FrameLayout and add a small target indicator dot
        return FrameLayout(this).apply {
            addView(textView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            val dotSize = (5 * density).toInt()
            val dotMargin = (2 * density).toInt()
            val dot = View(this@ReadingProgressActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_TARGET_DOT)
                }
            }
            addView(dot, FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dotMargin, dotMargin, 0)
            })
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
    }

    private fun showChapterDetail(book: BibleBook) {
        binding.chapterDetailSection.visibility = View.VISIBLE
        binding.chapterDetailTitle.text = kjva.getLongName(book)
        binding.chaptersGrid.removeAllViews()

        // Scroll so the "Bible Overview" heading is at the top of the screen
        binding.readingContent.post {
            binding.readingContent.smoothScrollTo(0, binding.bibleHeatmapTitle.top)
        }

        val totalChapters = kjva.getLastChapter(book)
        // Use at least 5 columns so a single-chapter book doesn't stretch to full width
        binding.chaptersGrid.columnCount = totalChapters.coerceIn(5, 10)

        val counts = ProgressControl.getChapterReadCountsForBook(book, currentCycle)
        val maxCount = ProgressControl.getMaxReadCountForBook(book, currentCycle).coerceAtLeast(1)
        showCountScale(maxCount)
        for (ch in 1..totalChapters) {
            val count = counts[ch] ?: 0
            val color = countToHeatColor(count, maxCount)
            val textColor = textColorForBackground(color)
            val btn = createChapterButton(
                ch, color, count > 0,
                onClick = { navigateToChapter(book, ch) },
                onLongClick = { showReadHistoryDialog("${kjva.getLongName(book)} $ch", book, chapter = ch) },
                textColor = textColor,
            )
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            binding.chaptersGrid.addView(btn, params)
        }
    }

    private fun createChapterButton(
        chapter: Int,
        bgColor: Int,
        isHighlighted: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        hasTarget: Boolean = false,
        textColor: Int? = null,
    ): View {
        val dp6 = 6.dp
        val density = resources.displayMetrics.density
        val textView = TextView(this).apply {
            text = "$chapter"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp6, dp6, dp6, dp6)
            minWidth = 36.dp
            setTextColor(textColor ?: if (isHighlighted) Color.WHITE else Color.DKGRAY)
            background = GradientDrawable().apply {
                cornerRadius = 4f * density
                setColor(bgColor)
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }

        if (!hasTarget) return textView

        return FrameLayout(this).apply {
            addView(textView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            val dotSize = (5 * density).toInt()
            val dotMargin = (2 * density).toInt()
            val dot = View(this@ReadingProgressActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_TARGET_DOT)
                }
            }
            addView(dot, FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dotMargin, dotMargin, 0)
            })
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
    }

    /**
     * Shows a scrollable dialog listing read history entries for [book] (all chapters if [chapter]
     * is null, or a single chapter). Each row shows date/time, chapter reference, Bible version,
     * and a delete button. Works for both count-mode and non-count mode.
     */
    private fun showReadHistoryDialog(title: String, book: BibleBook, chapter: Int?, onClosed: (() -> Unit)? = null) {
        val entries = if (chapter == null) ProgressControl.getReadHistoryForBook(book, currentCycle)
                      else ProgressControl.getReadHistoryForChapter(book, chapter, currentCycle)
        showReadHistoryDialog(title, entries, onClosed)
    }

    private fun showDayReadHistoryDialog(dayTimestamp: Long) {
        val entries = ProgressControl.getReadHistoryForDay(dayTimestamp, currentCycle)
        showReadHistoryDialog(historyDialogTitle(dayTimestamp), entries)
    }

    /**
     * Shows the scrollable read-history dialog for an arbitrary set of [entries].
     * This is used by book/chapter history and the Reading Activity heat map day view.
     */
    private fun showReadHistoryDialog(title: String, entries: List<ChapterReadEntry>, onClosed: (() -> Unit)? = null) {
        val dateFormat = DateFormat.getDateFormat(this)
        val timeFormat = DateFormat.getTimeFormat(this)
        val dp8 = 8.dp
        val dp4 = 4.dp
        val pendingDeleteIds = mutableSetOf<IdType>()

        var outerDialog: AlertDialog? = null

        fun updateDeleteRowState(row: LinearLayout, deleteButton: TextView, pendingDelete: Boolean) {
            row.alpha = if (pendingDelete) 0.45f else 1f
            deleteButton.text = if (pendingDelete) "\u21B6" else "\u00D7"
            deleteButton.setTextColor(if (pendingDelete) COLOR_HEAT_MAX else Color.DKGRAY)
        }

        fun applyPendingDeletes() {
            val pendingEntries = entries.filter { it.id in pendingDeleteIds }
            if (pendingEntries.isEmpty()) return

            ProgressControl.deleteReadHistoryEntries(pendingEntries, currentCycle)
            refreshAll()
        }

        fun closeDialogWithPendingConfirmation() {
            if (pendingDeleteIds.isEmpty()) {
                outerDialog?.dismiss()
                onClosed?.invoke()
                return
            }

            AlertDialog.Builder(this)
                .setMessage(resources.getQuantityString(
                    R.plurals.reading_progress_history_delete_confirm_multiple,
                    pendingDeleteIds.size,
                    pendingDeleteIds.size,
                ))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    applyPendingDeletes()
                    outerDialog?.dismiss()
                    onClosed?.invoke()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp4, dp8, dp4)
        }

        if (entries.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = getString(R.string.reading_progress_history_no_entries)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp8, dp8 * 2, dp8, dp8 * 2)
                setTextColor(Color.GRAY)
            })
        } else {
            for (entry in entries) {
                val kjvBook = BibleBook.entries.getOrNull(entry.kjvBookOrdinal)
                val chapterRef = if (kjvBook != null) "${kjva.getLongName(kjvBook)} ${entry.chapter}" else "?"
                val dateStr = dateFormat.format(Date(entry.readAt))
                val timeStr = timeFormat.format(Date(entry.readAt))
                val versionStr = entry.bookInitials.ifEmpty {
                    getString(R.string.reading_progress_history_version_unknown)
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp4, 0, dp4)
                }

                // Info column
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(this@ReadingProgressActivity).apply {
                        text = chapterRef
                        textSize = 14f
                    })
                    addView(TextView(this@ReadingProgressActivity).apply {
                        text = "$dateStr $timeStr  ·  $versionStr"
                        textSize = 11f
                        setTextColor(Color.GRAY)
                    })
                })

                val deleteButton = TextView(this).apply {
                    text = "\u00D7"
                    textSize = 20f
                    setPadding(dp8, 0, dp4, 0)
                    setTextColor(Color.DKGRAY)
                    setOnClickListener {
                        val pendingDelete = if (entry.id in pendingDeleteIds) {
                            pendingDeleteIds.remove(entry.id)
                            false
                        } else {
                            pendingDeleteIds.add(entry.id)
                            true
                        }
                        updateDeleteRowState(row, this, pendingDelete)
                    }
                }
                row.addView(deleteButton)
                updateDeleteRowState(row, deleteButton, pendingDelete = false)

                listContainer.addView(row)

                // Divider
                listContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(0, dp4, 0, 0) }
                    setBackgroundColor(Color.LTGRAY)
                })
            }
        }

        val scrollView = ScrollView(this).apply {
            val maxHeightPx = (resources.displayMetrics.heightPixels * 0.6).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxHeightPx
            )
            addView(listContainer)
        }

        outerDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .create().apply {
                setCanceledOnTouchOutside(false)
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        closeDialogWithPendingConfirmation()
                        true
                    } else {
                        false
                    }
                }
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        closeDialogWithPendingConfirmation()
                    }
                }
                show()
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

        val records = ProgressControl.getReadingCalendar(startMs, endMs, currentCycle)

        val dailyCounts = mutableMapOf<Long, Int>()
        for (record in records) {
            dailyCounts[record.dayTimestamp] = record.count
        }

        binding.calendarHeatmap.setData(dailyCounts)
        binding.calendarHeatmapScroll.post {
            binding.calendarHeatmapScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun refreshCycleLabel() {
        val latest = ProgressControl.getLatestCycle()
        binding.cycleLabel.text = getString(R.string.reading_progress_cycle, currentCycle)
        binding.cyclePrevButton.isEnabled = currentCycle > 1
        binding.cyclePrevButton.alpha = if (currentCycle > 1) 1f else 0.3f
        binding.cycleNextButton.isEnabled = currentCycle < latest
        binding.cycleNextButton.alpha = if (currentCycle < latest) 1f else 0.3f
        binding.newCycleButton.visibility = if (currentCycle >= latest) View.VISIBLE else View.GONE
    }

    // --- Memorize tab ---

    private fun refreshMemorizeListView() {
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

        val rangesWithTimestamps = ProgressControl.getMemorizedVerseRangesWithTimestamps()

        if (rangesWithTimestamps.isEmpty()) {
            binding.noMemorizedPassages.visibility = View.VISIBLE
            return
        }
        binding.noMemorizedPassages.visibility = View.GONE

        val displayed = rangesWithTimestamps.take(memorizedPassagesShown)
        for (rangeTs in displayed) {
            val item = createPassageItem(rangeTs.verseRange.name, rangeTs.verseRange, rangeTs.latestMemorizedAt)
            binding.memorizedPassagesList.addView(item)
        }

        val remaining = rangesWithTimestamps.size - memorizedPassagesShown
        if (remaining > 0) {
            addShowMoreButton(binding.memorizedPassagesList, remaining) {
                memorizedPassagesShown += PAGE_SIZE
                refreshMemorizedPassages()
            }
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

        val incompleteTargets = targets.filter { target ->
            val memorizedCount = ProgressControl.getMemorizedOrdinalsInRange(
                target.kjvOrdinalStart, target.kjvOrdinalEnd
            ).size
            memorizedCount < target.verseCount
        }

        if (incompleteTargets.isEmpty()) {
            binding.noMemorizeTargets.visibility = View.VISIBLE
            return
        }

        val displayed = incompleteTargets.take(memorizeTargetsShown)
        for (target in displayed) {
            val range = target.verseRange
            val memorizedCount = ProgressControl.getMemorizedOrdinalsInRange(
                target.kjvOrdinalStart, target.kjvOrdinalEnd
            ).size
            val item = createTargetItem(range.name, memorizedCount, target.verseCount, target.id, range, target.createdAt)
            binding.memorizeTargetsList.addView(item)
        }

        val remaining = incompleteTargets.size - memorizeTargetsShown
        if (remaining > 0) {
            addShowMoreButton(binding.memorizeTargetsList, remaining) {
                memorizeTargetsShown += PAGE_SIZE
                refreshMemorizeTargets()
            }
        }
    }

    private fun addShowMoreButton(container: LinearLayout, remaining: Int, onClick: () -> Unit) {
        val accentColor = run {
            val attrs = intArrayOf(android.R.attr.colorAccent)
            val ta = obtainStyledAttributes(attrs)
            val color = ta.getColor(0, Color.BLUE)
            ta.recycle()
            color
        }
        val btn = TextView(this).apply {
            text = getString(R.string.memorize_show_more, minOf(remaining, PAGE_SIZE))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8.dp, 12.dp, 8.dp, 12.dp)
            setTextColor(accentColor)
            setOnClickListener { onClick() }
        }
        container.addView(btn)
    }

    private fun formatRelativeTime(timestampMs: Long): CharSequence {
        return DateUtils.getRelativeTimeSpanString(
            timestampMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    private fun createPassageItem(text: String, range: VerseRange, memorizedAt: Long): LinearLayout {
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

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(context).apply {
                    this.text = text
                    textSize = 14f
                })
                addView(TextView(context).apply {
                    this.text = formatRelativeTime(memorizedAt)
                    textSize = 11f
                    setTextColor(Color.GRAY)
                })
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
                            refreshMemorizeSummary()
                            refreshMemorizedPassages()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            })
        }
    }

    private fun createTargetItem(text: String, memorized: Int, total: Int, targetId: IdType, range: VerseRange, createdAt: Long): LinearLayout {
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

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(context).apply {
                        this.text = "$text ($memorized/$total)"
                        textSize = 14f
                    })
                    addView(TextView(context).apply {
                        this.text = formatRelativeTime(createdAt)
                        textSize = 11f
                        setTextColor(Color.GRAY)
                    })
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
                                refreshMemorizeSummary()
                                refreshMemorizeTargets()
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

    // --- Memorization heatmap (Overview) ---

    private fun refreshMemorizationHeatmap() {
        val bookProgress = ProgressControl.getBookMemorizationProgress()
        val booksWithTargets = ProgressControl.getBooksWithMemorizationTargets()

        binding.memOtBooksGrid.removeAllViews()
        binding.memNtBooksGrid.removeAllViews()
        binding.memChapterDetailSection.visibility = View.GONE

        for (book in kjva.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val isNT = book.ordinal >= BibleBook.MATT.ordinal
            val grid = if (isNT) binding.memNtBooksGrid else binding.memOtBooksGrid

            val progress = bookProgress[book] ?: 0f
            val hasTarget = book in booksWithTargets
            val btn = createBookButton(book, progress, ::memorizationProgressToColor, { showMemChapterDetail(book) }, hasTarget = hasTarget)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            grid.addView(btn, params)
        }

        refreshMemorizationCalendarHeatmap()
    }

    private fun showMemChapterDetail(book: BibleBook) {
        binding.memChapterDetailSection.visibility = View.VISIBLE
        binding.memChapterDetailTitle.text = kjva.getLongName(book)
        binding.memChaptersGrid.removeAllViews()

        val totalChapters = kjva.getLastChapter(book)
        val chaptersWithTargets = ProgressControl.getChaptersWithMemorizationTargets(book)

        for (ch in 1..totalChapters) {
            val progress = ProgressControl.getMemorizationProgress(kjva, book, ch)
            val bgColor = memorizationProgressToColor(progress)
            val hasTarget = ch in chaptersWithTargets
            val btn = createChapterButton(ch, bgColor, progress >= 1f, { navigateToChapter(book, ch) }, hasTarget = hasTarget)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(2.dp, 2.dp, 2.dp, 2.dp)
            }
            binding.memChaptersGrid.addView(btn, params)
        }
    }

    private fun refreshMemorizationCalendarHeatmap() {
        val cal = Calendar.getInstance()
        val endMs = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        val startMs = cal.timeInMillis

        val records = ProgressControl.getMemorizationCalendar(startMs, endMs)
        val dailyCounts = mutableMapOf<Long, Int>()
        for (record in records) {
            dailyCounts[record.dayTimestamp] = record.count
        }

        binding.memCalendarHeatmap.setData(dailyCounts)
        binding.memCalendarHeatmapScroll.post {
            binding.memCalendarHeatmapScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    /**
     * Builds and shows the book percentage scale above the OT grid in count-mode.
     * Scale: light blue → dark blue (100%) → red at [maxReadPercent]*100%.
     * The legend stays at 25–100% by default, then expands in 25% steps once any book
     * exceeds 100%.
     */
    private fun showBookPercentScale(maxReadPercent: Float) {
        val container = binding.bookPercentScale
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val percentSteps = buildBookPercentScaleSteps(maxReadPercent)

        val density = resources.displayMetrics.density
        val bandHeight = (9 * density).toInt()

        val bandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bandHeight)
        }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        percentSteps.forEach { pct ->
            val color = countBookProgressToColor(pct / 100f, maxReadPercent)
            bandRow.addView(View(this).apply {
                background = GradientDrawable().apply { setColor(color) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            })
            labelRow.addView(TextView(this).apply {
                text = getString(R.string.reading_progress_percent_label, pct)
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        val scaleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(bandRow)
            addView(labelRow)
        }

        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(TextView(this@ReadingProgressActivity).apply {
                text = getString(R.string.reading_progress_percent_read_scale)
                textSize = 10f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, (6 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(scaleColumn)
        })
    }

    /**
     * Builds and shows the read-count colour scale legend between the book title and chapter grid.
     * Always anchors at 1 (pale yellow), 5 (orange), and effectiveMax (red, min 10).
     */
    private fun showCountScale(maxCount: Int) {
        val container = binding.chapterCountScale
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val effectiveMax = maxCount.coerceAtLeast(10)

        // Build list of step counts: always include 1, HEAT_MID_COUNT (5), and effectiveMax
        val steps: List<Int> = if (effectiveMax <= 10) {
            (1..effectiveMax).toList()
        } else {
            val n = 10
            val evenly = (0 until n).map { i -> 1 + (i.toLong() * (effectiveMax - 1) / (n - 1)).toInt() }.toSet()
            (evenly + setOf(1, HEAT_MID_COUNT, effectiveMax)).sorted().take(n)
        }

        val density = resources.displayMetrics.density
        val bandHeight = (9 * density).toInt()

        // Row of colour bands
        val bandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, bandHeight
            )
        }
        // Row of labels
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        steps.forEach { count ->
            val color = countToHeatColor(count, effectiveMax)
            bandRow.addView(View(this).apply {
                background = GradientDrawable().apply { setColor(color) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            })
            labelRow.addView(TextView(this).apply {
                text = "$count"
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        val scaleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(bandRow)
            addView(labelRow)
        }

        val outerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(this@ReadingProgressActivity).apply {
                text = getString(R.string.reading_progress_read_count_scale)
                textSize = 10f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, (6 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            addView(scaleColumn)
        }

        container.addView(outerRow)
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

    private fun memorizationProgressToColor(progress: Float): Int {
        return when {
            progress <= 0f -> COLOR_EMPTY
            progress < 0.25f -> COLOR_MEM_LOW
            progress < 0.50f -> COLOR_MEM_MEDIUM
            progress < 0.75f -> COLOR_MEM_HIGH
            progress < 1.0f -> COLOR_MEM_ALMOST
            else -> COLOR_MEM_FULL
        }
    }

    /** Interpolates smoothly between two ARGB colors by ratio (0..1). */
    private fun interpolateColor(from: Int, to: Int, ratio: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    /**
     * Returns [Color.WHITE] for dark backgrounds and [Color.DKGRAY] for light ones,
     * using WCAG relative luminance so text stays readable over any heat-map color.
     */
    private fun textColorForBackground(bgColor: Int): Int {
        val r = Color.red(bgColor) / 255.0
        val g = Color.green(bgColor) / 255.0
        val b = Color.blue(bgColor) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (luminance < 0.45) Color.WHITE else Color.DKGRAY
    }

    /**
     * Heat color for a chapter button in count-mode.
     * 3 fixed anchors: pale yellow at 1, orange at 5, deep red at max(maxCount,10).
     * Colours at 1 and 5 are always identical regardless of max.
     */
    private fun countToHeatColor(count: Int, maxCount: Int): Int {
        if (count == 0) return COLOR_EMPTY
        val effectiveMax = maxCount.coerceAtLeast(10)
        return when {
            count <= HEAT_MID_COUNT -> {
                val ratio = (count - 1).toFloat() / (HEAT_MID_COUNT - 1).coerceAtLeast(1)
                interpolateColor(COLOR_HEAT_MIN, COLOR_HEAT_MID, ratio.coerceIn(0f, 1f))
            }
            else -> {
                val ratio = (count - HEAT_MID_COUNT).toFloat() / (effectiveMax - HEAT_MID_COUNT).coerceAtLeast(1)
                interpolateColor(COLOR_HEAT_MID, COLOR_HEAT_MAX, ratio.coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Color for a book button in count-mode.
     * [readPercent] = totalReads / totalChapters (1.0 = 100%).
     * Light blue → dark blue at 100% → red at [effectiveMaxPercent]*100%.
     */
    private fun countBookProgressToColor(readPercent: Float, effectiveMaxPercent: Float): Int {
        if (readPercent <= 0f) return COLOR_EMPTY
        return when {
            readPercent <= 1.0f -> interpolateColor(COLOR_COUNT_BOOK_BLUE_LOW, COLOR_COUNT_BOOK_BLUE_HIGH, readPercent)
            else -> {
                val ratio = ((readPercent - 1.0f) / (effectiveMaxPercent - 1.0f)).coerceIn(0f, 1f)
                interpolateColor(COLOR_COUNT_BOOK_BLUE_HIGH, COLOR_COUNT_BOOK_RED, ratio)
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val PAGE_SIZE = 10

        private val COLOR_EMPTY = Color.parseColor("#E8E8E8")
        private val COLOR_LOW = Color.parseColor("#C6E48B")
        private val COLOR_MEDIUM = Color.parseColor("#7BC96F")
        private val COLOR_HIGH = Color.parseColor("#239A3B")
        private val COLOR_ALMOST = Color.parseColor("#196127")
        private val COLOR_READ = Color.parseColor("#196127")

        private val COLOR_MEM_LOW = Color.parseColor("#C6E48B")
        private val COLOR_MEM_MEDIUM = Color.parseColor("#7BC96F")
        private val COLOR_MEM_HIGH = Color.parseColor("#239A3B")
        private val COLOR_MEM_ALMOST = Color.parseColor("#196127")
        private val COLOR_MEM_FULL = Color.parseColor("#196127")

        private val COLOR_TARGET_DOT = Color.parseColor("#9C27B0")

        private const val HEAT_MID_COUNT = 5  // fixed colour anchor in the chapter heat map

        // Count-mode chapter heat map: amber → fixed orange at 5 → deep red at max(max,10)
        private val COLOR_HEAT_MIN = Color.parseColor("#FFF9C4")  // 1 read – pale yellow (text contrast handled by textColorForBackground)
        private val COLOR_HEAT_MID = Color.parseColor("#FF6D00")  // 5 reads (fixed anchor)
        private val COLOR_HEAT_MAX = Color.parseColor("#B71C1C")  // max reads (≥10)

        // Count-mode book heat map: light blue → dark blue at 100% → red at the current scale max.
        private val COLOR_COUNT_BOOK_BLUE_LOW  = Color.parseColor("#E3F2FD")  // ~1%
        private val COLOR_COUNT_BOOK_BLUE_HIGH = Color.parseColor("#1565C0")  // 100%
        private val COLOR_COUNT_BOOK_RED       = Color.parseColor("#B71C1C")

        internal fun resolveBookPercentScaleMax(maxReadPercent: Float?): Float {
            val actualMax = maxReadPercent ?: 0f
            // If less than 100%, we default to 1.0 (100%)
            if (actualMax <= 1.0f) return 1.0f

            // This logic ensures the max fits into our clean steps of 25% (0.25f)
            // It rounds 1.33 up to 1.50
            return ceil(actualMax * 4f) / 4f
        }

        internal fun buildBookPercentScaleSteps(maxReadPercent: Float): List<Int> {
            val maxPercent = (maxReadPercent * 100).roundToInt().coerceAtLeast(100)

            // 1. Define allowed "clean" increments
            val allowedSteps = listOf(10, 20, 25, 50, 100, 200, 500)

            // 2. Find the first step that results in <= 10 cells
            // Formula: maxPercent / step <= 10
            val bestStep = allowedSteps.firstOrNull { step ->
                (maxPercent / step) <= 10
            } ?: 100 // Fallback to 100 if it's a massive number

            // 3. Generate the list
            return (bestStep..maxPercent step bestStep).toList()
        }

        private const val PREF_LAST_TAB = "reading_progress_last_tab"
        private const val PREF_MEM_OVERVIEW = "reading_progress_mem_overview"

        const val EXTRA_TAB = "tab"
        const val EXTRA_HISTORY_BOOK_ORDINAL = "history_book_ordinal"
        const val EXTRA_HISTORY_CHAPTER = "history_chapter"
        const val EXTRA_HISTORY_ONLY = "history_only"
    }
}
