/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import androidx.sqlite.SQLiteConnection
import net.bible.android.database.bookmarks.isAddonIdSql
import net.bible.service.db.exec
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows

/**
 * Add-on collection rows (spec 2026-11-20-addon-bookmark-collections-design) never leave the device through sync:
 * every device builds them from its own installed module. A row of a synced bookmark table is an add-on row when its
 * first sync key carries the add-on id tag ([isAddonIdSql]); a user's own link row to an add-on label (first key: the
 * user's bookmark) is not one and syncs normally.
 *
 * Layers: the sync triggers skip add-on rows ([addonRowTriggerCondition]), patches are written without them, incoming
 * patches are stripped of them before they are read ([stripAddonRows]), and whole-database snapshots (the initial
 * upload, a downloaded initial file, the merge snapshot) are stripped too, together with the per-device add-on tables.
 */
private const val EXCLUSION_TAG = "AddonRowsExclusion"

/** Per-device add-on tables of the bookmark database; emptied in every whole-database snapshot that leaves the device. */
val ADDON_LOCAL_TABLES = listOf("AddonCollectionModule", "AddonCollectionRow", "AddonLabelLocalState", "AddonLinkLocalState")

/** Whether rows of [category] can be add-on rows. */
val SyncableDatabaseDefinition.hasAddonRows: Boolean get() = this == SyncableDatabaseDefinition.BOOKMARKS

/** `AND NOT <add-on row>` for the sync trigger of [table], with [prefix] `NEW` or `OLD`; empty for other categories. */
fun addonRowTriggerCondition(category: SyncableDatabaseDefinition, table: SyncableDatabaseDefinition.Table, prefix: String): String =
    if (!category.hasAddonRows) "" else " AND NOT (${isAddonIdSql("$prefix.${table.idField1}")})"

/** `AND NOT <add-on row>` for a query over [table] (alias-free column names); empty for other categories. */
fun addonRowExclusion(category: SyncableDatabaseDefinition, table: SyncableDatabaseDefinition.Table): String =
    if (!category.hasAddonRows) "" else " AND NOT (${isAddonIdSql(table.idField1)})"

/**
 * The strip statements for the tables that exist in [schema] ([existing]: a downloaded file may be empty or of an older
 * schema; it is migrated later).
 */
private fun stripStatements(category: SyncableDatabaseDefinition, schema: String, wholeDatabase: Boolean, existing: Set<String>): List<String> = buildList {
    val tables = category.tables.filter { it.tableName in existing }
    for (table in tables.reversed()) {
        add("DELETE FROM $schema.${table.tableName} WHERE ${isAddonIdSql(table.idField1)}")
    }
    val names = tables.joinToString(",") { "'${it.tableName}'" }
    if ("LogEntry" in existing && tables.isNotEmpty()) add("DELETE FROM $schema.LogEntry WHERE tableName IN ($names) AND ${isAddonIdSql("entityId1")}")
    if (wholeDatabase) ADDON_LOCAL_TABLES.filter { it in existing }.forEach { add("DELETE FROM $schema.$it") }
}

private const val TABLE_NAMES_SQL = "SELECT name FROM %s.sqlite_master WHERE type = 'table'"

/**
 * Deletes add-on rows and their log entries from the database attached as [schema] on [conn] (an incoming patch, a
 * merge snapshot). With [wholeDatabase] the per-device add-on tables are emptied too. Returns the number of rows
 * removed from the synced tables and the log. No-op for categories without add-on rows.
 */
suspend fun stripAddonRows(conn: PooledConnection, category: SyncableDatabaseDefinition, schema: String, wholeDatabase: Boolean = false): Long {
    if (!category.hasAddonRows) return 0
    var removed = 0L
    val existing = conn.queryRows(TABLE_NAMES_SQL.replace("%s", schema)) { it.getText(0) }.toSet()
    for (sql in stripStatements(category, schema, wholeDatabase, existing)) {
        conn.exec(sql)
        if (!sql.contains(".Addon")) removed += conn.queryLong("SELECT changes()") ?: 0L
    }
    return removed
}

/** [stripAddonRows] for a raw connection to a database file (the initial upload copy, a downloaded initial file). */
fun stripAddonRows(conn: SQLiteConnection, category: SyncableDatabaseDefinition, wholeDatabase: Boolean = true): Long {
    if (!category.hasAddonRows) return 0
    var removed = 0L
    val existing = conn.queryRows(TABLE_NAMES_SQL.replace("%s", "main")) { it.getText(0) }.toSet()
    conn.exec("PRAGMA foreign_keys=OFF")
    for (sql in stripStatements(category, "main", wholeDatabase, existing)) {
        conn.exec(sql)
        if (!sql.contains(".Addon")) removed += conn.queryLong("SELECT changes()") ?: 0L
    }
    if (removed > 0) net.bible.sharedcore.log.Log.w(EXCLUSION_TAG, "Removed $removed add-on rows from a ${category.name} database file")
    return removed
}
