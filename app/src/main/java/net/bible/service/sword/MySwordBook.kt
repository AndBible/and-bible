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
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications

import java.io.File
import java.io.IOException

private fun getConfig(abbreviation: String, description: String): String {
    return """[mysword-$abbreviation]
Description=$description
Abbreviation=$abbreviation
Category=Biblical Texts
AndBibleSqliteSwordBook=1
Language=en
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=KJV"""
}

fun readBookMetaData(sqlDb: SQLiteDatabase): SwordBookMetaData {
    val c = sqlDb.query("Details", arrayOf("Description", "Abbreviation"), null, null, null, null, null)
    c.moveToFirst()
    val description = c.getString(0)
    val abbreviation = c.getString(1)
    c.close()
    val conf = getConfig(abbreviation, description)
    return SwordBookMetaData(conf.toByteArray(), "mysword-$abbreviation")
}

class SqliteVerseBackendState(sqliteFile: File): OpenFileState {
    val sqlDb: SQLiteDatabase = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun close() {
        //sqlDb.close()
    }

    override fun getBookMetaData(): SwordBookMetaData = readBookMetaData(sqlDb)
    override fun releaseResources() {} //sqlDb.close()

    private var _lastAccess: Long  = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

class SqliteBackend(val state: SqliteVerseBackendState, metadata: SwordBookMetaData): AbstractKeyBackend<SqliteVerseBackendState>(metadata) {
    override fun initState(): SqliteVerseBackendState = state

    override fun getCardinality(): Int {
        val cur = state.sqlDb.rawQuery("select count(*) as count from Bible", null)
        cur.moveToNext()
        val count = cur.getInt(0);
        cur.close()
        return count
    }

    override fun get(index: Int): Key {
        val cur = state.sqlDb.rawQuery("select Book,Chapter,Verse from Bible WHERE _rowid_ = ?", arrayOf("$index"))
        cur.moveToNext()
        val bookNum = cur.getInt(0)
        val v11n = Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
        val book = v11n.getBook(bookNum)
        val chapter = cur.getInt(1)
        val verse = cur.getInt(2)
        cur.close()
        return Verse(v11n, book, chapter, verse)
    }

    override fun indexOf(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        val cur = state.sqlDb.rawQuery("select _rowid_ from Bible WHERE Book = ? AND Chapter = ? AND Verse = ?",
            arrayOf("${verse.book.ordinal-1}", "${verse.chapter}", "${verse.verse}"))
        cur.moveToNext() || return -1
        val rowid = cur.getInt(0)
        cur.close()
        return rowid
    }

    override fun readRawContent(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        val cur = state.sqlDb.rawQuery(
            "select Scripture from Bible WHERE Book = ? AND Chapter = ? AND Verse = ?",
            arrayOf("${verse.book.ordinal-1}", "${verse.chapter}", "${verse.verse}")
        )
        cur.moveToNext() || throw IOException("Can't read")
        var text = cur.getString(0)
        text = text.replace("<CM>", "<div type=\"x-p\" sID=\"1\"/>")
        cur.close()
        return text
    }
}

fun getBook(file: File): SwordBook {
    val state = SqliteVerseBackendState(file)
    val metadata = state.bookMetaData
    val backend = SqliteBackend(state, metadata)
    return SwordBook(metadata, backend)
}
