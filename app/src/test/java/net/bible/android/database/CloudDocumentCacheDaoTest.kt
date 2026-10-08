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
import kotlinx.coroutines.withTimeout
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
class CloudDocumentCacheDaoTest {
    private lateinit var db: DocumentSyncDatabase
    private lateinit var dao: CloudDocumentCacheDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, DocumentSyncDatabase::class.java).allowMainThreadQueries().build()
        dao = db.cloudDocumentCacheDao()
    }

    @After fun tearDown() { db.close() }

    private fun doc(initials: String, version: String = "1.0") =
        CachedCloudDocument(initials, initials, "SWORD", version, 10, "en", "BIBLE", "dev", 1L, null, false)

    @Test fun insertAllReplacesRowsWithSameInitials() = runBlocking {
        dao.insertAll(listOf(doc("KJV", "1.0"), doc("ESV")))
        dao.insertAll(listOf(doc("KJV", "2.0")))
        val all = dao.all().associateBy { it.initials }
        assertEquals(setOf("KJV", "ESV"), all.keys)
        assertEquals("2.0", all.getValue("KJV").version)
    }

    @Test fun markDeletedAndDeleteByInitialsTouchOnlyThatRow() = runBlocking {
        dao.insertAll(listOf(doc("A"), doc("B"), doc("C")))
        dao.markDeleted("A")
        dao.deleteByInitials("B")
        val all = dao.all().associateBy { it.initials }
        assertEquals(setOf("A", "C"), all.keys)
        assertTrue(all.getValue("A").deleted)
        assertFalse(all.getValue("C").deleted)
    }

    /** [CloudDocumentCacheDao.replaceAll] is a `@Transaction`: it must not deadlock and must swap the whole set. */
    @Test fun replaceAllTransactionSwapsTheWholeSetUnderTimeout() = runBlocking {
        dao.insertAll(listOf(doc("OLD1"), doc("OLD2")))
        withTimeout(10_000) { dao.replaceAll(listOf(doc("NEW"))) }
        assertEquals(listOf("NEW"), dao.all().map { it.initials })
        withTimeout(10_000) { dao.replaceAll(emptyList()) }
        assertTrue(dao.all().isEmpty())
    }
}
