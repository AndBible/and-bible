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

import android.util.Log
import androidx.room3.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.page.application
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import java.io.File
import java.lang.Exception

private const val TAG = "ImportDb"

suspend fun bookmarksDbStats(category: SyncableDatabaseDefinition, dbFile: File): String = withContext(Dispatchers.IO) {
    val dbDef = category.accessor
    val importDbFile = dbDef.dbFactory(dbFile.absolutePath)
    try {
        importDbFile.useReaderConnectionMarked {
            it.run {
                val firstLabel = queryRows("""SELECT name from Label WHERE name NOT LIKE '\_\_%' ESCAPE '\'""") { st -> st.getText(0) }.firstOrNull() ?: '-'
                val labels = queryLong("""SELECT count(*) from Label""")!!
                val bookmarks =
                    queryLong("""SELECT count(*) from BibleBookmark""")!! +
                        queryLong("""SELECT count(*) from GenericBookmark""")!!
                application.getString(R.string.bookmarks_db_stats, firstLabel, (labels - 1).toString(), bookmarks.toString())
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
 */
suspend fun importDatabaseFile(category: SyncableDatabaseDefinition, dbFile: File) = withContext(Dispatchers.IO) {
    val dbDef = category.accessor
    val importDbFile = dbDef.dbFactory(dbFile.absolutePath)
    try { importDbFile.useWriterConnection { } } finally { importDbFile.close() }
    dbDef.localDb.useWriterConnectionMarked { db ->
        // ATTACH inside the protected region, so DETACH always runs once it may have succeeded.
        db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE import") {
            db.exec("ATTACH DATABASE '${dbFile.absolutePath}' AS import")
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
}
