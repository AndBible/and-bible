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

package net.bible.sharedcore.progress

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the UI state for the Reading tab of the reading-progress screen: cycle navigation, the
 * assembled [ReadingProgressModel] (summary + OT/NT book heat + calendar heatmap), and the
 * optional open chapter-detail popup. Assembly logic (scale/steps/heat-levels) lives in the pure
 * [ReadingProgressScale] / [CalendarHeatmapLayout] helpers; the actual data reads (Room/JSword/
 * platform date formatting) live behind the [ReadingProgressService] seam, keeping this class
 * portable to iOS.
 *
 * (Plan 8b extends this controller with Memorize-tab state; [selectTab] currently only tracks
 * which tab is selected.)
 */
class ReadingProgressController(
    private val service: ReadingProgressService,
    private val scope: CoroutineScope,
    initialTab: ReadingTab,
    // one-shot host actions:
    private val onNavigateToChapter: (bookId: String, chapter: Int) -> Unit,
    private val onShowDayHistory: (dayTimestamp: Long) -> Unit,
    private val onShowBookHistory: (bookId: String) -> Unit,
    private val onShowChapterHistory: (bookId: String, chapter: Int) -> Unit,
    // Plan 8b adds: onNavigateToMemorize, onConfirm... callbacks
) {
    private val _model = MutableStateFlow(
        ReadingProgressModel(
            tab = initialTab,
            summary = ReadingSummary(0, 0, 0, 0f),
            otBooks = emptyList(),
            ntBooks = emptyList(),
            bookPercentScaleMax = 0f,
            bookPercentScaleSteps = emptyList(),
            chapterDetail = null,
            calendar = CalendarHeatmap(emptyList(), emptyList(), 0, emptyList()),
            cycle = 1,
            latestCycle = 1,
        )
    )
    val model: StateFlow<ReadingProgressModel> = _model.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load() {
        _loading.value = true
        val openChapterBookId = _model.value.chapterDetail?.bookId
        scope.launch {
            val cycle = service.currentCycle()
            val latest = service.latestCycle()
            val summary = service.readingSummary(cycle)
            val books = service.bookReadProgress(cycle)
            val scaleMax = ReadingProgressScale.resolveBookPercentScaleMax(books.maxOfOrNull { it.readPercent })
            val steps = ReadingProgressScale.buildBookPercentScaleSteps(scaleMax)
            val skeleton = service.readingCalendarSkeleton()
            val daily = service.dailyReadCounts(cycle)
            val calendar = CalendarHeatmapLayout.assemble(skeleton, daily)
            val chapterDetail = openChapterBookId?.let { service.chapterReadCounts(it, cycle) }
            // Plan 8b: also (re)load Memorize-tab state here.
            _model.update {
                it.copy(
                    summary = summary,
                    otBooks = books.filter { b -> !b.isNT },
                    ntBooks = books.filter { b -> b.isNT },
                    bookPercentScaleMax = scaleMax,
                    bookPercentScaleSteps = steps,
                    chapterDetail = chapterDetail,
                    calendar = calendar,
                    cycle = cycle,
                    latestCycle = latest,
                )
            }
            _loading.value = false
        }
    }

    fun refresh() = load()

    fun selectTab(tab: ReadingTab) {
        _model.update { it.copy(tab = tab) }
    }

    fun prevCycle() {
        val m = _model.value
        if (!m.canPrevCycle) return
        service.setActiveCycle(m.cycle - 1)
        refresh()
    }

    fun nextCycle() {
        val m = _model.value
        if (!m.canNextCycle) return
        service.setActiveCycle(m.cycle + 1)
        refresh()
    }

    fun newCycle() {
        service.startNewCycle()
        refresh()
    }

    fun openChapterDetail(bookId: String) {
        scope.launch {
            val d = service.chapterReadCounts(bookId, _model.value.cycle)
            _model.update { it.copy(chapterDetail = d) }
        }
    }

    fun chapterTap(bookId: String, chapter: Int) = onNavigateToChapter(bookId, chapter)
    fun calendarDayTap(dayTimestamp: Long) = onShowDayHistory(dayTimestamp)
    fun bookLongPress(bookId: String) = onShowBookHistory(bookId)
    fun chapterLongPress(bookId: String, chapter: Int) = onShowChapterHistory(bookId, chapter)
}
