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

import androidx.annotation.VisibleForTesting
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import okio.FileSystem
import okio.Path

/** Timestamp columns in order of preference; a row without a log entry is dated by the first one its table has. */
private val MERGE_TIMESTAMP_COLUMNS = listOf("lastUpdatedOn", "updatedAt", "readAt", "memorizedAt", "createdAt")

/** Public only because `:app` tests (another module) cannot see `internal`. The column of [table] that dates a row that has no log entry: the first of [MERGE_TIMESTAMP_COLUMNS] in [columns], or null. */
@VisibleForTesting
@Suppress("UNUSED_PARAMETER")
fun mergeTimestampColumn(table: String, columns: List<String>): String? =
    MERGE_TIMESTAMP_COLUMNS.firstOrNull { it in columns }

/**
 * Writes a consistent copy of the category's local database to [target] (the copy method of the initial upload)
 * and gives every synced row of the copy that has no `LogEntry` an `UPSERT` entry from this device, dated by
 * [mergeTimestampColumn] (0 when the table has none). Existing entries, `DELETE`s included, are kept.
 * The local database itself is not modified apart from the `VACUUM` that makes the file copy consistent.
 *
 * With [source] (an already consistent copy of the database, e.g. the merge's pristine copy) no `VACUUM` is run and
 * the copy is made from that file instead of the live database.
 */
suspend fun prepareMergeSnapshot(dbDef: SyncableDatabaseAccessor<*>, target: Path, source: Path? = null) {
    if (source == null) {
        dbDef.localDb.useWriterConnectionMarked { it.exec("VACUUM;") }
        FileSystem.SYSTEM.copy(dbDef.localDbPath.toPath(), target)
    } else {
        FileSystem.SYSTEM.copy(source, target)
    }
    openSqlite(target.toString()).use { conn ->
        conn.exec("BEGIN")
        try {
            for (table in dbDef.tableDefinitions) {
                val columns = conn.queryRows("SELECT name FROM pragma_table_info(?)", table.tableName) { it.getText(0) }
                val timestamp = mergeTimestampColumn(table.tableName, columns)
                    ?.let { "COALESCE(CAST(t.$it AS INTEGER), 0)" } ?: "0"
                val id2 = table.idField2?.let { "t.$it" } ?: "''"
                conn.exec(
                    "INSERT OR IGNORE INTO LogEntry (tableName, entityId1, entityId2, type, lastUpdated, sourceDevice) " +
                        "SELECT ?, t.${table.idField1}, $id2, 'UPSERT', $timestamp, ? FROM ${table.tableName} t",
                    table.tableName, dbDef.deviceId,
                )
            }
            conn.exec("COMMIT")
        } catch (e: Throwable) {
            runCatching { conn.exec("ROLLBACK") }
            throw e
        }
    }
}

private fun String.toPath(): Path = with(okio.Path.Companion) { this@toPath.toPath() }
