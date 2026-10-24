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

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.migrations.oldMonolithicAppDatabaseMigrations
import net.bible.service.common.CommonUtils
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.textOrNull
import net.bible.service.db.oldmigrations.ReadingPlanDatabaseOperations
import net.bible.service.db.oldmigrations.oldMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The legacy pre-split chain (`oldMonolithicAppDatabaseMigrations` + `oldMigrations`) on Room 2.8.4 with the
 * bundled driver, which is what the app runs it on after D1: every exported old version reaches
 * [OLD_DATABASE_VERSION], and the data-moving migrations (33->34 bookmarks, 37->38 my notes, 38->39 label
 * colours, 53->54 shared preferences) produce the rows they did on the framework API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OldMonolithicMigrationChainTest {
    private val schemaDir = "net.bible.android.database.OldMonolithicAppDatabase"

    /**
     * Exports of 41, 46 and 50 were taken after the column the next migration adds (cipherKey, primaryLabelId,
     * underlineStyle) was already in the entity, so a database built from them cannot take that migration.
     * Real databases at those versions lack the column; the fixtures are wrong, not the migrations.
     */
    private val EXPORTS_WITH_NEXT_COLUMN = setOf(41, 46, 50)

    private fun open(name: String): RoomDatabase =
        Room.databaseBuilder(application, OldMonolithicAppDatabase::class.java, name)
            .addMigrations(*oldMonolithicAppDatabaseMigrations, *oldMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun migrate(name: String) {
        val db = open(name)
        try { runBlocking { db.useWriterConnection { } } } finally { db.close() }
    }

    private fun userVersion(file: File) = openSqlite(file.path, readOnly = true).use { it.queryRows("PRAGMA user_version") { st -> st.getLong(0) }.single() }

    @Test fun everyExportedVersionReachesTheLastOldVersion() {
        val failures = mutableListOf<String>()
        val versions = SchemaExportFixtures.exportedVersions(schemaDir).filter { it < OLD_DATABASE_VERSION && it !in EXPORTS_WITH_NEXT_COLUMN }
        assertTrue("expected the exports 33..68 (minus the skipped)", versions.first() == 33 && versions.last() == 68 && versions.size == 36 - 3)
        for (v in versions) {
            val name = "old-chain-$v.sqlite3"
            application.deleteDatabase(name)
            val file = application.getDatabasePath(name)
            SchemaExportFixtures.createFromExport(schemaDir, v, file)
            try {
                migrate(name)
                assertEquals(OLD_DATABASE_VERSION.toLong(), userVersion(file))
            } catch (e: Throwable) {
                failures += "v$v: $e"
            } finally { application.deleteDatabase(name) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun dataMovingMigrationsKeepTheirRows() {
        val name = "old-chain-data.sqlite3"
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        SchemaExportFixtures.createFromExport(schemaDir, 33, file)
        val prefs = CommonUtils.realSharedPreferences
        prefs.edit().clear().putLong("pl", 5L).putInt("pi", 6).putBoolean("pb", true).putString("ps", "str").putFloat("pf", 1.5f).apply()
        openSqlite(file.path).use { db ->
            fun sql(s: String) = db.prepare(s).use { it.step() }
            sql("INSERT INTO bookmark (_id, created_on, key, versification, speak_settings) VALUES (1, 111, 'Gen.1.1', NULL, 'speak')")
            sql("INSERT INTO bookmark (_id, created_on, key, versification, speak_settings) VALUES (2, 112, 'not a verse', 'KJV', NULL)")
            sql("INSERT INTO label (_id, name, bookmark_style) VALUES (1, 'Lab', 'SPEAK')")
            sql("INSERT INTO label (_id, name, bookmark_style) VALUES (2, 'NoStyle', NULL)")
            sql("INSERT INTO bookmark_label (bookmark_id, label_id) VALUES (1, 1)")
            sql("INSERT INTO mynote (_id, key, versification, mynote, last_updated_on, created_on) VALUES (1, 'Gen.1.2', NULL, 'my note', 222, 111)")
            sql("INSERT INTO mynote (_id, key, versification, mynote, last_updated_on, created_on) VALUES (2, 'bad key', 'KJV', 'dropped', 1, 1)")
        }
        migrate(name)
        openSqlite(file.path, readOnly = true).use { db ->
            // 33->34 copies bookmarks that parse (the id is kept), 37->38 appends the parsable my notes
            val bookmarks = db.queryRows("SELECT id, createdAt, lastUpdatedOn, notes, playbackSettings, v11n FROM Bookmark ORDER BY id") {
                listOf(it.getLong(0), it.getLong(1), it.getLong(2), it.textOrNull(3), it.textOrNull(4), it.getText(5))
            }
            assertEquals(listOf(
                listOf<Any?>(1L, 111L, 111L, null, "speak", "KJV"),   // lastUpdatedOn = createdAt (37->38)
                listOf<Any?>(2L, 111L, 222L, "my note", null, "KJV"),
            ), bookmarks)
            // 38->39: SPEAK label renamed and coloured, the style-less one gets the BLUE_HIGHLIGHT default
            val labels = db.queryRows("SELECT id, name, color FROM Label ORDER BY id") { Triple(it.getLong(0), it.getText(1), it.getLong(2)) }
            assertEquals("NoStyle", labels[1].second)
            assertEquals(net.bible.android.database.bookmarks.BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor.toLong(), labels[1].third)
            assertEquals(net.bible.android.database.bookmarks.BookmarkStyle.SPEAK.backgroundColor.toLong(), labels[0].third)
            // the migrated-my-notes label is linked to the my-note bookmark only
            val migratedLabel = labels.single { it.second == application.getString(net.bible.android.activity.R.string.migrated_my_notes) }.first
            assertEquals(listOf(2L to migratedLabel),
                db.queryRows("SELECT bookmarkId, labelId FROM BookmarkToLabel WHERE labelId = ?", migratedLabel) { it.getLong(0) to it.getLong(1) })
            // 53->54: shared preferences land in the typed setting tables
            fun setting(table: String, key: String) = db.queryRows("SELECT value FROM $table WHERE key = ?", key) { it.getText(0) }
            assertEquals(listOf("5"), setting("LongSetting", "pl"))
            assertEquals(listOf("6"), setting("LongSetting", "pi"))
            assertEquals(listOf("1"), setting("BooleanSetting", "pb"))
            assertEquals(listOf("str"), setting("StringSetting", "ps"))
            assertEquals(listOf("1.5"), setting("DoubleSetting", "pf"))
        }
        application.deleteDatabase(name)
    }

    @Test fun readingPlanPrefsMigrationFillsBothTables() {
        val name = "old-chain-plans.sqlite3"
        application.deleteDatabase(name)
        val code = org.koin.core.context.GlobalContext.get().get<net.bible.service.readingplan.ReadingPlanTextFileDao>().internalPlanCodes.first()
        CommonUtils.realSharedPreferences.edit().clear().putLong("${code}_start", 1234L).putInt("${code}_day", 2).putString("${code}_2", "101").apply()
        openSqlite(application.getDatabasePath(name).path).use { db ->
            ReadingPlanDatabaseOperations.instance.onCreate(db)
            ReadingPlanDatabaseOperations.instance.migratePrefsToDatabase(db)
            assertEquals(listOf(listOf<Any?>(code, 1234L, 2L)),
                db.queryRows("SELECT plan_code, plan_start_date, plan_current_day FROM readingplan") { listOf(it.getText(0), it.getLong(1), it.getLong(2)) })
            assertEquals(listOf(listOf<Any?>(code, 2L)),
                db.queryRows("SELECT plan_code, plan_day FROM readingplan_status") { listOf(it.getText(0), it.getLong(1)) })
        }
        application.deleteDatabase(name)
    }
}
