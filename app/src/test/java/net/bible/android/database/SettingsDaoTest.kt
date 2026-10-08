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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsDaoTest {
    private lateinit var db: SettingsDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, SettingsDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun booleanSetOverwritesAndNullDeletes() = runBlocking {
        val dao = db.booleanSettingDao()
        assertFalse(dao.get("k"))
        dao.set("k", true)
        dao.set("k", false)
        assertEquals(1, dao.all().size)
        assertFalse(dao.get("k", true))
        dao.set("k", null)
        assertTrue(dao.all().isEmpty())
        assertTrue("default returned after delete", dao.get("k", true))
    }

    @Test fun longStringDoubleRoundTripWithDefaults() = runBlocking {
        db.longSettingDao().set("l", 123456789012L)
        db.stringSettingDao().set("s", "v")
        db.doubleSettingDao().set("d", 2.5)
        assertEquals(123456789012L, db.longSettingDao().get("l", -1L))
        assertEquals("v", db.stringSettingDao().get("s", null))
        assertEquals(2.5, db.doubleSettingDao().get("d", -1.0), 0.0)

        db.longSettingDao().set("l", null)
        db.stringSettingDao().set("s", null)
        db.doubleSettingDao().set("d", null)
        assertEquals(-1L, db.longSettingDao().get("l", -1L))
        assertEquals("dflt", db.stringSettingDao().get("s", "dflt"))
        assertEquals(-1.0, db.doubleSettingDao().get("d", -1.0), 0.0)
    }
}
