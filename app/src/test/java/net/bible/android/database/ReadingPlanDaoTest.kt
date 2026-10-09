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
import net.bible.android.TestBibleApplication
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * `updatePlan`'s INSERT once listed `:planCurrentDay` where `:planStartDate` belongs (`4bebe89ee`,
 * 2023), so every plan's FIRST write stored its day number (1) as the start date -> Date(1) ->
 * "Jan 1, 1970". The ON CONFLICT branch was right, which is why only a first start showed it.
 *
 * sdk = 35, not TEST_SDK: the query uses a target-less `ON CONFLICT DO UPDATE` (SQLite >= 3.35),
 * which the SQLite Robolectric ships for API 33 rejects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class ReadingPlanDaoTest {
    private lateinit var db: ReadingPlanDatabase
    private lateinit var dao: ReadingPlanDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory())
            .build()
        dao = db.readingPlanDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun aFirstInsertStoresTheStartDateItWasGiven() = runBlocking {
        val start = Date(1_727_740_800_000L) // 2024-10-01
        dao.updatePlan(ReadingPlan("y1ot1nt1_OTthenNT", start, planCurrentDay = 3))
        val stored = dao.getPlan("y1ot1nt1_OTthenNT")!!
        assertEquals(start, stored.planStartDate)
        assertEquals(3, stored.planCurrentDay)
    }

    @Test fun anUpdateOfAnExistingRowStillWritesBothFields() = runBlocking {
        val first = ReadingPlan("p", Date(1_727_740_800_000L), planCurrentDay = 1)
        dao.updatePlan(first)
        val later = Date(1_730_419_200_000L)
        dao.updatePlan(first.copy(planStartDate = later, planCurrentDay = 7))
        val stored = dao.getPlan("p")!!
        assertEquals(later, stored.planStartDate)
        assertEquals(7, stored.planCurrentDay)
    }
}
