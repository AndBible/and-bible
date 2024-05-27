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

package net.bible.service.cloudsync

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.activity.R
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.LogEntry
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.migrations.getColumnNames
import net.bible.android.database.migrations.getColumnNamesJoined
import net.bible.service.common.CommonUtils
import net.bible.service.common.getFirst
import net.bible.service.db.DatabaseContainer
import java.io.File
import java.lang.Exception

const val TRIGGERS_DISABLED_KEY = "triggersDisabled"

enum class SyncableDatabaseDefinition {
    BOOKMARKS, WORKSPACES, READINGPLANS;
    class Table(
        val tableName: String,
        val idField1: String = "id",
        val idField2: String? = null,
    )
    val contentDescription: Int get() = when(this) {
        READINGPLANS -> R.string.reading_plans_content
        BOOKMARKS -> R.string.bookmarks_contents
        WORKSPACES -> R.string.workspaces_contents
    }

    val filename get() = when(this) {
        BOOKMARKS -> BookmarkDatabase.dbFileName
        READINGPLANS ->ReadingPlanDatabase.dbFileName
        WORKSPACES -> WorkspaceDatabase.dbFileName
    }

    val tables get() = when(this) {
        BOOKMARKS -> listOf(
            Table(
                tableName = "Label"
            ),
            Table(
                tableName = "BibleBookmark"
            ),
            Table(
                tableName = "BibleBookmarkNotes",
                idField1 = "bookmarkId"
            ),
            Table(
                tableName = "BibleBookmarkToLabel",
                idField1 = "bookmarkId",
                idField2 = "labelId"
            ),
            Table(
                tableName = "GenericBookmark"
            ),
            Table(
                tableName = "GenericBookmarkNotes",
                idField1 = "bookmarkId"
            ),
            Table(
                tableName = "GenericBookmarkToLabel",
                idField1 = "bookmarkId",
                idField2 = "labelId"
            ),
            Table(
                tableName = "StudyPadTextEntry"
            ),
            Table(
                tableName = "StudyPadTextEntryText",
                idField1 = "studyPadTextEntryId"
            ),
        )
        WORKSPACES -> listOf(
            Table(tableName = "Workspace"),
            Table(tableName = "Window"),
            Table(tableName = "PageManager", idField1 = "windowId"),
        )
        READINGPLANS -> listOf(
            Table(tableName = "ReadingPlan"),
            Table(tableName = "ReadingPlanStatus"),
        )
    }

    var syncEnabled
        get() = CommonUtils.settings.getBoolean("gdrive_"+ name.lowercase(), false)
        set(value) = CommonUtils.settings.setBoolean("gdrive_"+name.lowercase(), value)

    val accessor get() = DatabaseContainer.databaseAccessorsByCategory[this]!!
    val lastSynchronized get() = if(!syncEnabled) null else accessor.dao.getLong(LAST_SYNCHRONIZED_KEY)

    companion object {
        val ALL = arrayOf(BOOKMARKS, WORKSPACES, READINGPLANS)
        val nameToCategory = ALL.associateBy { it.name }
        val filenameToCategory = ALL.associateBy { it.filename }
    }
}

class SyncableDatabaseAccessor<T: SyncableRoomDatabase>(
    var localDb: T,
    val dbFactory: (filename: String) -> T,
    private val _resetLocalDb: () -> T,
    val localDbFile: File,
    val category: SyncableDatabaseDefinition,
    val _reactToUpdates: ((entries: List<LogEntry>) -> Unit)? = null,
    val deviceId: String = CommonUtils.deviceIdentifier
) {
    fun resetLocalDb() {
        localDb = _resetLocalDb()
    }

    fun reactToUpdates(lastSynchronized: Long) {
        val newEntries = dao.newLogEntries(lastSynchronized, deviceId)
        if(newEntries.isNotEmpty()) {
            _reactToUpdates?.invoke(newEntries)
        }
    }

    val bytesUsed: Long get() {
        return dao.totalBytesUsed()
    }
    val hasChanges: Boolean get() {
        val lastSynchronized = dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        return dao.countNewLogEntries(lastSynchronized, deviceId) > 0
    }

    val categoryName get() = category.name.lowercase()
    val dao get() = localDb.syncDao()
    val writableDb get() = localDb.openHelper.writableDatabase
    val tableDefinitions get() = category.tables
    val version get() = writableDb.version
}

