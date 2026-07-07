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
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [EpubDao.clear], the guard that prevents stale fragment rows from surviving a
 * re-optimization. The EPUB database lives in internal storage and can outlive the (external)
 * epub directory when a module is removed via a failure path; a later re-download of the
 * same-named epub reuses the orphaned database. Without clearing, old fragment rows — whose
 * optimized files were wiped — remain and later point the reader at non-existent files, which
 * crashes the whole-book reading-progress computation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubDaoTest {
    private lateinit var db: EpubDatabase
    private lateinit var dao: EpubDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, EpubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.epubDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun clearRemovesRowsFromEveryTable() {
        val ids = dao.insert(
            EpubFragment("origA", 0, 10),
            EpubFragment("origB", 11, 20),
        )
        dao.insert(EpubHtmlToFrag("origA", ids[0]))
        dao.insert(StyleSheet("origA", "style.css"))
        dao.insert(EpubMeta(totalCharacters = 1234))

        // sanity: everything is present before clearing
        assertEquals(2, dao.fragments().size)
        assertEquals(1, dao.epubHtmlToFrags().size)
        assertEquals(1, dao.styleSheets("origA").size)
        assertNotNull(dao.getMeta())

        dao.clear()

        assertTrue("fragments must be cleared", dao.fragments().isEmpty())
        assertTrue("html-to-frag map must be cleared", dao.epubHtmlToFrags().isEmpty())
        assertTrue("stylesheets must be cleared", dao.styleSheets("origA").isEmpty())
        assertNull("meta must be cleared", dao.getMeta())
    }

    @Test
    fun freshInsertsAfterClearHaveNoStaleRows() {
        // Simulate a first optimization that leaves rows behind.
        dao.insert(EpubFragment("stale", 0, 5))
        dao.insert(EpubMeta(totalCharacters = 999))

        // Re-optimization clears first, then inserts fresh data.
        dao.clear()
        val freshIds = dao.insert(EpubFragment("fresh", 0, 42))

        val fragments = dao.fragments()
        assertEquals(1, fragments.size)
        assertEquals("fresh", fragments[0].originalId)
        // The cached character count from the previous run must not survive.
        assertNull(dao.getMeta())
        assertTrue(freshIds.isNotEmpty())
    }
}
