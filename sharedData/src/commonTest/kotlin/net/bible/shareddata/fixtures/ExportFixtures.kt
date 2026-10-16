package net.bible.shareddata.fixtures

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.database.AI_SETTINGS_DATABASE_VERSION
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.DOCUMENT_SYNC_DATABASE_VERSION
import net.bible.android.database.DocumentSyncDatabase
import net.bible.android.database.EPUB_DATABASE_VERSION
import net.bible.android.database.EpubDatabase
import net.bible.android.database.REPO_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SETTINGS_DATABASE_VERSION
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.TEMPORARY_DATABASE_VERSION
import net.bible.android.database.TemporaryDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.epubMigrations
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.migrations.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.migrations.myDocumentMigrations
import net.bible.android.database.migrations.progressMigrations
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.migrations.workspacesMigrations
import net.bible.android.database.mydocument.MY_DOCUMENT_DATABASE_VERSION
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.PROGRESS_DATABASE_VERSION
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.database.temporaryMigrations
import net.bible.shareddata.openTestDatabase

/**
 * One current (non-legacy) database under test. [open] goes through the same configuration and the same
 * migrations array as the production builder of that database (`DatabaseContainer`), against a bundled-driver
 * file in [open]'s directory.
 */
class CurrentDb(
    val fqn: String,
    val currentVersion: Int,
    val open: (dir: String, name: String) -> RoomDatabase,
)

/**
 * The 11 current databases. The legacy [net.bible.android.database.OldMonolithicAppDatabase] is deliberately
 * excluded: it stays tested from `:app` (R5b).
 */
val CURRENT_DB_SPECS: List<CurrentDb> = listOf(
    CurrentDb("net.bible.android.database.BookmarkDatabase", BOOKMARK_DATABASE_VERSION) { d, n ->
        openTestDatabase<BookmarkDatabase>(d, n, *bookmarkMigrations)
    },
    CurrentDb("net.bible.android.database.ReadingPlanDatabase", READING_PLAN_DATABASE_VERSION) { d, n ->
        openTestDatabase<ReadingPlanDatabase>(d, n, *readingPlanMigrations)
    },
    CurrentDb("net.bible.android.database.WorkspaceDatabase", WORKSPACE_DATABASE_VERSION) { d, n ->
        openTestDatabase<WorkspaceDatabase>(d, n, *workspacesMigrations)
    },
    CurrentDb("net.bible.android.database.mydocument.MyDocumentDatabase", MY_DOCUMENT_DATABASE_VERSION) { d, n ->
        openTestDatabase<MyDocumentDatabase>(d, n, *myDocumentMigrations)
    },
    CurrentDb("net.bible.android.database.AiSettingsDatabase", AI_SETTINGS_DATABASE_VERSION) { d, n ->
        openTestDatabase<AiSettingsDatabase>(d, n, *aiSettingsMigrations)
    },
    CurrentDb("net.bible.android.database.progress.ProgressDatabase", PROGRESS_DATABASE_VERSION) { d, n ->
        openTestDatabase<ProgressDatabase>(d, n, *progressMigrations)
    },
    CurrentDb("net.bible.android.database.TemporaryDatabase", TEMPORARY_DATABASE_VERSION) { d, n ->
        openTestDatabase<TemporaryDatabase>(d, n, *temporaryMigrations)
    },
    CurrentDb("net.bible.android.database.DocumentSyncDatabase", DOCUMENT_SYNC_DATABASE_VERSION) { d, n ->
        openTestDatabase<DocumentSyncDatabase>(d, n)
    },
    CurrentDb("net.bible.android.database.RepoDatabase", REPO_DATABASE_VERSION) { d, n ->
        openTestDatabase<RepoDatabase>(d, n)
    },
    CurrentDb("net.bible.android.database.SettingsDatabase", SETTINGS_DATABASE_VERSION) { d, n ->
        openTestDatabase<SettingsDatabase>(d, n)
    },
    CurrentDb("net.bible.android.database.EpubDatabase", EPUB_DATABASE_VERSION) { d, n ->
        openTestDatabase<EpubDatabase>(d, n, *epubMigrations)
    },
)

/** The database class FQNs of [CURRENT_DB_SPECS]. */
val CURRENT_DATABASES: List<String> = CURRENT_DB_SPECS.map { it.fqn }

private fun export(dbClass: String, version: Int): JsonObject {
    val raw = checkNotNull(SchemaExports.all[dbClass]?.get(version)) { "No schema export for $dbClass v$version" }
    return Json.parseToJsonElement(raw).jsonObject["database"]!!.jsonObject
}

