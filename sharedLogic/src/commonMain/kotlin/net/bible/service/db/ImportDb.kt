/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.db

import kotlinx.coroutines.IO
import androidx.room3.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.cloudsync.canonical.CANONICAL_KEYS
import net.bible.service.cloudsync.canonical.healCanonicalIds
import net.bible.service.cloudsync.canonical.rekeyToCanonical
import net.bible.service.cloudsync.TRIGGERS_DISABLED_KEY
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.CoreStrings
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val TAG = "ImportDb"

suspend fun bookmarksDbStats(category: SyncableDatabaseDefinition, dbFile: Path, strings: CoreStrings): String = withContext(Dispatchers.IO) {
    val dbDef = category.accessor
    val importDbFile = dbDef.dbFactory(dbFile.toString())
    try {
        importDbFile.useReaderConnectionMarked {
            it.run {
                val firstLabel = queryRows("""SELECT name from Label WHERE name NOT LIKE '\_\_%' ESCAPE '\'""") { st -> st.getText(0) }.firstOrNull() ?: '-'
                val labels = queryLong("""SELECT count(*) from Label""")!!
                val bookmarks =
                    queryLong("""SELECT count(*) from BibleBookmark""")!! +
                        queryLong("""SELECT count(*) from GenericBookmark""")!!
                strings.bookmarksDbStats(firstLabel.toString(), (labels - 1).toString(), bookmarks.toString())
            }
        }
    } finally {
        importDbFile.close()
    }
}

/**
 * Copies every synced table of [dbFile] (a database of [category]'s kind, migrated to the current schema first)
 * into the local database with `INSERT OR IGNORE`: rows already present locally win. One transaction; a failure
 * rolls the whole import back and is rethrown.
 *
 * For a category with canonical tables the copy is read from a temporary re-keyed copy of [dbFile] ([rekeyedCopy]):
 * a file made by an older version holds its books and models under random ids, and `INSERT OR IGNORE` would drop one
 * that clashes with the local canonical row on its natural key and leave its children without a parent (#3906).
 */
suspend fun importDatabaseFile(category: SyncableDatabaseDefinition, dbFile: Path) = withContext(Dispatchers.IO) {
    val dbDef = category.accessor
    val importDbFile = dbDef.dbFactory(dbFile.toString())
    try { importDbFile.useWriterConnection { } } finally { importDbFile.close() }
    val source = if (category in CANONICAL_KEYS) rekeyedCopy(category, dbFile) else dbFile
    try {
        dbDef.localDb.useWriterConnectionMarked { db ->
            // ATTACH inside the protected region, so DETACH always runs once it may have succeeded.
            db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE import") {
                db.exec("ATTACH DATABASE '${source.toString()}' AS import")
                db.exec("PRAGMA foreign_keys=OFF;")
                try {
                    db.inTransaction<Unit> {
                        for (tableDef in dbDef.tableDefinitions) {
                            val table = tableDef.tableName
                            val cols = columnNamesJoined(table)
                            exec("""
                                INSERT OR IGNORE INTO $table ($cols)
                                SELECT $cols FROM import.$table 
                            """.trimIndent())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurred in importDatabaseFile", e)
                    throw e
                }
            }
        }
    } finally {
        if (source != dbFile) for (suffix in listOf("", "-journal", "-wal", "-shm")) {
            runCatching { FileSystem.SYSTEM.delete("$source$suffix".toPath(), mustExist = false) }
        }
    }
    // The file's rows may still clash with local rows that have random ids; outside the transaction above (the heal opens
    // its own); categories without canonical tables are left alone.
    if (category in CANONICAL_KEYS) healCanonicalIds(dbDef, force = true)
}

/**
 * A temporary copy of [dbFile] next to it, re-keyed to canonical ids ([rekeyToCanonical]) in one transaction on its own
 * connection, with the sync triggers off so nothing is logged in the copy. The chosen file itself is never changed
 * (beyond the schema migration that already ran). The caller deletes the copy.
 */
private suspend fun rekeyedCopy(category: SyncableDatabaseDefinition, dbFile: Path): Path {
    val copy = "$dbFile.canonical-import".toPath()
    FileSystem.SYSTEM.delete(copy, mustExist = false)
    FileSystem.SYSTEM.copy(dbFile, copy)
    try {
        openSqlite(copy.toString()).use { conn ->
            conn.exec("PRAGMA foreign_keys = OFF")
            conn.inTransaction {
                exec("INSERT OR REPLACE INTO SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, 1)", TRIGGERS_DISABLED_KEY)
                rekeyToCanonical(this, category)
                exec("INSERT OR REPLACE INTO SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, 0)", TRIGGERS_DISABLED_KEY)
            }
        }
    } catch (e: Throwable) {
        FileSystem.SYSTEM.delete(copy, mustExist = false)
        throw e
    }
    return copy
}
