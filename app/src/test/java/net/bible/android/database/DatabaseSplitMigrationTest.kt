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

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.OLD_MONOLITHIC_DATABASE_NAME
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.service.db.textOrNull
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * D1.3 guard: an old monolithic `andBibleDatabase.db` (built from the last real export, v68) is split into the
 * per-topic databases on the first `DatabaseContainer` construction, and the old file is consumed. Every table
 * the split copies gets one row, including the id remapping (INTEGER ids become UUID blobs) and the JSON
 * rewriting of label references.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseSplitMigrationTest {
    private val schemaDir = "net.bible.android.database.OldMonolithicAppDatabase"
    private val dbDir: File get() = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME).parentFile!!
    private var wasReady = false

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true
        CommonUtils.settings.setBoolean("first-time", false)
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
    }

    private fun buildOldDatabase() {
        SchemaExportFixtures.createFromExport(schemaDir, 68, File(dbDir, OLD_MONOLITHIC_DATABASE_NAME))
        openSqlite(File(dbDir, OLD_MONOLITHIC_DATABASE_NAME).path).use { db ->
            fun sql(s: String) = db.prepare(s).use { it.step() }
            sql("INSERT INTO Label (id, name, bookmarkStyle, color) VALUES (1, 'L1', 'BLUE_HIGHLIGHT', 5)")
            sql("INSERT INTO Label (id, name, bookmarkStyle, color) VALUES (2, 'L2', 'SPEAK', 6)")
            sql("INSERT INTO Bookmark (kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, id, createdAt, primaryLabelId, notes) " +
                "VALUES (10, 11, 10, 11, 'KJVA', 1, 1000, 1, 'a note')")
            sql("INSERT INTO BookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (1, 2, 4, 1, 1)")
            sql("INSERT INTO JournalTextEntry (id, labelId, text, orderNumber, indentLevel) VALUES (1, 1, 'pad text', 2, 3)")
            sql("INSERT INTO readingplan (_id, plan_code, plan_start_date, plan_current_day) VALUES (1, 'plan1', 1234, 3)")
            sql("INSERT INTO readingplan_status (_id, plan_code, plan_day, reading_status) VALUES (1, 'plan1', 3, '101')")
            sql("INSERT INTO Workspace (id, name, orderNumber, window_behavior_settings_recentLabels, window_behavior_settings_favouriteLabels, " +
                "window_behavior_settings_autoAssignLabels, text_display_settings_bookmarks_showLabels, window_behavior_settings_autoAssignPrimaryLabel, " +
                "text_display_settings_font_fontSize) VALUES (1, 'WS', 7, '[{\"labelId\":1,\"lastAccess\":123},{\"labelId\":99,\"lastAccess\":5}]', " +
                "'[2]', '[1]', '[1,2]', 2, 18)")
            sql("INSERT INTO Window (id, workspaceId, isSynchronized, isPinMode, isLinksWindow, orderNumber, window_layout_state, window_layout_weight) " +
                "VALUES (1, 1, 1, 0, 0, 0, 'SPLIT', 1.0)")
            sql("INSERT INTO HistoryItem (id, windowId, createdAt, document, key) VALUES (1, 1, 99, 'KJV', 'Gen.1.1')")
            sql("INSERT INTO PageManager (windowId, currentCategoryName, bible_verse_versification, bible_verse_bibleBook, bible_verse_chapterNo, " +
                "bible_verse_verseNo, bible_document, text_display_settings_bookmarks_showLabels) VALUES (1, 'bible', 'KJVA', 0, 1, 1, 'KJV', '[1]')")
            sql("INSERT INTO CustomRepository (id, name, description, type, host, catalogDirectory, packageDirectory) VALUES (1, 'repo', 'd', 'SWORD', 'h', 'c', 'p')")
            sql("INSERT INTO DocumentBackup (osisId, name, abbreviation, language, repository, cipherKey) VALUES ('KJV', 'King James', 'KJV', 'en', 'Crosswire', 'key1')")
            sql("INSERT INTO BooleanSetting (key, value) VALUES ('b', 1)")
            sql("INSERT INTO StringSetting (key, value) VALUES ('s', 'v')")
            sql("INSERT INTO LongSetting (key, value) VALUES ('l', 7)")
            sql("INSERT INTO LongSetting (key, value) VALUES ('current_workspace_id', 1)")
            sql("INSERT INTO DoubleSetting (key, value) VALUES ('d', 1.5)")
        }
    }

    private fun <T> read(db: String, sql: String, row: (androidx.sqlite.SQLiteStatement) -> T): List<T> =
        openSqlite(application.getDatabasePath(db).path, readOnly = true).use { it.queryRows(sql, row = row) }

    @Test fun splitsEveryTableIntoItsOwnDatabaseAndConsumesTheOldFile() {
        DatabaseContainer.reset() // setUp already built a container (the settings write); the next one must see the old file
        dbDir.listFiles()!!.filter { it.isFile }.forEach { it.delete() }
        buildOldDatabase()
        val old = File(dbDir, OLD_MONOLITHIC_DATABASE_NAME)
        assertTrue(old.exists())

        DatabaseContainer.instance
        DatabaseContainer.reset() // close everything so the files can be read

        assertFalse("the old file is consumed", old.exists())
        assertEquals("no journal leftovers", emptyList<String>(), dbDir.listFiles()!!.map { it.name }.filter { it.startsWith(OLD_MONOLITHIC_DATABASE_NAME) })

        // Bookmarks: ids became UUID blobs and references follow
        val labels = read(BookmarkDatabase.dbFileName, "SELECT id, name, color FROM Label ORDER BY name") { Triple(it.getBlob(0), it.getText(1), it.getLong(2)) }
        assertEquals(listOf("L1", "L2"), labels.map { it.second })
        assertEquals(listOf(5L, 6L), labels.map { it.third })
        assertEquals(16, labels[0].first.size)
        val bookmark = read(BookmarkDatabase.dbFileName, "SELECT id, primaryLabelId, notes, createdAt, kjvOrdinalStart FROM Bookmark") {
            listOf(it.getBlob(0), it.getBlob(1), it.textOrNull(2), it.getLong(3), it.getLong(4))
        }.single()
        assertArrayEquals(labels[0].first, bookmark[1] as ByteArray)
        assertEquals(listOf<Any?>("a note", 1000L, 10L), bookmark.drop(2))
        val link = read(BookmarkDatabase.dbFileName, "SELECT bookmarkId, labelId, orderNumber, indentLevel, expandContent FROM BookmarkToLabel") {
            listOf(it.getBlob(0), it.getBlob(1), it.getLong(2), it.getLong(3), it.getLong(4))
        }.single()
        assertArrayEquals(bookmark[0] as ByteArray, link[0] as ByteArray)
        assertArrayEquals(labels[1].first, link[1] as ByteArray)
        assertEquals(listOf<Any?>(4L, 1L, 1L), link.drop(2))
        val pad = read(BookmarkDatabase.dbFileName, "SELECT labelId, text, orderNumber, indentLevel FROM StudyPadTextEntry") {
            listOf(it.getBlob(0), it.getText(1), it.getLong(2), it.getLong(3))
        }.single()
        assertArrayEquals(labels[0].first, pad[0] as ByteArray)
        assertEquals(listOf<Any?>("pad text", 2L, 3L), pad.drop(1))

        // Reading plans
        assertEquals(listOf(listOf<Any?>("plan1", 1234L, 3L)),
            read(ReadingPlanDatabase.dbFileName, "SELECT planCode, planStartDate, planCurrentDay FROM ReadingPlan") { listOf(it.getText(0), it.getLong(1), it.getLong(2)) })
        assertEquals(listOf(listOf<Any?>("plan1", 3L, "101")),
            read(ReadingPlanDatabase.dbFileName, "SELECT planCode, planDay, readingStatus FROM ReadingPlanStatus") { listOf(it.getText(0), it.getLong(1), it.getText(2)) })

        // Repositories
        assertEquals(listOf(listOf<Any?>("repo", "SWORD")),
            read(RepoDatabase.dbFileName, "SELECT name, type FROM CustomRepository") { listOf(it.getText(0), it.getText(1)) })
        assertEquals(listOf(listOf<Any?>("KJV", "King James", "key1")),
            read(RepoDatabase.dbFileName, "SELECT initials, name, cipherKey FROM SwordDocumentInfo") { listOf(it.getText(0), it.getText(1), it.getText(2)) })

        // Workspaces, with the label references rewritten to the new UUIDs
        val l1 = IdType.fromByteArray(labels[0].first).toString()
        val l2 = IdType.fromByteArray(labels[1].first).toString()
        val ws = read(WorkspaceDatabase.dbFileName,
            "SELECT id, name, orderNumber, workspace_settings_recentLabels, workspace_settings_favouriteLabels, workspace_settings_autoAssignLabels, " +
                "text_display_settings_bookmarksHideLabels, workspace_settings_autoAssignPrimaryLabel, text_display_settings_fontSize FROM Workspace") {
            listOf(it.getBlob(0), it.getText(1), it.getLong(2), it.textOrNull(3), it.textOrNull(4), it.textOrNull(5), it.textOrNull(6), it.getBlob(7), it.getLong(8))
        }.single()
        assertEquals("WS", ws[1])
        assertEquals(7L, ws[2])
        assertEquals("""[{"labelId":"$l1","lastAccess":123}]""", ws[3])
        assertEquals("""["$l2"]""", ws[4])
        assertEquals("""["$l1"]""", ws[5])
        assertEquals("""["$l1","$l2"]""", ws[6])
        assertArrayEquals(labels[1].first, ws[7] as ByteArray)
        assertEquals(18L, ws[8])
        val win = read(WorkspaceDatabase.dbFileName, "SELECT id, workspaceId, window_layout_state FROM Window") { listOf(it.getBlob(0), it.getBlob(1), it.getText(2)) }.single()
        assertArrayEquals(ws[0] as ByteArray, win[1] as ByteArray)
        assertEquals("SPLIT", win[2])
        assertEquals(listOf(listOf<Any?>(99L, "KJV", "Gen.1.1")),
            read(WorkspaceDatabase.dbFileName, "SELECT createdAt, document, key FROM HistoryItem") { listOf(it.getLong(0), it.getText(1), it.getText(2)) })
        val pm = read(WorkspaceDatabase.dbFileName, "SELECT windowId, bible_document, text_display_settings_bookmarksHideLabels FROM PageManager") {
            Triple(it.getBlob(0), it.getText(1), it.textOrNull(2))
        }.single()
        assertArrayEquals(win[0] as ByteArray, pm.first)
        assertEquals("KJV", pm.second)
        assertNotNull(pm.third)

        // Settings: copied, and current_workspace_id moved from the Long to the String table as the new UUID
        assertEquals(listOf("b" to 1L), read(SettingsDatabase.dbFileName, "SELECT key, value FROM BooleanSetting") { it.getText(0) to it.getLong(1) })
        assertEquals(listOf("l" to 7L), read(SettingsDatabase.dbFileName, "SELECT key, value FROM LongSetting ORDER BY key") { it.getText(0) to it.getLong(1) })
        assertEquals(listOf("d" to 1.5), read(SettingsDatabase.dbFileName, "SELECT key, value FROM DoubleSetting") { it.getText(0) to it.getDouble(1) })
        assertEquals(
            listOf("current_workspace_id" to IdType.fromByteArray(ws[0] as ByteArray).toString(), "s" to "v"),
            read(SettingsDatabase.dbFileName, "SELECT key, value FROM StringSetting ORDER BY key") { it.getText(0) to it.getText(1) },
        )
        assertNull(openSqlite(application.getDatabasePath(SettingsDatabase.dbFileName).path, readOnly = true).use {
            it.queryLong("SELECT value FROM LongSetting WHERE key = 'current_workspace_id'")
        })
    }
}
