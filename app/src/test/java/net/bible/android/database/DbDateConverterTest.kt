/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.service.db.queryLong
import net.bible.service.db.sqliteDriverFactory
import net.bible.service.db.useReaderConnectionMarked
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the stored value of [DbDate] columns: epoch milliseconds, negative values included. Characterization of
 * the existing `Converters` behaviour, kept while the date type moves behind [DbDate].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class DbDateConverterTest {
    private val converters = Converters()
    private lateinit var db: ReadingPlanDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory())
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test fun dateToTimestampStoresEpochMillis() {
        assertEquals(1_700_000_000_123L, converters.dateToTimestamp(DbDate(1_700_000_000_123L)))
    }

    @Test fun epochZeroReadsBackAsZero() {
        assertEquals(0L, converters.fromTimestamp(0).getTime())
    }

    @Test fun negativeMillisRoundTripThroughTheConverter() {
        val millis = -86_400_001L
        assertEquals(millis, converters.fromTimestamp(millis).getTime())
        assertEquals(millis, converters.dateToTimestamp(converters.fromTimestamp(millis)))
    }

    @Test fun negativeMillisRoundTripThroughTheReadingPlanDao() = runBlocking {
        val dao = db.readingPlanDao()
        val millis = -1_000_000_000_000L // 1938, before the epoch
        dao.updatePlan(ReadingPlan("negative-date", DbDate(millis), planCurrentDay = 1))
        assertEquals(millis, dao.getPlan("negative-date")!!.planStartDate.getTime())
    }

    @Test fun storedColumnHoldsTheRawEpochMillis() = runBlocking {
        val millis = -1_000_000_000_000L
        db.readingPlanDao().updatePlan(ReadingPlan("raw-column", DbDate(millis), planCurrentDay = 1))
        val stored = db.useReaderConnectionMarked {
            it.queryLong("SELECT planStartDate FROM ReadingPlan WHERE planCode = 'raw-column'")
        }
        assertEquals(millis, stored)
    }
}
