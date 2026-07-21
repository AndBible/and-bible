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
        override fun currentCycle() = cycle
        override fun latestCycle() = latest
        override fun setActiveCycle(c: Int) { setCycleTo = c; cycle = c }
        override fun startNewCycle(): Int { startedNew = true; latest += 1; cycle = latest; return cycle }
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
    }

    private fun controller(fake: Fake, scope: kotlinx.coroutines.CoroutineScope) =
        ReadingProgressController(fake, scope, ReadingTab.READING, { _, _ -> }, {}, {}, { _, _ -> })

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
}
