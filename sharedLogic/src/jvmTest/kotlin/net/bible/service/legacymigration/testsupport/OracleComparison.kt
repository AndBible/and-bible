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

/**
 * A documented, accepted deviation of the migrated database from the Swift exporter's, for one column of one table.
 * [kind] says how narrow the approval is; prefer [Kind.NULL_EQUALS_EMPTY_COLLECTION] over skipping a whole column.
 */
data class ApprovedDifference(val table: String, val column: String, val reason: String, val kind: Kind = Kind.WHOLE_COLUMN) {
    enum class Kind {
        /** The column is not compared at all. */
        WHOLE_COLUMN,

        /** A NULL on one side equals an empty JSON collection (`[]` or `{}`) on the other; every other value is compared. */
        NULL_EQUALS_EMPTY_COLLECTION,
    }
}

/** Which side an [ApprovedRow] difference is on. */
enum class RowSide {
    /** The row exists only in the staged database. */
    ONLY_IN_STAGED,

    /** The row exists only in the oracle (is missing from the staged database). */
    ONLY_IN_ORACLE,
}

/**
 * An expected extra staged row, or an expected oracle row that the migration does not produce, identified by [table]
 * and [primaryKey] exactly as it appears in a difference message (`column=cell,column=cell`, for example
 * `id=i:1`). Used where the migration deliberately differs from the Swift export in whole rows.
 */
data class ApprovedRow(val table: String, val primaryKey: String, val side: RowSide, val reason: String)

/** Text columns that hold JSON and are compared as parsed JSON (object key order not significant), by table. Every other text cell is compared byte-exactly. */
val KNOWN_JSON_COLUMNS: Map<String, Set<String>> = mapOf(
    "Workspace" to setOf(
        "text_display_settings_bookmarksHideLabels", "workspace_settings_speakSettings", "workspace_settings_recentLabels",
        "workspace_settings_autoAssignLabels", "workspace_settings_studyPadCursors", "workspace_settings_hideCompareDocuments",
    ),
    "PageManager" to setOf("text_display_settings_bookmarksHideLabels"),
    "GlobalTextDisplaySettings" to setOf("text_display_settings_bookmarksHideLabels"),
)

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
 * row on its primary key over all columns, values typed (an integer 1 is not a real 1.0, a blob is not text). Text cells
 * are compared byte-exactly, except the columns in [KNOWN_JSON_COLUMNS], which are compared as parsed JSON (object key
 * order is not significant, array order is). [approved] accepts documented column deviations (a whole column, or only
 * NULL versus an empty collection); [approvedRows] accepts expected extra or missing whole rows. A category the
 * fixture has no oracle for yields a difference rather than silently passing.
 */
suspend fun compareWithOracle(
    fixture: FixtureDir,
    category: LegacyCategory,
    staging: StagingDatabases,
    approved: Set<ApprovedDifference> = emptySet(),
    approvedRows: Set<ApprovedRow> = emptySet(),
): List<String> = compareWithOracleDetailed(fixture, category, staging, approved, approvedRows).differences

/** [compareWithOracle] plus the per-table count of compared oracle rows. */
suspend fun compareWithOracleDetailed(
    fixture: FixtureDir,
    category: LegacyCategory,
    staging: StagingDatabases,
    approved: Set<ApprovedDifference> = emptySet(),
    approvedRows: Set<ApprovedRow> = emptySet(),
): OracleComparison {
    val source = ORACLE_SOURCES.getValue(category)
    val work = Files.createTempDirectory("oracle-").toFile()
    try {
        val file = locateOracle(fixture, source, work)
            ?: return OracleComparison(listOf("$category: fixture ${fixture.label()} has no oracle database ${source.fileName}"), emptyMap())
        val spec = CURRENT_DB_SPECS.first { it.fqn == source.database }
        val oracleDb = spec.open(file.parentFile.path, file.name)
        try {
            return compareDatabases(category, oracleDb, staging.openedDatabase(source.fileName), approved, approvedRows)
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
    approvedRows: Set<ApprovedRow>,
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
        val skip = approved.filter { it.table == table && it.kind == ApprovedDifference.Kind.WHOLE_COLUMN }.map { it.column }.toSet()
        val nullEmpty = approved.filter { it.table == table && it.kind == ApprovedDifference.Kind.NULL_EQUALS_EMPTY_COLLECTION }.map { it.column }.toSet()
        val json = KNOWN_JSON_COLUMNS[table].orEmpty()
        if (oracleRows.columns != stagedRows.columns) {
            differences += "$category.$table: columns differ, oracle ${oracleRows.columns} staged ${stagedRows.columns}"
            continue
        }
        for ((key, row) in oracleRows.rows) {
            val other = stagedRows.rows[key]
            if (other == null) {
                if (approvedRows.none { it.table == table && it.primaryKey == key && it.side == RowSide.ONLY_IN_ORACLE }) {
                    differences += "$category.$table[$key]: missing in staged database"
                }
                continue
            }
            oracleRows.columns.forEachIndexed { i, column ->
                if (column !in skip && !sameCell(row[i], other[i], column in json, column in nullEmpty)) {
                    differences += "$category.$table[$key].$column: oracle ${row[i]} staged ${other[i]}"
                }
            }
        }
        for (key in stagedRows.rows.keys - oracleRows.rows.keys) {
            if (approvedRows.none { it.table == table && it.primaryKey == key && it.side == RowSide.ONLY_IN_STAGED }) {
                differences += "$category.$table[$key]: only in staged database"
            }
        }
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
                    SQLITE_DATA_TEXT -> st.getBlob(i).let { bytes ->
                        // The stored bytes, so an embedded NUL is compared too (getText would stop at it).
                        val text = bytes.decodeToString()
                        if (text.encodeToByteArray().contentEquals(bytes)) "t:$text" else "t!" + bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    }
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

internal fun sameCell(a: String, b: String, isJson: Boolean, nullEqualsEmpty: Boolean): Boolean {
    if (a == b) return true
    if (nullEqualsEmpty && ((a == "null" && isEmptyCollection(b)) || (b == "null" && isEmptyCollection(a)))) return true
    if (!isJson || !a.startsWith("t:") || !b.startsWith("t:")) return false
    val ja = parseJsonContainer(a.substring(2)) ?: return false
    val jb = parseJsonContainer(b.substring(2)) ?: return false
    return canonical(ja) == canonical(jb)
}

private fun isEmptyCollection(cell: String) = cell == "t:[]" || cell == "t:{}"

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
