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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.WorkspaceDatabase
import java.io.Closeable
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TableDef(val tableName: String, val idField1: String = "id", val idField2: String? = null)
class DatabaseDef<T: RoomDatabase>(val db: T, dbClass: Class<T>, name: String, val tableDefs: List<TableDef>):
    Closeable {
    val patchDbFile: File = BibleApplication.application.getDatabasePath("patch-$name").apply {
        if(exists()) delete()
    }
    private val patchDb = Room.databaseBuilder(BibleApplication.application, dbClass, "patch-$name")
        .build()
    init {
        // Let's drop all indices to save space, they are useless in patch file
        patchDb.openHelper.writableDatabase.use { db -> db.run {
            execSQL("PRAGMA foreign_keys=OFF;")
            query("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_autoindex_%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(0)
                    db.execSQL("DROP INDEX IF EXISTS $indexName")
                }
            }
        } }
    }
    override fun close() {
        patchDb.openHelper.writableDatabase.execSQL("VACUUM;")
        patchDb.close()
    }
}
object DatabasePatching {
    fun createTriggersForTable(db: SupportSQLiteDatabase, tableName: String, idField1: String = "id", idField2: String? = null) = db.run {
        fun where(prefix: String): String =
            if(idField2 == null) {
                "entityId1 = $prefix.$idField1"
            } else {
                "entityId1 = $prefix.$idField1 AND entityId2 = $prefix.$idField2"
            }
        fun insert(prefix: String): String =
            if(idField2 == null) {
                "$prefix.$idField1,NULL"
            } else {
                "$prefix.$idField1,$prefix.$idField2"
            }

        execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName BEGIN " +
                "DELETE FROM Edit WHERE ${where("NEW")} AND tableName = '$tableName';" +
                "INSERT INTO Edit VALUES (NULL, '$tableName', ${insert("NEW")}, 'INSERT', STRFTIME('%s')); " +
                "END;")
        execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName BEGIN " +
                "DELETE FROM Edit WHERE ${where("OLD")} AND tableName = '$tableName';" +
                "INSERT INTO Edit VALUES (NULL, '$tableName', ${insert("OLD")}, 'UPDATE', STRFTIME('%s')); " +
                "END;")
        execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName BEGIN " +
                "DELETE FROM Edit WHERE ${where("OLD")} AND tableName = '$tableName';" +
                "INSERT INTO Edit VALUES (NULL, '$tableName', ${insert("OLD")}, 'DELETE', STRFTIME('%s')); " +
                "END;")
    }

    private fun writePatchData(db: SupportSQLiteDatabase, table: String, idField1: String = "id", idField2: String? = null) = db.run {
        if(idField2 == null) {
            execSQL("INSERT INTO patch.$table SELECT * FROM $table WHERE $idField1 IN " +
                "(SELECT entityId1 FROM Edit WHERE tableName = '$table' AND editType IN ('INSERT', 'UPDATE'))")
        } else {
            execSQL("INSERT INTO patch.$table SELECT * FROM $table WHERE ($idField1, $idField2) IN " +
                "(SELECT entityId1, entityId2 FROM Edit WHERE tableName = '$table' AND editType IN ('INSERT', 'UPDATE'))")
        }
        execSQL("INSERT INTO patch.Edit SELECT * FROM Edit WHERE tableName = '$table' AND editType = 'DELETE'")
    }

    private fun readPatchData(db: SupportSQLiteDatabase, table: String, idField1: String = "id", idField2: String? = null) = db.run {
        if(idField2 == null) {
            execSQL("INSERT OR REPLACE INTO $table SELECT * FROM patch.$table WHERE $idField1 IN " +
                "(SELECT entityId1 FROM Edit WHERE tableName = '$table' AND editType IN ('INSERT', 'UPDATE'))")
            execSQL("DELETE FROM $table WHERE $idField1 IN (SELECT entityId1 FROM patch.Edit WHERE tableName = '$table' AND editType = 'DELETE')")
        } else {
            execSQL("INSERT OR REPLACE INTO $table SELECT * FROM patch.$table WHERE ($idField1, $idField2) IN " +
                "(SELECT entityId1, entityId2 FROM Edit WHERE tableName = '$table' AND editType IN ('INSERT', 'UPDATE'))")
            execSQL("DELETE FROM $table WHERE ($idField1, $idField2) IN (SELECT entityId1, entityId2 FROM patch.Edit WHERE tableName = '$table' AND editType = 'DELETE')")
        }
    }

    private fun createPatchForDatabase(dbFactory: () -> DatabaseDef<*>) {
        val db = dbFactory()
        db.use {
            Log.i(TAG, "Writing patch file ${it.patchDbFile.name}")
            it.db.openHelper.writableDatabase.run {
                execSQL("ATTACH DATABASE '${it.patchDbFile.absolutePath}' AS patch")
                execSQL("PRAGMA foreign_keys=OFF;")
                for (tableDef in it.tableDefs) {
                    writePatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                }
                execSQL("PRAGMA foreign_keys=ON;")
                execSQL("DETACH DATABASE patch")
                execSQL("DELETE FROM Edit")
            }
        }
        val gzippedOutput = File(BackupControl.internalDbBackupDir, db.patchDbFile.name + ".gz")
        gzippedOutput.delete()
        gzippedOutput.outputStream().use {
            GZIPOutputStream(it).use {
                db.patchDbFile.inputStream().use { input ->
                    input.copyTo(it)
                }
            }
        }
        db.patchDbFile.delete()
    }
    private fun applyPatchForDatabase(dbFactory: () -> DatabaseDef<*>) {
        val db = dbFactory()
        val gzippedInput = File(BackupControl.internalDbBackupDir, db.patchDbFile.name + ".gz")
        gzippedInput.inputStream().use {
            GZIPInputStream(it).use {
                db.patchDbFile.outputStream().use { output ->
                    it.copyTo(output)
                }
            }
        }

        db.use {
            Log.i(TAG, "Reading patch file ${it.patchDbFile.name}")
            it.db.openHelper.writableDatabase.run {
                execSQL("ATTACH DATABASE '${it.patchDbFile.absolutePath}' AS patch")
                execSQL("PRAGMA foreign_keys=OFF;")
                for (tableDef in it.tableDefs) {
                    readPatchData(this, tableDef.tableName, tableDef.idField1, tableDef.idField2)
                }
                execSQL("PRAGMA foreign_keys=ON;")
                execSQL("DETACH DATABASE patch")
                execSQL("DELETE FROM Edit")
            }

        }
    }

    private val dbFactories = listOf(
        {
            DatabaseDef(DatabaseContainer.instance.bookmarkDb, BookmarkDatabase::class.java, BookmarkDatabase.dbFileName, listOf(
                TableDef("Bookmark"),
                TableDef("Label"),
                TableDef("BookmarkToLabel", "bookmarkId", "labelId"),
                TableDef("StudyPadTextEntry"),
            ))
        },
        {
            DatabaseDef(DatabaseContainer.instance.workspaceDb, WorkspaceDatabase::class.java, WorkspaceDatabase.dbFileName, listOf(
                TableDef("Workspace"),
                TableDef("Window"),
                TableDef("PageManager", "windowId"),
            ))
        },
        {
            DatabaseDef(DatabaseContainer.instance.readingPlanDb, ReadingPlanDatabase::class.java, ReadingPlanDatabase.dbFileName, listOf(
                TableDef("ReadingPlan"),
                TableDef("ReadingPlanStatus"),
            ))
        },
    )

    suspend fun createPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating db patch files")
        awaitAll(
            *dbFactories.map { async(Dispatchers.IO) {createPatchForDatabase(it)} }.toTypedArray()
        )
    }

    suspend fun applyPatchFiles() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Applying db patch files")
        awaitAll(
            *dbFactories.map { async(Dispatchers.IO) {applyPatchForDatabase(it)} }.toTypedArray()
        )
    }
}
