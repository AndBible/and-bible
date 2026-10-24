/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.merge

import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.createTempFile
import net.bible.service.db.exec
import net.bible.service.db.inTransaction
import net.bible.service.db.openSqlite
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.AppEnvironment
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Merges the device's [snapshot] (made by [prepareMergeSnapshot]) into the local database, which already holds
 * the cloud's data (the fetched initial file plus every cloud patch), and marks the device's winning rows for upload.
 *
 * 1. [rekeyNaturalKeys] gives snapshot rows whose natural key exists locally under another id the local id.
 * 2. The snapshot is gzipped and applied with the unchanged [applyPatchesForDatabase]: per row the snapshot's
 *    log entry wins only when the local side has no entry or a strictly older one (a tie keeps the cloud row).
 * 3. Winners are re-stamped: `lastUpdated = mergeTime`, `sourceDevice = dbDef.deviceId`.
 *
 * **How a winning entry is identified.** The apply copies the entries of winning rows into the local `LogEntry`
 * table (`INSERT OR REPLACE`) and touches no other entry, because triggers are off. So the local log is read
 * before and after the apply, and an entry that is new or differs in type, `lastUpdated` or `sourceDevice` after
 * the apply is one the snapshot won. Entries the device lost (the cloud row was newer or tied) are byte-identical
 * before and after, and are neither re-stamped nor uploaded again. This does not depend on the snapshot's
 * `sourceDevice`, which may be another device for entries that were synced earlier.
 *
 * The next `createPatchForDatabase` then carries exactly the winners (their stamp is above
 * `LAST_PATCH_WRITTEN_KEY`). Throws [IllegalStateException] before touching anything when `LAST_PATCH_WRITTEN_KEY`
 * is not below [mergeTime], since the winners would never be uploaded.
 *
 * The re-stamp is a separate transaction from the apply (`applyPatchesForDatabase` stays unchanged): a crash between
 * them leaves the winners un-uploaded, so the caller must not persist folder or sync state until this returns, so
 * that the failure restore covers the gap. Precondition: no concurrent writers during the call (a user edit between
 * the before and after log reads would be counted as a winner); callers hold the `replacingDatabases` block.
 */
suspend fun mergeSnapshotIntoLocal(dbDef: SyncableDatabaseAccessor<*>, env: AppEnvironment, snapshot: Path, mergeTime: Long) {
    val lastPatchWritten = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY) ?: 0L
    check(lastPatchWritten < mergeTime) {
        "LAST_PATCH_WRITTEN_KEY ($lastPatchWritten) must be below the merge time ($mergeTime), or the merged rows are never uploaded"
    }

    val before = readLog(dbDef).map { it.signature }.toHashSet()
    openSqlite(dbDef.localDbPath, readOnly = true).use { rekeyNaturalKeys(dbDef.category, snapshot, it) }

    val gz = createTempFile(env.tmpDir.toPath(), "merge-snapshot-${dbDef.categoryName}-", ".sqlite3.gz")
    try {
        ArchiveUtils.gzipFile(snapshot, gz)
        applyPatchesForDatabase(dbDef, env, gz)
    } finally {
        FileSystem.SYSTEM.delete(gz)
    }

    val winners = readLog(dbDef).filter { it.signature !in before }.map { it.rowId }
    if (winners.isEmpty()) return
    dbDef.localDb.useWriterConnectionMarked { db ->
        db.inTransaction<Unit> {
            for (chunk in winners.chunked(500)) {
                exec(
                    "UPDATE LogEntry SET lastUpdated = ?, sourceDevice = ? WHERE rowid IN (${chunk.joinToString(",")})",
                    mergeTime, dbDef.deviceId,
                )
            }
        }
    }
}

private class LogRow(val rowId: Long, val signature: String)

/** Every local log entry with a signature of its whole content (the key columns in hex, they may be blobs or text). */
private suspend fun readLog(dbDef: SyncableDatabaseAccessor<*>): List<LogRow> = dbDef.localDb.useWriterConnectionMarked { db ->
    db.usePrepared(
        "SELECT rowid, tableName || '|' || hex(entityId1) || '|' || hex(entityId2) || '|' || type || '|' || lastUpdated || '|' || sourceDevice FROM LogEntry"
    ) { st -> buildList { while (st.step()) add(LogRow(st.getLong(0), st.getText(1))) } }
}
