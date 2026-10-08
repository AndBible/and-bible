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

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSearchDaoTest {
    private lateinit var db: TemporaryDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, TemporaryDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun ds(osisId: String, name: String, language: String = "en") =
        DocumentSearch(osisId, osisId, name, language, "repo")

    @Test fun insertThenSearchMatchesOnNameAndLanguage() = runBlocking {
        val dao = db.documentSearchDao()
        dao.insertDocuments(listOf(ds("KJV", "King James Version"), ds("FinPR", "Pyhä Raamattu", "fi"), ds("ESV", "English Standard")))
        assertEquals(3L, dao.count())
        assertEquals(listOf("KJV"), dao.search("James"))
        assertEquals(listOf("FinPR"), dao.search("fi"))
        assertTrue(dao.search("nonexistent").isEmpty())
    }

    @Test fun clearEmptiesTheTable() = runBlocking {
        val dao = db.documentSearchDao()
        dao.insertDocuments(listOf(ds("KJV", "King James Version")))
        dao.clear()
        assertEquals(0L, dao.count())
        assertTrue(dao.search("James").isEmpty())
        dao.insertDocuments(listOf(ds("ESV", "English Standard")))
        assertEquals(listOf("ESV"), dao.search("English"))
    }
}
