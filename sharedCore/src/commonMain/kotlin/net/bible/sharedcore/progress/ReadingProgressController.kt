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
 * Owns the UI state for the Reading and Memorize tabs of the reading-progress screen: cycle
 * navigation, the assembled [ReadingProgressModel] (summary + OT/NT book heat + calendar
 * heatmap), the optional open chapter-detail popups, and (Memorize) the overview/list toggle plus
 * paged passages/targets lists. Assembly logic (scale/steps/heat-levels) lives in the pure
 * [ReadingProgressScale] / [CalendarHeatmapLayout] helpers; the actual data reads (Room/JSword/
 * platform date formatting) live behind the [ReadingProgressService] seam, keeping this class
 * portable to iOS.
 *
 * Memorize state is loaded lazily: [selectTab] triggers [loadMemorize] the first time the
 * Memorize tab is shown, and every memorize mutation ([unmarkPassage], [removeTarget],
 * [setMemOverview]) reloads it. [showMorePassages]/[showMoreTargets] just grow the paging window
 * over the already-fetched full lists ([allPassages]/[allTargets]) and republish — no reload.
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
    initialOverviewActive: Boolean,
    private val onNavigateToMemorize: (startOrdinal: Int, endOrdinal: Int) -> Unit,
    private val persistOverview: (Boolean) -> Unit,
) {
    companion object {
        const val PAGE_SIZE = 10
    }

    private var overviewActive = initialOverviewActive
    private var allPassages: List<PassageRow> = emptyList()
    private var allTargets: List<TargetRow> = emptyList()
    private var passagesShown = PAGE_SIZE
    private var targetsShown = PAGE_SIZE

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
        if (tab == ReadingTab.MEMORIZE && _model.value.memorize == null) {
            loadMemorize()
        }
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

    /**
     * (Re)loads Memorize-tab state: summary always, plus either the overview (OT/NT book heat +
     * calendar heatmap) or the passages/targets lists, depending on [overviewActive]. The inactive
     * side's data is left as-is (not cleared) so switching back doesn't need a reload.
     */
    fun loadMemorize() {
        scope.launch {
            val summary = service.memorizeSummary()
            val prev = _model.value.memorize
            var otBooks = prev?.otBooks ?: emptyList()
            var ntBooks = prev?.ntBooks ?: emptyList()
            var calendar = prev?.calendar ?: CalendarHeatmap(emptyList(), emptyList(), 0, emptyList())
            if (overviewActive) {
                val books = service.bookMemorizationProgress()
                otBooks = books.filter { !it.isNT }
                ntBooks = books.filter { it.isNT }
                val skeleton = service.readingCalendarSkeleton()
                val daily = service.dailyMemorizationCounts()
                calendar = CalendarHeatmapLayout.assemble(skeleton, daily)
            } else {
                allPassages = service.memorizedPassages()
                allTargets = service.memorizeTargets()
            }
            val targetTotal = summary.targetTotal
            val targetMemorized = summary.targetMemorized
            val targetPermille = if (targetTotal > 0) targetMemorized * 1000 / targetTotal else 0
            val targetPercent = if (targetTotal > 0) targetMemorized * 100f / targetTotal else 0f
            _model.update {
                it.copy(
                    memorize = MemorizeModel(
                        overviewActive = overviewActive,
                        memorizedCount = summary.memorizedCount,
                        targetTotal = targetTotal,
                        targetMemorized = targetMemorized,
                        targetPermille = targetPermille,
                        targetPercent = targetPercent,
                        otBooks = otBooks,
                        ntBooks = ntBooks,
                        memChapterDetail = prev?.memChapterDetail,
                        calendar = calendar,
                        passages = allPassages.take(passagesShown),
                        passagesShown = passagesShown,
                        passagesTotal = allPassages.size,
                        targets = allTargets.take(targetsShown),
                        targetsShown = targetsShown,
                        targetsTotal = allTargets.size,
                    )
                )
            }
        }
    }

    fun setMemOverview(overview: Boolean) {
        overviewActive = overview
        persistOverview(overview)
        loadMemorize()
    }

    fun openMemChapterDetail(bookId: String) {
        scope.launch {
            val d = service.chapterMemorizationProgress(bookId)
            _model.update { m ->
                val mem = m.memorize ?: return@update m
                m.copy(memorize = mem.copy(memChapterDetail = d))
            }
        }
    }

    fun showMorePassages() {
        passagesShown += PAGE_SIZE
        republishMemorizeSlices()
    }

    fun showMoreTargets() {
        targetsShown += PAGE_SIZE
        republishMemorizeSlices()
    }

    private fun republishMemorizeSlices() {
        _model.update { m ->
            val mem = m.memorize ?: return@update m
            m.copy(
                memorize = mem.copy(
                    passages = allPassages.take(passagesShown),
                    passagesShown = passagesShown,
                    targets = allTargets.take(targetsShown),
                    targetsShown = targetsShown,
                )
            )
        }
    }

    fun memorizePassageTap(startOrdinal: Int, endOrdinal: Int) = onNavigateToMemorize(startOrdinal, endOrdinal)

    fun unmarkPassage(startOrdinal: Int, endOrdinal: Int) {
        scope.launch {
            service.unmarkMemorized(startOrdinal, endOrdinal)
            loadMemorize()
        }
    }

    fun removeTarget(id: String) {
        scope.launch {
            service.removeMemorizationTarget(id)
            loadMemorize()
        }
    }
}
