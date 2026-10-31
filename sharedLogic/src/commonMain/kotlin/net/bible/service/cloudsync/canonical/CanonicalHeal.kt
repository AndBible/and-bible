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
package net.bible.service.cloudsync.canonical

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLiteConnection
import net.bible.android.database.IdType
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.TRIGGERS_DISABLED_KEY
import net.bible.service.cloudsync.merge.NATURAL_KEYS
import net.bible.service.cloudsync.merge.NaturalKey
import net.bible.service.cloudsync.merge.hasTable
import net.bible.service.cloudsync.merge.renameIds
import net.bible.service.db.exec
import net.bible.service.db.inTransaction
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked

/** `SyncConfiguration` key: the canonical-id scheme version this database has been healed to. */
const val CANONICAL_IDS_VERSION_KEY = "canonicalIdsVersion"

/** `SyncConfiguration` key: the heal re-keyed rows, so peers must receive the rows again (re-send). */
const val CANONICAL_HEAL_MOVED_ROWS_KEY = "canonicalHealMovedRows"

/** `SyncConfiguration` key: this device still has to announce itself with a marker patch. */
const val CANONICAL_MARKER_PENDING_KEY = "canonicalMarkerPending"

/**
 * A natural key whose rows get the id derived from the key ([idOf]).
 *
 * @property key the natural key with the child columns that follow a re-key
 * @property idOf the canonical id for the key column values in `key.keyColumns` order, decoded exactly as the
 * helpers in `CanonicalIds.kt` expect them: text as is, integers as decimal strings, 16-byte blobs as `IdType.toString()`
 */
data class CanonicalKey(val key: NaturalKey, val idOf: (List<String>) -> IdType)

private val CANONICAL_ID_FUNCTIONS: Map<String, (List<String>) -> IdType> = mapOf(
    "ReadingPlan" to { k -> canonicalId("ReadingPlan", *k.toTypedArray()) },
    "ReadingPlanStatus" to { k -> canonicalId("ReadingPlanStatus", *k.toTypedArray()) },
    "MyDocument" to { k -> canonicalId("MyDocument", *k.toTypedArray()) },
    "MemorizedVerse" to { k -> canonicalId("MemorizedVerse", *k.toTypedArray()) },
    "LlmConfiguredModel" to { k -> canonicalId("LlmConfiguredModel", *k.toTypedArray()) },
)

/**
 * The tables whose rows get canonical ids, per category, with the child columns that follow. Taken from
 * [NATURAL_KEYS] restricted to the five canonical tables (`MyDocumentPage` and `LlmUsageRecord` keep their ids).
 */
val CANONICAL_KEYS: Map<SyncableDatabaseDefinition, List<CanonicalKey>> =
    NATURAL_KEYS.mapValues { (_, keys) ->
        keys.mapNotNull { k -> CANONICAL_ID_FUNCTIONS[k.table]?.let { CanonicalKey(k, it) } }
    }.filterValues { it.isNotEmpty() }

/** Test seam: when set, [healCanonicalIds] calls it after the rename and before commit (to prove the rollback). */
var canonicalHealFailureInjector: (() -> Unit)? = null

/**
 * Re-keys every row of the canonical tables of [category] in the main schema of [conn] to the id derived from its
 * natural key, with its child rows and `LogEntry` entries ([renameIds]; timestamps and source devices of the log
 * entries stay untouched). Works on unprefixed tables, so [conn] may be a local database or a patch file; tables
 * absent from the file are skipped. The caller owns the transaction, `foreign_keys` and the sync triggers.
 *
 * @return the number of moved parent rows
 */
