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

/** `SyncConfiguration` key: this device has started its one-time full re-send for the id aliases ([startIdAliasResendOnce]). */
const val ID_ALIAS_RESEND_DONE_KEY = "idAliasResendDone"

/**
 * `SyncConfiguration` key: an apply learned a new alias pair (R2), so the next upload re-sends the whole log
 * ([startIdAliasResendOnce] consumes it). A flag rather than lowering `LAST_PATCH_WRITTEN_KEY` in the apply: an upload
 * that overlaps the apply writes its own watermark when it finishes and would undo the lowering.
 */
const val ID_ALIAS_RESEND_PENDING_KEY = "idAliasResendPending"

/** The natural-key tables whose foreign ids are aliased to the local row of the same key. */
private val ALIASED_TABLES = setOf("ReadingPlan", "ReadingPlanStatus", "MyDocument", "MemorizedVerse", "LlmConfiguredModel")

/** [NATURAL_KEYS] restricted to [ALIASED_TABLES], per category (`MyDocumentPage` and `LlmUsageRecord` never collide). */
private val ALIASED_KEYS: Map<SyncableDatabaseDefinition, List<NaturalKey>> =
    NATURAL_KEYS.mapValues { (_, keys) -> keys.filter { it.table in ALIASED_TABLES } }.filterValues { it.isNotEmpty() }

/**
 * The child tables of the aliased tables of [this] category (`MyDocumentPage`, `AgentPrompt`, `GlobalAiSettings`, ...):
 * their rows are the ones the foreign-key clean-up dropped while the parent ids diverged, so only they are restored from
 * a re-send (`readPatchData` `restoreDropped`). An aliased table itself is never restored that way: before #3906 an
 * incoming row of it merged in place onto the local row of the same key, leaving an equal log entry for an id that
 * never had a row here, and a later local delete is logged under the local id only.
 */
val SyncableDatabaseDefinition.idAliasChildTables: Set<String>
    get() = ALIASED_KEYS[this].orEmpty().flatMap { k -> k.children.map { it.first } }.toSet() - ALIASED_TABLES

/** Whether the category has tables whose incoming ids are aliased ([aliasIncomingIds]). */
val SyncableDatabaseDefinition.hasIdAliases: Boolean get() = this in ALIASED_KEYS

/**
 * R1 of #3906: once per device and category (persisted in [ID_ALIAS_RESEND_DONE_KEY]), lowers `LAST_PATCH_WRITTEN_KEY`
 * to 0 so the next patch carries the whole log again, with the original timestamps. Devices that diverged before this
 * version exchanged their rows long ago and the children were dropped on arrival; the re-send lets an updated peer
 * learn the aliases and restore those rows (`readPatchData` `restoreDropped`). Old peers skip it: every entry is equal
 * to one they have. Called before the patch of a sync is created. Also starts the
 * re-send that an apply asked for when it learned a new pair (R2, [ID_ALIAS_RESEND_PENDING_KEY]).
 */
suspend fun startIdAliasResendOnce(dbDef: SyncableDatabaseAccessor<*>) {
    if (!dbDef.category.hasIdAliases) return
    val first = dbDef.dao.getBoolean(ID_ALIAS_RESEND_DONE_KEY) != true
    val pending = dbDef.dao.getBoolean(ID_ALIAS_RESEND_PENDING_KEY) == true
    if (!first && !pending) return
    // Lowered before the flags are cleared: a failed upload leaves the watermark at 0 and the next one retries.
    dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
    if (first) dbDef.dao.setConfig(ID_ALIAS_RESEND_DONE_KEY, true)
    if (pending) dbDef.dao.removeConfig(ID_ALIAS_RESEND_PENDING_KEY)
}

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
 * pair where the incoming file also carries the local id itself (its row or a delete that wins), and a stored alias whose local row now has another
 * natural key than the incoming row (the alias is dropped: the rows are no longer the same).
 *
 * @param persist false (an import of a backup file) uses the stored aliases but stores no new ones: a backup is not a
 * peer, and may even be an older copy of this device whose rows were since deleted and re-created.
 */
