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

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useWriterConnection
import net.bible.service.db.sqliteDriverFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.cloudsync.ID_ALIAS_PREFIX
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.CommonUtils
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmUsageRecord
import net.bible.test.DatabaseResetter
import net.bible.test.testCoreStrings
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Bookmark database import (`importDatabaseFile`, `bookmarksDbStats`) and StudyPad export
 * (`writeStudyPadExportDb`) end to end: the ImportDb and ExportStudyPads transaction sites (D1 Task 15). Import
 * runs against the live container's bookmark database, export on a Room database built like production (bundled
 * driver; its primary-label fix uses `pragma_foreign_key_check(table)`, which Robolectric's framework SQLite lacks).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ImportExportDbTest {
    private val files = mutableListOf<File>()
    private val local get() = DatabaseContainer.instance.bookmarkDb
    private val dao get() = local.bookmarkDao()

    @Before fun setUp() = DatabaseResetter.resetDatabase()

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private fun tmp(): File = File.createTempFile("importexport-", ".sqlite3", CommonUtils.tmpDir).also { files += it }

    /** A bookmark database file (built by Room, then closed) with whatever [fill] puts in it. */
    private suspend fun bookmarkFile(fill: suspend (net.bible.android.database.bookmarks.BookmarkDao) -> Unit): File {
        val file = tmp()
        val db = DatabaseContainer.instance.getBookmarkDb(file.absolutePath)
        try { fill(db.bookmarkDao()) } finally { db.close() }
        return file
    }

    /** `foreign_keys` and whether an import/export schema is still attached, on [db]'s writer connection (both are per connection). */
    private suspend fun connectionState(db: RoomDatabase = local): Pair<Long, Boolean> = db.useWriterConnection { t ->
        t.usePrepared("PRAGMA foreign_keys") { it.step(); it.getLong(0) } to
            t.usePrepared("PRAGMA database_list") { st -> buildList { while (st.step()) add(st.getText(1)) } }
                .any { it == "import" || it == "export" }
    }

    private fun bundled(path: String): BookmarkDatabase =
        Room.databaseBuilder(application, BookmarkDatabase::class.java, path)
            .addMigrations(*bookmarkMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(sqliteDriverFactory())
            .build()

    // ---- import ----

    @Test fun importAddsNewRowsAndKeepsLocalOnes() = run {
        val shared = IdType()
        dao.insert(BookmarkEntities.Label(id = shared, name = "Local"))
        val importedLabel = BookmarkEntities.Label(name = "Imported")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 3, kjvOrdinalEnd = 3)
        val file = bookmarkFile {
            it.insert(BookmarkEntities.Label(id = shared, name = "Remote"))
            it.insert(importedLabel)
            it.insert(bookmark.bookmarkEntity)
            it.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.bookmarkEntity, importedLabel))
        }

        importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, file.toOkioPath())

        assertEquals("Local", dao.labelById(shared)?.name) // INSERT OR IGNORE: a local row wins
        assertEquals("Imported", dao.labelById(importedLabel.id)?.name)
        assertEquals(listOf(importedLabel.id), dao.getBookmarkToLabelsForBookmark(bookmark.id).map { it.labelId })
        assertEquals(1L to false, connectionState())
    }

    /** #3906: a page of the same-initials document under another id lands under the local document, not orphaned. */
    @Test fun importedPagesOfADivergedDocumentLandUnderTheLocalDocument() = run {
        val localDao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val localDoc = net.bible.android.database.mydocument.MyDocument(name = "Doc", initials = "MyDoc_import3906")
        localDao.insert(localDoc)
        val file = tmp()
        val db = Room.databaseBuilder(application, net.bible.android.database.mydocument.MyDocumentDatabase::class.java, file.absolutePath)
            .addMigrations(*net.bible.android.database.migrations.myDocumentMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(sqliteDriverFactory())
            .build()
        val page: net.bible.android.database.mydocument.MyDocumentPage
        try {
            val d = db.myDocumentDao()
            val other = net.bible.android.database.mydocument.MyDocument(name = "Doc", initials = "MyDoc_import3906")
            d.insert(other)
            page = net.bible.android.database.mydocument.MyDocumentPage(documentId = other.id, title = "t", pageKey = "k", orderNumber = 1)
            d.insert(page)
            d.insertOrUpdateContent(net.bible.android.database.mydocument.MyDocumentPageContent(page.id, "c"))
        } finally { db.close() }

        importDatabaseFile(SyncableDatabaseDefinition.MYDOCUMENTS, file.toOkioPath())

        assertEquals(listOf(localDoc.id), localDao.allDocuments().filter { it.initials == "MyDoc_import3906" }.map { it.id })
        assertEquals(localDoc.id, localDao.pageById(page.id)?.documentId)
        assertEquals("c", localDao.getContent(page.id))
    }

    /**
     * #3906, review finding 6: an import uses the aliases sync learned (a usage row naming the other device's model id
     * lands under the local model) and maps a fresh natural-key match without storing it (a backup is not a peer).
     */
    @Test fun importUsesStoredAliasesAndStoresNoNewOnes() = run {
        val ai = DatabaseContainer.instance.aiSettingsDb
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        ai.llmProviderConfigDao().insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { ai.llmConfiguredModelDao().insert(it) }
        val model2 = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m2").also { ai.llmConfiguredModelDao().insert(it) }
        val foreign = IdType() // the other device's id of model "m", learned by sync
        fun IdType.hex() = toString().replace("-", "").uppercase()
        ai.syncDao().setConfig("${ID_ALIAS_PREFIX}LlmConfiguredModel:${foreign.hex()}", model.id.hex())

        val file = tmp()
        val db = Room.databaseBuilder(application, AiSettingsDatabase::class.java, file.absolutePath)
            .addMigrations(*aiSettingsMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(sqliteDriverFactory())
            .build()
        try {
            db.llmProviderConfigDao().insert(provider)
            val other2 = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m2").also { db.llmConfiguredModelDao().insert(it) }
            db.llmUsageRecordDao().upsert(LlmUsageRecord(configuredModelId = foreign, deviceId = "x", inputTokens = 5))
            db.llmUsageRecordDao().upsert(LlmUsageRecord(configuredModelId = other2.id, deviceId = "y", inputTokens = 7))
        } finally { db.close() }

        importDatabaseFile(SyncableDatabaseDefinition.AI_SETTINGS, file.toOkioPath())

        assertEquals(listOf(5L), ai.llmUsageRecordDao().getByModel(model.id).map { it.inputTokens })
        assertEquals(listOf(7L), ai.llmUsageRecordDao().getByModel(model2.id).map { it.inputTokens })
        assertEquals(setOf(model.id, model2.id), ai.llmConfiguredModelDao().all().map { it.id }.toSet())
        val aliases = ai.useWriterConnection { t ->
            t.usePrepared("SELECT count(*) FROM SyncConfiguration WHERE keyName LIKE '$ID_ALIAS_PREFIX%'") { it.step(); it.getLong(0) }
        }
        assertEquals(1L, aliases)
    }

    @Test fun anImportThatFailsHalfwayLeavesNothingImported() = run {
        val importedLabel = BookmarkEntities.Label(name = "Imported")
        val file = bookmarkFile { it.insert(importedLabel) }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE StudyPadTextEntryText") // the last table: Label rows are copied before the failure
        }

        val e = runCatching { importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, file.toOkioPath()) }.exceptionOrNull()
        assertTrue("expected the import to fail, got $e", e != null && "StudyPadTextEntryText" in e.toString())
        assertNull(dao.labelById(importedLabel.id))
        assertEquals(1L to false, connectionState())

        val good = bookmarkFile { it.insert(importedLabel) }
        importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, good.toOkioPath())
        assertEquals("Imported", dao.labelById(importedLabel.id)?.name)
    }

    @Test fun statsDescribeTheFile() = run {
        val file = bookmarkFile {
            it.insert(BookmarkEntities.Label(name = "__special"))
            it.insert(BookmarkEntities.Label(name = "My pad"))
            it.insert(BookmarkEntities.BibleBookmarkWithNotes().bookmarkEntity)
        }
        assertEquals(
            application.getString(R.string.bookmarks_db_stats, "My pad", "1", "1"),
            bookmarksDbStats(SyncableDatabaseDefinition.BOOKMARKS, file.toOkioPath(), testCoreStrings()),
        )
    }

    @Test fun importOfTask1StudyPadFixtureAddsItsLabel() = run {
        val dir = File(CommonUtils.tmpDir, "fixture-unzip-${System.nanoTime()}").also { it.mkdirs(); files += it }
        val zip = File(dir, "fixture.zip")
        javaClass.classLoader!!.getResourceAsStream("l1b-g2/studypad-export.abdb.zip")!!.use { i -> zip.outputStream().use { i.copyTo(it) } }
        ArchiveUtils.unzip(zip.toOkioPath(), File(dir, "out").toOkioPath())
        val db = File(dir, "out/db/bookmarks.sqlite3")
        importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, db.toOkioPath())
        assertTrue(dao.allLabelsSortedByName().any { it.name == "G2 export pad" })
        dir.deleteRecursively()
    }

    // ---- export ----

    @Test fun exportCopiesOnlyTheChosenStudyPad() = run {
        val source = bundled(tmp().absolutePath)
        try { exportCopiesOnlyTheChosenStudyPad(source) } finally { source.close() }
    }

    private suspend fun exportCopiesOnlyTheChosenStudyPad(source: BookmarkDatabase) {
        val dao = source.bookmarkDao()
        val pad = BookmarkEntities.Label(name = "Pad")
        val primary = BookmarkEntities.Label(name = "Primary")
        val other = BookmarkEntities.Label(name = "Other")
        listOf(pad, primary, other).forEach { dao.insert(it) }
        val inPad = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 1, kjvOrdinalEnd = 1, primaryLabelId = primary.id, notes = "note")
        val elsewhere = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 2, kjvOrdinalEnd = 2)
        dao.insert(inPad.bookmarkEntity); dao.insert(inPad.noteEntity!!)
        dao.insert(elsewhere.bookmarkEntity)
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(inPad.bookmarkEntity, pad))
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(inPad.bookmarkEntity, primary))
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(elsewhere.bookmarkEntity, other))
        val entry = BookmarkEntities.StudyPadTextEntry(labelId = pad.id, orderNumber = 0)
        dao.insert(entry); dao.insert(BookmarkEntities.StudyPadTextEntryText(entry.id, "hello"))

        val out = tmp()
        writeStudyPadExportDb(out.toOkioPath(), listOf(pad), source) { bundled(it) }

        openSqlite(out.path).use { db ->
            fun hexIds(sql: String) = db.queryRows(sql) { it.getText(0) }
            fun hex(id: IdType) = id.toString().replace("-", "").uppercase()
            assertEquals(listOf(hex(pad.id)), hexIds("SELECT hex(id) FROM Label"))
            assertEquals(listOf(hex(inPad.id)), hexIds("SELECT hex(id) FROM BibleBookmark"))
            assertEquals(listOf(hex(pad.id)), hexIds("SELECT hex(labelId) FROM BibleBookmarkToLabel"))
            assertEquals(listOf("note"), hexIds("SELECT notes FROM BibleBookmarkNotes"))
            assertEquals(listOf("hello"), hexIds("SELECT t.text FROM StudyPadTextEntry e JOIN StudyPadTextEntryText t ON t.studyPadTextEntryId = e.id"))
            // Pinned as it is (SQL unchanged by Task 15): fixPrimaryLabels' unqualified UPDATE and pragma_foreign_key_check
            // resolve to the main schema, so the exported bookmark keeps its primary label id although that label is
            // not exported. Pre-existing; reported in the Task 15 report.
            assertEquals(listOf(hex(primary.id)), hexIds("SELECT hex(primaryLabelId) FROM BibleBookmark"))
        }
        // The live database is untouched and back to its normal connection state.
        assertEquals(primary.id, dao.bibleBookmarkById(inPad.id)?.primaryLabelId)
        assertEquals(1L to false, connectionState(source))
    }
}
