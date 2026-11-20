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

package net.bible.service.addons.collections

import androidx.annotation.VisibleForTesting
import androidx.room3.PooledConnection
import androidx.room3.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.AI_LABEL_ID
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.database.bookmarks.isAddonIdSql
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.TRIGGERS_DISABLED_KEY
import net.bible.service.common.createTempFile
import net.bible.service.db.buildAppDatabase
import net.bible.service.db.columnNamesJoined
import net.bible.service.db.exec
import net.bible.service.db.inTransaction
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.queryRows
import net.bible.service.db.useReaderConnectionMarked
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.withCleanup
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedlogic.platform.CollectionPackRef
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

private const val TAG = "AddonCollectionImporter"

/** The device's special labels by name: links to a special label in a collection file are mapped to these. */
private val SPECIAL_LABEL_IDS: Map<String, IdType> = mapOf(
    SPEAK_LABEL_NAME to SPEAK_LABEL_ID,
    UNLABELED_NAME to UNLABELED_LABEL_ID,
    PARAGRAH_BREAK_LABEL_NAME to PARAGRAPH_BREAK_LABEL_ID,
    AI_LABEL_NAME to AI_LABEL_ID,
)

/** Reference columns of the synced bookmark tables that hold a bookmark-database row id, per table. */
private val ID_COLUMNS: Map<String, List<String>> = mapOf(
    "Label" to listOf("id"),
    "BibleBookmark" to listOf("id", "primaryLabelId"),
    "BibleBookmarkNotes" to listOf("bookmarkId"),
    "BibleBookmarkToLabel" to listOf("bookmarkId", "labelId"),
    "GenericBookmark" to listOf("id", "primaryLabelId"),
    "GenericBookmarkNotes" to listOf("bookmarkId"),
    "GenericBookmarkToLabel" to listOf("bookmarkId", "labelId"),
    "StudyPadTextEntry" to listOf("id", "labelId"),
    "StudyPadTextEntryText" to listOf("studyPadTextEntryId"),
)

private val SYNCED_TABLES get() = SyncableDatabaseDefinition.BOOKMARKS.tables

/** No collection rows could be read from a module's file (not a bookmark database, or only special labels). */
class EmptyAddonCollectionException(moduleInitials: String, reason: String) :
    Exception("Add-on collection $moduleInitials provides nothing: $reason")

/**
 * Imports add-on collection files (`AndBibleProvidesLabels`, spec 2026-11-20-addon-bookmark-collections-design) into
 * the bookmark database as read-only rows owned by their module.
 *
 * - Every row gets the id [addonId] (module, original id); every reference column is rewritten the same way.
 * - Special labels (speak, unlabeled, paragraph break, AI) are never imported: links to them point at the device's
 *   own special label of the same kind (dropped when the device has none).
 * - Ownership is recorded in `AddonCollectionRow`, the version and hash in `AddonCollectionModule`.
 * - Writes run with the sync triggers disabled, so no add-on row (and no cascade it causes) reaches `LogEntry`.
 * - [replace] and [remove] are each one transaction: a failure leaves the previous content.
 *
 * The module's file is never written: it is copied to [AppEnvironment.tmpDir], migrated to the current schema
 * there (as a database import does) and rewritten in the copy.
 *
 * [bookmarkDb] is a provider, not a database: the container replaces the bookmark database on restore and sync.
 */
