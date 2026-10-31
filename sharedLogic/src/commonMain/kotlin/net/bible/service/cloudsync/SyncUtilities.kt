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
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.service.cloudsync.canonical.CANONICAL_HEAL_MOVED_ROWS_KEY
import net.bible.service.cloudsync.canonical.CANONICAL_KEYS
import net.bible.service.cloudsync.canonical.CANONICAL_IDS_VERSION_KEY
import net.bible.service.cloudsync.canonical.CANONICAL_MARKER_PENDING_KEY
import net.bible.service.cloudsync.canonical.rekeyToCanonical
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
    // Carry the children of the parent rows in this patch, without their log entries, so a
    // device that deleted the parent can restore them if this edit wins.
    for ((parent, column) in tableDef.parents) {
        exec("""
            INSERT OR IGNORE INTO patch.$table ($cols) SELECT $cols FROM $table
            WHERE $column IN (SELECT id FROM patch.$parent)
            """.trimIndent())
    }
}

private suspend fun readPatchData(
    db: PooledConnection,
    tableDef: SyncableDatabaseDefinition.Table,
    canonical: Boolean = false, // callers pass it already limited to categories with canonical tables
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

    // Delete all marked deletions from patch LogEntry table. Before the upserts: a row deleted on the other device
    // and created again under the same natural key (a reset reading plan started again: ReadingPlan.planCode,
    // ReadingPlanStatus(planCode, planDay)) would otherwise be upserted onto the old local row by ON CONFLICT and
    // then deleted with it (#3902). An id is either upserted or deleted in one patch, never both.
    exec("""
            DELETE FROM $table WHERE $idFields IN ${where("DELETE")}
            """.trimIndent()
    )

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

    exec("""
            INSERT OR REPLACE INTO LogEntry SELECT * FROM patch.LogEntry pe 
            WHERE pe.tableName = '$table' AND ($select) IN ${where()}
            """.trimIndent()
    )

    // A row an old receiver dropped in the foreign key clean-up above keeps its log entry, so a re-send with the same
    // timestamp is skipped by the upsert. Put back every patch row whose UPSERT entry equals the local one (same
    // lastUpdated and sourceDevice) and that is missing here, then clean up again (#3906). `parents` is not used:
    // it also drives the delete triggers. A natural-key clash is ignored (INSERT OR IGNORE).
    if (canonical) {
        exec("""
            INSERT OR IGNORE INTO $table ($cols)
            SELECT $cols FROM patch.$table WHERE $idFields IN
            (SELECT $select FROM patch.LogEntry pe
              JOIN LogEntry me ON pe.entityId1 = me.entityId1 AND pe.entityId2 = me.entityId2 AND pe.tableName = me.tableName
              WHERE pe.tableName = '$table' AND pe.type = 'UPSERT' AND me.type = 'UPSERT'
              AND pe.lastUpdated = me.lastUpdated AND pe.sourceDevice = me.sourceDevice)
            """.trimIndent())
        // Nothing restored: nothing new can violate a foreign key either.
        if (queryLong("SELECT changes()") != 0L) {
            exec("""
                DELETE FROM $table
                WHERE rowId in (SELECT rowid FROM pragma_foreign_key_check('$table'));
                """.trimIndent()
            )
        }
    }

    // Restore carried children whose parents exist here, unless they were deleted on their own.
    // This brings back the children of a parent whose delete lost to a newer edit.
    if (tableDef.parents.isNotEmpty()) {
        val parentsExist = tableDef.parents.entries.joinToString(" AND ") { (parent, column) ->
            "$column IN (SELECT id FROM $parent)"
        }
        exec("""
            INSERT OR IGNORE INTO $table ($cols)
            SELECT $cols FROM patch.$table
            WHERE $parentsExist AND $idFields NOT IN
            (SELECT $select FROM LogEntry pe WHERE pe.tableName = '$table' AND pe.type = 'DELETE')
            """.trimIndent())
    }
}

/** Prefix of the `SyncConfiguration` keys in a patch file that tell the receiver which devices have healed (#3906). */
const val CANONICAL_HEALED_DEVICE_PREFIX = "canonicalIdsHealedDevice:"

/**
 * Prefix of the patch-file keys that say the sender's heal moved rows. A receiver that holds the same natural key under
 * a non-canonical id drops the sender's children, which is exactly when its own heal moves rows, so the receiver
 * of such a marker must send its rows again.
 */
const val CANONICAL_MOVED_ROWS_DEVICE_PREFIX = "canonicalHealMovedRowsDevice:"

