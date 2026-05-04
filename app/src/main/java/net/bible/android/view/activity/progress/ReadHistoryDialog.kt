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

package net.bible.android.view.activity.progress

import android.app.Activity
import android.graphics.Color
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.progress.ProgressControl.ChapterReadEntry
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.progress.ReadingProgressColors.COLOR_HEAT_MAX
import org.crosswire.jsword.versification.BibleBook
import java.util.Date

/**
 * Standalone read-history dialog that can be shown over any [Activity].
 *
 * Used both by [ReadingProgressActivity] (which refreshes its view via [onChanged])
 * and by `BibleJavascriptInterface` (which relies on `ChapterReadStatusChangedEvent`
 * propagation to update the BibleView).
 */
object ReadHistoryDialog {

    fun <A> showForChapter(
        activity: A,
        book: BibleBook,
        chapter: Int,
        onChanged: (() -> Unit)? = null,
    ) where A : Activity, A : LifecycleOwner {
        loadAndShow(
            activity,
            subject = "${KJVA.getShortName(book)} $chapter",
            showChapterPerRow = false,
            onChanged = onChanged,
        ) { cycle -> ProgressControl.getReadHistoryForChapter(book, chapter, cycle) }
    }

    fun <A> showForBook(
        activity: A,
        book: BibleBook,
        onChanged: (() -> Unit)? = null,
    ) where A : Activity, A : LifecycleOwner {
        loadAndShow(
            activity,
            subject = KJVA.getLongName(book),
            showChapterPerRow = true,
            onChanged = onChanged,
        ) { cycle -> ProgressControl.getReadHistoryForBook(book, cycle) }
    }

    fun <A> showForDay(
        activity: A,
        dayTimestamp: Long,
        onChanged: (() -> Unit)? = null,
    ) where A : Activity, A : LifecycleOwner {
        val subject = DateFormat.getDateFormat(activity).format(Date(dayTimestamp))
        loadAndShow(
            activity,
            subject = subject,
            showChapterPerRow = true,
            onChanged = onChanged,
        ) { cycle -> ProgressControl.getReadHistoryForDay(dayTimestamp, cycle) }
    }

    private fun <A> loadAndShow(
        activity: A,
        subject: String,
        showChapterPerRow: Boolean,
        onChanged: (() -> Unit)?,
        fetch: (cycle: Int) -> List<ChapterReadEntry>,
    ) where A : Activity, A : LifecycleOwner {
        activity.lifecycleScope.launch {
            val cycle = ProgressControl.getCurrentCycle()
            val entries = withContext(Dispatchers.IO) { fetch(cycle) }
            show(activity, subject, showChapterPerRow, entries, cycle, onChanged)
        }
    }

    private fun <A> show(
        activity: A,
        subject: String,
        showChapterPerRow: Boolean,
        entries: List<ChapterReadEntry>,
        cycle: Int,
        onChanged: (() -> Unit)?,
    ) where A : Activity, A : LifecycleOwner {
        val dateFormat = DateFormat.getDateFormat(activity)
        val timeFormat = DateFormat.getTimeFormat(activity)
        val density = activity.resources.displayMetrics.density
        val dp4 = (4 * density).toInt()
        val dp8 = (8 * density).toInt()
        val pendingDeleteIds = mutableSetOf<IdType>()

        fun updateDeleteRowState(row: LinearLayout, deleteButton: TextView, pendingDelete: Boolean) {
            row.alpha = if (pendingDelete) 0.45f else 1f
            deleteButton.text = if (pendingDelete) "\u21B6" else "\u00D7"
            deleteButton.setTextColor(if (pendingDelete) COLOR_HEAT_MAX else Color.DKGRAY)
        }

        fun applyPendingDeletes() {
            val pendingEntries = entries.filter { it.id in pendingDeleteIds }
            if (pendingEntries.isEmpty()) return
            activity.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    ProgressControl.deleteReadHistoryEntries(pendingEntries, cycle)
                }
                onChanged?.invoke()
            }
        }

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp4, dp8, dp4)
        }

        if (entries.isEmpty()) {
            listContainer.addView(TextView(activity).apply {
                text = activity.getString(R.string.reading_progress_history_no_entries)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp8, dp8 * 2, dp8, dp8 * 2)
                setTextColor(Color.GRAY)
            })
        } else {
            for (entry in entries) {
                val kjvBook = BibleBook.entries.getOrNull(entry.kjvBookOrdinal)
                val chapterRef = if (kjvBook != null) "${KJVA.getShortName(kjvBook)} ${entry.chapter}" else "?"
                val dateStr = dateFormat.format(Date(entry.readAt))
                val timeStr = timeFormat.format(Date(entry.readAt))
                val versionStr = entry.bookInitials.ifEmpty {
                    activity.getString(R.string.reading_progress_history_version_unknown)
                }
                val (primaryText, secondaryText) = if (showChapterPerRow) {
                    "$chapterRef · $timeStr" to "$dateStr · $versionStr"
                } else {
                    "$dateStr $timeStr" to versionStr
                }

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp4, 0, dp4)
                }

                row.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(activity).apply {
                        text = primaryText
                        textSize = 16f
                    })
                    addView(TextView(activity).apply {
                        text = secondaryText
                        textSize = 12f
                        setTextColor(Color.GRAY)
                    })
                })

                val deleteButton = TextView(activity).apply {
                    text = "\u00D7"
                    textSize = 24f
                    minWidth = dp8 * 6
                    minHeight = dp8 * 6
                    gravity = Gravity.CENTER
                    setPadding(dp8, dp4, dp8, dp4)
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

                listContainer.addView(View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(0, dp4, 0, 0) }
                    setBackgroundColor(Color.LTGRAY)
                })
            }
        }

        val scrollView = ScrollView(activity).apply {
            val maxHeightPx = (activity.resources.displayMetrics.heightPixels * 0.6).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxHeightPx
            )
            addView(listContainer)
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.reading_progress_history_for, subject))
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { applyPendingDeletes() }
            .show()
    }
}