class AddonCollectionImporter(
    private val bookmarkDb: () -> BookmarkDatabase,
    private val env: AppEnvironment,
    private val openCopy: (String) -> BookmarkDatabase = { buildAppDatabase<BookmarkDatabase>(it, *bookmarkMigrations) },
) {
    /** Test seam: runs inside the replace transaction, after the module's previous rows are deleted. */
    @VisibleForTesting
    var afterDeleteHookForTest: (suspend () -> Unit)? = null

    /**
     * Replaces whatever [ref]'s module provided before with the content of [ref]'s file, in one transaction, and
     * records [contentHash] and the module version. Throws (and changes nothing) when the file cannot be read.
     */
    suspend fun replace(ref: CollectionPackRef, contentHash: String): Unit = withContext(Dispatchers.IO) {
        val copy = createTempFile(env.tmpDir.toPath(), "addon-collection-", ".sqlite3")
        try {
            FileSystem.SYSTEM.delete(copy)
            FileSystem.SYSTEM.copy(ref.file, copy)
            // Open through Room so older schemas migrate (as importDatabaseFile does).
            openCopy(copy.toString()).let { db -> try { db.useWriterConnection { } } finally { db.close() } }
            prepareCopy(copy, ref.moduleInitials)
            writeLocal(ref.moduleInitials, attach = copy) {
                insertFromCopy(this, ref.moduleInitials)
                exec(
                    "INSERT OR REPLACE INTO AddonCollectionModule (moduleInitials, version, contentHash, importedAt) VALUES (?, ?, ?, ?)",
                    ref.moduleInitials, ref.version, contentHash, Clock.System.now().toEpochMilliseconds(),
                )
            }
            Log.i(TAG, "Imported add-on collection ${ref.moduleInitials} ${ref.version}")
        } finally {
            runCatching { FileSystem.SYSTEM.delete(copy, mustExist = false) }
            runCatching { FileSystem.SYSTEM.delete("$copy-journal".toPath(), mustExist = false) }
        }
    }

    /** Deletes every row of [moduleInitials] (and its record), in one transaction. User rows are never deleted. */
    suspend fun remove(moduleInitials: String): Unit = withContext(Dispatchers.IO) {
        writeLocal(moduleInitials) {
            exec("DELETE FROM AddonCollectionModule WHERE moduleInitials = ?", moduleInitials)
        }
        Log.i(TAG, "Removed add-on collection $moduleInitials")
    }

    /**
     * Deletes add-on rows that no module owns (a restored backup or an imported file may bring them) and the
     * per-device state and user links that point at add-on rows which no longer exist. Unlogged.
     */
    suspend fun sweep(): Long = withContext(Dispatchers.IO) {
        writeLocal(null) { }
    }

    /**
     * One writer transaction with the sync triggers disabled and foreign keys off: deletes [moduleInitials]'s owned
     * rows (when given), runs [body], then removes unowned add-on rows and dangling references (see [sweep]).
     * Foreign keys are off so that replacing a module's rows never cascades into user rows (a user bookmark's link
     * to an add-on label survives an update that keeps the label); dangling references are removed explicitly.
     */
    private suspend fun writeLocal(moduleInitials: String?, attach: Path? = null, body: suspend PooledConnection.() -> Unit): Long {
        val db = bookmarkDb()
        return db.useWriterConnectionMarked { conn ->
            val cleanup = if (attach != null) arrayOf("PRAGMA foreign_keys=ON;", "DETACH DATABASE addon") else arrayOf("PRAGMA foreign_keys=ON;")
            conn.withCleanup(*cleanup) {
                // ATTACH and foreign_keys cannot change inside a transaction.
                if (attach != null) conn.exec("ATTACH DATABASE '${attach.toString().replace("'", "''")}' AS addon")
                conn.exec("PRAGMA foreign_keys=OFF;")
                conn.inTransaction<Long> {
                    db.syncDao().setConfig(TRIGGERS_DISABLED_KEY, true)
                    val changesBefore = queryLong("SELECT total_changes()")!!
                    if (moduleInitials != null) {
                        deleteOwned(this, moduleInitials)
                        afterDeleteHookForTest?.invoke()
                    }
                    body()
                    removeUnownedAndDangling(this)
                    val changed = queryLong("SELECT total_changes()")!! - changesBefore
                    db.syncDao().setConfig(TRIGGERS_DISABLED_KEY, false)
                    changed
                }
            }
        }
    }

    private suspend fun deleteOwned(conn: PooledConnection, moduleInitials: String) = conn.run {
        for (table in SYNCED_TABLES.reversed()) {
            val name = table.tableName
            val id2 = table.idField2
            if (id2 == null) {
                exec(
                    "DELETE FROM $name WHERE ${table.idField1} IN " +
                        "(SELECT id1 FROM AddonCollectionRow WHERE moduleInitials = ? AND tableName = '$name')",
                    moduleInitials,
                )
            } else {
                exec(
                    "DELETE FROM $name WHERE EXISTS (SELECT 1 FROM AddonCollectionRow a WHERE a.moduleInitials = ? " +
                        "AND a.tableName = '$name' AND a.id1 = $name.${table.idField1} AND a.id2 = $name.$id2)",
                    moduleInitials,
                )
            }
        }
        exec("DELETE FROM AddonCollectionRow WHERE moduleInitials = ?", moduleInitials)
    }

    private suspend fun insertFromCopy(conn: PooledConnection, moduleInitials: String) = conn.run {
        for (table in SYNCED_TABLES) {
            val name = table.tableName
            val cols = columnNamesJoined(name, "addon")
            // A row with this id here can only be a stale add-on row (a restored backup, an imported export of a
            // collection): ids derived for a module belong to it. REPLACE (foreign keys off: no cascade) takes it over.
            exec("INSERT OR REPLACE INTO main.$name ($cols) SELECT $cols FROM addon.$name")
            val id2 = table.idField2?.let { "t.$it" } ?: "NULL"
            exec(
                "INSERT OR REPLACE INTO AddonCollectionRow (moduleInitials, tableName, id1, id2) " +
                    "SELECT ?, '$name', t.${table.idField1}, $id2 FROM addon.$name t",
                moduleInitials,
            )
        }
    }

    private suspend fun removeUnownedAndDangling(conn: PooledConnection) = conn.run {
        for (cleanup in cleanups()) exec(cleanup.sql)
    }

    /** Whether [sweep] would change anything (a read; most reconciliations find nothing to sweep). */
    suspend fun needsSweep(): Boolean = withContext(Dispatchers.IO) {
        bookmarkDb().useReaderConnectionMarked { conn ->
            cleanups().any { (conn.queryLong("SELECT EXISTS (SELECT 1 FROM ${it.table} WHERE ${it.where})") ?: 0L) > 0L }
        }
    }

    /** One clean-up step: rows of [table] matching [where] are deleted, or (with [set]) updated. */
    private class Cleanup(val table: String, val where: String, val set: String? = null) {
        val sql get() = if (set == null) "DELETE FROM $table WHERE $where" else "UPDATE $table SET $set WHERE $where"
    }

    /**
     * Add-on rows that no module owns; references to rows that are gone (an add-on row's link to a special label this
     * device lacks, a user bookmark's link to or primary label of an add-on label that was removed; never a user row
     * itself); ownership records of rows that are gone; per-device state of add-on labels and links that are gone.
     * In this order.
     */
    private fun cleanups(): List<Cleanup> = buildList {
        for (table in SYNCED_TABLES.reversed()) {
            val name = table.tableName
            val id2Match = table.idField2?.let { "AND a.id2 = $name.$it" } ?: ""
            add(Cleanup(name, "${isAddonIdSql("$name.${table.idField1}")} AND NOT EXISTS " +
                "(SELECT 1 FROM AddonCollectionRow a WHERE a.tableName = '$name' AND a.id1 = $name.${table.idField1} $id2Match)"))
        }
        for ((linkTable, bookmarkTable) in listOf("BibleBookmarkToLabel" to "BibleBookmark", "GenericBookmarkToLabel" to "GenericBookmark")) {
            add(Cleanup(linkTable, "(${isAddonIdSql("bookmarkId")} OR ${isAddonIdSql("labelId")}) AND " +
                "(labelId NOT IN (SELECT id FROM Label) OR bookmarkId NOT IN (SELECT id FROM $bookmarkTable))"))
            add(Cleanup(bookmarkTable, "primaryLabelId IS NOT NULL AND (${isAddonIdSql("id")} OR ${isAddonIdSql("primaryLabelId")}) " +
                "AND primaryLabelId NOT IN (SELECT id FROM Label)", set = "primaryLabelId = NULL"))
        }
        for ((notes, bookmarks) in listOf("BibleBookmarkNotes" to "BibleBookmark", "GenericBookmarkNotes" to "GenericBookmark")) {
            add(Cleanup(notes, "${isAddonIdSql("bookmarkId")} AND bookmarkId NOT IN (SELECT id FROM $bookmarks)"))
        }
        add(Cleanup("StudyPadTextEntry", "${isAddonIdSql("id")} AND labelId NOT IN (SELECT id FROM Label)"))
        add(Cleanup("StudyPadTextEntryText", "${isAddonIdSql("studyPadTextEntryId")} AND studyPadTextEntryId NOT IN (SELECT id FROM StudyPadTextEntry)"))
        for (table in SYNCED_TABLES) {
            val name = table.tableName
            val id2Match = table.idField2?.let { "AND t.$it = AddonCollectionRow.id2" } ?: ""
            add(Cleanup("AddonCollectionRow", "tableName = '$name' AND NOT EXISTS " +
                "(SELECT 1 FROM $name t WHERE t.${table.idField1} = AddonCollectionRow.id1 $id2Match)"))
        }
        add(Cleanup("AddonLabelLocalState", "labelId NOT IN (SELECT id FROM Label)"))
        add(Cleanup("AddonLinkLocalState",
            "NOT EXISTS (SELECT 1 FROM BibleBookmarkToLabel l WHERE l.bookmarkId = AddonLinkLocalState.bookmarkId AND l.labelId = AddonLinkLocalState.labelId) " +
                "AND NOT EXISTS (SELECT 1 FROM GenericBookmarkToLabel l WHERE l.bookmarkId = AddonLinkLocalState.bookmarkId AND l.labelId = AddonLinkLocalState.labelId)"))
    }

    /**
     * Rewrites the (already migrated) copy at [copy] so that it holds exactly the rows to import: special labels
     * removed and references to them mapped to the fixed local ids, every other id derived with [addonId], and
     * rows whose parent is missing from the file dropped.
     */
    private suspend fun prepareCopy(copy: Path, moduleInitials: String) {
        openSqlite(copy.toString()).use { c ->
            c.exec("PRAGMA foreign_keys=OFF")
            c.inTransaction {
                val specials = queryRows("SELECT id, name FROM Label") { st -> IdType.fromByteArray(st.getBlob(0))!! to st.getText(1) }
                    .mapNotNull { (id, name) -> SPECIAL_LABEL_IDS[name]?.let { id to it } }
                exec("CREATE TEMP TABLE idmap (old BLOB PRIMARY KEY, new BLOB NOT NULL)")
                for ((old, fixed) in specials) exec("INSERT OR REPLACE INTO idmap VALUES (?, ?)", old.toByteArray(), fixed.toByteArray())
                for (table in listOf("Label", "BibleBookmark", "GenericBookmark", "StudyPadTextEntry")) {
                    val ids = queryRows("SELECT id FROM $table") { st -> IdType.fromByteArray(st.getBlob(0))!! }
                    for (id in ids) {
                        if (specials.any { it.first == id }) continue
                        exec("INSERT OR IGNORE INTO idmap VALUES (?, ?)", id.toByteArray(), addonId(moduleInitials, id).toByteArray())
                    }
                }
                exec("DELETE FROM Label WHERE name IN (${SPECIAL_LABEL_IDS.keys.joinToString(",") { "'$it'" }})")
                for ((table, columns) in ID_COLUMNS) {
                    for (column in columns) {
                        exec("UPDATE OR REPLACE $table SET $column = (SELECT new FROM idmap WHERE old = $table.$column) WHERE $column IN (SELECT old FROM idmap)")
                    }
                }
                exec("DROP TABLE idmap")
                // Drop what has no parent in the file (an unmapped id left as is would be the author's own id).
                val labels = "(SELECT id FROM Label UNION SELECT new FROM (SELECT ${SPECIAL_LABEL_IDS.values.joinToString(" UNION SELECT ") { "X'${it.toHex()}' AS new" }}))"
                for ((bookmarks, prefix) in listOf("BibleBookmark" to "Bible", "GenericBookmark" to "Generic")) {
                    exec("DELETE FROM ${prefix}BookmarkToLabel WHERE bookmarkId NOT IN (SELECT id FROM $bookmarks) OR labelId NOT IN $labels")
                    exec("DELETE FROM ${prefix}BookmarkNotes WHERE bookmarkId NOT IN (SELECT id FROM $bookmarks)")
                    exec("UPDATE $bookmarks SET primaryLabelId = NULL WHERE primaryLabelId IS NOT NULL AND primaryLabelId NOT IN $labels")
                }
                exec("DELETE FROM StudyPadTextEntry WHERE labelId NOT IN $labels")
                exec("DELETE FROM StudyPadTextEntryText WHERE studyPadTextEntryId NOT IN (SELECT id FROM StudyPadTextEntry)")
                // Every row left must be an add-on row (first sync key derived): otherwise it could overwrite a user row.
                for (table in SYNCED_TABLES) {
                    val foreign = queryLong("SELECT COUNT(*) FROM ${table.tableName} WHERE NOT ${isAddonIdSql(table.idField1)}")!!
                    check(foreign == 0L) { "$foreign rows of ${table.tableName} in $moduleInitials kept a non-derived id" }
                }
                if (queryLong("SELECT COUNT(*) FROM Label")!! == 0L) {
                    throw EmptyAddonCollectionException(moduleInitials, "no labels besides special labels")
                }
            }
        }
    }
}
