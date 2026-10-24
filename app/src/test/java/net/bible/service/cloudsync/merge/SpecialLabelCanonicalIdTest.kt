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

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.SchemaExportFixtures
import net.bible.android.database.bookmarks.AI_LABEL_ID
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.productionConfig
import net.bible.service.db.queryRows
import net.bible.service.legacymigration.map.BookmarksMapper
import net.bible.service.legacymigration.map.LegacyBookmarkData
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testOrderedLauncher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pins the fact the sync merge relies on (spec 2026-10-24-sync-merge-data-design.md section 4.1
 * step 6): the four special labels (`__SPEAK_LABEL__`, `__UNLABELED__`, `__PARAGRAPH_BREAK_LABEL__`,
 * `__AI_LABEL__`) meet across devices by one fixed id each, so a merge never needs a cross-database
 * label remap. Each test covers one way a bookmarks database comes to hold special labels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SpecialLabelCanonicalIdTest {
    private val canonical = mapOf(
        SPEAK_LABEL_NAME to SPEAK_LABEL_ID,
        UNLABELED_NAME to UNLABELED_LABEL_ID,
        PARAGRAH_BREAK_LABEL_NAME to PARAGRAPH_BREAK_LABEL_ID,
        AI_LABEL_NAME to AI_LABEL_ID,
    )

    private var db: BookmarkDatabase? = null
    private var dbFile: File? = null

    @After
    fun tearDown() {
        db?.close()
        dbFile?.delete()
    }

    private fun <T> BookmarkDatabase.query(sql: String, row: (SQLiteStatement) -> T): List<T> = runBlocking {
        useReaderConnection { c -> c.queryRows(sql, row = row) }
    }

    @Test
    fun freshDatabaseUsesCanonicalIds() = runBlocking {
        val bc = BookmarkControl(
            Mockito.mock(WindowControl::class.java), testAppSettings(), testCoreStrings(), testOrderedLauncher(),
            net.bible.android.platform.androidSaxTextPolicy(),
        )
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        // Start from a database without any special label, whatever an earlier test left behind.
        dao.allLabelsSortedByName().filter { it.name in canonical }.forEach { dao.delete(it) }
        assertEquals(emptyList<String>(), dao.allLabelsSortedByName().filter { it.name in canonical }.map { it.name })

        val created = listOf(bc.speakLabel(), bc.labelUnlabelled(), bc.paragraphBreakLabel(), bc.aiLabel())
        // A second call must find the stored label, not create another one.
        bc.speakLabel(); bc.aiLabel()

        assertEquals(canonical.values.toSet(), created.map { it.id }.toSet())
        val stored = dao.allLabelsSortedByName().filter { it.name in canonical }
        assertEquals(4, stored.size)
        stored.forEach { assertEquals("id of ${it.name}", canonical[it.name], it.id) }
    }

    @Test
    fun migratedV11WithDuplicateSpecialLabelsEndsCanonical() {
        val file = File.createTempFile("bookmarks-v11-", ".sqlite3").also { it.delete() }
        dbFile = file
        SchemaExportFixtures.createFromExport("net.bible.android.database.BookmarkDatabase", 11, file)
        val randomA = "aa".repeat(16)
        val randomB = "bb".repeat(16)
        val randomUnl = "cc".repeat(16)
        val bm1 = "11".repeat(16)
        val bm2 = "22".repeat(16)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            fun label(id: String, name: String) = raw.execSQL(
                """INSERT INTO Label (id, name, color, markerStyle, markerStyleWholeVerse, underlineStyle,
                       underlineStyleWholeVerse, hideStyle, hideStyleWholeVerse, favourite, type, customIcon)
                   VALUES (X'$id', ?, 0, 0, 0, 0, 0, 0, 0, 0, NULL, NULL)""", arrayOf<Any?>(name))
            label(randomA, SPEAK_LABEL_NAME)
            label(randomB, SPEAK_LABEL_NAME)
            label(randomUnl, UNLABELED_NAME)
            fun bookmark(id: String, primary: String) = raw.execSQL(
                """INSERT INTO BibleBookmark (kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n,
                       id, createdAt, primaryLabelId) VALUES (100, 100, 100, 100, 'KJV', X'$id', 0, X'$primary')""")
            bookmark(bm1, randomA)
            bookmark(bm2, randomB)
            raw.execSQL("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (X'$bm1', X'$randomA', 0, 0, 0)")
            raw.execSQL("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (X'$bm2', X'$randomB', 0, 0, 0)")
            raw.execSQL("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (X'$bm2', X'$randomUnl', 1, 0, 0)")
            raw.execSQL("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (X'${"33".repeat(16)}', X'$randomB', 0, 0)")
        }
        val migrated = Room.databaseBuilder<BookmarkDatabase>(application, file.absolutePath)
            .productionConfig().addMigrations(*bookmarkMigrations).build().also { db = it }

        val labels = migrated.query("SELECT hex(id), name FROM Label WHERE name LIKE '\\_\\_%' ESCAPE '\\'") { it.getText(0) to it.getText(1) }
        assertEquals(
            setOf(SPEAK_LABEL_ID.toHex().uppercase() to SPEAK_LABEL_NAME, UNLABELED_LABEL_ID.toHex().uppercase() to UNLABELED_NAME),
            labels.toSet(),
        )
        assertEquals(2, labels.size)
        val speak = SPEAK_LABEL_ID.toHex().uppercase()
        assertEquals(listOf(speak, speak), migrated.query("SELECT hex(primaryLabelId) FROM BibleBookmark ORDER BY id") { it.getText(0) })
        assertEquals(
            listOf(Triple(bm1.uppercase(), speak, 0), Triple(bm2.uppercase(), UNLABELED_LABEL_ID.toHex().uppercase(), 1), Triple(bm2.uppercase(), speak, 0)).sortedBy { "${it.first}${it.second}" },
            migrated.query("SELECT hex(bookmarkId), hex(labelId), orderNumber FROM BibleBookmarkToLabel") { Triple(it.getText(0), it.getText(1), it.getInt(2)) }.sortedBy { "${it.first}${it.second}" },
        )
        assertEquals(listOf(speak), migrated.query("SELECT hex(labelId) FROM StudyPadTextEntry") { it.getText(0) })
    }

    @Test
    fun legacyMapperProducesCanonicalIds() {
        fun uuid(n: Int) = "14000000-0000-0000-0000-%012d".format(n)
        fun row(pk: Long, name: String) = LegacyRow(
            pk, mapOf("id" to LegacyValue.UuidV(uuid(pk.toInt())), "name" to LegacyValue.TextV(name)), emptyMap(),
        )
        val data = LegacyBookmarkData(
            listOf(
                row(1, SPEAK_LABEL_NAME), row(2, UNLABELED_NAME), row(3, PARAGRAH_BREAK_LABEL_NAME),
                row(4, AI_LABEL_NAME), row(5, "Mine"),
            ),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(),
        )
        val stage = BookmarksMapper().build(data, MigrationReport())
        val byName = stage.labels.associate { it.name to it.id }
        canonical.forEach { (name, id) -> assertEquals(name, id, byName[name]) }
        assertEquals(IdType.fromString(uuid(5)), byName["Mine"])
        assertEquals(5, stage.labels.size)
    }
}
