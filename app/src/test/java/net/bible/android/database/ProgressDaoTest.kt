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

package net.bible.android.database

import androidx.room3.Room
import net.bible.service.db.sqliteDriverFactory
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.progress.ChapterReadHistory
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.android.database.progress.MemorizationTarget
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ProgressDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ProgressDaoTest {
    private lateinit var db: ProgressDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ProgressDatabase::class.java).setDriver(sqliteDriverFactory()).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun memorizedVerseReplaceKeepsOneRowPerOrdinalAndRangeQueriesAreOrdered() = runBlocking {
        val dao = db.progressDao()
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 30, memorizedAt = 100))
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 10, memorizedAt = 300))
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 20, memorizedAt = 200))
        // same ordinal again (new id): the unique index + REPLACE leaves a single row, newest timestamp
        dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 20, memorizedAt = 400))

        assertEquals(3, dao.countTotalMemorizedVerses())
        assertEquals(listOf(20, 10, 30), dao.allMemorizedVerses().map { it.kjvOrdinal }) // memorizedAt DESC
        assertEquals(listOf(10, 20), dao.memorizedOrdinalsInRange(5, 25))
        assertEquals(2, dao.countMemorizedVersesInRange(5, 25))
        assertTrue(dao.isVerseMemorized(10))
        assertFalse(dao.isVerseMemorized(11))

        dao.deleteMemorizedVerse(10)
        assertFalse(dao.isVerseMemorized(10))
        dao.deleteMemorizedVersesInRange(15, 40)
        assertEquals(0, dao.countTotalMemorizedVerses())
    }

    @Test fun memorizationTargetsRoundTripOverlapAndDelete() = runBlocking {
        val dao = db.progressDao()
        val a = MemorizationTarget(kjvOrdinalStart = 10, kjvOrdinalEnd = 20, createdAt = 1)
        val b = MemorizationTarget(kjvOrdinalStart = 30, kjvOrdinalEnd = 40, createdAt = 2)
        dao.insertMemorizationTarget(a)
        dao.insertMemorizationTarget(b)
        assertEquals(2, dao.countMemorizationTargets())
        assertEquals(listOf(b.id, a.id), dao.allMemorizationTargets().map { it.id }) // createdAt DESC
        assertEquals(a.id, dao.findMemorizationTarget(10, 20)!!.id)
        assertNull(dao.findMemorizationTarget(10, 21))
        assertEquals(listOf(a.id), dao.memorizationTargetsOverlapping(15, 25).map { it.id })
        assertEquals(listOf(a.id, b.id).toSet(), dao.memorizationTargetsOverlapping(20, 30).map { it.id }.toSet())
        dao.deleteMemorizationTarget(a.id)
        assertEquals(listOf(b.id), dao.allMemorizationTargets().map { it.id })
    }

    @Test fun chapterReadHistoryCountsCyclesAndDeletes() = runBlocking {
        val dao = db.progressDao()
        assertEquals("empty history reports cycle 1", 1, dao.getLatestCycle())
        val r1 = ChapterReadHistory(kjvBookOrdinal = 0, chapter = 1, cycle = 1, readAt = 1000)
        val r2 = ChapterReadHistory(kjvBookOrdinal = 0, chapter = 1, cycle = 1, readAt = 2000)
        val r3 = ChapterReadHistory(kjvBookOrdinal = 0, chapter = 2, cycle = 1, readAt = 3000)
        val r4 = ChapterReadHistory(kjvBookOrdinal = 0, chapter = 1, cycle = 2, readAt = 4000)
        listOf(r1, r2, r3, r4).forEach { dao.insertChapterReadHistory(it) }

        assertEquals(2, dao.getChapterReadCount(0, 1, 1))
        assertEquals(listOf(r2.id, r1.id), dao.getChapterReadHistory(0, 1, 1).map { it.id }) // readAt DESC
        assertEquals(2, dao.getDistinctReadChaptersCountForBook(0, 1))
        assertEquals(3, dao.getTotalReadCountForBook(0, 1))
        assertEquals(listOf(1, 2), dao.getReadChaptersForBook(0, 1))
        assertEquals(mapOf(1 to 2, 2 to 1), dao.getChapterReadCountsForBook(0, 1).associate { it.chapter to it.count })
        assertEquals(2, dao.countDistinctChaptersRead(1))
        assertEquals(listOf(r3.id, r2.id), dao.getHistoryForDay(1500, 3500, 1).map { it.id })
        assertEquals(listOf(1000L, 2000L, 3000L), dao.getAllReadingTimestampsForCycle(1).sorted())
        assertEquals(listOf(2000L), dao.getReadingTimestamps(1500, 2500, 1))
        assertEquals(2, dao.getLatestCycle())

        dao.insertChapterReadHistory(r1.copy(readAt = 1111)) // same id: REPLACE, not a duplicate
        assertEquals(2, dao.getChapterReadCount(0, 1, 1))
        dao.deleteChapterReadHistoryById(r1.id)
        assertEquals(1, dao.getChapterReadCount(0, 1, 1))
    }

    @Test fun globalReadingProgressSettingsRoundTripReplacesSingleton() = runBlocking {
        val dao = db.globalReadingProgressSettingsDao()
        assertNull(dao.get())
        dao.set(GlobalReadingProgressSettings(autoTrackReading = true, activeCycle = 3))
        dao.set(GlobalReadingProgressSettings(autoTrackReading = false, memorizeWordVisibility = "hidden", activeCycle = 4))
        val read = dao.get()!!
        assertFalse(read.autoTrackReading)
        assertEquals("hidden", read.memorizeWordVisibility)
        assertEquals(4, read.activeCycle)
    }
}
