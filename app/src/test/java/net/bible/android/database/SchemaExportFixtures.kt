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

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.migrations.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.mydocument.MY_DOCUMENT_DATABASE_VERSION
import net.bible.android.database.progress.PROGRESS_DATABASE_VERSION
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.productionConfig
import net.bible.service.sword.epub.getEpubDatabase
import java.io.File

/**
 * Builds database files from the committed Room schema exports in `app/schemas/`.
 * Unit tests run with the module directory (`app/`) as the working directory.
 */
object SchemaExportFixtures {
    private fun export(dbClass: String, version: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/$dbClass/$version.json").readText()).jsonObject["database"]!!.jsonObject

    fun exportedVersions(dbClass: String): List<Int> =
        File("schemas/$dbClass").listFiles()!!.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()

    fun identityHash(dbClass: String, version: Int): String =
        export(dbClass, version)["identityHash"]!!.jsonPrimitive.content

    /** Executes every CREATE of [version], writes room_master_table like Room does, stamps user_version. */
    fun createFromExport(dbClass: String, version: Int, file: File) {
        val schema = export(dbClass, version)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                raw.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach {
                    raw.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]?.jsonArray?.forEach { view ->
                val v = view.jsonObject
                raw.execSQL(v["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", v["viewName"]!!.jsonPrimitive.content))
            }
            raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            raw.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf<Any?>(schema["identityHash"]!!.jsonPrimitive.content),
            )
            raw.version = version
        }
    }
}

/** Opens the file (running any migrations) and reads `room_master_table`'s identity hash. */
fun RoomDatabase.identityHash(): String = runBlocking {
    useReaderConnection { c ->
        c.usePrepared("SELECT identity_hash FROM room_master_table WHERE id = 42") { st -> st.step(); st.getText(0) }
    }
}

/**
 * One Room database exercised by the schema tests. [open] must go through the production builder
 * (or [productionConfig] with the production migrations) so the tests see the same driver, migrations and
 * journal mode as the app.
 */
data class DbUnderTest(val schemaDir: String, val currentVersion: Int, val open: (fileName: String) -> RoomDatabase)

/**
 * Databases without a `getXDb(name)` factory in `DatabaseContainer` (Temporary, DocumentSync, Repo, Settings) are
 * built here with the production [productionConfig] and the same migrations array as their production builder
 * (`DatabaseContainer.kt`). The migrations passed must stay identical to production.
 */
private inline fun <reified T : RoomDatabase> copyOfProductionBuilder(
    name: String, vararg migrations: androidx.room3.migration.Migration,
): T = Room.databaseBuilder<T>(application, name)
    .productionConfig()
    .addMigrations(*migrations)
    .build()

val DB_UNDER_TEST: List<DbUnderTest> = listOf(
    DbUnderTest("net.bible.android.database.BookmarkDatabase", BOOKMARK_DATABASE_VERSION) {
        DatabaseContainer.instance.getBookmarkDb(it)
    },
    DbUnderTest("net.bible.android.database.ReadingPlanDatabase", READING_PLAN_DATABASE_VERSION) {
        DatabaseContainer.instance.getReadingPlanDb(it)
    },
    DbUnderTest("net.bible.android.database.WorkspaceDatabase", WORKSPACE_DATABASE_VERSION) {
        DatabaseContainer.instance.getWorkspaceDb(it)
    },
    DbUnderTest("net.bible.android.database.mydocument.MyDocumentDatabase", MY_DOCUMENT_DATABASE_VERSION) {
        DatabaseContainer.instance.getMyDocumentDb(it)
    },
    DbUnderTest("net.bible.android.database.AiSettingsDatabase", AI_SETTINGS_DATABASE_VERSION) {
        DatabaseContainer.instance.getAiSettingsDb(it)
    },
    DbUnderTest("net.bible.android.database.progress.ProgressDatabase", PROGRESS_DATABASE_VERSION) {
        DatabaseContainer.instance.getProgressDb(it)
    },
    DbUnderTest("net.bible.android.database.TemporaryDatabase", TEMPORARY_DATABASE_VERSION) {
        copyOfProductionBuilder<TemporaryDatabase>(it, *temporaryMigrations)
    },
    DbUnderTest("net.bible.android.database.DocumentSyncDatabase", DOCUMENT_SYNC_DATABASE_VERSION) {
        copyOfProductionBuilder<DocumentSyncDatabase>(it)
    },
    DbUnderTest("net.bible.android.database.RepoDatabase", REPO_DATABASE_VERSION) {
        copyOfProductionBuilder<RepoDatabase>(it)
    },
    DbUnderTest("net.bible.android.database.SettingsDatabase", SETTINGS_DATABASE_VERSION) {
        copyOfProductionBuilder<SettingsDatabase>(it)
    },
    DbUnderTest("net.bible.android.database.EpubDatabase", EPUB_DATABASE_VERSION) {
        getEpubDatabase(it)
    },
)
