/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.service.db.migrations.getColumnNamesJoined
import net.bible.service.googledrive.GoogleDrive
import net.bible.service.googledrive.GoogleDrive.timeStampFromPatchFileName
import java.io.Closeable
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TableDef(val tableName: String, val idField1: String = "id", val idField2: String? = null)

class DatabaseDef<T: RoomDatabase>(
    val db: T,
    dbFactory: (filename: String) -> T,
    dbFileName: String,
    val tableDefs: List<TableDef>,
    private val readOnly: Boolean,
): Closeable {
    private val patchFileName = "patch-$dbFileName";
    val patchDbFile: File = BibleApplication.application.getDatabasePath(patchFileName).apply {
        if(!readOnly && exists()) delete()
    }
    val categoryName= dbFileName.split(".").first()
    private val patchDb = dbFactory.invoke(patchFileName)
    init {
        if(!readOnly) {
            // Let's drop all indices to save space, they are useless in patch file
            patchDb.openHelper.writableDatabase.run {
                execSQL("PRAGMA foreign_keys=OFF;")
                query("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_autoindex_%'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val indexName = cursor.getString(0)
                        execSQL("DROP INDEX IF EXISTS $indexName")
                    }
                }
            }
        }
    }
    override fun close() {
        if(!readOnly) {
            patchDb.openHelper.writableDatabase.execSQL("VACUUM;")
            patchDb.openHelper.writableDatabase.close()
        }
        patchDb.close()
    }
}
object DatabasePatching {
    private fun writePatchData(db: SupportSQLiteDatabase, table: String, idField1: String = "id", idField2: String? = null) = db.run {
        val cols = getColumnNamesJoined(db, table, "patch")
        if(idField2 == null) {
            execSQL("INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE $idField1 IN " +
                "(SELECT entityId1 FROM Log WHERE tableName = '$table' AND type IN ('INSERT', 'UPDATE'))")
        } else {
            execSQL("INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE ($idField1, $idField2) IN " +
                "(SELECT entityId1, entityId2 FROM Log WHERE tableName = '$table' AND type IN ('INSERT', 'UPDATE'))")
        }
        execSQL("INSERT INTO patch.Log SELECT * FROM Log WHERE tableName = '$table'")
    }

    private fun readPatchData(db: SupportSQLiteDatabase, table: String, idField1: String = "id", idField2: String? = null) = db.run {
        val cols = getColumnNamesJoined(db, table)
        if(idField2 == null) {
            // Insert all rows from patch table that don't have more recent entry in Log table
            execSQL("INSERT OR REPLACE INTO $table ($cols) " +
                "SELECT $cols FROM patch.$table WHERE $idField1 IN " +
                "(SELECT pe.entityId1 FROM patch.Log pe OUTER LEFT JOIN Log me " +
                "WHERE pe.tableName = '$table' AND " +
                "(me.entityId1 = NULL OR (pe.entityId1 = me.entityId1 AND me.tableName = '$table' AND pe.createdAt > me.createdAt)))")
            // Delete all marked deletions from patch Log table
            execSQL("DELETE FROM $table WHERE $idField1 IN (SELECT entityId1 FROM patch.Log WHERE tableName = '$table' AND type = 'DELETE')")
        } else {
            execSQL("INSERT OR REPLACE INTO $table ($cols) " +
                "SELECT $cols FROM patch.$table WHERE ($idField1,$idField2) IN " +
                "(SELECT pe.entityId1,pe.entityId2 FROM patch.Log pe OUTER LEFT JOIN Log me " +
                "WHERE pe.tableName = '$table' AND " +
                "(me.entityId1 = NULL OR " +
                "(pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND me.tableName = '$table' AND pe.createdAt > me.createdAt)))")

            execSQL("DELETE FROM $table WHERE ($idField1, $idField2) IN " +
                "(SELECT entityId1, entityId2 FROM patch.Log WHERE tableName = '$table' AND type = 'DELETE')")
        }
    }

