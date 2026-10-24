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

import androidx.sqlite.SQLiteConnection
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import okio.Path

/**
 * A synced table whose rows are unique by [keyColumns] as well as by the id in [idColumn]. Two independent devices
 * create such rows with different ids, so a merge must make the ids equal before the rows meet.
 *
 * @property children (childTable, foreignKeyColumn) pairs whose column holds this table's [idColumn] value; they
 * are rewritten together with it. A child that is itself keyed by that column in the change log follows too.
 */
data class NaturalKey(
    val table: String,
    val keyColumns: List<String>,
    val idColumn: String = "id",
    val children: List<Pair<String, String>> = emptyList(),
    /** For a child with a unique index over its foreign key column plus these columns: child table -> the other columns. */
    val childUnique: Map<String, List<String>> = emptyMap(),
)

/**
 * The natural keys per category, parents before children (a child's key may contain the parent's id column, which
 * is already rewritten when the child is processed). Derived from the unique indexes of the entities and the
 * schema exports (`sharedData/schemas`); every other unique index there is the primary key itself.
 */
val NATURAL_KEYS: Map<SyncableDatabaseDefinition, List<NaturalKey>> = mapOf(
    SyncableDatabaseDefinition.READINGPLANS to listOf(
        NaturalKey("ReadingPlan", listOf("planCode")),
        NaturalKey("ReadingPlanStatus", listOf("planCode", "planDay")),
    ),
    SyncableDatabaseDefinition.MYDOCUMENTS to listOf(
        NaturalKey("MyDocument", listOf("initials"), children = listOf("MyDocumentPage" to "documentId")),
        NaturalKey(
            "MyDocumentPage", listOf("documentId", "pageKey"),
            children = listOf("MyDocumentPageContent" to "pageId", "AiPageCacheEntry" to "pageId"),
        ),
    ),
    SyncableDatabaseDefinition.PROGRESS to listOf(
        NaturalKey("MemorizedVerse", listOf("kjvOrdinal")),
    ),
    SyncableDatabaseDefinition.AI_SETTINGS to listOf(
        NaturalKey(
            "LlmConfiguredModel", listOf("providerConfigId", "modelId"),
            children = listOf(
                "AgentPrompt" to "configuredModelId",
                "BuiltinPromptOverride" to "configuredModelId",
                "GlobalAiSettings" to "defaultModelId",
                // No foreign key, but the row belongs to the model.
                "LlmUsageRecord" to "configuredModelId",
            ),
            childUnique = mapOf("LlmUsageRecord" to listOf("deviceId")),
        ),
        // Extra unique index (configuredModelId, deviceId): one row per device per model. It only collides with
        // the snapshot of the same device, e.g. one that synced before, but then it would break the apply.
        NaturalKey("LlmUsageRecord", listOf("configuredModelId", "deviceId")),
    ),
)

/**
 * Rewrites the ids in the [snapshot] file (made by [prepareMergeSnapshot]) so that every row whose natural key
 * exists in [local] (the freshly fetched cloud database) under another id gets the local id. The row's children
 * and the `LogEntry` rows of the snapshot follow, chained re-keys included (`MyDocument` -> `MyDocumentPage` ->
 * content and cache rows). Rows whose key is absent locally are left unchanged, Renames run in two phases per
 * table, so swaps and chains work. If a local id is held by a snapshot row that is not re-keyed itself, it throws
 * (the transaction is rolled back, the snapshot stays as it was). [local] is only read.
 */
@Suppress("RedundantSuspendModifier")
suspend fun rekeyNaturalKeys(category: SyncableDatabaseDefinition, snapshot: Path, local: SQLiteConnection) {
    val keys = NATURAL_KEYS[category].orEmpty()
    if (keys.isEmpty()) return
    val tableDefs = category.tables.associateBy { it.tableName }
    openSqlite(snapshot.toString()).use { snap ->
        // A parent is renamed before its children, so enforcement would reject the intermediate state.
        snap.exec("PRAGMA foreign_keys = OFF")
        snap.exec("BEGIN")
        try {
            for (key in keys) {
                if (!snap.hasTable(key.table) || !local.hasTable(key.table)) continue
                rekeyTable(key, tableDefs, snap, local)
            }
            snap.exec("COMMIT")
        } catch (e: Throwable) {
            runCatching { snap.exec("ROLLBACK") }
            throw e
        }
    }
}

private fun SQLiteConnection.hasTable(name: String): Boolean =
    queryRows("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", name) { 1 }.isNotEmpty()