suspend fun PooledConnection.aliasIncomingIds(category: SyncableDatabaseDefinition, schema: String, persist: Boolean = true) {
    val keys = ALIASED_KEYS[category] ?: return
    val tableDefs = category.tables.associateBy { it.tableName }
    // A merge snapshot or a backup is a whole database with its own sync triggers: the rewrite must not log into it.
    setTriggersDisabled(schema, true)
    for (key in keys) {
        val t = key.table
        val id = key.idColumn
        val keyMatch = key.keyColumns.joinToString(" AND ") { "p.$it IS m.$it" }
        // The incoming file carries the local id itself (its row, or a delete of it that wins here): leave the pair alone.
        // An UPSERT entry without a row is only an echo of this device's own entry (the sender merged the row in place)
        // and does not count. Nor does a delete that loses to a newer local entry: the local row stays, and a re-creation
        // of it under a fresh id in the same file (a deleted MyDocument is re-created under a random id) must still map.
        val carried = "SELECT $id FROM $schema.$t UNION SELECT pe.entityId1 FROM $schema.LogEntry pe " +
            "WHERE pe.tableName = '$t' AND pe.type = 'DELETE' AND NOT EXISTS (SELECT 1 FROM main.LogEntry me " +
            "WHERE me.tableName = pe.tableName AND me.entityId1 = pe.entityId1 AND me.entityId2 = pe.entityId2 " +
            "AND me.lastUpdated >= pe.lastUpdated)"
        val fresh = queryRows(
            """
            SELECT quote(p.$id), quote(m.$id) FROM $schema.$t p JOIN main.$t m ON $keyMatch
            WHERE p.$id IS NOT m.$id
              AND p.$id NOT IN (SELECT $id FROM main.$t)
              AND m.$id NOT IN ($carried)
            """.trimIndent(),
        ) { it.getText(0) to it.getText(1) }.toMap()
        val prefix = "$ID_ALIAS_PREFIX$t:"
        val storedBefore = queryRows(
            "SELECT keyName, stringValue FROM main.SyncConfiguration WHERE keyName LIKE ?", "$prefix%",
        ) { "X'${it.getText(0).removePrefix(prefix)}'" to "X'${it.getText(1)}'" }.toMap()
        val learned = fresh.filter { (foreign, local) -> storedBefore[foreign] != local }
        if (persist) for ((foreign, local) in learned) {
            exec(
                "INSERT OR REPLACE INTO main.SyncConfiguration (keyName, stringValue, longValue, booleanValue) " +
                    "VALUES ('$prefix' || hex($foreign), hex($local), NULL, NULL)",
            )
        }
        if (persist && learned.isNotEmpty()) {
            // R2: the peer may have dropped this device's rows of the pair before (it was not mapping yet): send the whole
            // log once more, at the next upload. Bounded: a pair is learned once, a re-send of a known pair learns nothing.
            exec(
                "INSERT OR REPLACE INTO main.SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, 1)",
                ID_ALIAS_RESEND_PENDING_KEY,
            )
        }
        val mapping = LinkedHashMap(fresh)
        // A stored foreign id that is a local row now can never map again (a shared id always wins): drop it, so that it
        // does not block a later alias whose target is that row (no-chain rule below).
        val stale = storedBefore.keys.filter { it !in learned && exists("SELECT 1 FROM main.$t WHERE $id = $it") }
        if (persist) for (foreign in stale) exec("DELETE FROM main.SyncConfiguration WHERE keyName = '$prefix' || hex($foreign)")
        val stored = (storedBefore - stale.toSet() + learned).toList()
        if (stored.isNotEmpty()) {
            val storedForeign = stored.mapTo(HashSet()) { it.first }
            // Only the stored aliases this file names at all (its rows, its log entries, a child column).
            val mentioned = HashSet<String>()
            val sources = listOf("SELECT quote($id) FROM $schema.$t", "SELECT quote(entityId1) FROM $schema.LogEntry WHERE tableName = '$t'") +
                key.children.map { (child, column) -> "SELECT quote($column) FROM $schema.$child WHERE $column IS NOT NULL" }
            for (sql in sources) queryRows(sql) { it.getText(0) }.filterTo(mentioned) { it in storedForeign }
            for ((foreign, local) in stored) {
                if (foreign in mapping || foreign !in mentioned) continue
                // A shared id wins; a target that is itself a foreign id would make a chain.
                if (local in storedForeign) continue
                if (exists("SELECT 1 FROM main.$t WHERE $id = $foreign")) continue
                if (exists("SELECT 1 FROM ($carried) WHERE $id = $local")) continue
                val incomingRow = exists("SELECT 1 FROM $schema.$t WHERE $id = $foreign")
                if (incomingRow && exists("SELECT 1 FROM main.$t WHERE $id = $local")) {
                    // Same key would have been a fresh match: the local row's natural key changed. Not the same row any more.
                    Log.i(ALIAS_TAG, "Dropping the id alias $foreign -> $local of $t: the natural keys differ now")
                    if (persist) exec("DELETE FROM main.SyncConfiguration WHERE keyName = '$prefix' || hex($foreign)")
                    continue
                }
                mapping[foreign] = local
            }
        }
        if (mapping.isEmpty()) continue
        Log.i(ALIAS_TAG, "Aliasing ${mapping.size} incoming $t ids to local ids (${learned.size} newly learned)")
        for ((foreign, local) in mapping) {
            exec("UPDATE $schema.$t SET $id = $local WHERE $id = $foreign")
            followLog(schema, tableDefs[t], t, id, foreign, local)
            for ((child, column) in key.children) {
                // A unique index over the column (LlmUsageRecord) keeps the row already at the local id; the clashing
                // row is dropped rather than left naming the foreign id.
                exec("UPDATE OR IGNORE $schema.$child SET $column = $local WHERE $column = $foreign")
                dropClashingChildLog(category, schema, tableDefs, child, column, foreign)
                exec("DELETE FROM $schema.$child WHERE $column = $foreign")
                followLog(schema, tableDefs[child], child, column, foreign, local)
            }
        }
    }
    setTriggersDisabled(schema, false)
}

