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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import net.bible.service.db.BlockingDbInTransaction
import net.bible.service.db.blockingDb
import net.bible.service.db.columnIndex
import net.bible.service.db.inTransaction
import net.bible.service.db.exec
import net.bible.service.db.insertOr
import net.bible.service.db.queryFirst
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.service.db.textOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The driver-API helpers the migrations use, against a real (bundled, in-memory) SQLite. */
class SqliteConnectionExtTest {
    private lateinit var c: SQLiteConnection

    @Before
    fun setUp() {
        c = BundledSQLiteDriver().open(":memory:")
        c.execSQL("CREATE TABLE t (i INTEGER, r REAL, s TEXT, b BLOB, n TEXT)")
    }

    @After
    fun tearDown() = c.close()

    @Test
    fun roundTripsEveryBindType() {
        val blob = byteArrayOf(0, 1, -1, 127, -128)
        c.exec("INSERT INTO t (i, r, s, b, n) VALUES (?, ?, ?, ?, ?)", 5_000_000_000L, 1.5, "hän ☃", blob, null)
        val row = c.queryRows("SELECT i, r, s, b, n FROM t") {
            listOf(it.getLong(0), it.getDouble(1), it.getText(2), it.getBlob(3), it.isNull(4))
        }.single()
        assertEquals(5_000_000_000L, row[0])
        assertEquals(1.5, row[1] as Double, 0.0)
        assertEquals("hän ☃", row[2])
        assertArrayEquals(blob, row[3] as ByteArray)
        assertEquals(true, row[4])
    }

    @Test
    fun intFloatAndBooleanAreWidened() {
        c.exec("INSERT INTO t (i, r, s) VALUES (?, ?, ?)", 7, 2.5f, true)
        val row = c.queryRows("SELECT i, r, s FROM t") { listOf(it.getLong(0), it.getDouble(1), it.getLong(2)) }.single()
        assertEquals(listOf(7L, 2.5, 1L), row)
    }

    @Test
    fun queryFirstMapsOnlyTheFirstRowAndIsNullWithoutRows() {
        assertNull(c.queryFirst("SELECT i FROM t") { it.getLong(0) })
        c.exec("INSERT INTO t (i) VALUES (?)", 1L)
        c.exec("INSERT INTO t (i) VALUES (?)", 2L)
        var mapped = 0
        assertEquals(1L, c.queryFirst("SELECT i FROM t ORDER BY i LIMIT ?", 5) { mapped++; it.getLong(0) })
        assertEquals(1, mapped)
    }

    @Test
    fun queryLongReturnsNullForNoRowsAndForSqlNull() {
        assertNull(c.queryLong("SELECT i FROM t"))
        c.exec("INSERT INTO t (i) VALUES (?)", null)
        assertNull(c.queryLong("SELECT i FROM t"))
        c.exec("UPDATE t SET i = ?", 42L)
        assertEquals(42L, c.queryLong("SELECT i FROM t WHERE i = ?", 42))
    }

    @Test
    fun queryRowsBindsArgsAndReturnsAllRowsInOrder() {
        for (v in listOf(3L, 1L, 2L)) c.exec("INSERT INTO t (i) VALUES (?)", v)
        assertEquals(listOf(2L, 3L), c.queryRows("SELECT i FROM t WHERE i > ? ORDER BY i", 1) { it.getLong(0) })
    }

    @Test
    fun unsupportedBindTypeThrows() {
        val e = assertThrows(IllegalStateException::class.java) { c.exec("INSERT INTO t (s) VALUES (?)", listOf(1)) }
        assertTrue(e.message!!.contains("unsupported bind type"))
    }

    @Test
    fun insertOrReturnsRowIdAndHonoursTheConflictClause() {
        c.execSQL("CREATE TABLE u (id INTEGER PRIMARY KEY AUTOINCREMENT, k TEXT NOT NULL UNIQUE, v TEXT)")
        assertEquals(1L, c.insertOr("FAIL", "u", "k" to "a", "v" to null))
        assertEquals(2L, c.insertOr("IGNORE", "u", "k" to "b", "v" to "x"))
        // duplicate key: IGNORE reports "no row" as -1 and keeps the old row; FAIL/ABORT log and return -1 too
        assertEquals(-1L, c.insertOr("IGNORE", "u", "k" to "a", "v" to "changed"))
        assertEquals(-1L, c.insertOr("FAIL", "u", "k" to "a", "v" to "changed"))
        assertEquals(-1L, c.insertOr("ABORT", "u", "k" to "a", "v" to "changed"))
        // NOT NULL violation is also swallowed
        assertEquals(-1L, c.insertOr("FAIL", "u", "k" to null))
        assertEquals(3L, c.insertOr("REPLACE", "u", "id" to 3, "k" to "a", "v" to "replaced"))
        assertEquals(listOf(Triple(2L, "b", "x"), Triple(3L, "a", "replaced")),
            c.queryRows("SELECT id, k, v FROM u ORDER BY id") { Triple(it.getLong(0), it.getText(1), it.getText(2)) })
    }

    @Test
    fun textOrNullAndColumnIndexReadByName() {
        c.exec("INSERT INTO t (s, n) VALUES (?, ?)", "text", null)
        c.queryRows("SELECT s, n FROM t") {
            assertEquals(1, it.columnIndex("n"))
            assertEquals("text", it.textOrNull(it.columnIndex("s")))
            assertNull(it.textOrNull(it.columnIndex("n")))
            assertThrows(IllegalArgumentException::class.java) { it.columnIndex("missing") }
        }
    }

    @Test
    fun inTransactionCommitsAndReturnsTheBlockValue() {
        val result = runBlocking { c.inTransaction { exec("INSERT INTO t (i) VALUES (1)"); queryLong("SELECT COUNT(*) FROM t") } }
        assertEquals(1L, result)
        assertFalse(c.inTransaction())
        assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM t"))
    }

    @Test
    fun inTransactionRollsBackAndRethrowsWhenTheBlockThrows() {
        val e = runCatching {
            runBlocking { c.inTransaction { exec("INSERT INTO t (i) VALUES (1)"); error("boom") } }
        }.exceptionOrNull()
        assertEquals("boom", e?.message)
        assertFalse(c.inTransaction())
        assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM t"))
    }

    @Test
    fun inTransactionRefusesBlockingDbInside() {
        val e = runCatching { runBlocking { c.inTransaction { exec("INSERT INTO t (i) VALUES (1)"); blockingDb { 1 } } } }.exceptionOrNull()
        assertTrue("$e", e is BlockingDbInTransaction)
        assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM t"))
    }
}
