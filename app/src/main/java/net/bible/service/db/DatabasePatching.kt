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
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.migrations.getColumnNames
import net.bible.android.database.migrations.getColumnNamesJoined
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.common.forEach
import net.bible.service.common.getFirst
import net.bible.service.googledrive.GoogleDrive
import net.bible.service.googledrive.GoogleDrive.timeStampFromPatchFileName
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TableDef(val tableName: String, val idField1: String = "id", val idField2: String? = null)

class DatabaseDefinition<T: SyncableRoomDatabase>(
    val localDb: T,
    val dbFactory: (filename: String) -> T,
    val localDbFileName: String,
    val tableDefinitions: List<TableDef>,
) {
    val categoryName = localDbFileName.split(".").first()
}
object DatabasePatching {
    private fun writePatchData(db: SupportSQLiteDatabase, tableDef: TableDef, lastSynchronized: Long) = db.run {
        val table = tableDef.tableName
        val idField1 = tableDef.idField1
        val idField2 = tableDef.idField2
        val cols = getColumnNamesJoined(db, table, "patch")

        var where = idField1
        var select = "pe.entityId1"
        if (idField2 != null) {
            where = "($idField1,$idField2)"
            select = "pe.entityId1,pe.entityId2"
        }
        execSQL("INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE $where IN " +
            "(SELECT $select FROM LogEntry pe WHERE tableName = '$table' AND type = 'UPSERT' AND lastUpdated > $lastSynchronized)")
        execSQL("INSERT INTO patch.LogEntry SELECT * FROM LogEntry WHERE tableName = '$table' AND lastUpdated > $lastSynchronized")
    }

    private fun readPatchData(
        db: SupportSQLiteDatabase,
        table: String,
        idField1: String = "id",
        idField2: String? = null
    ) = db.run {
        val colList = getColumnNames(this, table)
        val cols = colList.joinToString(",") { "`$it`" }
        val setValues = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=excluded.`$it`" }
        val amount = query("SELECT COUNT(*) FROM patch.LogEntry WHERE tableName = '$table'").getFirst { it.getInt(0)}
        Log.i(TAG, "Reading patch data for $table: $amount log entries")
        var idFields = idField1
        var select = "pe.entityId1"
        if (idField2 != null) {
            idFields = "($idField1,$idField2)"
            select = "pe.entityId1,pe.entityId2"
        }

        // Insert all rows from patch table that don't have more recent entry in LogEntry table
        execSQL("""INSERT INTO $table ($cols)
                  |SELECT $cols FROM patch.$table WHERE $idFields IN
                  |(SELECT $select FROM patch.LogEntry pe
                  | OUTER LEFT JOIN LogEntry me
                  | ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName
                  | WHERE pe.tableName = '$table' AND pe.type = 'UPSERT' AND (me.lastUpdated IS NULL OR pe.lastUpdated > me.lastUpdated)
                  |) ON CONFLICT DO UPDATE SET $setValues;
                """.trimMargin())

        // Delete all marked deletions from patch LogEntry table
        execSQL("DELETE FROM $table WHERE $idFields IN (SELECT $select FROM patch.LogEntry pe WHERE tableName = '$table' AND type = 'DELETE')")

        // Let's fix LogEntry table timestamps (all above insertions have created new entries)
        execSQL("INSERT OR REPLACE INTO LogEntry SELECT * FROM patch.LogEntry")
    }

