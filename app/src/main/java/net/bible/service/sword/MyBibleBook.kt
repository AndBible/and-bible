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
import android.database.sqlite.SQLiteException
import android.util.Log
import net.bible.android.BibleApplication
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse

import java.io.File
import java.io.IOException

private fun getConfig(initials: String, description: String, language: String, category: String): String = """
[$initials]
Description=$description
Abbreviation=$initials
Category=$category
AndBibleMyBibleModule=1
Lang=$language
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=KJVA"""

const val TAG = "MyBibleBook"

class MockDriver: AbstractBookDriver() {
    override fun getBooks(): Array<Book> {
        return emptyArray()
    }

    override fun getDriverName(): String {
        return "MyBible"
    }

    override fun isDeletable(dead: Book?): Boolean {
        return false
    }
}

class SqliteVerseBackendState(sqliteFile: File, val moduleName: String?): OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData): this(sqliteFile, null) {
        this.metadata = metadata
    }

    val sqlDb: SQLiteDatabase = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun close() = sqlDb.close()

    var hasStories: Boolean = false

    var metadata: SwordBookMetaData? = null

    override fun getBookMetaData(): SwordBookMetaData {
        return metadata?: synchronized(this) {
            val initials = moduleName ?: "MyBible-" + File(sqlDb.path).nameWithoutExtension.split(".", limit = 2)[0]
            val description = sqlDb.rawQuery("select value from info where name = ?", arrayOf("description")).use {
                it.moveToFirst()
                it.getString(0)
            }
            val language = sqlDb.rawQuery("select value from info where name = ?", arrayOf("language")).use {
                it.moveToFirst()
                it.getString(0)
            }

            val tables =
                sqlDb.rawQuery("select name from sqlite_master where type = 'table' AND name not like 'sqlite_%'", null)
                    .use {
                        val names = arrayListOf<String>()
                        while (it.moveToNext()) {
                            names.add(it.getString(0))
                        }
                        names
                    }
            val isCommentary = tables.contains("commentaries")
            val isBible = tables.contains("verses")
            hasStories = tables.contains("stories")

            val category = when {
                isBible -> "Biblical Texts"
                isCommentary -> "Commentaries"
                else -> "Illegal"
            }

            val conf = getConfig(initials, description, language, category)
            Log.i(TAG, "Creating MyBibleBook metadata $initials, $description $language $category")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)

            metadata.driver = MockDriver()
            this.metadata = metadata
            return@synchronized metadata
        }
    }

    override fun releaseResources() {}

    private var _lastAccess: Long  = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

class SqliteBackend(val state: SqliteVerseBackendState, metadata: SwordBookMetaData): AbstractKeyBackend<SqliteVerseBackendState>(metadata) {
    override fun initState(): SqliteVerseBackendState {
        return state
    }

    override fun getCardinality(): Int {
        val cur = state.sqlDb.rawQuery("select count(*) as count from Bible", null)
        cur.moveToNext()
        val count = cur.getInt(0);
        cur.close()
        return count
    }

    override fun get(index: Int): Key {
        val cur = state.sqlDb.rawQuery("select book_number,chapter,verse from verses WHERE _rowid_ = ?", arrayOf("$index"))
        cur.moveToNext()
        val bookNum = cur.getInt(0)
        val book = intToBibleBook[bookNum]
        val chapter = cur.getInt(1)
        val verse = cur.getInt(2)
        cur.close()
        return Verse(KJVA, book, chapter, verse)
    }

    private fun indexOfBible(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        val cur = state.sqlDb.rawQuery("select _rowid_ from verses WHERE book_number = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}"))
        cur.moveToNext() || return -1
        val rowid = cur.getInt(0)
        cur.close()
        return rowid
    }

    private fun indexOfCommentary(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        val cur = state.sqlDb.rawQuery(
            """select _rowid_ from commentaries WHERE book_number = ? AND 
                            ((chapter_number_from <= ? AND verse_number_from <= ? AND
                            chapter_number_to >= ? AND verse_number_to >= ?) OR
                            (chapter_number_from = ? AND verse_number_from = ? AND chapter_number_to IS NULL AND verse_number_to IS NULL))

                            """,
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}"))
        cur.moveToNext() || return -1
        val rowid = cur.getInt(0)
        cur.close()
        return rowid
    }

    override fun indexOf(that: Key): Int {
        return when(bookMetaData.bookCategory) {
            BookCategory.BIBLE -> indexOfBible(that)
            BookCategory.COMMENTARY -> indexOfCommentary(that)
            else -> -1
        }
    }

    private fun readBible(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        var text = state.sqlDb.rawQuery(
            "select text from verses WHERE book_number = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}")
        ).use {
            it.moveToNext() || throw IOException("Can't read")
            it.getString(0)
        }

        val stories = if(state.hasStories) {
            state.sqlDb.rawQuery(
                "select title from stories WHERE book_number = ? AND chapter = ? AND verse = ?",
                arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}")
            ).use {
                val result = arrayListOf<String>()
                while (it.moveToNext()) {
                    result.add(it.getString(0))
                }
                result
            }
        } else {
            arrayListOf()
        }

        for(story in stories) {
            text = if(story.startsWith("<")) {
                "$text$story"
            } else {
                "<title canonical=\"false\">$story</title>$text"
            }
        }
        return text
    }

    private fun readCommentary(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        return state.sqlDb.rawQuery(
            """select text from commentaries WHERE book_number = ? AND
                            ((chapter_number_from <= ? AND verse_number_from <= ? AND
                            chapter_number_to >= ? AND verse_number_to >= ?) OR
                            (chapter_number_from = ? AND verse_number_from = ? AND chapter_number_to IS NULL AND verse_number_to IS NULL))
                """,
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}")
        ).use {
            it.moveToNext() || throw IOException("Can't read")
            it.getString(0)
        }
    }

    override fun readRawContent(state: SqliteVerseBackendState, key: Key): String {
        return when(bookMetaData.bookCategory) {
            BookCategory.BIBLE -> readBible(state, key)
            BookCategory.COMMENTARY -> readCommentary(state, key)
            else -> ""
        }
    }
}

val myBibleBible = object: BookType("MyBibleBible", BookCategory.BIBLE, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.SQLite3")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

val myBibleCommentary = object: BookType("MyBibleCommentary", BookCategory.COMMENTARY, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.SQLite3")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

fun addMyBibleBook(file: File, name: String? = null): SwordBook? {
    if(!(file.canRead() && file.isFile)) return null
    val state = SqliteVerseBackendState(file, name)
    val metadata = try { state.bookMetaData } catch (err: SQLiteException) {
        Log.e(TAG, "Failed to load MyBible module $file", err)
        return null
    }
    val backend = SqliteBackend(state, metadata)
    val book = SwordBook(metadata, backend)
    if(IndexManagerFactory.getIndexManager().isIndexed(book)) {
        metadata.indexStatus = IndexStatus.DONE
    } else {
        metadata.indexStatus = IndexStatus.UNDONE
    }

    Books.installed().addBook(book)
    return book
}

fun addManuallyInstalledMyBibleBooks() {
    val dir = File(BibleApplication.application.getExternalFilesDir(null), "mybible")
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()?: emptyArray()) {
        addMyBibleBook(f)
    }
}

val Book.isMyBibleBook get() = bookMetaData.getProperty("AndBibleMyBibleModule") != null
