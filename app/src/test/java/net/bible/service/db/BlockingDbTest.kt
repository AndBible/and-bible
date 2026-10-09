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

import androidx.room3.Room
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
import org.junit.Assert.fail
import java.io.File
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
        db = Room.inMemoryDatabaseBuilder(application, EpubDatabase::class.java).setDriver(sqliteDriverFactory()).build()
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

    /**
     * Runs [block] on its own thread and fails, with a dump of every thread, if it has not finished after
     * [timeoutMs]. A deadlock here is a blocked `runBlocking` (or native SQLite wait), which `withTimeout` cannot
     * interrupt; a watchdog thread can at least turn the hang into a failure (D1 final review M7).
     */
    private fun <T> withWatchdog(timeoutMs: Long = 30_000, block: () -> T): Result<T> {
        var result: Result<T>? = null
        val worker = Thread({ result = runCatching(block) }, "blockingdb-test-worker").apply { isDaemon = true; start() }
        worker.join(timeoutMs)
        if (worker.isAlive) {
            val dump = Thread.getAllStackTraces().entries.joinToString("\n\n") { (t, st) ->
                "\"${t.name}\" ${t.state}\n" + st.joinToString("\n") { "    at $it" }
            }
            worker.interrupt()
            fail("still running after ${timeoutMs}ms (deadlock?)\n$dump")
        }
        return result!!
    }

    private fun assertRefusedWithin(block: () -> Unit) {
        val e = withWatchdog { block() }.exceptionOrNull()
        assertTrue("expected BlockingDbInTransaction but was $e", e is BlockingDbInTransaction)
    }

    @Test fun refusesInsideRoomTransaction() = assertRefusedWithin {
        runBlocking { db.roomTransaction { blockingDb { 1 } } }
    }

    @Test fun refusesInsideRoomTransactionAfterHoppingThreads() = assertRefusedWithin {
        runBlocking { db.roomTransaction { withContext(Dispatchers.IO) { blockingDb { 1 } } } }
    }

    // D1 final review I3: with one pooled connection, a nested blockingDb DAO call inside a connection block waits
    // for that connection forever; the marked helpers turn the hang into BlockingDbInTransaction.
    @Test fun refusesInsideAMarkedWriterConnection() = assertRefusedWithin {
        runBlocking { db.useWriterConnectionMarked { blockingDb { db.epubDao().getMeta() } } }
    }

    @Test fun refusesInsideAMarkedReaderConnection() = assertRefusedWithin {
        runBlocking { db.useReaderConnectionMarked { blockingDb { db.epubDao().getMeta() } } }
    }

    @Test fun refusesInsideAMarkedWriterConnectionAfterHoppingThreads() = assertRefusedWithin {
        runBlocking { db.useWriterConnectionMarked { withContext(Dispatchers.IO) { blockingDb { db.epubDao().getMeta() } } } }
    }

    @Test fun markedConnectionsReturnTheBlockValueAndClearTheMarkAfterwards() {
        assertEquals(1L, withWatchdog { runBlocking { db.useWriterConnectionMarked { it.queryLong("SELECT 1") } } }.getOrThrow())
        assertEquals(2L, withWatchdog { runBlocking { db.useReaderConnectionMarked { it.queryLong("SELECT 2") } } }.getOrThrow())
        assertEquals(3, blockingDb { 3 })
    }

    /** Every non-empty Room connection block in app code is a marked one (the empty `{ }` open/migrate calls hold nothing). */
    @Test fun noUnmarkedNonEmptyConnectionBlockInAppCode() {
        val root = File("src/main/java")
        assertTrue("run from app/", root.isDirectory)
        val unmarked = Regex("""\.use(Writer|Reader)Connection\s*\{(?!\s*\})""")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (unmarked.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }.toList()
        assertEquals(emptyList<String>(), offenders)
        assertTrue(unmarked.containsMatchIn("db.useWriterConnection { c -> c.exec(x) }"))
        assertTrue(!unmarked.containsMatchIn("db.useWriterConnection { }") && !unmarked.containsMatchIn("db.useWriterConnectionMarked { c -> }"))
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
