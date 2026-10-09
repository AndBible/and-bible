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
package net.bible.android.control.progress

import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidDateTimeFormats
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.android.database.bookmarks.KJVA
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingProgressServiceImplTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val service by lazy { ReadingProgressServiceImpl(AndroidDateTimeFormats(ApplicationProvider.getApplicationContext())) }

    @Test fun emptyDb_summary_is_zero_but_total_positive() = runBlocking {
        val s = service.readingSummary(1)
        assertEquals(0, s.chaptersRead)
        assertEquals(0, s.activeDays)
        assertTrue(s.overallPercent == 0f)
    }

    @Test fun bookReadProgress_lists_all_scripture_books_split_ot_nt() = runBlocking {
        val books = service.bookReadProgress(1)
        assertTrue(books.any { it.bookId == "GEN" && !it.isNT })
        assertTrue(books.any { it.bookId == "MATT" && it.isNT })
        assertTrue(books.none { it.readPercent > 0f }) // empty db
    }

    @Test fun display_helpers_delegate_to_DateTimeFormats() {
        val fake = object : DateTimeFormats {
            override fun shortDate(epochMs: Long) = "date:$epochMs"
            override fun shortTime(epochMs: Long) = "time:$epochMs"
            override fun relativeTimeSpan(epochMs: Long, nowMs: Long) = "rel:$epochMs"
        }
        val s = ReadingProgressServiceImpl(fake)
        assertEquals("date:5", s.dayTitle(5))
        assertEquals("date:6", s.formatEntryDate(6))
        assertEquals("time:7", s.formatEntryTime(7))
    }

    @Test fun osisId_for_chapter() {
        // Verse.getOsisID() always includes the verse (Verse(kjva, book, chapter, 1) -> verse 1),
        // matching the classic ReadingProgressActivity.navigateToChapter behaviour exactly.
        assertEquals("Gen.1.1", service.osisIdForChapter("GEN", 1))
    }

    // --- read history (Task 27, platform-dialog removal run 3: ComposeReadingViewHost's
    // ReadingQuickSheet.ReadHistory branch is this pair's only production caller now that
    // ReadHistoryDialog.kt is deleted) ---

    @Test fun readHistoryForChapter_then_delete_removes_only_the_deleted_entries() = runBlocking {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1, "KJV")
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1, "KJV")
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 2, "KJV") // different chapter, must not appear
        val cycle = service.currentCycle()

        val entries = service.readHistoryForChapter("GEN", 1, cycle)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.bookId == "GEN" && it.chapter == 1 && it.bookInitials == "KJV" })

        service.deleteReadHistoryEntries(listOf(entries.first().id), cycle)

        val remaining = service.readHistoryForChapter("GEN", 1, cycle)
        assertEquals(1, remaining.size)
        assertEquals(entries[1].id, remaining.single().id)
    }

    @Test fun deleteReadHistoryEntries_with_empty_ids_deletes_nothing() = runBlocking {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1, "KJV")
        val cycle = service.currentCycle()

        service.deleteReadHistoryEntries(emptyList(), cycle)

        assertEquals(1, service.readHistoryForChapter("GEN", 1, cycle).size)
    }

    // readingCalendarSkeleton() uses the real Calendar.getInstance(), so only deterministic
    // structural properties are asserted (not concrete dates/labels, which depend on "today").
    @Test fun readingCalendarSkeleton_has_expected_structure() = runBlocking {
        val skeleton = service.readingCalendarSkeleton()

        assertEquals(53, skeleton.weeks)
        assertEquals(listOf("", "M", "", "W", "", "F", ""), skeleton.dayOfWeekLabels)

        assertTrue(skeleton.slots.isNotEmpty())
        assertTrue(skeleton.slots.all { it.weekIndex in 0..52 })
        assertTrue(skeleton.slots.all { it.dayIndex in 0..6 })

        assertTrue(skeleton.monthLabels.isNotEmpty())
        assertTrue(skeleton.monthLabels.all { it.weekIndex in 0..52 })

        val now = System.currentTimeMillis()
        assertTrue(skeleton.slots.last().dayTimestamp <= now)
        assertTrue(skeleton.slots.zipWithNext().all { (a, b) -> a.dayTimestamp < b.dayTimestamp })
    }

    // --- memorize (Task 8b-2) ---

    @Test fun emptyDb_memorizeSummary_is_zero() = runBlocking {
        val s = service.memorizeSummary()
        assertEquals(0, s.memorizedCount)
        assertEquals(0, s.targetMemorized)
        assertEquals(0, s.targetTotal)
    }

    @Test fun bookMemorizationProgress_lists_all_scripture_books_zero_progress() = runBlocking {
        val books = service.bookMemorizationProgress()
        assertTrue(books.any { it.bookId == "GEN" && !it.isNT })
        assertTrue(books.any { it.bookId == "MATT" && it.isNT })
        assertTrue(books.none { it.readPercent > 0f })
        assertTrue(books.none { it.hasTarget })
        assertTrue(books.none { it.isComplete })
    }

    @Test fun emptyDb_memorizedPassages_and_targets_are_empty() = runBlocking {
        assertTrue(service.memorizedPassages().isEmpty())
        assertTrue(service.memorizeTargets().isEmpty())
    }
}
