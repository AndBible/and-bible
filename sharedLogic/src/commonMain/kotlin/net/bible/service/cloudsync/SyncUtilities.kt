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

import net.bible.sharedcore.log.Log
import androidx.room3.PooledConnection
import androidx.room3.useWriterConnection
import net.bible.android.database.LogEntry
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.service.common.ArchiveUtils
import net.bible.service.db.columnNames
import net.bible.service.db.columnNamesJoined
import net.bible.service.db.exec
import net.bible.service.db.inTransaction
import net.bible.service.db.queryLong
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.withCleanup
import net.bible.service.common.createTempFile
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock


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
suspend fun createPatchForDatabase(dbDef: SyncableDatabaseAccessor<*>, env: AppEnvironment, updateTimestamp: Boolean = true): Path? {
    val lastPatchWritten = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY)?: 0
    val patchDbFile = createTempFile(env.tmpDir.toPath(), "created-patch-${dbDef.categoryName}-", ".sqlite3")

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
            db.exec("ATTACH DATABASE '$patchDbFile' AS patch")
            db.exec("PRAGMA patch.foreign_keys=OFF;")
            db.inTransaction<Unit> {
                for (tableDef in dbDef.tableDefinitions) {
                    writePatchData(this, tableDef, lastPatchWritten)
                }
            }
        }
    }

    val gzippedOutput = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
    Log.i(TAG, "Saving patch file ${dbDef.categoryName}")
    ArchiveUtils.gzipFile(patchDbFile, gzippedOutput)

    if(!env.isDebugMode) {
        FileSystem.SYSTEM.delete(patchDbFile)
    }
    if(updateTimestamp) {
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, Clock.System.now().toEpochMilliseconds())
    }
    return gzippedOutput
}

/** Opens [file] as a database of [dbDef]'s kind (creating it, or migrating it to the current schema) and closes it. */
private suspend fun createWithSchema(dbDef: SyncableDatabaseAccessor<*>, file: Path) {
    val db = dbDef.dbFactory(file.toString())
    try { db.useWriterConnection { } } finally { db.close() }
}

/**
 * Merges each gzipped patch into the local database, one transaction per patch: rows whose log entry is newer
 * than the local one are upserted or deleted, and the log entries are taken over. The sync triggers are
 * disabled (through SyncConfiguration, inside the same transaction) while the rows are written, so the merge is
 * not logged as a local change. A patch that fails is rolled back completely and the exception rethrown.
 */
suspend fun applyPatchesForDatabase(dbDef: SyncableDatabaseAccessor<*>, env: AppEnvironment, vararg patchFiles: Path?) {
    for(gzippedPatchFile in patchFiles.filterNotNull()) {
        val patchDbFile = createTempFile(env.tmpDir.toPath(), "downloaded-patch-${dbDef.categoryName}-", ".sqlite3")
        Log.i(TAG, "Applying patch file ${patchDbFile.name}")
        ArchiveUtils.gunzipFile(gzippedPatchFile, patchDbFile)
        // Let's apply possible migrations first
        createWithSchema(dbDef, patchDbFile)
        dbDef.localDb.useWriterConnectionMarked { db ->
            // ATTACH inside the protected region, as in createPatchForDatabase.
            db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE patch") {
                db.exec("ATTACH DATABASE '$patchDbFile' AS patch")
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
        if(!env.isDebugMode) {
            FileSystem.SYSTEM.delete(patchDbFile)
        }
    }
}

/** Records completion before notifying consumers. */
fun recordSyncFinished(settings: AppSettings, cloudSync: CloudSync, now: Long = Clock.System.now().toEpochMilliseconds()) {
    settings.setLong("globalLastSynchronized", now)
    cloudSync.notifySyncRunning(false)
}
