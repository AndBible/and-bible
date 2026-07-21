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
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
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

    private val service = ReadingProgressServiceImpl()

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

    @Test fun osisId_for_chapter() {
        // Verse.getOsisID() always includes the verse (Verse(kjva, book, chapter, 1) -> verse 1),
        // matching the classic ReadingProgressActivity.navigateToChapter behaviour exactly.
        assertEquals("Gen.1.1", service.osisIdForChapter("GEN", 1))
    }
}