private fun createTriggersForTable(
    dbDef: SyncableDatabaseAccessor<*>,
    tableDef: SyncableDatabaseDefinition.Table,
) = tableDef.run {
    fun where(prefix: String): String =
        if(idField2 == null) {
            "entityId1 = $prefix.$idField1"
        } else {
            "entityId1 = $prefix.$idField1 AND entityId2 = $prefix.$idField2"
        }
    fun insert(prefix: String): String =
        if(idField2 == null) {
            "$prefix.$idField1,''"
        } else {
            "$prefix.$idField1,$prefix.$idField2"
        }
    val timeStampFunc = "CAST(UNIXEPOCH('subsec') * 1000 AS INTEGER)"

    val db = dbDef.writableDb
    val deviceId = dbDef.deviceId
    val whenCondition = """
            WHEN (SELECT count(*) FROM SyncConfiguration WHERE keyName='${TRIGGERS_DISABLED_KEY}' AND booleanValue = 1 LIMIT 1) = 0
            """.trimIndent()

    db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("NEW")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("NEW")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'DELETE', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
}

private fun dropTriggersForTable(
    dbDef: SyncableDatabaseAccessor<*>,
    tableDef: SyncableDatabaseDefinition.Table
) = dbDef.writableDb.run {
    execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_inserts")
    execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_updates")
    execSQL("DROP TRIGGER IF EXISTS ${tableDef.tableName}_deletes")
}


fun createTriggers(dbDef: SyncableDatabaseAccessor<*>) {
    for(tableDef in dbDef.tableDefinitions) {
        createTriggersForTable(dbDef, tableDef)
    }
}

fun dropTriggers(dbDef: SyncableDatabaseAccessor<*>) {
    for(tableDef in dbDef.tableDefinitions) {
        dropTriggersForTable(dbDef, tableDef)
    }
}

private fun writePatchData(
    db: SupportSQLiteDatabase,
    tableDef: SyncableDatabaseDefinition.Table,
    lastPatchWritten: Long
) = db.run {
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
    execSQL("""
            INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE $where IN 
            (SELECT $select FROM LogEntry pe WHERE tableName = '$table' AND type = 'UPSERT' 
            AND lastUpdated > $lastPatchWritten)
            """.trimIndent())
    execSQL("""
            INSERT INTO patch.LogEntry SELECT * FROM LogEntry 
            WHERE tableName = '$table' AND lastUpdated > $lastPatchWritten
            """.trimIndent())
}

