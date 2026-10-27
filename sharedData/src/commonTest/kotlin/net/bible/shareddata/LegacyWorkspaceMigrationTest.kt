package net.bible.shareddata

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import net.bible.android.database.migrations.oldMonolithicAppDatabaseMigrations
import net.bible.service.db.queryRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyWorkspaceMigrationTest {
    @Test
    fun missingWorkspaceDoesNotBlockLegacyMigration() = runTest {
        withVersion10 { db ->
            db.execSQL("DROP TABLE Workspace")
            migrateTo27(db)

            assertEquals(emptyList(), db.queryRows("SELECT name FROM Workspace") { it.getText(0) })
            assertEquals(listOf(7L), db.queryRows("SELECT workspaceId FROM Window") { it.getLong(0) })
            assertEquals(listOf("KJV"), db.queryRows("SELECT bible_document FROM PageManager") { it.getText(0) })
            assertEquals(listOf("Gen.1.1"), db.queryRows("SELECT `key` FROM HistoryItem") { it.getText(0) })
            assertPersonalData(db)
        }
    }

    @Test
    fun missingWorkspaceTablesAreRecreatedWithoutLosingPersonalData() = runTest {
        withVersion10 { db ->
            listOf("PageManager", "HistoryItem", "Window", "Workspace").forEach {
                db.execSQL("DROP TABLE `$it`")
            }
            migrateTo27(db)

            for (table in listOf("Workspace", "Window", "PageManager", "HistoryItem")) {
                assertEquals(listOf(0L), db.queryRows("SELECT count(*) FROM `$table`") { it.getLong(0) }, table)
            }
            // Later legacy migrations and the database split still need the history table and its index.
            assertTrue(db.queryRows("PRAGMA index_list('HistoryItem')") { it.getText(1) }.contains("index_HistoryItem_windowId"))
            assertPersonalData(db)
        }
    }

    @Test
    fun eachMissingChildTableIsRecreatedWithoutReplacingOtherTables() = runTest {
        for (missing in listOf("Window", "PageManager", "HistoryItem")) {
            withVersion10 { db ->
                db.execSQL("DROP TABLE `$missing`")
                migrateTo27(db)

                assertEquals(listOf("Study"), db.queryRows("SELECT name FROM Workspace") { it.getText(0) }, missing)
                for (table in listOf("Window", "PageManager", "HistoryItem")) {
                    assertEquals(listOf(if (table == missing) 0L else 1L), db.queryRows("SELECT count(*) FROM `$table`") { it.getLong(0) }, "$missing: $table")
                }
                assertPersonalData(db)
            }
        }
    }

    @Test
    fun tablesWhoseIndexIsMissingStillMigrate() = runTest {
        withVersion10 { db ->
            db.execSQL("DROP INDEX index_Window_workspaceId")
            db.execSQL("DROP INDEX index_PageManager_windowId")
            migrateTo27(db)

            assertEquals(listOf("Study"), db.queryRows("SELECT name FROM Workspace") { it.getText(0) })
            assertEquals(listOf(1L), db.queryRows("SELECT count(*) FROM Window") { it.getLong(0) })
            assertEquals(listOf("KJV"), db.queryRows("SELECT bible_document FROM PageManager") { it.getText(0) })
            assertPersonalData(db)
        }
    }

    @Test
    fun healthyVersion10KeepsWorkspaceAndWindowState() = runTest {
        withVersion10 { db ->
            migrateTo27(db)

            assertEquals(listOf("Study" to 7L), db.queryRows("SELECT name, id FROM Workspace") { it.getText(0) to it.getLong(1) })
            assertEquals(listOf(0L to "SPLIT"), db.queryRows("SELECT isPinMode, window_layout_state FROM Window") { it.getLong(0) to it.getText(1) })
            assertEquals(listOf("KJV"), db.queryRows("SELECT bible_document FROM PageManager") { it.getText(0) })
            assertEquals(listOf("Gen.1.1"), db.queryRows("SELECT `key` FROM HistoryItem") { it.getText(0) })
            assertPersonalData(db)
        }
    }

    private suspend fun migrateTo27(db: SQLiteConnection) {
        assertEquals(listOf(10L), db.queryRows("PRAGMA user_version") { it.getLong(0) })
        val migration = oldMonolithicAppDatabaseMigrations.single { it.startVersion == 10 && it.endVersion == 27 }
        db.execSQL("BEGIN TRANSACTION")
        try {
            migration.migrate(db)
            db.execSQL("PRAGMA user_version = 27")
            db.execSQL("COMMIT")
        } catch (e: Throwable) {
            db.execSQL("ROLLBACK")
            throw e
        }
    }

    private fun assertPersonalData(db: SQLiteConnection) {
        assertEquals(listOf("Gen.1.2"), db.queryRows("SELECT `key` FROM bookmark") { it.getText(0) })
        assertEquals(listOf("Keep my note"), db.queryRows("SELECT mynote FROM mynote") { it.getText(0) })
    }

    /** The version-10 workspace schema, with unrelated personal data that recovery must not delete. */
    private suspend fun withVersion10(block: suspend (SQLiteConnection) -> Unit) {
        val db = BundledSQLiteDriver().open(":memory:")
        try {
            db.execSQL("CREATE TABLE Workspace (name TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE Window (workspaceId INTEGER NOT NULL, isSynchronized INTEGER NOT NULL, wasMinimised INTEGER NOT NULL, isLinksWindow INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, orderNumber INTEGER NOT NULL, window_layout_state TEXT NOT NULL, window_layout_weight REAL NOT NULL, FOREIGN KEY(workspaceId) REFERENCES Workspace(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_Window_workspaceId ON Window(workspaceId)")
            db.execSQL("CREATE TABLE HistoryItem (windowId INTEGER NOT NULL, createdAt INTEGER NOT NULL, document TEXT NOT NULL, `key` TEXT NOT NULL, yOffsetRatio REAL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(windowId) REFERENCES Window(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_HistoryItem_windowId ON HistoryItem(windowId)")
            db.execSQL("CREATE TABLE PageManager (windowId INTEGER NOT NULL PRIMARY KEY, currentCategoryName TEXT NOT NULL, bible_document TEXT, bible_verse_versification TEXT NOT NULL, bible_verse_bibleBook INTEGER NOT NULL, bible_verse_chapterNo INTEGER NOT NULL, bible_verse_verseNo INTEGER NOT NULL, commentary_document TEXT, dictionary_document TEXT, dictionary_key TEXT, general_book_document TEXT, general_book_key TEXT, map_document TEXT, map_key TEXT, commentary_currentYOffsetRatio REAL, dictionary_currentYOffsetRatio REAL, general_book_currentYOffsetRatio REAL, map_currentYOffsetRatio REAL, FOREIGN KEY(windowId) REFERENCES Window(id) ON DELETE CASCADE)")
            db.execSQL("CREATE UNIQUE INDEX index_PageManager_windowId ON PageManager(windowId)")
            db.execSQL("INSERT INTO Workspace VALUES ('Study', 7)")
            db.execSQL("INSERT INTO Window VALUES (7, 1, 0, 0, 11, 0, 'MAXIMISED', 1.0)")
            db.execSQL("INSERT INTO PageManager (windowId, currentCategoryName, bible_document, bible_verse_versification, bible_verse_bibleBook, bible_verse_chapterNo, bible_verse_verseNo) VALUES (11, 'BIBLE', 'KJV', 'KJV', 1, 1, 1)")
            db.execSQL("INSERT INTO HistoryItem VALUES (11, 123, 'KJV', 'Gen.1.1', NULL, 1)")
            db.execSQL("CREATE TABLE bookmark (_id INTEGER PRIMARY KEY, `key` TEXT)")
            db.execSQL("INSERT INTO bookmark VALUES (1, 'Gen.1.2')")
            db.execSQL("CREATE TABLE mynote (_id INTEGER PRIMARY KEY, mynote TEXT)")
            db.execSQL("INSERT INTO mynote VALUES (1, 'Keep my note')")
            db.execSQL("PRAGMA user_version = 10")
            block(db)
        } finally {
            db.close()
        }
    }
}
