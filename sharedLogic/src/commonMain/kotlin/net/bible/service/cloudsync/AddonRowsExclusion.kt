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
import net.bible.service.db.useReaderConnectionMarked
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows

/**
 * Add-on collection rows (spec 2026-11-20-addon-bookmark-collections-design) never leave the device through sync, and
 * no user row ever references one: every device builds them from its own installed module, and they are strictly
 * read-only (BookmarkControl refuses every new association with them). A row of a synced bookmark table is an add-on
 * row when its first sync key carries the add-on id tag ([isAddonIdSql]).
 *
 * Layers: the sync triggers skip add-on rows ([addonRowTriggerCondition]), patches are written without them, incoming
 * patches are stripped of them before they are read ([stripAddonRows]), and whole-database snapshots (the initial
 * upload, a downloaded initial file, the merge snapshot) are stripped too, together with the per-device add-on tables.
 * The same strip removes any user row's reference to an add-on id ([userRefsToAddonRowsStatements]): such a row can
 * only come from another device or an older build, and must not create the association here.
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

/** What a strip statement removes: an add-on row (or its log entry), a per-device add-on table row, or a user row's reference. */
private enum class StripKind { ADDON_ROW, LOCAL_STATE, USER_REF }

/**
 * The strip statements for the tables that exist in [schema] ([existing]: a downloaded file may be empty or of an older
 * schema; it is migrated later).
 */
private fun stripStatements(category: SyncableDatabaseDefinition, schema: String, wholeDatabase: Boolean, existing: Set<String>): List<Pair<String, StripKind>> = buildList {
    val tables = category.tables.filter { it.tableName in existing }
    for (table in tables.reversed()) {
        add("DELETE FROM $schema.${table.tableName} WHERE ${isAddonIdSql(table.idField1)}" to StripKind.ADDON_ROW)
    }
    val names = tables.joinToString(",") { "'${it.tableName}'" }
    if ("LogEntry" in existing && tables.isNotEmpty()) add("DELETE FROM $schema.LogEntry WHERE tableName IN ($names) AND ${isAddonIdSql("entityId1")}" to StripKind.ADDON_ROW)
    userRefsToAddonRowsStatements(schema, existing).forEach { add(it to StripKind.USER_REF) }
    if (wholeDatabase) ADDON_LOCAL_TABLES.filter { it in existing }.forEach { add("DELETE FROM $schema.$it" to StripKind.LOCAL_STATE) }
}

/**
 * Statements that remove every user row's reference to an add-on id from the bookmark database attached as [schema]
 * ([existing]: its tables): a user bookmark's link to an add-on label (with its log entry), a user's StudyPad text
 * entry in an add-on label (with its text and log entries), and a user bookmark's add-on primary label (set to NULL,
 * as deleting that label would). Such a row never arises on this build (BookmarkControl refuses it); an incoming patch
 * or file from another device or an older build could still carry one, and where the module is installed here it would
 * pass the foreign-key check and make the association. Where it is not installed, the plain foreign-key clean-up would
 * delete the whole user bookmark instead of only its primary label.
 */
private fun userRefsToAddonRowsStatements(schema: String, existing: Set<String>): List<String> = buildList {
    val log = "LogEntry" in existing
    for (link in listOf("BibleBookmarkToLabel", "GenericBookmarkToLabel").filter { it in existing }) {
        if (log) add("DELETE FROM $schema.LogEntry WHERE tableName = '$link' AND ${isAddonIdSql("entityId2")}")
        add("DELETE FROM $schema.$link WHERE ${isAddonIdSql("labelId")}")
    }
    if ("StudyPadTextEntry" in existing) {
        val userEntries = "SELECT id FROM $schema.StudyPadTextEntry WHERE ${isAddonIdSql("labelId")} AND NOT (${isAddonIdSql("id")})"
        if (log) add("DELETE FROM $schema.LogEntry WHERE tableName IN ('StudyPadTextEntry','StudyPadTextEntryText') AND entityId1 IN ($userEntries)")
        if ("StudyPadTextEntryText" in existing) add("DELETE FROM $schema.StudyPadTextEntryText WHERE studyPadTextEntryId IN ($userEntries)")
        add("DELETE FROM $schema.StudyPadTextEntry WHERE id IN ($userEntries)")
    }
    for (bookmark in listOf("BibleBookmark", "GenericBookmark").filter { it in existing }) {
        add("UPDATE $schema.$bookmark SET primaryLabelId = NULL WHERE primaryLabelId IS NOT NULL AND ${isAddonIdSql("primaryLabelId")}")
    }
}

private const val TABLE_NAMES_SQL = "SELECT name FROM %s.sqlite_master WHERE type = 'table'"

/**
 * Deletes add-on rows and their log entries, and user rows' references to add-on rows
 * ([userRefsToAddonRowsStatements]), from the database attached as [schema] on [conn] (a patch, a merge snapshot, a
 * file being imported). With [wholeDatabase] the per-device add-on tables are emptied too. Returns the number of
 * add-on rows removed from the synced tables and the log. No-op for categories without add-on rows.
 */
