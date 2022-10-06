/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Environment
import android.util.Log
import net.bible.android.BibleApplication
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBook
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import java.io.File
import java.io.IOException

private fun getConfig(initials: String, description: String, language: String, category: String, hasStrongsDef: Boolean, hasStrongs: Boolean): String {
    var conf = """
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
    if(hasStrongsDef) {
        conf += "\nFeature=GreekDef"
        conf += "\nFeature=HebrewDef"
    }
    if(hasStrongs) {
        conf += "\nGlobalOptionFilter = OSISStrongs"
    }
    return conf
}

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

class SqliteVerseBackendState(private val sqliteFile: File, val moduleName: String?): OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData): this(sqliteFile, null) {
        this.metadata = metadata
    }

    private var _sqlDb: SQLiteDatabase? = null

    val sqlDb: SQLiteDatabase get() = synchronized(this) {
        _sqlDb?.run {
            if (isOpen) this else null
        } ?: run {
            Log.i(TAG, "initDatabase $moduleName ${sqliteFile.name}")
            val db = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)
            _sqlDb = db
            db
        }
    }

    override fun close() {
        Log.i(TAG, "close database $moduleName ${sqliteFile.name}")
        _sqlDb?.close()
        _sqlDb = null
    }

    var hasStories: Boolean = false

    var metadata: SwordBookMetaData? = null

    private val re = Regex("[^a-zA-z0-9]")
    private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

    override fun getBookMetaData(): SwordBookMetaData {
        return metadata?: synchronized(this) {
            val db = this.sqlDb
            val initials = moduleName ?: "MyBible-" + sanitizeModuleName(File(db.path).nameWithoutExtension.split(".", limit = 2)[0])
            val description = db.rawQuery("select value from info where name = ?", arrayOf("description")).use {
                it.moveToFirst()
                it.getString(0)
            }
            val language = db.rawQuery("select value from info where name = ?", arrayOf("language")).use {
                it.moveToFirst()
                it.getString(0)
            }
            val hasStrongsDef = db.rawQuery("select value from info where name = ?", arrayOf("is_strong")).use {
                it.moveToFirst() || return@use false
                it.getString(0) == "true"
            }
            val hasStrongs = db.rawQuery("select value from info where name = ?", arrayOf("strong_numbers")).use {
                it.moveToFirst() || return@use false
                it.getString(0) == "true"
            }

            val tables =
                db.rawQuery("select name from sqlite_master where type = 'table' AND name not like 'sqlite_%'", null)
                    .use {
                        val names = arrayListOf<String>()
                        while (it.moveToNext()) {
                            names.add(it.getString(0))
                        }
                        names
                    }
            val isCommentary = tables.contains("commentaries")
            val isBible = tables.contains("verses")
            val isDictionary = tables.contains("dictionary")
            hasStories = tables.contains("stories")

            val category = when {
                isBible -> "Biblical Texts"
                isCommentary -> "Commentaries"
                isDictionary -> "Lexicons / Dictionaries"
                else -> "Illegal"
            }

            val conf = getConfig(initials, description, language, category, hasStrongsDef, hasStrongs)
            Log.i(TAG, "Creating MyBibleBook metadata $initials, $description $language $category")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)

            metadata.driver = MockDriver()
            this.metadata = metadata
            return@synchronized metadata
        }
    }

    override fun releaseResources() {
        close()
    }

    private var _lastAccess: Long  = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

class SqliteBackend(val state: SqliteVerseBackendState, metadata: SwordBookMetaData): AbstractKeyBackend<SqliteVerseBackendState>(metadata) {
    override fun initState(): SqliteVerseBackendState {
        Log.i(TAG, "initState")
        state.sqlDb
        return state
    }

    override fun getCardinality(): Int {
        val table = when(bookMetaData.bookCategory) {
            BookCategory.DICTIONARY -> "dictionary"
            BookCategory.BIBLE -> "verses"
            BookCategory.COMMENTARY -> "commentaries"
            else -> throw RuntimeException("Illegal book category")
        }
        state.sqlDb.rawQuery("select count(*) as count from $table", null).use { cur ->
            cur.moveToNext()
            return cur.getInt(0)
        }
    }

    override fun iterator(): MutableIterator<Key> =
        when(bookMetaData.bookCategory) {
            BookCategory.DICTIONARY -> {
                val cur = state.sqlDb.rawQuery("select topic from dictionary", null)
                object: MutableIterator<Key> {
                    override fun hasNext(): Boolean {
                        return !cur.isLast
                    }
                    override fun next(): Key {
                        cur.moveToNext()
                        val k = DefaultLeafKeyList(cur.getString(0))
                        if(cur.isLast) {
                            Log.i(TAG, "Closing dict cursor")
                            cur.close()
                        }
                        return k
                    }
                    override fun remove() {
                        throw UnsupportedOperationException()
                    }
                }
            }
            else -> super.iterator()
        }

    override fun get(index: Int): Key {
        when(bookMetaData.bookCategory) {
            BookCategory.DICTIONARY -> {
                state.sqlDb.rawQuery(
                    "select topic from dictionary WHERE _rowid_ = ?",
                    arrayOf("${index + 1}")
                ).use { c ->
                    c.moveToNext()
                    val topic = c.getString(0)
                    return DefaultLeafKeyList(topic)
                }
            }
            else -> throw RuntimeException("Per-index lookup unsupported")
        }
    }

    private fun indexOfBible(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        state.sqlDb.rawQuery("select _rowid_ from verses WHERE book_number = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}")).use {
            it.moveToNext() || return -1
            return it.getInt(0)
        }
    }

    private fun indexOfDictionary(that: Key): Int {
        if(that !is DefaultLeafKeyList) return -1;
        val keyName = that.name
        state.sqlDb.rawQuery("select _rowid_ from dictionary WHERE topic = ?", arrayOf(keyName)).use {
            it.moveToNext() || return -1
            return it.getInt(0)
        }

    }

    private fun indexOfCommentary(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        state.sqlDb.rawQuery(
            """select _rowid_ from commentaries WHERE book_number = ? AND 
                            ((chapter_number_from <= ? AND verse_number_from <= ? AND
                            chapter_number_to >= ? AND verse_number_to >= ?) OR
                            (chapter_number_from = ? AND verse_number_from = ? AND chapter_number_to IS NULL AND verse_number_to IS NULL))

                            """,
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}", "${verse.chapter}", "${verse.verse}")).use {

            it.moveToNext() || return -1
            return it.getInt(0)
        }
    }

    override fun indexOf(that: Key): Int {
        return when(bookMetaData.bookCategory) {
            BookCategory.BIBLE -> indexOfBible(that)
            BookCategory.COMMENTARY -> indexOfCommentary(that)
            BookCategory.DICTIONARY -> indexOfDictionary(that)
            else -> -1
        }
    }

    private fun readBible(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        var text = state.sqlDb.rawQuery(
            "select text from verses WHERE book_number = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToInt[verse.book]}", "${verse.chapter}", "${verse.verse}")
        ).use {
            it.moveToNext() || throw IOException("Can't read $key")
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
    private fun readDictionary(state: SqliteVerseBackendState, key: Key): String {
        if(key !is DefaultLeafKeyList) throw RuntimeException("Invalid key");
        val keyName = key.name
        return state.sqlDb.rawQuery("select definition from dictionary WHERE topic = ?", arrayOf(keyName)
        ).use {
            it.moveToNext() || throw IOException("Can't read $key")
            it.getString(0)
        }
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
            it.moveToNext() || throw IOException("Can't read $key")
            it.getString(0)
        }
    }

    override fun readRawContent(state: SqliteVerseBackendState, key: Key): String {
        return when(bookMetaData.bookCategory) {
            BookCategory.BIBLE -> readBible(state, key)
            BookCategory.COMMENTARY -> readCommentary(state, key)
            BookCategory.DICTIONARY -> readDictionary(state, key)
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

val myBibleDictionary = object: BookType("MyBibleDictionary", BookCategory.DICTIONARY, KeyType.LIST) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordDictionary(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.SQLite3")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

fun addMyBibleBook(file: File, name: String? = null): AbstractBook? {
    if(!(file.canRead() && file.isFile)) return null
    val state = SqliteVerseBackendState(file, name)
    val metadata = try { state.bookMetaData } catch (err: SQLiteException) {
        Log.e(TAG, "Failed to load MyBible module $file", err)
        return null
    }
    val backend = SqliteBackend(state, metadata)
    val book =
        if (metadata.bookCategory == BookCategory.DICTIONARY)
            SwordDictionary(metadata, backend)
        else
            SwordBook(metadata, backend)

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
//    val dir = File(BibleApplication.application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MyBible")
//    val dir = File(BibleApplication.application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
//    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"MyBible")
//    val g = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"MyBible/Summ-c.commentaries.SQLite3")
//    addMyBibleBook(g)

    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()?.filter { it.path.lowercase().endsWith(".sqlite3") }?: emptyList()) {
        addMyBibleBook(f)
    }
}

val Book.isMyBibleBook get() = bookMetaData.getProperty("AndBibleMyBibleModule") != null
