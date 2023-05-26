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
import net.bible.android.BibleApplication
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
import java.io.Closeable
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TableDef(val tableName: String, val idField1: String = "id", val idField2: String? = null)

class DatabaseDef<T: SyncableRoomDatabase>(
    val db: T,
    dbFactory: (filename: String) -> T,
    val resetDb: () -> Unit,
    val dbFileName: String,
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
            patchDb.openHelper.writableDatabase
            // dropPatchIndices() // TODO: consider enabling.
        } else {
            patchDb.openHelper.writableDatabase.close()
        }
    }

    private fun dropPatchIndices() {
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

    override fun close() {
        if(!readOnly) {
            patchDb.openHelper.writableDatabase.execSQL("VACUUM;")
            patchDb.openHelper.writableDatabase.close()
        }
        patchDb.close()
    }
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
            "(SELECT $select FROM Log pe WHERE tableName = '$table' AND type IN ('INSERT', 'UPDATE') AND lastUpdated > $lastSynchronized)")
        execSQL("INSERT INTO patch.Log SELECT * FROM Log WHERE tableName = '$table' AND lastUpdated > $lastSynchronized")
    }

    private fun readPatchData(
        db: SupportSQLiteDatabase,
        table: String,
        idField1: String = "id",
        idField2: String? = null
    ) = db.run {
        val colList = getColumnNames(this, table)
        val cols = colList.joinToString(",") { "`$it`" }
        //val setValues = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=excluded.`$it`" }
        val setValues2 = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=p.`$it`" }
        val amount = query("SELECT COUNT(*) FROM patch.Log WHERE tableName = '$table'").getFirst { it.getInt(0)}
        Log.i(TAG, "Reading patch data for $table: $amount log entries")
        var idFields = idField1
        var select = "pe.entityId1"
        if (idField2 != null) {
            idFields = "($idField1,$idField2)"
            select = "pe.entityId1,pe.entityId2"
        }

        fun subSelect(type: String) = """SELECT $select FROM patch.Log pe 
                |OUTER LEFT JOIN Log me ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName 
                |WHERE pe.tableName = '$table' AND pe.type = '$type' AND (me.lastUpdated IS NULL OR pe.lastUpdated > me.lastUpdated)
                |""".trimMargin()

        //// Insert all rows from patch table that don't have more recent entry in Log table
        execSQL("INSERT INTO $table ($cols) SELECT $cols FROM patch.$table WHERE $idFields IN (${subSelect("INSERT")})")
        execSQL("UPDATE $table SET $setValues2 FROM (SELECT $cols FROM patch.$table WHERE $idFields IN (${subSelect("UPDATE")})) as p")

        // Insert all rows from patch table that don't have more recent entry in Log table
        //execSQL("""INSERT INTO $table ($cols)
        //          |SELECT $cols FROM patch.$table WHERE $idFields IN
        //          |(SELECT $select FROM patch.Log pe
        //          | OUTER LEFT JOIN Log me
        //          | ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName
        //          | WHERE pe.tableName = '$table' AND (me.lastUpdated IS NULL OR pe.lastUpdated > me.lastUpdated)
        //          |) ON CONFLICT DO UPDATE SET $setValues;
        //        """.trimMargin())
        // Delete all marked deletions from patch Log table
        execSQL("DELETE FROM $table WHERE $idFields IN (SELECT $select FROM patch.Log pe WHERE tableName = '$table' AND type = 'DELETE')")

        // Let's fix Log table timestamps (all above insertions have created new entries)
        execSQL("INSERT OR REPLACE INTO Log SELECT * FROM patch.Log")

        //val deletions = dbDef.db.logDao().allDeletions()
        //Log.i(TAG, "deletions $table \n${deletions.joinToString("\n")}")
    }

    private fun createPatchForDatabase(dbDefFactory: () -> DatabaseDef<*>, lastSynchronized: Long) {
        val dbDef = dbDefFactory()
        var needPatch: Boolean
        dbDef.use {
            it.db.openHelper.writableDatabase.run {
                val amountUpdated = query("SELECT COUNT(*) FROM Log WHERE lastUpdated > $lastSynchronized").getFirst { c -> c.getInt(0)}
                needPatch = amountUpdated > 0
                if (needPatch) {
                    Log.i(TAG, "Creating patch for ${dbDef.categoryName}: $amountUpdated updated")
                    execSQL("ATTACH DATABASE '${it.patchDbFile.absolutePath}' AS patch")
                    execSQL("PRAGMA patch.foreign_keys=OFF;")
                    for (tableDef in it.tableDefs) {
                        writePatchData(this, tableDef, lastSynchronized)
                    }
                    execSQL("PRAGMA patch.foreign_keys=ON;")
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
                    for (tableDef in it.tableDefs) {
                        readPatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                    }
                    execSQL("DETACH DATABASE patch")
                    if(CommonUtils.isDebugMode) {
                        checkForeignKeys(this)
                    }
                }
            }
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

    private fun getDbFactories(readOnly: Boolean): List<() -> DatabaseDef<*>> = DatabaseContainer.instance.run { listOf(
        {
            DatabaseDef(bookmarkDb, {n -> getBookmarkDb(n)}, {resetBookmarkDb()}, BookmarkDatabase.dbFileName, listOf(
                TableDef("Bookmark"),
                TableDef("Label"),
                TableDef("BookmarkToLabel", "bookmarkId", "labelId"),
                TableDef("StudyPadTextEntry"),
            ), readOnly)
        },
        {
            DatabaseDef(workspaceDb, {n -> getWorkspaceDb(n)}, {resetWorkspaceDb()}, WorkspaceDatabase.dbFileName, listOf(
                TableDef("Workspace"),
                TableDef("Window"),
                TableDef("PageManager", "windowId"),
            ), readOnly)
        },
        {
            DatabaseDef(readingPlanDb, {n -> getReadingPlanDb(n)}, {resetReadingPlanDb()}, ReadingPlanDatabase.dbFileName, listOf(
                TableDef("ReadingPlan"),
                TableDef("ReadingPlanStatus"),
            ), readOnly)
        },
    ) }

    suspend fun createPatchFiles(lastSynchronized: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating db patch files")
        awaitAll(
            *getDbFactories(false).map { async(Dispatchers.IO) {createPatchForDatabase(it, lastSynchronized)} }.toTypedArray()
        )
    }

    suspend fun applyPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Applying db patch files")
        awaitAll(
            *getDbFactories(true).map { async(Dispatchers.IO) {applyPatchForDatabase(it)} }.toTypedArray()
        )
    }
}
