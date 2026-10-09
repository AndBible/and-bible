/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import androidx.room3.PooledConnection
import androidx.room3.useWriterConnection
import net.bible.android.activity.R
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.LogEntry
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.columnNames
import net.bible.service.db.columnNamesJoined
import net.bible.service.db.exec
import net.bible.service.db.inTransaction
import net.bible.service.db.queryLong
import net.bible.service.db.useReaderConnectionMarked
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.withCleanup
import java.io.File
import java.lang.Exception

const val TRIGGERS_DISABLED_KEY = "triggersDisabled"

enum class SyncableDatabaseDefinition {
    BOOKMARKS, WORKSPACES, READINGPLANS, MYDOCUMENTS, AI_SETTINGS, PROGRESS;
    class Table(
        val tableName: String,
        val idField1: String = "id",
        val idField2: String? = null,
    )
    val contentDescription: Int get() = when(this) {
        READINGPLANS -> R.string.reading_plans_content
        BOOKMARKS -> R.string.bookmarks_contents
        WORKSPACES -> R.string.workspaces_contents
        MYDOCUMENTS -> R.string.my_documents_contents
        AI_SETTINGS -> R.string.ai_settings_sync_contents
        PROGRESS -> R.string.progress_sync_contents
    }

    val filename get() = when(this) {
        BOOKMARKS -> BookmarkDatabase.dbFileName
        READINGPLANS ->ReadingPlanDatabase.dbFileName
        WORKSPACES -> WorkspaceDatabase.dbFileName
        MYDOCUMENTS -> MyDocumentDatabase.dbFileName
        AI_SETTINGS -> AiSettingsDatabase.dbFileName
        PROGRESS -> ProgressDatabase.dbFileName
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
            Table(tableName = "WorkspaceLabelOverride", idField1 = "workspaceId", idField2 = "labelId"),
            Table(tableName = "GlobalTextDisplaySettings"),
        )
        READINGPLANS -> listOf(
            Table(tableName = "ReadingPlan"),
            Table(tableName = "ReadingPlanStatus"),
        )
        MYDOCUMENTS -> listOf(
            Table(tableName = "MyDocument"),
            Table(tableName = "MyDocumentPage"),
            Table(tableName = "MyDocumentPageContent", idField1 = "pageId"),
            Table(tableName = "AiPageCacheEntry", idField1 = "pageId"),
        )
        AI_SETTINGS -> listOf(
            Table(tableName = "LlmProviderConfig"),
            Table(tableName = "LlmConfiguredModel"),
            Table(tableName = "AgentPrompt"),
            Table(tableName = "GlobalAiSettings"),
            Table(tableName = "LlmUsageRecord"),
            Table(tableName = "PromptCategory"),
            Table(tableName = "BuiltinPromptOverride"),
        )
        PROGRESS -> listOf(
            Table(tableName = "MemorizedVerse"),
            Table(tableName = "ChapterReadHistory"),
            Table(tableName = "MemorizationTarget"),
            Table(tableName = "GlobalReadingProgressSettings"),
        )
    }

    var syncEnabled
        get() = CommonUtils.settings.getBoolean("sync_enable_"+ name.lowercase(), false)
        set(value) = CommonUtils.settings.setBoolean("sync_enable_"+name.lowercase(), value)

    val accessor get() = DatabaseContainer.databaseAccessorsByCategory[this]!!
    suspend fun lastSynchronized(): Long? = if(!syncEnabled) null else accessor.dao.getLong(LAST_SYNCHRONIZED_KEY)

    companion object {
        val ALL = arrayOf(BOOKMARKS, WORKSPACES, READINGPLANS, MYDOCUMENTS, AI_SETTINGS, PROGRESS)
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

    suspend fun reactToUpdates(lastSynchronized: Long) {
        val newEntries = dao.newLogEntries(lastSynchronized, deviceId)
        if(newEntries.isNotEmpty()) {
            _reactToUpdates?.invoke(newEntries)
        }
    }

    suspend fun bytesUsed(): Long {
        return dao.totalBytesUsed()
    }
    suspend fun hasChanges(): Boolean {
        val lastSynchronized = dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        return dao.countNewLogEntries(lastSynchronized, deviceId) > 0
    }

    val categoryName get() = category.name.lowercase()
    val dao get() = localDb.syncDao()
    val tableDefinitions get() = category.tables
    /** `PRAGMA user_version` of the local database. */
    suspend fun version(): Int = localDb.useReaderConnectionMarked { it.queryLong("PRAGMA user_version")!!.toInt() }
}

