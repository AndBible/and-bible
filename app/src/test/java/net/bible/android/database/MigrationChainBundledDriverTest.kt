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

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.migrations.workspacesMigrations
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the migration chains [MigrationChainTest] has to skip ([NEEDS_MODERN_SQLITE]: they contain
 * `ALTER TABLE ... DROP COLUMN`, which Robolectric's framework SQLite rejects) through Room 2.8.4
 * opened on the bundled SQLite driver, with the production migration arrays. Each old version is built
 * from its schema export, migrated to the current version, and must end with the current
 * `user_version` and the identity hash of the current export (Room's own schema validation runs too).
 *
 * TEMPORARY: removed in D1 Task 17, when production itself uses the bundled driver and
 * [MigrationChainTest] covers these versions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MigrationChainBundledDriverTest {
    private class Subject(
        val schemaDir: String,
        val currentVersion: Int,
        val build: (path: String) -> RoomDatabase,
    )

    private val subjects = listOf(
        Subject("net.bible.android.database.BookmarkDatabase", net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION) {
            Room.databaseBuilder(application, BookmarkDatabase::class.java, it)
                .addMigrations(*bookmarkMigrations)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .setDriver(BundledSQLiteDriver())
                .build()
        },
        Subject("net.bible.android.database.WorkspaceDatabase", net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION) {
            Room.databaseBuilder(application, WorkspaceDatabase::class.java, it)
                .addMigrations(*workspacesMigrations)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .setDriver(BundledSQLiteDriver())
                .build()
        },
    )

    @Test
    fun dropColumnChainsReachCurrentVersionOnBundledDriver() {
        val failures = mutableListOf<String>()
        var tested = 0
        for (s in subjects) {
            val currentHash = SchemaExportFixtures.identityHash(s.schemaDir, s.currentVersion)
            for (v in NEEDS_MODERN_SQLITE.getValue(s.schemaDir).sorted()) {
                val name = "bundled-chain-${s.schemaDir}-$v.sqlite3"
                application.deleteDatabase(name)
                val file = application.getDatabasePath(name)
                SchemaExportFixtures.createFromExport(s.schemaDir, v, file)
                try {
                    val db = s.build(file.absolutePath)
                    try {
                        val (version, hash) = runBlocking {
                            db.useWriterConnection { t ->
                                t.usePrepared("PRAGMA user_version") { st -> st.step(); st.getLong(0).toInt() } to
                                    t.usePrepared("SELECT identity_hash FROM room_master_table WHERE id = 42") { st -> st.step(); st.getText(0) }
                            }
                        }
                        assertEquals("${s.schemaDir} v$v user_version", s.currentVersion, version)
                        assertEquals("${s.schemaDir} v$v identity hash", currentHash, hash)
                        tested++
                    } finally {
                        db.close()
                    }
                } catch (e: Throwable) {
                    failures += "${s.schemaDir} v$v: ${e::class.simpleName}: ${e.message?.take(300)}"
                }
            }
        }
        println("MigrationChainBundledDriverTest: $tested old versions migrated, ${failures.size} failed")
        assertEquals(failures.joinToString("\n"), 0, failures.size)
        assertEquals(NEEDS_MODERN_SQLITE.values.sumOf { it.size }, tested)
    }
}
