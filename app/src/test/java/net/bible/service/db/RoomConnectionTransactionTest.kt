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
import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The pattern the sync, import and export sites use on a Room database: `useWriterConnection`, ATTACH and
 * `foreign_keys` outside a transaction, then `Transactor.inTransaction` with suspend DAO calls inside. Run in both
 * Room 2.8 modes: compatibility mode (open helper, what production runs until D1 Task 17; with requery there,
 * the framework helper here) and the bundled driver (production from Task 17).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RoomConnectionTransactionTest {
    private fun bothModes(block: suspend (mode: String, db: BookmarkDatabase) -> Unit) = runBlocking {
        for (mode in listOf("compat", "driver")) {
            val file = File.createTempFile("roomconn-", ".sqlite3").apply { delete() }
            val builder = Room.databaseBuilder(application, BookmarkDatabase::class.java, file.absolutePath)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            val db = (if (mode == "driver") builder.setDriver(BundledSQLiteDriver()) else builder).build()
            try {
                withTimeout(10_000) { block(mode, db) }
            } finally {
                db.close()
                file.delete(); File(file.path + "-journal").delete()
            }
        }
    }

    private suspend fun BookmarkDatabase.long(key: String) = syncDao().getLong(key)

    @Test fun daoCallsJoinTheTransactionAndCommitWithIt() = bothModes { mode, db ->
        val result = db.useWriterConnection { t ->
            t.inTransaction {
                db.syncDao().setConfig("dao", 5L)
                exec("INSERT INTO SyncConfiguration (keyName, longValue) VALUES ('raw', 6)")
                // The DAO sees the uncommitted raw row: same connection, same transaction.
                db.long("raw")
            }
        }
        assertEquals(mode, 6L, result)
        assertEquals(mode, 5L, db.long("dao"))
        assertEquals(mode, 6L, db.long("raw"))
    }

    @Test fun aThrowingBodyCommitsNothing() = bothModes { mode, db ->
        val e = runCatching {
            db.useWriterConnection { t ->
                t.inTransaction {
                    db.syncDao().setConfig("dao", 5L)
                    exec("INSERT INTO SyncConfiguration (keyName, longValue) VALUES ('raw', 6)")
                    error("boom")
                }
            }
        }.exceptionOrNull()
        assertEquals(mode, "boom", e?.message)
        assertNull(mode, db.long("dao"))
        assertNull(mode, db.long("raw"))
        // The connection is usable again, outside any transaction.
        db.useWriterConnection { t -> assertFalse(mode, t.inTransaction()) }
        db.syncDao().setConfig("after", 1L)
        assertEquals(mode, 1L, db.long("after"))
    }

    @Test fun aFailingCleanupDoesNotMaskTheOriginalExceptionAndTheRestStillRuns() = bothModes { mode, db ->
        db.useWriterConnection { t ->
            t.exec("PRAGMA foreign_keys=OFF;")
            val e = runCatching {
                t.withCleanup("DETACH DATABASE nonexistent", "PRAGMA foreign_keys=ON;") { error("original") }
            }.exceptionOrNull()
            assertEquals(mode, "original", e?.message)
            assertEquals(mode, 1, e?.suppressed?.size)
            assertEquals(mode, 1L, t.queryLong("PRAGMA foreign_keys"))
        }
    }

    @Test fun aFailingCleanupSurfacesWhenTheBodySucceeded() = bothModes { mode, db ->
        db.useWriterConnection { t ->
            t.exec("PRAGMA foreign_keys=OFF;")
            val e = runCatching {
                t.withCleanup("DETACH DATABASE nonexistent", "PRAGMA foreign_keys=ON;") { 42 }
            }.exceptionOrNull()
            assertTrue("$mode: $e", e != null)
            assertEquals(mode, 1L, t.queryLong("PRAGMA foreign_keys"))
        }
    }

    @Test fun blockingDbIsRefusedInside() = bothModes { mode, db ->
        val e = runCatching {
            db.useWriterConnection { t -> t.inTransaction { blockingDb { 1 } } }
        }.exceptionOrNull()
        assertTrue("$mode: $e", e is BlockingDbInTransaction)
    }

    /** ATTACH and `PRAGMA foreign_keys` fail or do nothing inside a transaction: Room must not open one around them. */
    @Test fun attachAndForeignKeysWorkOutsideTheTransaction() = bothModes { mode, db ->
        val other = File.createTempFile("roomconn-attached-", ".sqlite3")
        try {
            openSqlite(other.path).use { it.exec("CREATE TABLE x (keyName TEXT PRIMARY KEY, longValue INTEGER)"); it.exec("INSERT INTO x VALUES ('attached', 7)") }
            db.useWriterConnection { t ->
                assertFalse(mode, t.inTransaction())
                t.exec("ATTACH DATABASE '${other.absolutePath}' AS other")
                t.exec("PRAGMA foreign_keys=OFF;")
                assertEquals(mode, 0L, t.queryLong("PRAGMA foreign_keys"))
                try {
                    t.inTransaction {
                        exec("INSERT INTO SyncConfiguration (keyName, longValue) SELECT keyName, longValue FROM other.x")
                        db.syncDao().setConfig("dao", 1L)
                    }
                } finally {
                    t.exec("PRAGMA foreign_keys=ON;")
                    t.exec("DETACH DATABASE other")
                }
                assertEquals(mode, 1L, t.queryLong("PRAGMA foreign_keys"))
            }
            assertEquals(mode, 7L, db.long("attached"))
            assertEquals(mode, 1L, db.long("dao"))
        } finally {
            other.delete()
        }
    }

    /** `VACUUM` cannot run inside a transaction either (DatabaseContainer.vacuum, CloudSync's initial upload). */
    @Test fun vacuumAndCheckpointRunOnTheWriterConnection() = bothModes { mode, db ->
        db.syncDao().setConfig("k", 1L)
        db.useWriterConnection { it.exec("VACUUM;") }
        db.useWriterConnection { it.exec("PRAGMA wal_checkpoint(FULL)") }
        assertEquals(mode, 1L, db.long("k"))
    }
}
