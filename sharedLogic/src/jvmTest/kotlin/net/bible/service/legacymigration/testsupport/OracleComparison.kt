package net.bible.service.legacymigration.testsupport

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.shareddata.fixtures.CURRENT_DB_SPECS

/** A documented, accepted deviation of the migrated database from the Swift exporter's: one column of one table, skipped in the comparison. */
data class ApprovedDifference(val table: String, val column: String, val reason: String)

/**
 * The outcome of [compareWithOracleDetailed]: the readable [differences] and, per table, how many oracle rows were
 * compared. The counts let a test prove that a green result compared something (two empty databases are always equal).
 */
data class OracleComparison(val differences: List<String>, val comparedRows: Map<String, Int>)

/** Tables that are not migrated data: sync bookkeeping, Room's identity table and SQLite's own `sqlite_*` tables. */
private val IGNORED_TABLES = setOf("LogEntry", "SyncStatus", "SyncConfiguration", "room_master_table")

private class OracleSource(val database: String, val fileName: String, val abdbMember: String?, val standalone: String?)

private val ORACLE_SOURCES = mapOf(
    LegacyCategory.WORKSPACES to OracleSource("net.bible.android.database.WorkspaceDatabase", "workspaces.sqlite3", "db/workspaces.sqlite3", null),
    LegacyCategory.BOOKMARKS to OracleSource("net.bible.android.database.BookmarkDatabase", "bookmarks.sqlite3", "db/bookmarks.sqlite3", null),
    LegacyCategory.READING_PLANS to OracleSource("net.bible.android.database.ReadingPlanDatabase", "readingplans.sqlite3", "db/readingplans.sqlite3", null),
    LegacyCategory.PROGRESS to OracleSource("net.bible.android.database.progress.ProgressDatabase", "progress.sqlite3", "db/progress.sqlite3", null),
    LegacyCategory.MY_DOCUMENTS to OracleSource("net.bible.android.database.mydocument.MyDocumentDatabase", "mydocuments.sqlite3", "db/mydocuments.sqlite3", null),
    // The AI oracle of the `preserved-ai` fixtures is a separate file next to the `.abdb.zip`, which has no AI entry.
    LegacyCategory.AI_SETTINGS to OracleSource("net.bible.android.database.AiSettingsDatabase", "ai_settings.sqlite3", "db/ai_settings.sqlite3", "oracle/ai_settings.sqlite3"),
    LegacyCategory.SETTINGS to OracleSource("net.bible.android.database.SettingsDatabase", "settings.sqlite3", "db/settings.sqlite3", null),
    LegacyCategory.REPOSITORIES to OracleSource("net.bible.android.database.RepoDatabase", "repositories.sqlite3", "db/repositories.sqlite3", null),
)

/**
 * Compares the staged database of [category] with the Swift app's own export of the same store (the fixture's
 * `oracle` `.abdb.zip` member, or `oracle/ai_settings.sqlite3` for AI settings). Returns one readable line per
 * difference; an empty list means identical.
 *
 * The oracle file is copied to a temporary directory and opened through the current Room database with the current
 * migrations array (`CURRENT_DB_SPECS`), so an oracle written at an older schema is upgraded first. Every table
 * except `LogEntry`, `SyncStatus`, `SyncConfiguration`, `room_master_table` and SQLite's own tables is compared row by
 * row on its primary key over all columns, values typed (an integer 1 is not a real 1.0, a blob is not text). A text
 * cell that is a JSON object or array on both sides is compared as parsed JSON (object key order is not significant);
 * array order is. A column listed in [approved] for its table is not compared. A category the fixture has no oracle for
 * yields a difference rather than silently passing.
 */
suspend fun compareWithOracle(
    fixture: FixtureDir,
    category: LegacyCategory,
    staging: StagingDatabases,
    approved: Set<ApprovedDifference> = emptySet(),
): List<String> = compareWithOracleDetailed(fixture, category, staging, approved).differences

/** [compareWithOracle] plus the per-table count of compared oracle rows. */
suspend fun compareWithOracleDetailed(
    fixture: FixtureDir,
    category: LegacyCategory,
    staging: StagingDatabases,
    approved: Set<ApprovedDifference> = emptySet(),
): OracleComparison {
    val source = ORACLE_SOURCES.getValue(category)
    val work = Files.createTempDirectory("oracle-").toFile()
    try {
        val file = locateOracle(fixture, source, work)
            ?: return OracleComparison(listOf("$category: fixture ${fixture.label()} has no oracle database ${source.fileName}"), emptyMap())
        val spec = CURRENT_DB_SPECS.first { it.fqn == source.database }
        val oracleDb = spec.open(file.parentFile.path, file.name)
        try {
            return compareDatabases(category, oracleDb, staging.openedDatabase(source.fileName), approved)
        } finally {
            oracleDb.close()
        }
    } finally {
        work.deleteRecursively()
    }
}

private fun FixtureDir.label() = "$app/$ios/$scenario"

