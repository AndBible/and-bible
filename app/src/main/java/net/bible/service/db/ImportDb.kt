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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.migrations.getColumnNamesJoined
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import java.io.File
import java.lang.Exception

private const val TAG = "ImportDb"

suspend fun importDatabaseFile(category: SyncableDatabaseDefinition, dbFile: File) = withContext(Dispatchers.IO) {
    val dbDef = category.accessor
    val importDbFile = dbDef.dbFactory(dbFile.absolutePath)
    importDbFile.openHelper.writableDatabase.use {}
    dbDef.writableDb.run {
        execSQL("ATTACH DATABASE '${dbFile.absolutePath}' AS import")
        execSQL("PRAGMA foreign_keys=OFF;")
        beginTransaction()
        try {
            for (tableDef in dbDef.tableDefinitions) {
                val table = tableDef.tableName
                val cols = getColumnNamesJoined(this, table)
                execSQL("""
                        INSERT OR IGNORE INTO $table ($cols)
                        SELECT $cols FROM import.$table 
                    """.trimIndent())
            }
            setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred in importDatabaseFile", e)
            throw e
        } finally {
            endTransaction()
            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DETACH DATABASE import")
        }
    }
}
