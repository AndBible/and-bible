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

package net.bible.service.db

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.EpubDatabase
import net.bible.android.database.EpubMeta
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BlockingDbTest {
    private lateinit var db: EpubDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, EpubDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test fun returnsTheBlockValue() { assertEquals(3, blockingDb { 1 + 2 }) }

    @Test fun refusesToRunInsideAMarkedTransaction() {
        assertThrows(BlockingDbInTransaction::class.java) {
            runBlocking(DbTransactionMarker) { blockingDb { 1 } }
        }
    }

    @Test fun markIsRestoredAfterTheMarkedBlockSoLaterCallsWork() {
        runBlocking(DbTransactionMarker) { 1 }
        assertEquals(3, blockingDb { 3 })
    }

    @Test fun refusesInsideRoomTransaction() = runBlocking {
        val e = runCatching {
            withTimeout(30_000) { db.roomTransaction { blockingDb { 1 } } }
        }.exceptionOrNull()
        assertTrue("expected BlockingDbInTransaction but was $e", e is BlockingDbInTransaction)
    }

    @Test fun refusesInsideRoomTransactionAfterHoppingThreads() = runBlocking {
        val e = runCatching {
            withTimeout(30_000) {
                db.roomTransaction { withContext(Dispatchers.IO) { blockingDb { 1 } } }
            }
        }.exceptionOrNull()
        assertTrue("expected BlockingDbInTransaction but was $e", e is BlockingDbInTransaction)
    }

    @Test fun roomTransactionReturnsItsValueAndCommits() = runBlocking {
        val result = withTimeout(30_000) {
            db.roomTransaction {
                db.epubDao().insert(EpubMeta(totalCharacters = 77))
                "done"
            }
        }
        assertEquals("done", result)
        assertEquals(77, db.epubDao().getMeta()?.totalCharacters)
    }

    @Test fun roomTransactionRollsBackWhenTheBodyThrows() = runBlocking {
        runCatching {
            withTimeout(30_000) {
                db.roomTransaction {
                    db.epubDao().insert(EpubMeta(totalCharacters = 5))
                    error("boom")
                }
            }
        }
        assertEquals(null, db.epubDao().getMeta())
    }

    @Test fun blockingDbWorksAgainOutsideTheTransactionAfterwards() = runBlocking {
        db.roomTransaction { 1 }
        assertEquals(2, blockingDb { 2 })
    }
}
