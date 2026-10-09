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

package net.bible.service.cloudsync

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.SchemaExportFixtures
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Cloud-sync patch creation (`createPatchForDatabase`) and application (`applyPatchesForDatabase`) end to end,
 * on real bookmark database files: the two transaction sites of SyncUtilities (D1 Task 15). One of the files
 * is built from the committed schema export, the other by Room.
 *
 * The round trips run on Room with the bundled driver: the sync triggers use `UNIXEPOCH('subsec')` (SQLite
 * 3.42) and the merge `pragma_foreign_key_check(table)`, which Robolectric's framework SQLite lacks (production
 * runs them on the bundled SQLite too). [creatingAPatchWorksThroughTheContainerBuilder] goes through
 * `DatabaseContainer.getBookmarkDb`, the production builder itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncPatchTransactionTest {
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

    private suspend fun accessor(fromSchemaExport: Boolean): SyncableDatabaseAccessor<BookmarkDatabase> {
        val file = File.createTempFile("bookmarks-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        if (fromSchemaExport) {
            file.delete()
            SchemaExportFixtures.createFromExport("net.bible.android.database.BookmarkDatabase", BOOKMARK_DATABASE_VERSION, file)
        }
        var db = bundled(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(
            db,
            { bundled(it) },
            { db.close(); db = bundled(file.absolutePath); db },
            file,
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

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    /** The log in a stable order: allLogEntries orders by timestamp first, and rows logged in one millisecond tie. */
    private suspend fun log(dbDef: SyncableDatabaseAccessor<*>) = dbDef.dao.allLogEntries().sortedBy { it.toString() }

    private suspend fun labelNames(dbDef: SyncableDatabaseAccessor<BookmarkDatabase>) =
        dbDef.localDb.bookmarkDao().allLabelsSortedByName().map { it.name }

    @Test fun aPatchCarriesRowsAndTheirLogToTheOtherDevice() = run {
        val dbDef1 = accessor(fromSchemaExport = true)
        val dbDef2 = accessor(fromSchemaExport = false)
        val dao1 = dbDef1.localDb.bookmarkDao()
        val label = BookmarkEntities.Label(name = "label 1")
        dao1.insert(label)
        dao1.insert(BookmarkEntities.Label(name = "label 2"))
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 5, kjvOrdinalEnd = 6)
        dao1.insert(bookmark.bookmarkEntity)
        dao1.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.bookmarkEntity, label))

        val patch = createPatchForDatabase(dbDef1)!!
        applyPatchesForDatabase(dbDef2, patch)

        assertEquals(listOf("label 1", "label 2"), labelNames(dbDef2))
        assertEquals(listOf(label.id), dbDef2.localDb.bookmarkDao().getBookmarkToLabelsForBookmark(bookmark.id).map { it.labelId })
        val log1 = log(dbDef1)
        assertEquals(4, log1.size)
        assertEquals(log1, log(dbDef2))
        // The triggers were off while the patch was applied: no entry is attributed to device 2.
        assertTrue(log(dbDef2).all { it.sourceDevice == dbDef1.deviceId })
        assertNotEquals(true, dbDef2.dao.getBoolean(TRIGGERS_DISABLED_KEY))
        // ... and back on afterwards.
        dbDef2.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 3"))
        assertEquals(listOf(dbDef2.deviceId), dbDef2.dao.newLogEntries(0, dbDef1.deviceId).map { it.sourceDevice })

        assertNull(createPatchForDatabase(dbDef1))
        // Device 2 has written no patch yet: its first one carries every logged row (label 3 and the merged ones).
        assertEquals(3, labelCountInPatch(createPatchForDatabase(dbDef2)!!))
    }

    /** Ported from androidTest `DatabasePatchingTests.testBookmarkToLabelUpdates`: needs `foreign_keys=OFF` while applying. */
    @Test fun aDeletedLabelWinsOverAReferenceAddedConcurrently() = run {
        val dbDef1 = accessor(fromSchemaExport = true)
        val dbDef2 = accessor(fromSchemaExport = false)
        val dao1 = dbDef1.localDb.bookmarkDao()
        val dao2 = dbDef2.localDb.bookmarkDao()
        val label1 = BookmarkEntities.Label()
        val bookmark1 = BookmarkEntities.BibleBookmarkWithNotes()
        val bookmark2 = BookmarkEntities.BibleBookmarkWithNotes()
        dao1.insert(bookmark1.bookmarkEntity)
        dao2.insert(bookmark2.bookmarkEntity)
        dao1.insert(label1)
        dao1.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark1.bookmarkEntity, label1))

        applyPatchesForDatabase(dbDef2, createPatchForDatabase(dbDef1)!!)
        dao2.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark2.bookmarkEntity, label1))
        dao1.delete(label1)
        assertEquals(1, dbDef1.dao.findLogEntries("BibleBookmarkToLabel", "DELETE").size)

        val patch1b = createPatchForDatabase(dbDef1)!!
        val patch2 = createPatchForDatabase(dbDef2)!!
        applyPatchesForDatabase(dbDef2, patch1b)
        assertEquals(0, dao2.getBookmarkToLabelsForBookmark(bookmark1.id).size)
        // The patch references a label that no longer exists here: the foreign-key cleanup removes it.
        applyPatchesForDatabase(dbDef1, patch2)
        assertEquals(0, dao1.getBookmarkToLabelsForBookmark(bookmark2.id).size)
        assertEquals(0, dao1.allLabelsSortedByName().size)
        assertEquals(log(dbDef1), log(dbDef2))
    }

    @Test fun aPatchThatFailsHalfwayLeavesNothingApplied() = run {
        val dbDef1 = accessor(fromSchemaExport = true)
        val dbDef2 = accessor(fromSchemaExport = false)
        dbDef1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        val good = createPatchForDatabase(dbDef1)!!
        // The last synced table is missing from this copy: Label rows are applied first, then the copy fails.
        val broken = tamperedPatch(good) { it.execSQL("DROP TABLE StudyPadTextEntryText") }

        val e = runCatching { applyPatchesForDatabase(dbDef2, broken) }.exceptionOrNull()
        assertTrue("expected the apply to fail, got $e", e != null && "StudyPadTextEntryText" in e.toString())
        assertEquals(emptyList<String>(), labelNames(dbDef2))
        assertEquals(emptyList<Any>(), log(dbDef2))
        assertNotEquals(true, dbDef2.dao.getBoolean(TRIGGERS_DISABLED_KEY))

        // The patch database was detached and foreign keys restored: the good patch applies normally afterwards.
        applyPatchesForDatabase(dbDef2, good)
        assertEquals(listOf("label 1"), labelNames(dbDef2))
    }

    private fun tamperedPatch(gzipped: File, change: (SQLiteConnection) -> Unit): File {
        val plain = File.createTempFile("tampered-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        CommonUtils.gunzipFile(gzipped, plain)
        openSqlite(plain.path).use(change)
        return File.createTempFile("tampered-", ".sqlite3.gz", CommonUtils.tmpDir).also {
            files += it
            CommonUtils.gzipFile(plain, it)
        }
    }

    private fun labelCountInPatch(gzipped: File): Long {
        val plain = File.createTempFile("patch-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        CommonUtils.gunzipFile(gzipped, plain)
        return openSqlite(plain.path).use { it.queryLong("SELECT COUNT(*) FROM Label")!! }
    }

    /**
     * Site `createPatchForDatabase` on a database from the production builder (`DatabaseContainer.getBookmarkDb`).
     * No triggers here: the log entries are written by hand, so exactly one row is logged.
     */
    @Test fun creatingAPatchWorksThroughTheContainerBuilder() = run {
        val file = File.createTempFile("bookmarks-compat-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        val db = DatabaseContainer.instance.getBookmarkDb(file.absolutePath)
        try {
            val dbDef = SyncableDatabaseAccessor(db, { DatabaseContainer.instance.getBookmarkDb(it) }, { db }, file,
                SyncableDatabaseDefinition.BOOKMARKS, deviceId = "compat-device")
            val label = BookmarkEntities.Label(name = "compat")
            db.bookmarkDao().insert(label)
            db.bookmarkDao().insert(BookmarkEntities.Label(name = "not logged"))
            db.useWriterConnection {
                it.exec("INSERT INTO LogEntry VALUES ('Label', ?, '', 'UPSERT', 1000, 'compat-device')", label.id.toString().replace("-", "").chunked(2).map { h -> h.toInt(16).toByte() }.toByteArray())
            }

            val patch = createPatchForDatabase(dbDef)!!

            val plain = File.createTempFile("patch-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
            CommonUtils.gunzipFile(patch, plain)
            openSqlite(plain.path).use {
                assertEquals(1L, it.queryLong("SELECT COUNT(*) FROM Label WHERE name = 'compat'"))
                assertEquals(1L, it.queryLong("SELECT COUNT(*) FROM Label"))
                assertEquals(1L, it.queryLong("SELECT COUNT(*) FROM LogEntry WHERE sourceDevice = 'compat-device'"))
            }
            assertNull(createPatchForDatabase(dbDef)) // lastPatchWritten moved past the entry
            val attached = db.useWriterConnection { t -> t.usePrepared("PRAGMA database_list") { st -> buildList { while (st.step()) add(st.getText(1)) } } }
            assertTrue("patch must be detached: $attached", "patch" !in attached)
        } finally {
            db.close()
        }
    }
}
