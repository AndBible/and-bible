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

import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.epub.deleteEpubModule
import net.bible.service.sword.epub.getEpubDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Corrupt-database recovery of [buildAppDatabase] / [recoverIfCorrupt] (D1 Task 17 fix round 1) for a regenerable
 * database, and what it must leave alone. The user-database case (bookmarks, real fixture) is
 * `RealFileFixturesTest.corruptedFixtureIsMovedAsideAndRecreatedEmpty`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CorruptDatabaseRecoveryTest {
    @Before fun setUp() = DatabaseContainer.reset()
    @After fun tearDown() = DatabaseContainer.reset()

    private val garbage = ByteArray(4096) { 7 }

    /** The moved-aside database files themselves (not their `-wal`/`.lck` siblings). */
    private fun corruptCopies(file: File) =
        file.parentFile!!.listFiles()!!.filter { Regex(Regex.escape(file.name) + "\\.corrupt-[0-9-]+").matches(it.name) }

    @Test fun aCorruptRegenerableDatabaseIsMovedAsideWithItsSiblingsAndRecreated() {
        DatabaseContainer.reset()
        val file = application.getDatabasePath("temporary.sqlite3").also { it.parentFile!!.mkdirs() }
        file.writeBytes(garbage)
        // Room 3's lock file (SQLite's own open may consume invalid -journal/-wal files, see recoverIfCorrupt)
        File(file.path + ".lck").writeText("l")

        val count = runBlocking { DatabaseContainer.instance.downloadDocumentsDb.documentSearchDao().count() }

        assertEquals(0L, count)
        val aside = corruptCopies(file).single()
        assertTrue(garbage.contentEquals(aside.readBytes()))
        assertEquals("the sibling moves with it", "l", File(aside.path + ".lck").readText())
    }

    @Test fun aCorruptEpubCacheIsRecreatedEmpty() {
        val name = "epub-d1corrupt.sqlite3"
        val file = application.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        file.writeBytes(garbage)
        val db = getEpubDatabase(name)
        try {
            assertNull(runBlocking { db.epubDao().getFragment(1L) })
        } finally {
            db.close()
        }
        assertEquals(1, corruptCopies(file).size)
    }

    @Test fun aHealthyFileIsNotMoved() {
        val file = application.getDatabasePath("healthy.sqlite3").also { it.parentFile!!.mkdirs(); it.delete() }
        openSqlite(file.path).use { it.execSQL("CREATE TABLE t (x)") }
        assertNull(recoverIfCorrupt(file))
        assertTrue(file.isFile)
        assertTrue(corruptCopies(file).isEmpty())
    }

    @Test fun aLockedFileIsNotMovedAside() {
        // SQLITE_BUSY is not corruption: the file must stay where it is.
        val file = application.getDatabasePath("locked.sqlite3").also { it.parentFile!!.mkdirs(); it.delete() }
        openSqlite(file.path).use { holder ->
            holder.execSQL("CREATE TABLE t (x)")
            holder.execSQL("BEGIN EXCLUSIVE")
            holder.execSQL("INSERT INTO t VALUES (1)")
            assertNull(recoverIfCorrupt(file))
            holder.execSQL("ROLLBACK")
        }
        assertTrue(file.isFile)
        assertTrue(corruptCopies(file).isEmpty())
    }

    @Test fun onlyCorruptionResultCodesCount() {
        assertTrue(isCorruptionError(SQLiteException("Error code: 26, message: file is not a database")))
        assertTrue(isCorruptionError(SQLiteException("Error code: 11, message: database disk image is malformed")))
        assertTrue("extended SQLITE_CORRUPT_INDEX (779)", isCorruptionError(SQLiteException("Error code: 779, message: x")))
        assertFalse(isCorruptionError(SQLiteException("Error code: 5, message: database is locked")))
        assertFalse(isCorruptionError(SQLiteException("Error code: 8, message: attempt to write a readonly database")))
        assertFalse("no code, even with corruption words", isCorruptionError(SQLiteException("file is not a database")))
    }

    @Test fun deletingAnEpubModuleRemovesRoomsLockFileToo() {
        val dir = File(application.filesDir, "epubtest/d1lck").also { it.mkdirs() }
        val initialsName = "epub-" + net.bible.service.sword.epub.epubInitials(dir.name) + ".sqlite3"
        getEpubDatabase(initialsName).also { runBlocking { it.epubDao().getFragment(1L) } }.close()
        val db = application.getDatabasePath(initialsName)
        assertTrue("Room 3 created its lock file", File(db.path + ".lck").exists())

        deleteEpubModule(dir)

        assertFalse(db.exists())
        assertFalse("the .lck must not be orphaned", File(db.path + ".lck").exists())
    }

    @Test fun theStartupVersionCheckRecoversInsteadOfThrowing() {
        val file = application.getDatabasePath("versioned.sqlite3").also { it.parentFile!!.mkdirs(); it.delete() }
        openSqlite(file.path).use { it.execSQL("PRAGMA user_version = 7") }
        assertEquals(7, userVersionRecoveringCorruption(file))
        file.writeBytes(garbage)
        assertEquals(0, userVersionRecoveringCorruption(file))
        assertFalse(file.exists())
        assertEquals(1, corruptCopies(file).size)
        assertEquals(0, userVersionRecoveringCorruption(file))
    }
}