private fun locateOracle(fixture: FixtureDir, source: OracleSource, work: File): File? {
    val target = File(work, source.fileName)
    source.standalone?.let { rel ->
        val f = File(fixture.root.toFile(), rel)
        if (f.isFile) return f.copyTo(target)
    }
    val abdb = File(fixture.root.toFile(), "oracle").listFiles { f -> f.name.endsWith(".abdb.zip") }.orEmpty().firstOrNull() ?: return null
    ZipFile(abdb).use { zip ->
        val entry = source.abdbMember?.let { zip.getEntry(it) } ?: return null
        zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
    }
    return target
}

private suspend fun compareDatabases(
    category: LegacyCategory,
    oracle: RoomDatabase,
    staged: RoomDatabase?,
    approved: Set<ApprovedDifference>,
): OracleComparison {
    val differences = mutableListOf<String>()
    val compared = linkedMapOf<String, Int>()
    val oracleTables = oracle.tableNames()
    val stagedTables = staged?.tableNames().orEmpty()
    for (table in (oracleTables + stagedTables).toSortedSet()) {
        if (table in IGNORED_TABLES || table.startsWith("sqlite_")) continue
        if (table !in oracleTables) { differences += "$category.$table: table only in staged database"; continue }
        val oracleRows = oracle.rows(table)
        compared[table] = oracleRows.rows.size
        if (staged == null || table !in stagedTables) {
            if (oracleRows.rows.isNotEmpty()) differences += "$category.$table: staged database has no such table, oracle has ${oracleRows.rows.size} rows"
            continue
        }
        val stagedRows = staged.rows(table)
        val skip = approved.filter { it.table == table }.map { it.column }.toSet()
        if (oracleRows.columns != stagedRows.columns) {
            differences += "$category.$table: columns differ, oracle ${oracleRows.columns} staged ${stagedRows.columns}"
            continue
        }
        for ((key, row) in oracleRows.rows) {
            val other = stagedRows.rows[key]
            if (other == null) { differences += "$category.$table[$key]: missing in staged database"; continue }
            oracleRows.columns.forEachIndexed { i, column ->
                if (column !in skip && !sameCell(row[i], other[i])) {
                    differences += "$category.$table[$key].$column: oracle ${row[i]} staged ${other[i]}"
                }
            }
        }
        for (key in stagedRows.rows.keys - oracleRows.rows.keys) differences += "$category.$table[$key]: only in staged database"
    }
    return OracleComparison(differences, compared)
}

/** One table's rows by primary key; a cell is `null`, `i:<long>`, `f:<double>`, `t:<text>` or `b:<hex>`. */
private class TableRows(val columns: List<String>, val rows: Map<String, List<String>>)

private suspend fun RoomDatabase.tableNames(): List<String> = useReaderConnection { c ->
    c.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") { st ->
        buildList { while (st.step()) add(st.getText(0)) }
    }
}

private suspend fun RoomDatabase.rows(table: String): TableRows = useReaderConnection { c ->
    val info = c.usePrepared("PRAGMA table_info(`$table`)") { st ->
        buildList { while (st.step()) add(Triple(st.getText(1), st.getLong(5).toInt(), st.getText(2))) }
    }
    val columns = info.map { it.first }
    val keyColumns = info.filter { it.second > 0 }.sortedBy { it.second }.map { columns.indexOf(it.first) }.ifEmpty { columns.indices.toList() }
    c.usePrepared("SELECT ${columns.joinToString(", ") { "`$it`" }} FROM `$table`") { st ->
        val rows = linkedMapOf<String, List<String>>()
        while (st.step()) {
            val cells = columns.indices.map { i ->
                when (st.getColumnType(i)) {
                    SQLITE_DATA_NULL -> "null"
                    SQLITE_DATA_INTEGER -> "i:${st.getLong(i)}"
                    SQLITE_DATA_FLOAT -> "f:${st.getDouble(i)}"
                    SQLITE_DATA_TEXT -> "t:${st.getText(i)}"
                    SQLITE_DATA_BLOB -> "b:" + st.getBlob(i).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    else -> error("unknown column type")
                }
            }
            val key = keyColumns.joinToString(",") { "${columns[it]}=${cells[it]}" }
            check(rows.put(key, cells) == null) { "duplicate primary key $key in $table" }
        }
        TableRows(columns, rows)
    }
}

private fun sameCell(a: String, b: String): Boolean {
    if (a == b) return true
    if (!a.startsWith("t:") || !b.startsWith("t:")) return false
    val ja = parseJsonContainer(a.substring(2)) ?: return false
    val jb = parseJsonContainer(b.substring(2)) ?: return false
    return canonical(ja) == canonical(jb)
}

private fun parseJsonContainer(text: String): JsonElement? {
    val t = text.trimStart()
    if (!t.startsWith("{") && !t.startsWith("[")) return null
    return try { Json.parseToJsonElement(text) } catch (e: Exception) { null }
}

private fun canonical(e: JsonElement): String = when (e) {
    is JsonObject -> e.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "\"${it.key}\":${canonical(it.value)}" }
    is JsonArray -> e.joinToString(",", "[", "]") { canonical(it) }
    else -> e.toString()
}