/** Prefix of the local `SyncConfiguration` keys that record the healed peers already seen (#3906). */
const val CANONICAL_PEER_SEEN_PREFIX = "canonicalPeerSeen:"

/**
 * Writes this device's log entries newer than the last patch, with the rows they name, into a new database
 * file of the same schema, gzipped. Null when there is nothing new. The local database attaches the new file as
 * `patch`; ATTACH and `foreign_keys` cannot change inside a transaction, so they come before and after it.
 *
 * With [canonical] (the default) a healed device (`canonicalIdsVersion` = 1) writes the marker
 * `canonicalIdsHealedDevice:<deviceId>` into the patch's own `SyncConfiguration` (the local configuration is never
 * copied into a patch), and a device whose marker is still pending produces a marker-only patch (full schema, empty
 * `LogEntry`) even with nothing new. The caller clears the pending flag after a successful upload
 * ([clearCanonicalMarkerPending]). `canonical = false` is the previous behaviour exactly: it stands for an old app
 * version and is the seam tests use for one.
 */
suspend fun createPatchForDatabase(dbDef: SyncableDatabaseAccessor<*>, env: AppEnvironment, updateTimestamp: Boolean = true, canonical: Boolean = true): Path? {
    val lastPatchWritten = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY)?: 0
    val patchDbFile = createTempFile(env.tmpDir.toPath(), "created-patch-${dbDef.categoryName}-", ".sqlite3")

    val amountUpdated = dbDef.dao.countNewLogEntries(lastPatchWritten, dbDef.deviceId)
    // The version is only written by healCanonicalIds and the pending flag by it or by a re-send in noteHealedPeers;
    // both skip categories without canonical tables, so those never carry a marker.
    val markerPending = canonical && dbDef.dao.getBoolean(CANONICAL_MARKER_PENDING_KEY) == true
    val writeMarker = canonical && dbDef.dao.getLong(CANONICAL_IDS_VERSION_KEY) == 1L
    val writeMovedMarker = writeMarker && dbDef.dao.getBoolean(CANONICAL_HEAL_MOVED_ROWS_KEY) == true
    if(amountUpdated == 0L && !markerPending) {
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
                for (key in listOfNotNull(
                    CANONICAL_HEALED_DEVICE_PREFIX.takeIf { writeMarker },
                    CANONICAL_MOVED_ROWS_DEVICE_PREFIX.takeIf { writeMovedMarker },
                )) {
                    exec(
                        "INSERT OR REPLACE INTO patch.SyncConfiguration (keyName, stringValue, longValue, booleanValue) " +
                            "VALUES (?, NULL, 1, NULL)", key + dbDef.deviceId,
                    )
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
 *
 * With [canonical] (the default), each patch file is first re-keyed to canonical ids on its own connection when this
 * device has healed (so a patch written before the sender or this device healed lines up with the local rows), and the healed-device
 * markers of the patch are read from its `SyncConfiguration`. After the patch is applied, a marker device that is
 * not this device and not yet recorded (`canonicalPeerSeen:<id>`) is recorded; when this device's heal moved rows
 * (`canonicalHealMovedRows`) or the peer's marker says its heal moved rows (`canonicalHealMovedRowsDevice:<id>`),
 * `LAST_PATCH_WRITTEN_KEY` is lowered to 0 and `canonicalMarkerPending` set, so the next patch carries the whole log
 * again even without own new entries (the re-send; the entries keep their timestamps and are never re-stamped). `canonical = false` is the
 * previous behaviour exactly: no re-key and no marker handling.
 */
suspend fun applyPatchesForDatabase(dbDef: SyncableDatabaseAccessor<*>, env: AppEnvironment, vararg patchFiles: Path?, canonical: Boolean = true) {
    for(gzippedPatchFile in patchFiles.filterNotNull()) {
        val patchDbFile = createTempFile(env.tmpDir.toPath(), "downloaded-patch-${dbDef.categoryName}-", ".sqlite3")
        Log.i(TAG, "Applying patch file ${patchDbFile.name}")
        ArchiveUtils.gunzipFile(gzippedPatchFile, patchDbFile)
        // Let's apply possible migrations first
        createWithSchema(dbDef, patchDbFile)
        val healedPeers = if (canonical) rekeyIncomingPatch(dbDef, patchDbFile) else PatchMarkers()
        dbDef.localDb.useWriterConnectionMarked { db ->
            // ATTACH inside the protected region, as in createPatchForDatabase.
            db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE patch") {
                db.exec("ATTACH DATABASE '$patchDbFile' AS patch")
                db.exec("PRAGMA foreign_keys=OFF;")
                try {
                    db.inTransaction<Unit> {
                        for (tableDef in dbDef.tableDefinitions) {
                            dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, true)
                            readPatchData(this, tableDef, canonical && dbDef.category in CANONICAL_KEYS)
                            dbDef.dao.setConfig(TRIGGERS_DISABLED_KEY, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurred in applyPatchesForDatabase", e)
                    throw e
                }
            }
        }
        // After the apply transaction, and nothing in the same sync cycle writes the watermark later than this.
        noteHealedPeers(dbDef, healedPeers)
        if(!env.isDebugMode) {
            FileSystem.SYSTEM.delete(patchDbFile)
        }
    }
}

/** The healed-device markers of a patch: [healed] devices, and those of them whose heal [moved] rows. */
private class PatchMarkers(val healed: List<String> = emptyList(), val moved: Set<String> = emptySet())

/**
 * Opens the downloaded patch [patchDbFile] (already migrated to the current schema) on its own connection, where it
 * is the main schema, reads its healed-device markers and, when this device has healed, re-keys its canonical tables
 * to canonical ids in one transaction ([rekeyToCanonical]; the log entries of the moved rows follow with their timestamps). The patch's
 * sync triggers are switched off for the re-key through its own `SyncConfiguration`, so nothing is logged into it.
 *
 * @return the markers of the patch
 */
private suspend fun rekeyIncomingPatch(dbDef: SyncableDatabaseAccessor<*>, patchDbFile: Path): PatchMarkers =
    openSqlite(patchDbFile.toString()).use { conn ->
        conn.exec("PRAGMA foreign_keys = OFF")
        // A device that has not healed yet keeps its random ids, so a canonical id from the patch would not line up
        // with its rows; its later heal re-keys the lot (and lowers the watermark itself).
        val rekey = dbDef.dao.getLong(CANONICAL_IDS_VERSION_KEY) == 1L
        fun markers(prefix: String) = conn.queryRows(
            "SELECT keyName FROM SyncConfiguration WHERE keyName LIKE ? AND longValue = 1", "$prefix%",
        ) { it.getText(0).removePrefix(prefix) }
        val markers = PatchMarkers(markers(CANONICAL_HEALED_DEVICE_PREFIX), markers(CANONICAL_MOVED_ROWS_DEVICE_PREFIX).toSet())
        conn.inTransaction {
            exec("INSERT OR REPLACE INTO SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, 1)", TRIGGERS_DISABLED_KEY)
            if (rekey) rekeyToCanonical(this, dbDef.category)
            exec("INSERT OR REPLACE INTO SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, 0)", TRIGGERS_DISABLED_KEY)
        }
        markers
    }

/**
 * Records healed peers seen for the first time and lowers the watermark (re-send) when this device's heal moved rows
 * or the peer's marker says its heal did. A lowered watermark also sets the marker-pending flag, so a patch is written
 * even when this device has no log entries of its own.
 */
private suspend fun noteHealedPeers(dbDef: SyncableDatabaseAccessor<*>, markers: PatchMarkers) {
    for (peer in markers.healed.distinct()) {
        if (peer == dbDef.deviceId) continue
        val seenKey = CANONICAL_PEER_SEEN_PREFIX + peer
        if (dbDef.dao.getLong(seenKey) == 1L) continue
        dbDef.dao.setConfig(seenKey, 1L)
        if (peer in markers.moved || dbDef.dao.getBoolean(CANONICAL_HEAL_MOVED_ROWS_KEY) == true) {
            dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
            // The re-send is built only when a patch is produced, and with no own log entries (`countNewLogEntries` counts
            // this device's own only) just a pending marker makes one; the patch then carries the whole log from 0.
            dbDef.dao.setConfig(CANONICAL_MARKER_PENDING_KEY, true)
        }
    }
}

/** Called after a patch was uploaded: the healed marker has reached the peers, so it is no longer pending. */
suspend fun clearCanonicalMarkerPending(dbDef: SyncableDatabaseAccessor<*>) {
    if (dbDef.dao.getBoolean(CANONICAL_MARKER_PENDING_KEY) == true) dbDef.dao.setConfig(CANONICAL_MARKER_PENDING_KEY, false)
}

/** Records completion before notifying consumers. */
fun recordSyncFinished(settings: AppSettings, cloudSync: CloudSync, now: Long = Clock.System.now().toEpochMilliseconds()) {
    settings.setLong("globalLastSynchronized", now)
    cloudSync.notifySyncRunning(false)
}
