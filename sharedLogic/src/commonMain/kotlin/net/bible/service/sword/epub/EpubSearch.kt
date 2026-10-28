/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.epub

import net.bible.sharedcore.log.Log
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import net.bible.service.db.exec
import net.bible.service.db.queryLong
import net.bible.service.db.openSqlite
import net.bible.service.db.queryFirst
import net.bible.service.db.queryRows
import net.bible.sharedlogic.sync.withMonitor
import okio.Path


data class EpubSearchResult(val fragId: Long, val ordinal: Int, val text: String)

class EpubSearch(val file: Path) {
    // Every use of the connection holds the lock: a bundled-driver connection is not safe for concurrent
    // use (the old framework-style database was), and the index is built on a bare thread while the UI may query it.
    private val db = try {
        openSqlite(file.toString())
    } catch (e: SQLiteException) {
        Log.e("EpubSearch", "Could not open database $file")
        null
    }
    fun close() = db?.let { c -> withMonitor(c) { c.close() } }

    /**
     * True when a COMPLETE index exists. A build fills [BUILD_TABLE] and only [commitIndex] renames it to
     * [INDEX_TABLE], so a process killed mid-build leaves no [INDEX_TABLE] (#3876).
     */
    val isIndexed: Boolean get() = db?.let { c ->
        withMonitor(c) {
            c.queryFirst("SELECT name FROM sqlite_master WHERE type='table' AND name=?", INDEX_TABLE) { true } ?: false
        }
    } ?: false

    /** Drops the index and any unfinished build. */
    fun deleteIndex() = db?.let { c ->
        withMonitor(c) {
            c.exec("DROP TABLE IF EXISTS $BUILD_TABLE")
            c.exec("DROP TABLE IF EXISTS $INDEX_TABLE")
        }
    }

    /**
     * Starts a build: a fresh [BUILD_TABLE] (the leftover of an interrupted build is dropped). The existing index,
     * if any, keeps serving searches until [commitIndex].
     */
    fun createTable() = checkNotNull(db) { "EPUB search database is unavailable" }.let { c ->
        withMonitor(c) {
            c.exec("DROP TABLE IF EXISTS $BUILD_TABLE")
            c.exec("""
                CREATE VIRTUAL TABLE $BUILD_TABLE USING FTS5(contentText, frag_id UNINDEXED, ordinal UNINDEXED);
            """.trimIndent())
        }
    }

    /** Adds a row to the build started by [createTable]. */
    fun addContent(content: String, fragId:Long, ordinal: Int) = checkNotNull(db) { "EPUB search database is unavailable" }.let { c ->
        withMonitor(c) {
            // Unlike the legacy insertOr helper, an index mutation must propagate SQLite failures.
            c.exec("INSERT INTO $BUILD_TABLE (contentText, frag_id, ordinal) VALUES (?, ?, ?)", content, fragId, ordinal)
            c.queryLong("SELECT last_insert_rowid()")!!
        }
    }

    /** Finishes the build: [BUILD_TABLE] atomically replaces [INDEX_TABLE], which marks the index complete. */
    fun commitIndex() = checkNotNull(db) { "EPUB search database is unavailable" }.let { c ->
        withMonitor(c) {
            c.execSQL("BEGIN IMMEDIATE")
            try {
                c.exec("DROP TABLE IF EXISTS $INDEX_TABLE")
                c.exec("ALTER TABLE $BUILD_TABLE RENAME TO $INDEX_TABLE")
                c.execSQL("COMMIT")
            } catch (t: Throwable) {
                try { c.execSQL("ROLLBACK") } catch (r: Throwable) { t.addSuppressed(r) }
                throw t
            }
        }
    }

    fun search(text: String): List<EpubSearchResult> = db?.let { c ->
        // `snippet` (not `highlight`): highlight() returns the WHOLE indexed block, so one hit used
        // to render as a screenful of text. 30 tokens around the best-matching window, with FTS5's
        // own ellipsis marker, is what a result row should show. No schema change, no re-index.
        withMonitor(c) {
            c.queryRows(
                "SELECT frag_id, ordinal, snippet($INDEX_TABLE, 0, '<b>', '</b>', '…', 30) " +
                    "FROM $INDEX_TABLE WHERE contentText MATCH ?",
                text,
            ) { EpubSearchResult(it.getLong(0), it.getInt(1), it.getText(2)) }
        }
    } ?: emptyList()

    private companion object {
        /** The complete index; its existence is the completion signal. */
        const val INDEX_TABLE = "SearchIndex"
        /** The index being built; renamed to [INDEX_TABLE] when the build finishes. */
        const val BUILD_TABLE = "SearchIndexBuilding"
    }
}
