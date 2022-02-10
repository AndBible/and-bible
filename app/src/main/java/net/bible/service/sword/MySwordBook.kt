/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.sword

import android.database.sqlite.SQLiteDatabase
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.book.basic.AbstractBookMetaData
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.filter.SourceFilter
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.processing.RawTextToXmlProcessor
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import org.jdom2.Content

import java.io.File

class MySwordMetadata(
    private val _name: String,
    private val _abbreviation: String

): AbstractBookMetaData() {
    override fun getName(): String = _name
    override fun getAbbreviation(): String = _abbreviation
    override fun getInitials(): String = _abbreviation
    override fun getBookCharset(): String = "UTF-8"
    override fun getBookCategory(): BookCategory = BookCategory.BIBLE
    override fun isLeftToRight(): Boolean = true

    val keys = emptySet<String>().toMutableSet()
    val props = emptyMap<String, String>().toMutableMap()
    val collections = emptyMap<String, MutableCollection<String>>().toMutableMap()

    override fun getPropertyKeys(): MutableSet<String> = keys
    override fun getProperty(key: String?): String? = props[key]
    override fun getValues(key: String?): MutableCollection<String>? = collections[key]
    override fun setProperty(key: String, value: String) = props.set(key, value)
    override fun putProperty(key: String, value: String, forFrontend: Boolean) = setProperty(key, value)
}


class SqliteOpenFileState(sqliteFile: File): OpenFileState {
    val sqlDb = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun close() {
        sqlDb.close()
    }

    override fun getBookMetaData(): BookMetaData {
        sqlDb.use { d ->
            val c = d.query("Details", arrayOf("Description", "Abbreviation"), null, null, null, null, null)
            c.moveToFirst()
            val description = c.getString(0)
            val abbreviation = c.getString(1)
            c.close()
            return MySwordMetadata(description, abbreviation)
        }
    }

    override fun releaseResources() {
        sqlDb.close()
    }

    private var _lastAccess: Long  = 0L

    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

class SqliteBackend(metadata: BookMetaData): AbstractKeyBackend<SqliteOpenFileState>() {
    override fun initState(): SqliteOpenFileState {
        TODO("Not yet implemented")
    }

    override fun getCardinality(): Int {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): Key {
        TODO("Not yet implemented")
    }

    override fun indexOf(that: Key?): Int {
        TODO("Not yet implemented")
    }

    override fun readRawContent(state: SqliteOpenFileState?, key: Key?): String {
        TODO("Not yet implemented")
    }

}

class MySwordBook(metadata: BookMetaData, backend: Backend<SqliteOpenFileState>): AbstractPassageBook(metadata, backend) {
    override fun getGlobalKeyList(): Key {
        TODO("Not yet implemented")
    }

    override fun contains(key: Key?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getRawText(key: Key?): String {
        TODO("Not yet implemented")
    }

    override fun setRawText(key: Key?, rawData: String?) {
        TODO("Not yet implemented")
    }

    override fun setAliasKey(alias: Key?, source: Key?) {
        TODO("Not yet implemented")
    }

    override fun getOsis(key: Key?, noOpRawTextProcessor: RawTextToXmlProcessor?): MutableList<Content> {
        TODO("Not yet implemented")
    }

    override fun getFilter(): SourceFilter {
        TODO("Not yet implemented")
    }
}