    private fun createPatchForDatabase(dbDefFactory: () -> DatabaseDef<*>) {
        val dbDef = dbDefFactory()
        var needPatch: Boolean
        dbDef.use {
            it.db.openHelper.writableDatabase.run {
                needPatch = query("SELECT COUNT(*) FROM Log").use {c -> c.moveToFirst(); c.getInt(0)} > 0
                if(needPatch) {
                    execSQL("ATTACH DATABASE '${it.patchDbFile.absolutePath}' AS patch")
                    execSQL("PRAGMA foreign_keys=OFF;")
                    for (tableDef in it.tableDefs) {
                        writePatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                    }
                    execSQL("PRAGMA foreign_keys=ON;")
                    execSQL("DETACH DATABASE patch")
                }
            }
        }
        if(needPatch) {
            val gzippedOutput = File(GoogleDrive.patchOutFilesDir, dbDef.categoryName + ".sqlite3.gz")
            Log.i(TAG, "Saving patch file ${gzippedOutput.name}")
            gzippedOutput.delete()
            gzippedOutput.outputStream().use {
                GZIPOutputStream(it).use {
                    dbDef.patchDbFile.inputStream().use { input ->
                        input.copyTo(it)
                    }
                }
            }
        }
        dbDef.patchDbFile.delete()
    }
    private fun applyPatchForDatabase(dbDefFactory: () -> DatabaseDef<*>) {
        val dbDef = dbDefFactory()
        val files = (GoogleDrive.patchInFilesDir.listFiles()?: emptyArray()).filter { it.name.startsWith(dbDef.categoryName) }
        for(gzippedPatchFile in files.sortedBy { timeStampFromPatchFileName(it.name) }) {
            Log.i(TAG, "Applying patch file ${gzippedPatchFile.name}")
            gzippedPatchFile.inputStream().use {
                GZIPInputStream(it).use {
                    dbDef.patchDbFile.outputStream().use { output ->
                        it.copyTo(output)
                    }
                }
            }
            dbDef.use {
                it.db.openHelper.writableDatabase.run {
                    execSQL("ATTACH DATABASE '${it.patchDbFile.absolutePath}' AS patch")
                    execSQL("PRAGMA foreign_keys=OFF;")
                    for (tableDef in it.tableDefs) {
                        readPatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                    }
                    execSQL("PRAGMA foreign_keys=ON;")
                    execSQL("DETACH DATABASE patch")
                }
            }
        }
    }

    private fun getDbFactories(readOnly: Boolean): List<() -> DatabaseDef<*>> = DatabaseContainer.instance.run { listOf(
        {
            DatabaseDef(bookmarkDb, {n -> getBookmarkDb(n)}, BookmarkDatabase.dbFileName, listOf(
                TableDef("Bookmark"),
                TableDef("Label"),
                TableDef("BookmarkToLabel", "bookmarkId", "labelId"),
                TableDef("StudyPadTextEntry"),
            ), readOnly)
        },
        {
            DatabaseDef(workspaceDb, {n -> getWorkspaceDb(n)}, WorkspaceDatabase.dbFileName, listOf(
                TableDef("Workspace"),
                TableDef("Window"),
                TableDef("PageManager", "windowId"),
            ), readOnly)
        },
        {
            DatabaseDef(readingPlanDb, {n -> getReadingPlanDb(n)}, ReadingPlanDatabase.dbFileName, listOf(
                TableDef("ReadingPlan"),
                TableDef("ReadingPlanStatus"),
            ), readOnly)
        },
    ) }

    suspend fun createPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating db patch files")
        awaitAll(
            *getDbFactories(false).map { async(Dispatchers.IO) {createPatchForDatabase(it)} }.toTypedArray()
        )
    }

    suspend fun applyPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Applying db patch files")
        awaitAll(
            *getDbFactories(true).map { async(Dispatchers.IO) {applyPatchForDatabase(it)} }.toTypedArray()
        )
    }
}