suspend fun stripAddonRows(conn: PooledConnection, category: SyncableDatabaseDefinition, schema: String, wholeDatabase: Boolean = false): Long {
    if (!category.hasAddonRows) return 0
    var removed = 0L
    var refs = 0L
    val existing = conn.queryRows(TABLE_NAMES_SQL.replace("%s", schema)) { it.getText(0) }.toSet()
    for ((sql, kind) in stripStatements(category, schema, wholeDatabase, existing)) {
        conn.exec(sql)
        val changed = conn.queryLong("SELECT changes()") ?: 0L
        when (kind) { StripKind.ADDON_ROW -> removed += changed; StripKind.USER_REF -> refs += changed; StripKind.LOCAL_STATE -> {} }
    }
    if (refs > 0) net.bible.sharedcore.log.Log.w(EXCLUSION_TAG, "Removed $refs user rows' references to add-on rows from $schema")
    return removed
}

/** [stripAddonRows] for a raw connection to a database file (the initial upload copy, a downloaded initial file). */
fun stripAddonRows(conn: SQLiteConnection, category: SyncableDatabaseDefinition, wholeDatabase: Boolean = true): Long {
    if (!category.hasAddonRows) return 0
    var removed = 0L
    var refs = 0L
    val existing = conn.queryRows(TABLE_NAMES_SQL.replace("%s", "main")) { it.getText(0) }.toSet()
    conn.exec("PRAGMA foreign_keys=OFF")
    for ((sql, kind) in stripStatements(category, "main", wholeDatabase, existing)) {
        conn.exec(sql)
        val changed = conn.queryLong("SELECT changes()") ?: 0L
        when (kind) { StripKind.ADDON_ROW -> removed += changed; StripKind.USER_REF -> refs += changed; StripKind.LOCAL_STATE -> {} }
    }
    if (removed > 0) net.bible.sharedcore.log.Log.w(EXCLUSION_TAG, "Removed $removed add-on rows from a ${category.name} database file")
    if (refs > 0) net.bible.sharedcore.log.Log.w(EXCLUSION_TAG, "Removed $refs user rows' references to add-on rows from a ${category.name} database file")
    return removed
}

/** Rows of the per-device add-on state tables ([ADDON_STATE_TABLES]), as read by [readAddonLocalState]. */
class AddonLocalState internal constructor(
    internal val rows: Map<String, List<List<Any?>>>,
    /** Column names of each table in [rows], in value order: the restore names them, so a column added to or moved in
     *  the new database cannot shift a value into the wrong column. */
    internal val columns: Map<String, List<String>>,
)

/** Per-device, user-chosen add-on state: carried across a whole-database swap (the other add-on tables are rebuilt). */
private val ADDON_STATE_TABLES = listOf("AddonLabelLocalState", "AddonLinkLocalState")

/**
 * This device's add-on state of [dbDef] before its file is replaced (an initial download, a merge's swap back), or null
 * for other categories. Never throws: losing per-device display state must not fail a sync.
 */
suspend fun readAddonLocalState(dbDef: SyncableDatabaseAccessor<*>): AddonLocalState? {
    if (!dbDef.category.hasAddonRows) return null
    return try {
        dbDef.localDb.useReaderConnectionMarked { c ->
            val columns = mutableMapOf<String, List<String>>()
            val rows = ADDON_STATE_TABLES.associateWith { table ->
                c.queryRows("SELECT * FROM $table") { st ->
                    columns[table] = (0 until st.getColumnCount()).map { i -> st.getColumnName(i) }
                    (0 until st.getColumnCount()).map { i ->
                        when (st.getColumnType(i)) {
                            androidx.sqlite.SQLITE_DATA_NULL -> null
                            androidx.sqlite.SQLITE_DATA_BLOB -> st.getBlob(i)
                            androidx.sqlite.SQLITE_DATA_INTEGER -> st.getLong(i)
                            else -> st.getText(i)
                        }
                    }
                }
            }
            AddonLocalState(rows, columns)
        }
    } catch (e: Exception) {
        net.bible.sharedcore.log.Log.e(EXCLUSION_TAG, "Could not read the add-on state to carry over", e)
        null
    }
}

/** Writes [state] ([readAddonLocalState]) into [dbDef]'s (new) database. Never throws. */
suspend fun restoreAddonLocalState(dbDef: SyncableDatabaseAccessor<*>, state: AddonLocalState?) {
    if (state == null || state.rows.values.all { it.isEmpty() }) return
    try {
        dbDef.localDb.useWriterConnectionMarked { c ->
            for ((table, rows) in state.rows) {
                val names = state.columns[table]?.joinToString(",") ?: continue
                for (row in rows) {
                    c.exec("INSERT OR REPLACE INTO $table ($names) VALUES (${row.joinToString(",") { "?" }})", *row.toTypedArray())
                }
            }
        }
    } catch (e: Exception) {
        net.bible.sharedcore.log.Log.e(EXCLUSION_TAG, "Could not carry the add-on state over", e)
    }
}
