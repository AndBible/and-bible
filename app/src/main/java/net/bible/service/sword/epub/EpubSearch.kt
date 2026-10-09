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

import android.util.Log
import androidx.sqlite.SQLiteException
import net.bible.service.db.exec
import net.bible.service.db.insertOr
import net.bible.service.db.openSqlite
import net.bible.service.db.queryFirst
import net.bible.service.db.queryRows
import java.io.File


data class EpubSearchResult(val fragId: Long, val ordinal: Int, val text: String)

class EpubSearch(val file: File) {
    // Every use of the connection holds the lock: a bundled-driver connection is not safe for concurrent
    // use (the old framework-style database was), and the index is built on a bare thread while the UI may query it.
    private val db = try {
        openSqlite(file.path)
    } catch (e: SQLiteException) {
        Log.e("EpubSearch", "Could not open database ${file.path}")
        null
    }
    val isIndexed: Boolean get() = db?.let { c ->
        synchronized(c) {
            c.queryFirst("SELECT name FROM sqlite_master WHERE type='table' AND name=?", "SearchIndex") { true } ?: false
        }
    } ?: false

    fun deleteIndex() = db?.let { c ->
        synchronized(c) { c.exec("""DROP TABLE IF EXISTS SearchIndex""") }
    }

    fun createTable() = db?.let { c ->
        synchronized(c) {
            c.exec("""
                CREATE VIRTUAL TABLE SearchIndex USING FTS5(contentText, frag_id UNINDEXED, ordinal UNINDEXED);
            """.trimIndent())
        }
    }

    fun addContent(content: String, fragId:Long, ordinal: Int) = db?.let { c ->
        synchronized(c) {
            c.insertOr("IGNORE", "SearchIndex", "contentText" to content, "frag_id" to fragId, "ordinal" to ordinal)
        }
    }

    fun search(text: String): List<EpubSearchResult> = db?.let { c ->
        // `snippet` (not `highlight`): highlight() returns the WHOLE indexed block, so one hit used
        // to render as a screenful of text. 30 tokens around the best-matching window, with FTS5's
        // own ellipsis marker, is what a result row should show. No schema change, no re-index.
        synchronized(c) {
            c.queryRows(
                "SELECT frag_id, ordinal, snippet(SearchIndex, 0, '<b>', '</b>', '…', 30) " +
                    "FROM SearchIndex WHERE contentText MATCH ?",
                text,
            ) { EpubSearchResult(it.getLong(0), it.getInt(1), it.getText(2)) }
        }
    } ?: emptyList()
}