@Suppress("RedundantSuspendModifier")
suspend fun rekeyToCanonical(conn: SQLiteConnection, category: SyncableDatabaseDefinition): Int {
    val keys = CANONICAL_KEYS[category].orEmpty()
    val tableDefs = category.tables.associateBy { it.tableName }
    var moved = 0
    for (canonical in keys) {
        val key = canonical.key
        if (!conn.hasTable(key.table)) continue
        val cols = key.keyColumns
        val select = "SELECT quote(${key.idColumn}), " + cols.joinToString { "quote($it)" } + ", " + cols.joinToString()
        val rows = conn.queryRows("$select FROM ${key.table}") { st ->
            val id = st.getText(0)
            val values = cols.indices.map { i ->
                val c = cols.size + 1 + i
                when (st.getColumnType(c)) {
                    SQLITE_DATA_INTEGER -> st.getLong(c).toString()
                    SQLITE_DATA_BLOB -> IdType.fromByteArray(st.getBlob(c))?.toString() ?: ""
                    else -> st.getText(c)
                }
            }
            id to values
        }
        val mapping = LinkedHashMap<String, String>() // old id literal -> canonical id literal
        for ((oldId, values) in rows) {
            val newId = "X'${canonical.idOf(values).toHex().uppercase()}'"
            if (newId != oldId) mapping[oldId] = newId
        }
        if (mapping.isEmpty()) continue
        // The unique natural-key index makes this impossible; a clash would overwrite another row.
        val blocked = rows.map { it.first }.filter { it !in mapping && it in mapping.values }
        check(blocked.isEmpty()) { "Cannot re-key ${key.table} to canonical ids: id ${blocked.first()} is held by another row" }
        conn.renameIds(key, tableDefs, mapping)
        moved += mapping.size
    }
    return moved
}

/**
 * Local heal of #3906: re-keys the rows of [dbDef] that were created with random ids to their canonical ids,
 * removes `UPSERT` log entries of canonical rows that do not exist, and records the result in `SyncConfiguration`:
 * [CANONICAL_IDS_VERSION_KEY] = 1, [CANONICAL_HEAL_MOVED_ROWS_KEY] and `LAST_PATCH_WRITTEN_KEY` = 0 when rows moved
 * (so the moved rows are written again), and always [CANONICAL_MARKER_PENDING_KEY]. Log entries keep their
 * `lastUpdated` and `sourceDevice`: the sync triggers are off during the heal. All of it is one transaction; a
 * failure leaves the database as it was.
 *
 * Skipped (returns 0) when already healed, unless [force] is set (after a cloud fetch or a merge, which can bring
 * rows with random ids back).
 *
 * The work runs on a second raw connection (as `mergeSnapshotIntoLocal` does) while the Room writer is held, so
 * other Room writes wait. Room's invalidation does not see the change; callers heal before loading the data.
 *
 * @return the number of moved parent rows
 */
suspend fun healCanonicalIds(dbDef: SyncableDatabaseAccessor<*>, force: Boolean = false): Int {
    if (!force && dbDef.dao.getLong(CANONICAL_IDS_VERSION_KEY) == 1L) return 0
    val keys = CANONICAL_KEYS[dbDef.category].orEmpty()
    return dbDef.localDb.useWriterConnectionMarked {
        openSqlite(dbDef.localDbPath).use { conn ->
            conn.exec("PRAGMA busy_timeout = 10000")
            conn.exec("PRAGMA foreign_keys = OFF")
            try {
                conn.inTransaction {
                    setConfig(TRIGGERS_DISABLED_KEY, boolean = true)
                    val moved = rekeyToCanonical(this, dbDef.category)
                    canonicalHealFailureInjector?.invoke()
                    for (canonical in keys) {
                        val t = canonical.key.table
                        exec(
                            "DELETE FROM LogEntry WHERE tableName = ? AND type = 'UPSERT' " +
                                "AND entityId1 NOT IN (SELECT ${canonical.key.idColumn} FROM $t)", t,
                        )
                    }
                    setConfig(CANONICAL_IDS_VERSION_KEY, long = 1L)
                    if (moved > 0) {
                        setConfig(CANONICAL_HEAL_MOVED_ROWS_KEY, boolean = true)
                        setConfig(LAST_PATCH_WRITTEN_KEY, long = 0L)
                    }
                    setConfig(CANONICAL_MARKER_PENDING_KEY, boolean = true)
                    setConfig(TRIGGERS_DISABLED_KEY, boolean = false)
                    moved
                }
            } finally {
                runCatching { conn.exec("PRAGMA foreign_keys = ON") }
            }
        }
    }
}

private fun SQLiteConnection.setConfig(key: String, long: Long? = null, boolean: Boolean? = null) =
    exec("INSERT OR REPLACE INTO SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, ?, ?)", key, long, boolean)
