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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingProgressControllerTest {
    private class Fake(var cycle: Int = 2, var latest: Int = 3) : ReadingProgressService {
        var setCycleTo: Int? = null
        var startedNew = false
        val emptyCal = CalendarSkeleton(emptyList(), emptyList(), 53, listOf("","M","","W","","F",""))
        override suspend fun currentCycle() = cycle
        override suspend fun latestCycle() = latest
        override suspend fun setActiveCycle(c: Int) { setCycleTo = c; cycle = c }
        override suspend fun startNewCycle(): Int { startedNew = true; latest += 1; cycle = latest; return cycle }
        override suspend fun readingSummary(c: Int) = ReadingSummary(10, 3, 100, 10f)
        override suspend fun bookReadProgress(c: Int) =
            listOf(BookHeat("GEN", "Gen", false, 0.5f, false), BookHeat("MATT", "Mat", true, 1.0f, true))
        override suspend fun chapterReadCounts(bookId: String, c: Int) =
            ChapterDetail(bookId, "Genesis", listOf(ChapterHeat(1, 2, 1)), 2, listOf(1,2))
        override suspend fun readingCalendarSkeleton() = emptyCal
        override suspend fun dailyReadCounts(c: Int) = emptyMap<Long, Int>()
        override suspend fun readHistoryForBook(bookId: String, c: Int) = emptyList<ReadHistoryEntry>()
        override suspend fun readHistoryForChapter(bookId: String, ch: Int, c: Int) = emptyList<ReadHistoryEntry>()
        override suspend fun readHistoryForDay(d: Long, c: Int) = emptyList<ReadHistoryEntry>()
        override suspend fun deleteReadHistoryEntries(ids: List<String>, c: Int) {}
        override fun dayTitle(d: Long) = "day"
        override fun formatEntryDate(t: Long) = "date"
        override fun formatEntryTime(t: Long) = "time"
        override fun bookShortName(id: String) = id
        override fun bookLongName(id: String) = id

        // --- memorize ---
        var memorizeSummary = MemorizeSummaryData(memorizedCount = 5, targetMemorized = 3, targetTotal = 10)
        var books = listOf(BookHeat("GEN", "Gen", false, 0.4f, false), BookHeat("MATT", "Mat", true, 0.6f, false))
        var passages = (1..25).map { PassageRow("Passage $it", it, it + 1, "ago") }
        var targets = (1..15).map { TargetRow("t$it", "Target $it", it, 10, it, it + 1, "ago") }
        var unmarkedCalls = mutableListOf<Pair<Int, Int>>()
        var removedTargetIds = mutableListOf<String>()

        override suspend fun memorizeSummary() = memorizeSummary
        override suspend fun bookMemorizationProgress() = books
        override suspend fun chapterMemorizationProgress(bookId: String) =
            ChapterDetail(bookId, "Genesis", listOf(ChapterHeat(1, 3, 2)), 3, listOf(1, 2, 3))
        override suspend fun dailyMemorizationCounts() = emptyMap<Long, Int>()
        override suspend fun memorizedPassages() = passages
        override suspend fun memorizeTargets() = targets
        override suspend fun unmarkMemorized(startOrdinal: Int, endOrdinal: Int) {
            unmarkedCalls.add(startOrdinal to endOrdinal)
        }
        override suspend fun removeMemorizationTarget(id: String) {
            removedTargetIds.add(id)
        }
    }

    private fun controller(
        fake: Fake,
        scope: kotlinx.coroutines.CoroutineScope,
        initialOverviewActive: Boolean = true,
        onNavigateToMemorize: (Int, Int) -> Unit = { _, _ -> },
        persistOverview: (Boolean) -> Unit = {},
    ) =
        ReadingProgressController(
            fake, scope, ReadingTab.READING, { _, _ -> }, {}, {}, { _, _ -> },
            initialOverviewActive, onNavigateToMemorize, persistOverview,
        )

    @Test fun load_assembles_model() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope); c.load()
        val m = c.model.value
        assertEquals(10, m.summary.chaptersRead)
        assertEquals(1, m.otBooks.size); assertEquals(1, m.ntBooks.size)
        assertEquals("GEN", m.otBooks[0].bookId)
        assertEquals(2, m.cycle); assertEquals(3, m.latestCycle)
        assertTrue(m.canPrevCycle); assertTrue(m.canNextCycle)
    }
    @Test fun prevCycle_sets_active_and_reloads() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(cycle = 2); val c = controller(fake, backgroundScope); c.load()
        c.prevCycle()
        assertEquals(1, fake.setCycleTo); assertEquals(1, c.model.value.cycle)
        assertTrue(!c.model.value.canPrevCycle)
    }
    @Test fun newCycle_starts_and_reloads() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(cycle = 3, latest = 3); val c = controller(fake, backgroundScope); c.load()
        assertTrue(c.model.value.showNewCycle)
        c.newCycle()
        assertTrue(fake.startedNew); assertEquals(4, c.model.value.cycle)
    }
    @Test fun openChapterDetail_loads_detail() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope); c.load()
        c.openChapterDetail("GEN")
        assertEquals("Genesis", c.model.value.chapterDetail?.title)
    }

    @Test fun load_with_initial_memorize_tab_also_loads_memorize() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val c = ReadingProgressController(
            fake, backgroundScope, ReadingTab.MEMORIZE, { _, _ -> }, {}, {}, { _, _ -> },
            true, { _, _ -> }, {},
        )
        assertEquals(null, c.model.value.memorize)
        c.load()
        assertEquals(ReadingTab.MEMORIZE, c.model.value.tab)
        assertTrue(c.model.value.memorize != null)
        assertEquals(5, c.model.value.memorize?.memorizedCount)
    }

    @Test fun selectTab_memorize_populates_model_with_overview() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope, initialOverviewActive = true)
        assertEquals(null, c.model.value.memorize)
        c.selectTab(ReadingTab.MEMORIZE)
        val mem = c.model.value.memorize
        assertEquals(ReadingTab.MEMORIZE, c.model.value.tab)
        assertEquals(true, mem?.overviewActive)
        assertEquals(5, mem?.memorizedCount)
        assertEquals(3, mem?.targetMemorized); assertEquals(10, mem?.targetTotal)
        assertEquals(300, mem?.targetPermille)
        assertEquals(1, mem?.otBooks?.size); assertEquals(1, mem?.ntBooks?.size)
        assertEquals("GEN", mem?.otBooks?.get(0)?.bookId)
    }

    @Test fun selectTab_memorize_does_not_reload_if_already_loaded() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope)
        c.selectTab(ReadingTab.MEMORIZE)
        fake.memorizeSummary = MemorizeSummaryData(memorizedCount = 999, targetMemorized = 0, targetTotal = 0)
        c.selectTab(ReadingTab.MEMORIZE)
        assertEquals(5, c.model.value.memorize?.memorizedCount)
    }

    @Test fun setMemOverview_false_loads_passages_and_targets_and_persists() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        var persisted: Boolean? = null
        val c = controller(fake, backgroundScope, initialOverviewActive = true, persistOverview = { persisted = it })
        c.setMemOverview(false)
        val mem = c.model.value.memorize
        assertEquals(false, persisted)
        assertEquals(false, mem?.overviewActive)
        assertEquals(10, mem?.passages?.size)
        assertEquals(25, mem?.passagesTotal)
        assertEquals(10, mem?.targets?.size)
        assertEquals(15, mem?.targetsTotal)
    }

    @Test fun showMorePassages_grows_shown_and_slice_without_reload() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope, initialOverviewActive = false)
        c.selectTab(ReadingTab.MEMORIZE)
        assertEquals(10, c.model.value.memorize?.passages?.size)
        fake.passages = emptyList() // prove no reload happens
        c.showMorePassages()
        val mem = c.model.value.memorize
        assertEquals(20, mem?.passagesShown)
        assertEquals(20, mem?.passages?.size)
    }

    @Test fun showMoreTargets_grows_shown_and_slice_without_reload() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope, initialOverviewActive = false)
        c.selectTab(ReadingTab.MEMORIZE)
        assertEquals(10, c.model.value.memorize?.targets?.size)
        fake.targets = emptyList() // prove no reload happens
        c.showMoreTargets()
        val mem = c.model.value.memorize
        assertEquals(20, mem?.targetsShown)
        assertEquals(15, mem?.targets?.size) // only 15 total targets available
    }

    @Test fun memorizePassageTap_invokes_onNavigateToMemorize() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        var navigated: Pair<Int, Int>? = null
        val c = controller(fake, backgroundScope, onNavigateToMemorize = { s, e -> navigated = s to e })
        c.memorizePassageTap(7, 9)
        assertEquals(7 to 9, navigated)
    }

    @Test fun unmarkPassage_calls_service_then_reloads() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope, initialOverviewActive = false)
        c.selectTab(ReadingTab.MEMORIZE)
        fake.memorizeSummary = MemorizeSummaryData(memorizedCount = 42, targetMemorized = 0, targetTotal = 0)
        c.unmarkPassage(3, 4)
        assertEquals(listOf(3 to 4), fake.unmarkedCalls)
        assertEquals(42, c.model.value.memorize?.memorizedCount)
    }

    @Test fun removeTarget_calls_service_then_reloads() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope, initialOverviewActive = false)
        c.selectTab(ReadingTab.MEMORIZE)
        fake.memorizeSummary = MemorizeSummaryData(memorizedCount = 77, targetMemorized = 0, targetTotal = 0)
        c.removeTarget("t3")
        assertEquals(listOf("t3"), fake.removedTargetIds)
        assertEquals(77, c.model.value.memorize?.memorizedCount)
    }

    @Test fun openMemChapterDetail_updates_memChapterDetail() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake(); val c = controller(fake, backgroundScope)
        c.selectTab(ReadingTab.MEMORIZE)
        c.openMemChapterDetail("GEN")
        assertEquals("Genesis", c.model.value.memorize?.memChapterDetail?.title)
    }
}
