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
package net.bible.service.cloudsync

import androidx.room3.PooledConnection
import net.bible.service.cloudsync.merge.NATURAL_KEYS
import net.bible.service.cloudsync.merge.NaturalKey
import net.bible.service.db.exec
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.sharedcore.log.Log


/**
 * Prefix of the local `SyncConfiguration` rows that remember an incoming id aliased to a local row (#3906):
 * `syncIdAlias:<table>:<hex of the foreign id>` with `stringValue` = hex of the local id. `SyncConfiguration` is
 * never synced, so no schema change and nothing an older client could see.
 */
const val ID_ALIAS_PREFIX = "syncIdAlias:"

private const val ALIAS_TAG = "IncomingIdAliases"

/** The natural-key tables whose foreign ids are aliased to the local row of the same key. */
private val ALIASED_TABLES = setOf("ReadingPlan", "ReadingPlanStatus", "MyDocument", "MemorizedVerse", "LlmConfiguredModel")

/** [NATURAL_KEYS] restricted to [ALIASED_TABLES], per category (`MyDocumentPage` and `LlmUsageRecord` never collide). */
private val ALIASED_KEYS: Map<SyncableDatabaseDefinition, List<NaturalKey>> =
    NATURAL_KEYS.mapValues { (_, keys) -> keys.filter { it.table in ALIASED_TABLES } }.filterValues { it.isNotEmpty() }

/**
 * Receive-side id mapping of #3906. Two devices that each created "the same" natural-key row (an AI Documents book, a
 * reading plan, ...) hold it under different ids. Rewrites the incoming rows in the attached schema [schema] (a
 * downloaded patch or an import file, writable, inside the caller's transaction) so that they name the local id:
 *
 * - An incoming row of an aliased table whose natural key matches a local row with another id is renamed to the local
 *   id, with its `LogEntry` entries (timestamps kept, so last-writer-wins decides as for a shared id) and every child
 *   column of [NATURAL_KEYS] that references it. The pair is stored durably ([ID_ALIAS_PREFIX]).
 * - A stored alias also rewrites rows that name the foreign id without carrying the parent row: child-only patches
 *   (a page added later to the other device's book) and `DELETE` log entries (a reading plan reset on the other device).
 *
 * Local rows are never re-keyed and outgoing patches are unchanged, so peers on older versions see exactly what they
 * saw before. Skipped, leaving the rows as they come: an incoming id that exists locally (a shared id always wins), a
 * pair where the incoming file also carries the local id itself, and a stored alias whose local row now has another
 * natural key than the incoming row (the alias is dropped: the rows are no longer the same).
 */
suspend fun PooledConnection.aliasIncomingIds(category: SyncableDatabaseDefinition, schema: String) {
    val tableDefs = category.tables.associateBy { it.tableName }
    for (key in ALIASED_KEYS[category].orEmpty()) {
        val t = key.table
        val id = key.idColumn
        val keyMatch = key.keyColumns.joinToString(" AND ") { "p.$it IS m.$it" }
        // The incoming file names the local id itself (its row or a log entry): leave the pair alone.
        val carried = "SELECT $id FROM $schema.$t UNION SELECT entityId1 FROM $schema.LogEntry WHERE tableName = '$t'"
        val fresh = queryRows(
            """
            SELECT quote(p.$id), quote(m.$id) FROM $schema.$t p JOIN main.$t m ON $keyMatch
            WHERE p.$id IS NOT m.$id
              AND p.$id NOT IN (SELECT $id FROM main.$t)
              AND m.$id NOT IN ($carried)
            """.trimIndent(),
        ) { it.getText(0) to it.getText(1) }.toMap()
        val prefix = "$ID_ALIAS_PREFIX$t:"
        for ((foreign, local) in fresh) {
            exec(
                "INSERT OR REPLACE INTO main.SyncConfiguration (keyName, stringValue, longValue, booleanValue) " +
                    "VALUES ('$prefix' || hex($foreign), hex($local), NULL, NULL)",
            )
        }
        val stored = queryRows(
            "SELECT keyName, stringValue FROM main.SyncConfiguration WHERE keyName LIKE ?", "$prefix%",
        ) { "X'${it.getText(0).removePrefix(prefix)}'" to "X'${it.getText(1)}'" }
        val mapping = LinkedHashMap(fresh)
        for ((foreign, local) in stored) {
            if (foreign in mapping) continue
            // A shared id wins; a target that is itself a foreign id would make a chain.
            if (exists("SELECT 1 FROM main.$t WHERE $id = $foreign")) continue
            if (stored.any { it.first == local }) continue
            if (exists("SELECT 1 FROM ($carried) WHERE $id = $local")) continue
            val incomingRow = exists("SELECT 1 FROM $schema.$t WHERE $id = $foreign")
            if (incomingRow && exists("SELECT 1 FROM main.$t WHERE $id = $local")) {
                // Same key would have been a fresh match: the local row's natural key changed. Not the same row any more.
                Log.i(ALIAS_TAG, "Dropping the id alias $foreign -> $local of $t: the natural keys differ now")
                exec("DELETE FROM main.SyncConfiguration WHERE keyName = '$prefix' || hex($foreign)")
                continue
            }
            mapping[foreign] = local
        }
        if (mapping.isEmpty()) continue
        Log.i(ALIAS_TAG, "Aliasing ${mapping.size} incoming $t ids to local ids (${fresh.size} new)")
        for ((foreign, local) in mapping) {
            exec("UPDATE $schema.$t SET $id = $local WHERE $id = $foreign")
            followLog(schema, tableDefs[t], t, id, foreign, local)
            for ((child, column) in key.children) {
                // A unique index over the column (LlmUsageRecord) keeps the row already at the local id.
                exec("UPDATE OR IGNORE $schema.$child SET $column = $local WHERE $column = $foreign")
                followLog(schema, tableDefs[child], child, column, foreign, local)
            }
        }
    }
}

private suspend fun PooledConnection.exists(sql: String): Boolean = queryLong("SELECT EXISTS ($sql)") == 1L

/** Moves the incoming `LogEntry` rows of [table] that name [from] through [column] (its id field 1 and/or 2) to [to]. */
private suspend fun PooledConnection.followLog(
    schema: String, def: SyncableDatabaseDefinition.Table?, table: String, column: String, from: String, to: String,
) {
    if (def == null) return
    if (def.idField1 == column) {
        exec("UPDATE OR REPLACE $schema.LogEntry SET entityId1 = $to WHERE tableName = '$table' AND entityId1 = $from")
    }
    if (def.idField2 == column) {
        exec("UPDATE OR REPLACE $schema.LogEntry SET entityId2 = $to WHERE tableName = '$table' AND entityId2 = $from")
    }
}
