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

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE


data class EpubSearchResult(val fragId: Long, val ordinal: Int, val text: String)

class EpubSearch(val db: SupportSQLiteDatabase) {
    val isIndexed: Boolean get() = db.run {
        !query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf("SearchIndex")).isAfterLast
    }

    fun deleteIndex() = db.run {
        execSQL("""DROP TABLE SearchIndex""")
    }

    fun createTable() = db.run {
        execSQL("""
            CREATE VIRTUAL TABLE SearchIndex USING FTS5(contentText, frag_id UNINDEXED, ordinal UNINDEXED);
        """.trimIndent())
    }

    fun addContent(content: String, fragId:Long, ordinal: Int) = db.run {
        insert("SearchIndex", CONFLICT_IGNORE, ContentValues().apply {
            put("contentText", content)
            put("frag_id", fragId)
            put("ordinal", ordinal)
        })
    }

    fun search(text: String): List<EpubSearchResult> = db.run {
        query("SELECT frag_id, ordinal, highlight(SearchIndex, 0, '<b>', '</b>') FROM SearchIndex WHERE contentText MATCH ?", bindArgs = arrayOf(text)).let { c ->
            c.moveToFirst()
            val list = mutableListOf<EpubSearchResult>()
            while (!c.isAfterLast){
                val fragId = c.getString(0).toLong()
                val ordinal = c.getString(1).toInt()
                val txt = c.getString(2)
                c.moveToNext()
                list.add(EpubSearchResult(fragId, ordinal, txt))
            }
            list
        }
    }
}