    private fun createPatchForDatabase(dbDefFactory: () -> DatabaseDefinition<*>, lastSynchronized: Long): File? {
        val dbDef = dbDefFactory()

        // let's create empty database with correct schema first.
        val patchDbFile = getTemporaryDbFile()
        val patchDb = dbDef.dbFactory(patchDbFile.absolutePath)
        patchDb.openHelper.writableDatabase.use {}

        var needPatch: Boolean
        dbDef.localDb.openHelper.writableDatabase.run {
            val amountUpdated = query("SELECT COUNT(*) FROM LogEntry WHERE lastUpdated > $lastSynchronized").getFirst { c -> c.getInt(0)}
            needPatch = amountUpdated > 0
            if (needPatch) {
                Log.i(TAG, "Creating patch for ${dbDef.categoryName}: $amountUpdated updated")
                execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
                execSQL("PRAGMA patch.foreign_keys=OFF;")
                for (tableDef in dbDef.tableDefinitions) {
                    writePatchData(this, tableDef, lastSynchronized)
                }
                execSQL("PRAGMA patch.foreign_keys=ON;")
                execSQL("DETACH DATABASE patch")
            }
        }
        val resultFile =
            if (needPatch) {
                val gzippedOutput = File(GoogleDrive.patchOutFilesDir, dbDef.categoryName + ".sqlite3.gz")
                Log.i(TAG, "Saving patch file ${gzippedOutput.name}")
                gzippedOutput.delete()
                gzippedOutput.outputStream().use {
                    GZIPOutputStream(it).use {
                        patchDbFile.inputStream().use { input ->
                            input.copyTo(it)
                        }
                    }
                }
                gzippedOutput
            } else {
                null
            }
        patchDbFile.delete()
        return resultFile
    }

    private fun getTemporaryDbFile(): File {
        return File(CommonUtils.tmpDir, UUID.randomUUID().toString() + ".sqlite3")
    }

    private fun applyPatchForDatabase(dbDefFactory: () -> DatabaseDefinition<*>) {
        val dbDef = dbDefFactory()
        val files = (GoogleDrive.patchInFilesDir.listFiles()?: emptyArray()).filter { it.name.startsWith(dbDef.categoryName) }
        for(gzippedPatchFile in files.sortedBy { timeStampFromPatchFileName(it.name) }) {
            Log.i(TAG, "Applying patch file ${gzippedPatchFile.name}")
            val patchDbFile = getTemporaryDbFile()
            gzippedPatchFile.inputStream().use {
                GZIPInputStream(it).use {
                    patchDbFile.outputStream().use { output ->
                        it.copyTo(output)
                    }
                }
            }
            dbDef.localDb.openHelper.writableDatabase.run {
                execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
                for (tableDef in dbDef.tableDefinitions) {
                    readPatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                }
                execSQL("DETACH DATABASE patch")
                if(CommonUtils.isDebugMode) {
                    checkForeignKeys(this)
                }
            }
            patchDbFile.delete()
        }
    }

    private fun checkForeignKeys(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check;").forEach {c->
            val tableName = c.getString(0)
            val rowId = c.getLong(1)
            val parent = c.getString(2)
            Log.w(TAG, "Foreign key check failure: $tableName:$rowId (<- $parent)")
            Dialogs.showErrorMsg("Foreign key check failure: $tableName:$rowId (<- $parent)")
        }
    }

    val dbFactories: List<() -> DatabaseDefinition<*>> get() = DatabaseContainer.instance.run { listOf(
        {
            DatabaseDefinition(bookmarkDb, { n -> getBookmarkDb(n)}, BookmarkDatabase.dbFileName, listOf(
                TableDef("Bookmark"),
                TableDef("Label"),
                TableDef("BookmarkToLabel", "bookmarkId", "labelId"),
                TableDef("StudyPadTextEntry"),
            ))
        },
        {
            DatabaseDefinition(workspaceDb, { n -> getWorkspaceDb(n)}, WorkspaceDatabase.dbFileName, listOf(
                TableDef("Workspace"),
                TableDef("Window"),
                TableDef("PageManager", "windowId"),
            ))
        },
        {
            DatabaseDefinition(readingPlanDb, { n -> getReadingPlanDb(n)}, ReadingPlanDatabase.dbFileName, listOf(
                TableDef("ReadingPlan"),
                TableDef("ReadingPlanStatus"),
            ))
        },
    ) }

    suspend fun createPatchFiles(lastSynchronized: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating db patch files")
        awaitAll(
            *dbFactories.map { async(Dispatchers.IO) {createPatchForDatabase(it, lastSynchronized)} }.toTypedArray()
        )
    }

    suspend fun applyPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Applying db patch files")
        awaitAll(
            *dbFactories.map { async(Dispatchers.IO) {applyPatchForDatabase(it)} }.toTypedArray()
        )
    }
}