/**
 * Renames the rows of [key] in two phases so that swaps and chains (A: X->Y while B holds Y and goes to Z) cannot
 * collide on the primary key or on a unique index: first every re-keyed row (with its children and log entries)
 * gets a temporary id, then its final id. A target id held by a snapshot row that is not itself re-keyed cannot
 * be resolved here, and applying the snapshot with it would overwrite the cloud row, so it throws.
 *
 * All values below are SQL literals produced by SQLite's own `quote()` (blobs as `X'..'`, text escaped), so they
 * compare across the two files whatever the column type is and can be spliced back into a statement safely.
 */
private fun rekeyTable(
    key: NaturalKey,
    tableDefs: Map<String, SyncableDatabaseDefinition.Table>,
    snap: SQLiteConnection,
    local: SQLiteConnection,
) {
    val select = "SELECT quote(${key.idColumn}), " + key.keyColumns.joinToString { "quote($it)" } + " FROM ${key.table}"
    val readRow = { st: androidx.sqlite.SQLiteStatement ->
        st.getText(0) to List(key.keyColumns.size) { i -> st.getText(i + 1) }
    }
    val localIds = local.queryRows(select, row = readRow).associate { (id, k) -> k to id }
    val snapshotRows = snap.queryRows(select, row = readRow)
    val mapping = LinkedHashMap<String, String>() // old id -> new id
    for ((oldId, naturalKey) in snapshotRows) {
        val newId = localIds[naturalKey] ?: continue
        if (newId != oldId) mapping[oldId] = newId
    }
    if (mapping.isEmpty()) return
    val blocked = snapshotRows.map { it.first }.filter { it !in mapping && it in mapping.values }
    check(blocked.isEmpty()) {
        "Cannot re-key ${key.table}: id ${blocked.first()} of the cloud row is used by a snapshot row with another natural key"
    }
    val temporary = mapping.keys.associateWith { snap.queryRows("SELECT quote(randomblob(16))") { it.getText(0) }.single() }
    fun move(from: String, to: String, last: Boolean) {
        snap.exec("UPDATE ${key.table} SET ${key.idColumn} = $to WHERE ${key.idColumn} = $from")
        snap.followLog(tableDefs[key.table], key.table, key.idColumn, from, to)
        for ((child, column) in key.children) {
            if (!snap.hasTable(child)) continue
            if (last) snap.dropStaleSiblings(child, column, from, to, key.childUnique[child])
            snap.exec("UPDATE $child SET $column = $to WHERE $column = $from")
            snap.followLog(tableDefs[child], child, column, from, to)
        }
    }
    for ((oldId, _) in mapping) move(oldId, temporary.getValue(oldId), last = false)
    for ((oldId, newId) in mapping) move(temporary.getValue(oldId), newId, last = true)
}

/**
 * Before [child] rows of [from] move to [to]: a row already at [to] (it belongs to a model the snapshot no longer
 * has) that shares the [uniqueColumns] values with a moving row would break the child's unique index. It is
 * stale next to the moving row, so it and its log entries are dropped.
 */
private fun SQLiteConnection.dropStaleSiblings(child: String, column: String, from: String, to: String, uniqueColumns: List<String>?) {
    if (uniqueColumns == null) return
    val same = uniqueColumns.joinToString(" AND ") { "m.$it = s.$it" }
    val stale = "SELECT s.rowid FROM $child s WHERE s.$column = $to AND EXISTS (SELECT 1 FROM $child m WHERE m.$column = $from AND $same)"
    exec("DELETE FROM LogEntry WHERE tableName = ? AND entityId1 IN (SELECT s.id FROM $child s WHERE s.rowid IN ($stale))", child)
    exec("DELETE FROM $child WHERE rowid IN ($stale)")
}

/** Moves the `LogEntry` rows of [table] that name the old id through [column] (its id field 1 and/or 2). */
private fun SQLiteConnection.followLog(
    def: SyncableDatabaseDefinition.Table?, table: String, column: String, oldId: String, newId: String,
) {
    if (def == null) return
    // OR REPLACE: a stale entry that already sits on the new id is superseded by the one that belongs to the row.
    if (def.idField1 == column) {
        exec("UPDATE OR REPLACE LogEntry SET entityId1 = $newId WHERE tableName = ? AND entityId1 = $oldId", table)
    }
    if (def.idField2 == column) {
        exec("UPDATE OR REPLACE LogEntry SET entityId2 = $newId WHERE tableName = ? AND entityId2 = $oldId", table)
    }
}