private fun readPatchData(
    dbDef: SyncableDatabaseAccessor<*>,
    tableDef: SyncableDatabaseDefinition.Table,
) = dbDef.writableDb.run {
    val table = tableDef.tableName
    val idField1 = tableDef.idField1
    val idField2 = tableDef.idField2

    val colList = getColumnNames(this, table)
    val cols = colList.joinToString(",") { "`$it`" }
    val setValues = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=excluded.`$it`" }
    val amount = query("SELECT COUNT(*) FROM patch.LogEntry WHERE tableName = '$table'")
        .getFirst { it.getInt(0)}

    Log.i(TAG, "Reading patch data for $table: $amount log entries")
    var idFields = idField1
    var select = "pe.entityId1"
    if (idField2 != null) {
        idFields = "($idField1,$idField2)"
        select = "pe.entityId1,pe.entityId2"
    }

    fun where(type: String? = null): String {
        val typeStr = if(type == null) "" else "AND pe.type = '$type'"
        return """
                (SELECT $select FROM patch.LogEntry pe
                  OUTER LEFT JOIN LogEntry me
                  ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName
                  WHERE pe.tableName = '$table' $typeStr AND 
                 (me.lastUpdated IS NULL OR pe.lastUpdated > me.lastUpdated))
                """.trimIndent()
    }

    // Insert all rows from patch table that don't have more recent entry in LogEntry table
    execSQL("""
            INSERT INTO $table ($cols)
            SELECT $cols FROM patch.$table 
            WHERE $idFields IN ${where("UPSERT")}
            ON CONFLICT DO UPDATE SET $setValues;
            """.trimIndent())

    // Let's fix all foreign key violations. Those will result if target object has been deleted here,
    // but patch still adds references
    execSQL("""
            DELETE FROM $table 
            WHERE rowId in (SELECT rowid FROM pragma_foreign_key_check('$table'));
            """.trimIndent()
    )

    // Delete all marked deletions from patch LogEntry table
    execSQL("""
            DELETE FROM $table WHERE $idFields IN ${where("DELETE")}
            """.trimIndent()
    )

    execSQL("""
            INSERT OR REPLACE INTO LogEntry SELECT * FROM patch.LogEntry pe 
            WHERE pe.tableName = '$table' AND ($select) IN ${where()}
            """.trimIndent()
    )
}

fun createPatchForDatabase(dbDef: SyncableDatabaseAccessor<*>, updateTimestamp: Boolean = true): File? {
    val lastPatchWritten = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY)?: 0
    val patchDbFile = File.createTempFile("created-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)

    dbDef.writableDb.run {
        val amountUpdated = dbDef.dao.countNewLogEntries(lastPatchWritten, dbDef.deviceId)
        if(amountUpdated == 0L) {
            Log.i(TAG, "No new entries ${dbDef.categoryName}")
            return null
        }
        // let's create empty database with correct schema first.
        val patchDb = dbDef.dbFactory(patchDbFile.absolutePath)
        patchDb.openHelper.writableDatabase.use {}
        Log.i(TAG, "Creating patch for ${dbDef.categoryName}: $amountUpdated updated")
        execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
        execSQL("PRAGMA patch.foreign_keys=OFF;")
        beginTransaction()
        for (tableDef in dbDef.tableDefinitions) {
            writePatchData(this, tableDef, lastPatchWritten)
        }
        setTransactionSuccessful()
        endTransaction()
        execSQL("PRAGMA patch.foreign_keys=ON;")
        execSQL("DETACH DATABASE patch")
    }

    val gzippedOutput = CommonUtils.tmpFile
    Log.i(TAG, "Saving patch file ${dbDef.categoryName}")
    CommonUtils.gzipFile(patchDbFile, gzippedOutput)

    if(!CommonUtils.isDebugMode) {
        patchDbFile.delete()
    }
    if(updateTimestamp) {
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
    }
    return gzippedOutput
}

fun applyPatchesForDatabase(dbDef: SyncableDatabaseAccessor<*>, vararg patchFiles: File?) {
    for(gzippedPatchFile in patchFiles.filterNotNull()) {
        val patchDbFile = File.createTempFile("downloaded-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)
        Log.i(TAG, "Applying patch file ${patchDbFile.name}")
        CommonUtils.gunzipFile(gzippedPatchFile, patchDbFile)
        // Let's apply possible migrations first
        dbDef.dbFactory(patchDbFile.absolutePath).openHelper.writableDatabase.use {  }
        dbDef.writableDb.run {
            execSQL("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
            execSQL("PRAGMA foreign_keys=OFF;")
            beginTransaction()
            try {
                for (tableDef in dbDef.tableDefinitions) {
                    dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, true)
                    readPatchData(dbDef, tableDef)
                    dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, false)
                }
                setTransactionSuccessful()
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred in applyPatchesForDatabase", e)
                throw e
            } finally {
                endTransaction()
                execSQL("PRAGMA foreign_keys=ON;")
                execSQL("DETACH DATABASE patch")
            }
        }
        if(!CommonUtils.isDebugMode) {
            patchDbFile.delete()
        }
    }
}
