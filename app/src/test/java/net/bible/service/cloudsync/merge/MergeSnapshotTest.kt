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

package net.bible.service.cloudsync.merge

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.common.CommonUtils
import net.bible.android.database.DbDate
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * [prepareMergeSnapshot] on a real bookmark database (bundled SQLite driver, as in `SyncPatchTransactionTest`:
 * the sync triggers need `UNIXEPOCH('subsec')`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MergeSnapshotTest {
    private val opened = mutableListOf<SyncableDatabaseAccessor<BookmarkDatabase>>()
    private val files = mutableListOf<File>()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        opened.forEach { it.localDb.close() }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private suspend fun accessor(): SyncableDatabaseAccessor<BookmarkDatabase> {
        val file = File.createTempFile("bookmarks-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        var db = bundled(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(
            db,
            { bundled(it) },
            { db.close(); db = bundled(file.absolutePath); db },
            file.path,
            SyncableDatabaseDefinition.BOOKMARKS,
            deviceId = UUID.randomUUID().toString(),
        )
        createTriggers(dbDef)
        return dbDef.also { opened += it }
    }

    private fun bundled(path: String): BookmarkDatabase =
        Room.databaseBuilder(application, BookmarkDatabase::class.java, path)
            .addMigrations(*bookmarkMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private fun newTarget() = File.createTempFile("merge-snapshot-", ".sqlite3", CommonUtils.tmpDir).also { files += it }

    /** Rows of the snapshot's LogEntry as `table|type|lastUpdated|device`, sorted. */
    private fun snapshotLog(target: File): List<String> = openSqlite(target.path).use { c ->
        c.queryRows("SELECT tableName, type, lastUpdated, sourceDevice FROM LogEntry ORDER BY tableName, type, lastUpdated") {
            "${it.getText(0)}|${it.getText(1)}|${it.getLong(2)}|${it.getText(3)}"
        }
    }

    @Test fun rowsWithoutLogGetUpsertEntries() = run {
        val dbDef = accessor()
        val dao = dbDef.localDb.bookmarkDao()
        dao.insert(BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 5, kjvOrdinalEnd = 6).bookmarkEntity
            .also { it.lastUpdatedOn = DbDate(1000) })
        dao.insert(BookmarkEntities.Label(name = "plain"))
        dbDef.dao.clearLog()

        val target = newTarget()
        prepareMergeSnapshot(dbDef, target.toOkioPath())

        assertEquals(
            listOf(
                "BibleBookmark|UPSERT|1000|${dbDef.deviceId}",
                "Label|UPSERT|0|${dbDef.deviceId}",
            ),
            snapshotLog(target),
        )
    }

    @Test fun existingEntriesKept() = run {
        val dbDef = accessor()
        val dao = dbDef.localDb.bookmarkDao()
        dao.insert(BookmarkEntities.Label(name = "kept"))
        dao.insert(BookmarkEntities.Label(name = "gone"))
        dbDef.localDb.useWriterConnectionMarked { it.exec("DELETE FROM Label WHERE name = 'gone'") }
        dbDef.localDb.useWriterConnectionMarked {
            it.exec("UPDATE LogEntry SET lastUpdated = 5000, sourceDevice = 'other' WHERE type = 'UPSERT'")
            it.exec("UPDATE LogEntry SET lastUpdated = 7000, sourceDevice = 'other' WHERE type = 'DELETE'")
        }

        val target = newTarget()
        prepareMergeSnapshot(dbDef, target.toOkioPath())

        assertEquals(
            listOf("Label|DELETE|7000|other", "Label|UPSERT|5000|other"),
            snapshotLog(target),
        )
    }

    @Test fun localDatabaseUntouched() = run {
        val dbDef = accessor()
        val dao = dbDef.localDb.bookmarkDao()
        dao.insert(BookmarkEntities.Label(name = "a"))
        dao.insert(BookmarkEntities.Label(name = "b"))
        dbDef.dao.clearLog()
        dao.insert(BookmarkEntities.Label(name = "c"))
        val before = dbDef.dao.allLogEntries().size

        prepareMergeSnapshot(dbDef, newTarget().toOkioPath())

        assertEquals(1, before)
        assertEquals(before, dbDef.dao.allLogEntries().size)
    }

    @Test fun compositeKeyTables() = run {
        val dbDef = accessor()
        val dao = dbDef.localDb.bookmarkDao()
        val label = BookmarkEntities.Label(name = "l")
        dao.insert(label)
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 1, kjvOrdinalEnd = 2).bookmarkEntity
        dao.insert(bookmark)
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark, label))
        dbDef.dao.clearLog()

        val target = newTarget()
        prepareMergeSnapshot(dbDef, target.toOkioPath())

        openSqlite(target.path).use { c ->
            assertEquals(1L, c.queryLong(
                "SELECT count(*) FROM LogEntry l JOIN BibleBookmarkToLabel t " +
                    "ON l.entityId1 = t.bookmarkId AND l.entityId2 = t.labelId WHERE l.tableName = 'BibleBookmarkToLabel'"
            ))
            // Single-key tables keep the empty second id.
            assertEquals(1L, c.queryLong("SELECT count(*) FROM LogEntry WHERE tableName = 'Label' AND entityId2 = ''"))
        }
    }

    @Test fun timestampColumnPreference() {
        assertEquals("lastUpdatedOn", mergeTimestampColumn("T", listOf("id", "createdAt", "lastUpdatedOn")))
        assertEquals("updatedAt", mergeTimestampColumn("T", listOf("updatedAt", "readAt", "createdAt")))
        assertEquals("readAt", mergeTimestampColumn("T", listOf("readAt", "createdAt")))
        assertEquals("memorizedAt", mergeTimestampColumn("T", listOf("memorizedAt", "createdAt")))
        assertEquals("createdAt", mergeTimestampColumn("T", listOf("id", "createdAt")))
        assertEquals(null, mergeTimestampColumn("T", listOf("id", "name")))
    }
}