private suspend fun createTriggersForTable(
    db: PooledConnection,
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

    val deviceId = dbDef.deviceId
    val whenCondition = """
            WHEN (SELECT count(*) FROM SyncConfiguration WHERE keyName='${TRIGGERS_DISABLED_KEY}' AND booleanValue = 1 LIMIT 1) = 0
            """.trimIndent()

    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("NEW")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("NEW")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'DELETE', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
}

private suspend fun dropTriggersForTable(
    db: PooledConnection,
    tableDef: SyncableDatabaseDefinition.Table
) = db.run {
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_inserts")
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_updates")
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_deletes")
}


suspend fun createTriggers(dbDef: SyncableDatabaseAccessor<*>) = dbDef.localDb.useWriterConnectionMarked { db ->
    for(tableDef in dbDef.tableDefinitions) {
        createTriggersForTable(db, dbDef, tableDef)
    }
}

suspend fun dropTriggers(dbDef: SyncableDatabaseAccessor<*>) = dbDef.localDb.useWriterConnectionMarked { db ->
    for(tableDef in dbDef.tableDefinitions) {
        dropTriggersForTable(db, tableDef)
    }
}

private suspend fun writePatchData(
    db: PooledConnection,
    tableDef: SyncableDatabaseDefinition.Table,
    lastPatchWritten: Long
) = db.run {
    val table = tableDef.tableName
    val idField1 = tableDef.idField1
    val idField2 = tableDef.idField2
    val cols = columnNamesJoined(table, "patch")

    var where = idField1
    var select = "pe.entityId1"
    if (idField2 != null) {
        where = "($idField1,$idField2)"
        select = "pe.entityId1,pe.entityId2"
    }
    exec("""
            INSERT INTO patch.$table ($cols) SELECT $cols FROM $table WHERE $where IN 
            (SELECT $select FROM LogEntry pe WHERE tableName = '$table' AND type = 'UPSERT' 
            AND lastUpdated > $lastPatchWritten)
            """.trimIndent())
    exec("""
            INSERT INTO patch.LogEntry SELECT * FROM LogEntry 
            WHERE tableName = '$table' AND lastUpdated > $lastPatchWritten
            """.trimIndent())
}

private suspend fun readPatchData(
    db: PooledConnection,
    tableDef: SyncableDatabaseDefinition.Table,
) = db.run {
    val table = tableDef.tableName
    val idField1 = tableDef.idField1
    val idField2 = tableDef.idField2

    val colList = columnNames(table)
    val cols = colList.joinToString(",") { "`$it`" }
    val setValues = colList.filterNot {it == idField1 || it == idField2}.joinToString(",\n") { "`$it`=excluded.`$it`" }
    val amount = queryLong("SELECT COUNT(*) FROM patch.LogEntry WHERE tableName = '$table'")!!

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
    exec("""
            INSERT INTO $table ($cols)
            SELECT $cols FROM patch.$table 
            WHERE $idFields IN ${where("UPSERT")}
            ON CONFLICT DO UPDATE SET $setValues;
            """.trimIndent())

    // Let's fix all foreign key violations. Those will result if target object has been deleted here,
    // but patch still adds references
    exec("""
            DELETE FROM $table 
            WHERE rowId in (SELECT rowid FROM pragma_foreign_key_check('$table'));
            """.trimIndent()
    )

    // Delete all marked deletions from patch LogEntry table
    exec("""
            DELETE FROM $table WHERE $idFields IN ${where("DELETE")}
            """.trimIndent()
    )

    exec("""
            INSERT OR REPLACE INTO LogEntry SELECT * FROM patch.LogEntry pe 
            WHERE pe.tableName = '$table' AND ($select) IN ${where()}
            """.trimIndent()
    )
}