fun exportedVersions(dbClass: String): List<Int> =
    checkNotNull(SchemaExports.all[dbClass]) { "No exports for $dbClass" }.keys.sorted()

fun exportedIdentityHash(dbClass: String, version: Int): String =
    export(dbClass, version)["identityHash"]!!.jsonPrimitive.content

/**
 * Common port of the app's `SchemaExportFixtures.createFromExport`: executes every CREATE (table, index, view)
 * of [version] on a bundled-driver connection to [path], writes `room_master_table` like Room does (row 42 with
 * the identity hash) and stamps `PRAGMA user_version`.
 */
fun createFromExport(dbClass: String, version: Int, path: String) {
    val schema = export(dbClass, version)
    val connection = BundledSQLiteDriver().open(path)
    try {
        schema["entities"]!!.jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            connection.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach {
                connection.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["views"]?.jsonArray?.forEach { view ->
            val v = view.jsonObject
            connection.execSQL(v["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", v["viewName"]!!.jsonPrimitive.content))
        }
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.prepare("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)").use {
            it.bindText(1, schema["identityHash"]!!.jsonPrimitive.content)
            it.step()
        }
        connection.execSQL("PRAGMA user_version = $version")
    } finally {
        connection.close()
    }
}

/** Reads Room's identity hash and the file's `user_version` from an open database. */
suspend fun RoomDatabase.storedIdentityHash(): String = useReaderConnection { c ->
    c.usePrepared("SELECT identity_hash FROM room_master_table WHERE id = 42") { st -> st.step(); st.getText(0) }
}

suspend fun RoomDatabase.userVersion(): Int = useReaderConnection { c ->
    c.usePrepared("PRAGMA user_version") { st -> st.step(); st.getInt(0) }
}

/** Raw single-value reads, bypassing every converter. */
suspend fun RoomDatabase.rawLong(sql: String): Long? = useReaderConnection { c ->
    c.usePrepared(sql) { st -> if (st.step() && !st.isNull(0)) st.getLong(0) else null }
}

suspend fun RoomDatabase.rawText(sql: String): String? = useReaderConnection { c ->
    c.usePrepared(sql) { st -> if (st.step() && !st.isNull(0)) st.getText(0) else null }
}

suspend fun RoomDatabase.rawBlob(sql: String): ByteArray? = useReaderConnection { c ->
    c.usePrepared(sql) { st -> if (st.step() && !st.isNull(0)) st.getBlob(0) else null }
}

/** Raw write that bypasses the DAOs (used to plant corrupt stored values). */
suspend fun RoomDatabase.rawExec(sql: String) {
    useWriterConnection { c -> c.usePrepared(sql) { st -> st.step() } }
}

/** Every row of [sql] with all [columns] read as text (null stays null). */
suspend fun RoomDatabase.rawRows(sql: String, columns: Int): List<List<String?>> = useReaderConnection { c ->
    c.usePrepared(sql) { st ->
        buildList {
            while (st.step()) add(List(columns) { if (st.isNull(it)) null else st.getText(it) })
        }
    }
}

/**
 * The structure of every table of an open database as a comparable set: one entry per column
 * (`table.column:type:notnull:pk`) and one per index (`table unique, columns`; index names are not compared, tables renamed by old migrations keep their old index names). Column order is not part of
 * it, because `ALTER TABLE ADD COLUMN` legitimately appends where a fresh CREATE would not. Column defaults are
 * not part of it either: the exports omit `DEFAULT NULL` and the shipped migrations add defaults that a fresh
 * CREATE of the current schema does not have (e.g. `ChapterReadHistory.cycle`), which is existing behaviour.
 */
suspend fun RoomDatabase.schemaShape(): Set<String> {
    val shape = mutableSetOf<String>()
    val tables = rawRows(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'", 1,
    ).map { it[0]!! }
    for (t in tables) {
        rawRows("PRAGMA table_info(`$t`)", 6).forEach { r ->
            shape += "$t.${r[1]}:${r[2]?.uppercase()}:${r[3]}:${r[5]}"
        }
        rawRows("PRAGMA index_list(`$t`)", 5).forEach { r ->
            if (r[3] == "c") {
                val cols = rawRows("PRAGMA index_info(`${r[1]}`)", 3).joinToString(",") { it[2]!! }
                shape += "$t index unique=${r[2]} on $cols"
            }
        }
    }
    shape += rawRows("SELECT name FROM sqlite_master WHERE type = 'view'", 1).map { "view ${it[0]}" }
    return shape
}
