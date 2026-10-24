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
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.mydocument.AiPageCacheEntry
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MyDocumentPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour of [net.bible.android.database.mydocument.MyDocumentDao], above all its `@Transaction` helpers.
 * Each transaction helper runs under `withTimeout`: a transaction that deadlocks on its own connection
 * (the failure mode of a nested blocking bridge on Room 3) fails here instead of hanging the suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyDocumentDaoTest {
    private lateinit var db: MyDocumentDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, MyDocumentDatabase::class.java).setDriver(sqliteDriverFactory()).build()
    }

    @After fun tearDown() { db.close() }

    private fun doc(initials: String = "D1") = MyDocument(name = "Doc $initials", initials = initials)
    private fun page(doc: MyDocument, key: String, order: Int = 0) =
        MyDocumentPage(documentId = doc.id, title = "T $key", pageKey = key, orderNumber = order)

    @Test fun insertPageWithContentWritesPageAndContent() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc().also { dao.insert(it) }
            val p = page(d, "a")
            dao.insertPageWithContent(p, "hello")
            assertEquals(p, dao.pageById(p.id))
            assertEquals("hello", dao.getContent(p.id))
            assertEquals("hello", dao.pageByKeyWithContent(d.id, "a")!!.content)
        }
    }

    @Test fun insertPageWithCacheEntryWritesAllThreeRows() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc().also { dao.insert(it) }
            val p = page(d, "a")
            val promptId = IdType()
            dao.insertPageWithCacheEntry(p, "body", AiPageCacheEntry(p.id, promptId, "ctx", 10, 12, "hash"))
            assertEquals("body", dao.getContent(p.id))
            assertEquals("hash", dao.getCacheEntry(p.id)!!.contextHash)
            assertEquals(p.id, dao.findCachedPageByContextHash(promptId, "hash")!!.pageId)
            assertEquals(1, dao.aiDocMarkersForRange(11, 11).size)
        }
    }

    @Test fun insertPageWithCacheEntryRollsBackEverythingWhenTheLastInsertFails() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc().also { dao.insert(it) }
            val p = page(d, "a")
            // Cache entry pointing at a nonexistent page violates the foreign key after page+content were written.
            val bad = AiPageCacheEntry(IdType(), IdType(), null, null, null, null)
            try {
                dao.insertPageWithCacheEntry(p, "body", bad)
                fail("expected a constraint failure")
            } catch (e: androidx.sqlite.SQLiteException) {
                // The bundled driver throws a plain SQLiteException, not the framework's SQLiteConstraintException.
                assertTrue(e.toString(), e.message.orEmpty().contains("FOREIGN KEY constraint failed"))
            }
            assertNull("page insert must be rolled back", dao.pageById(p.id))
            assertNull("content insert must be rolled back", dao.getContent(p.id))
        }
    }

    @Test fun updatePageWithContentReplacesBoth() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc().also { dao.insert(it) }
            val p = page(d, "a")
            dao.insertPageWithContent(p, "old")
            dao.updatePageWithContent(p.copy(title = "New title"), "new")
            assertEquals("New title", dao.pageById(p.id)!!.title)
            assertEquals("new", dao.getContent(p.id))
            assertEquals(1, dao.pageCount(d.id))
        }
    }

    @Test fun deletePageWithContentCascadesToContent() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc().also { dao.insert(it) }
            val p = page(d, "a")
            val keep = page(d, "b", 1)
            dao.insertPageWithContent(p, "x")
            dao.insertPageWithContent(keep, "y")
            dao.deletePageWithContent(p)
            assertNull(dao.pageById(p.id))
            assertNull(dao.getContent(p.id))
            assertEquals("y", dao.getContent(keep.id))
        }
    }

    @Test fun deleteDocumentWithPagesCascadesToPagesAndContent() = runBlocking {
        withTimeout(10_000) {
            val dao = db.myDocumentDao()
            val d = doc("D1").also { dao.insert(it) }
            val other = doc("D2").also { dao.insert(it) }
            val p = page(d, "a")
            val q = page(other, "a")
            dao.insertPageWithContent(p, "x")
            dao.insertPageWithContent(q, "z")
            dao.deleteDocumentWithPages(d)
            assertNull(dao.documentById(d.id))
            assertTrue(dao.pagesForDocument(d.id).isEmpty())
            assertNull(dao.getContent(p.id))
            assertNotNull(dao.documentById(other.id))
            assertEquals("z", dao.getContent(q.id))
        }
    }

    @Test fun documentLookupsAndOrdering() = runBlocking {
        val dao = db.myDocumentDao()
        assertNull(dao.maxDocumentOrderNumber())
        val a = MyDocument(name = "A", initials = "A", orderNumber = 2).also { dao.insert(it) }
        val b = MyDocument(name = "B", initials = "B", orderNumber = 1).also { dao.insert(it) }
        assertEquals(listOf("B", "A"), dao.allDocuments().map { it.initials })
        assertEquals(2, dao.maxDocumentOrderNumber())
        assertEquals(a, dao.documentByInitials("A"))
        assertEquals(b, dao.documentByName("B"))
        val p = page(a, "k").also { dao.insert(it) }
        assertEquals(setOf("A"), dao.initialsByPageIds(listOf(p.id)).toSet())
        assertEquals(setOf("A", "B"), dao.initialsByIds(listOf(a.id, b.id)).toSet())
    }
}