/**
 * Writes this device's log entries newer than the last patch, with the rows they name, into a new database
 * file of the same schema, gzipped. Null when there is nothing new. The local database attaches the new file as
 * `patch`; ATTACH and `foreign_keys` cannot change inside a transaction, so they come before and after it.
 */
suspend fun createPatchForDatabase(dbDef: SyncableDatabaseAccessor<*>, updateTimestamp: Boolean = true): File? {
    val lastPatchWritten = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY)?: 0
    val patchDbFile = File.createTempFile("created-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)

    val amountUpdated = dbDef.dao.countNewLogEntries(lastPatchWritten, dbDef.deviceId)
    if(amountUpdated == 0L) {
        Log.i(TAG, "No new entries ${dbDef.categoryName}")
        return null
    }
    // let's create empty database with correct schema first.
    createWithSchema(dbDef, patchDbFile)
    Log.i(TAG, "Creating patch for ${dbDef.categoryName}: $amountUpdated updated")
    dbDef.localDb.useWriterConnectionMarked { db ->
        // ATTACH inside the protected region: if anything fails after it, DETACH still runs (the pool has one
        // connection, and a schema left attached would fail every later sync until restart).
        db.withCleanup("PRAGMA patch.foreign_keys=ON;", "DETACH DATABASE patch") {
            db.exec("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
            db.exec("PRAGMA patch.foreign_keys=OFF;")
            db.inTransaction<Unit> {
                for (tableDef in dbDef.tableDefinitions) {
                    writePatchData(this, tableDef, lastPatchWritten)
                }
            }
        }
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

/** Opens [file] as a database of [dbDef]'s kind (creating it, or migrating it to the current schema) and closes it. */
private suspend fun createWithSchema(dbDef: SyncableDatabaseAccessor<*>, file: File) {
    val db = dbDef.dbFactory(file.absolutePath)
    try { db.useWriterConnection { } } finally { db.close() }
}

/**
 * Merges each gzipped patch into the local database, one transaction per patch: rows whose log entry is newer
 * than the local one are upserted or deleted, and the log entries are taken over. The sync triggers are
 * disabled (through SyncConfiguration, inside the same transaction) while the rows are written, so the merge is
 * not logged as a local change. A patch that fails is rolled back completely and the exception rethrown.
 */
suspend fun applyPatchesForDatabase(dbDef: SyncableDatabaseAccessor<*>, vararg patchFiles: File?) {
    for(gzippedPatchFile in patchFiles.filterNotNull()) {
        val patchDbFile = File.createTempFile("downloaded-patch-${dbDef.categoryName}-", ".sqlite3", CommonUtils.tmpDir)
        Log.i(TAG, "Applying patch file ${patchDbFile.name}")
        CommonUtils.gunzipFile(gzippedPatchFile, patchDbFile)
        // Let's apply possible migrations first
        createWithSchema(dbDef, patchDbFile)
        dbDef.localDb.useWriterConnectionMarked { db ->
            // ATTACH inside the protected region, as in createPatchForDatabase.
            db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE patch") {
                db.exec("ATTACH DATABASE '${patchDbFile.absolutePath}' AS patch")
                db.exec("PRAGMA foreign_keys=OFF;")
                try {
                    db.inTransaction<Unit> {
                        for (tableDef in dbDef.tableDefinitions) {
                            dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, true)
                            readPatchData(this, tableDef)
                            dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurred in applyPatchesForDatabase", e)
                    throw e
                }
            }
        }
        if(!CommonUtils.isDebugMode) {
            patchDbFile.delete()
        }
    }
}
