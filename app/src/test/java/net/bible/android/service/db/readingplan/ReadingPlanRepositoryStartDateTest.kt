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

package net.bible.service.db.readingplan

import androidx.room3.Room
import net.bible.service.db.sqliteDriverFactory
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/**
 * Every plan started since 2023 stored its day number as its start date (see ReadingPlanDaoTest).
 * Fixing the INSERT does not repair those rows, and `ReadingPlanControl.startReadingPlan`'s
 * `startDate == null` guard never restarts them, so the read heals them once: a stored start date
 * below CORRUPT_START_DATE_LIMIT_MS becomes "today minus (currentDay - 1) days" and is written back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: target-less ON CONFLICT DO UPDATE needs the newer SQLite (see ReadingPlanDaoTest)
class ReadingPlanRepositoryStartDateTest {
    private lateinit var db: ReadingPlanDatabase
    private val today: Date = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 2, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.time
    private lateinit var repo: ReadingPlanRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
        repo = ReadingPlanRepository(daoProvider = { db.readingPlanDao() }, today = { today })
    }

    @After fun tearDown() { db.close() }

    private fun daysBefore(n: Int): Date =
        Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, -n) }.time

    @Test fun aCorruptStartDateOnDayOneHealsToToday() = runBlocking {
        db.readingPlanDao().updatePlan(ReadingPlan("p", Date(1), planCurrentDay = 1))
        assertEquals(today, repo.getStartDate("p"))
        assertEquals(today, db.readingPlanDao().getPlan("p")!!.planStartDate) // written back
    }

    @Test fun aCorruptStartDateHealsToTodayMinusTheCurrentDay() = runBlocking {
        db.readingPlanDao().updatePlan(ReadingPlan("p", Date(40), planCurrentDay = 40))
        assertEquals(daysBefore(39), repo.getStartDate("p"))
        assertEquals(40, db.readingPlanDao().getPlan("p")!!.planCurrentDay) // progress untouched
    }

    @Test fun aRealStartDateIsNeverRewritten() = runBlocking {
        val real = daysBefore(100)
        db.readingPlanDao().updatePlan(ReadingPlan("p", real, planCurrentDay = 5))
        assertEquals(real, repo.getStartDate("p"))
        assertEquals(real, db.readingPlanDao().getPlan("p")!!.planStartDate)
    }

    @Test fun aMissingPlanHasNoStartDate() = runBlocking {
        assertNull(repo.getStartDate("nope"))
    }
}