/**
 * The child rows that still name [foreign] after the `UPDATE OR IGNORE` clash with a row already at the local id and are
 * dropped from the incoming file: their own `UPSERT` entries go too, or the local log would take over a version whose
 * row never arrived. So do the entries of their own children (a `MyDocumentPage`'s content and cache rows): the
 * foreign-key clean-up removes those rows with the page, and an entry would stay without its row. Only for a child whose
 * log is keyed by its own id (not by [column]: `followLog` moves those).
 *
 * For `LlmUsageRecord` the row already at the local id is the one that is kept, not the newer one: acceptable for a
 * cache-like usage counter.
 */
private suspend fun PooledConnection.dropClashingChildLog(
    category: SyncableDatabaseDefinition, schema: String, tableDefs: Map<String, SyncableDatabaseDefinition.Table>,
    child: String, column: String, foreign: String,
) {
    val def = tableDefs[child]
    if (def == null || def.idField1 == column || def.idField2 == column) return
    dropLogOfRows(schema, def, child, column, foreign)
    val childKey = NATURAL_KEYS[category].orEmpty().firstOrNull { it.table == child } ?: return
    for ((grandchild, grandColumn) in childKey.children) {
        val gdef = tableDefs[grandchild] ?: continue
        dropLogOfRows(schema, gdef, grandchild, grandColumn, "(SELECT ${childKey.idColumn} FROM $schema.$child WHERE $column = $foreign)", inList = true)
    }
}

/** Deletes the incoming `UPSERT` entries of the [table] rows whose [column] equals [value] (or is in the [value] subquery). */
private suspend fun PooledConnection.dropLogOfRows(
    schema: String, def: SyncableDatabaseDefinition.Table, table: String, column: String, value: String, inList: Boolean = false,
) {
    val (ids, fields) = if (def.idField2 == null) "entityId1" to def.idField1 else "entityId1, entityId2" to "${def.idField1}, ${def.idField2}"
    val cond = if (inList) "$column IN $value" else "$column = $value"
    exec(
        "DELETE FROM $schema.LogEntry WHERE tableName = '$table' AND type = 'UPSERT' AND ($ids) IN " +
            "(SELECT $fields FROM $schema.$table WHERE $cond)",
    )
}

private suspend fun PooledConnection.setTriggersDisabled(schema: String, disabled: Boolean) = exec(
    "INSERT OR REPLACE INTO $schema.SyncConfiguration (keyName, stringValue, longValue, booleanValue) VALUES (?, NULL, NULL, ?)",
    TRIGGERS_DISABLED_KEY, disabled,
)

private suspend fun PooledConnection.exists(sql: String): Boolean = queryLong("SELECT EXISTS ($sql)") == 1L

/**
 * Moves the incoming `LogEntry` rows of [table] that name [from] through [column] (its id field 1 and/or 2) to [to].
 * Two incoming entries can land on one id (a delete of the foreign row and an upsert of a re-created row that maps to
 * the same local row): the newer one is kept, as the log keeps one entry per id.
 */
private suspend fun PooledConnection.followLog(
    schema: String, def: SyncableDatabaseDefinition.Table?, table: String, column: String, from: String, to: String,
) {
    if (def == null) return
    for ((field, other) in listOfNotNull(
        ("entityId1" to "entityId2").takeIf { def.idField1 == column },
        ("entityId2" to "entityId1").takeIf { def.idField2 == column },
    )) {
        val log = "$schema.LogEntry"
        exec(
            "DELETE FROM $log WHERE tableName = '$table' AND $field = $to AND lastUpdated < " +
                "(SELECT max(o.lastUpdated) FROM $log o WHERE o.tableName = '$table' AND o.$field = $from AND o.$other = $log.$other)",
        )
        exec("UPDATE OR IGNORE $log SET $field = $to WHERE tableName = '$table' AND $field = $from")
        exec("DELETE FROM $log WHERE tableName = '$table' AND $field = $from")
    }
}
