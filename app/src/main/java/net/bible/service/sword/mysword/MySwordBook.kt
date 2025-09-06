/*
 * Copyright (c) 2022-2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.mysword

import android.database.sqlite.SQLiteException
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import net.bible.android.SharedConstants
import net.bible.service.sword.SqliteSwordDriver
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBook
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

private fun getConfig(data: MySwordModuleInfo): String {
    var conf = """
[${data.initials}]
Description=${data.description}
Abbreviation=${data.abbreviation}
Category=${data.category}
AndBibleMySwordModule=1
AndBibleDbFile=${data.moduleFileName}
Lang=${data.language}
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=KJVA"""
    if(data.isStrongsDict) {
        conf += "\nFeature=GreekDef"
        conf += "\nFeature=HebrewDef"
    }
    if(data.hasStrongs) {
        conf += "\nGlobalOptionFilter = OSISStrongs"
        conf += "\nGlobalOptionFilter = OSISMorph"
    }
    return conf
}

const val TAG = "MySwordBook"

class MySwordModuleInfo (
    val moduleFileName: String,
    val initials: String,
    val title: String,
    val description: String,
    val abbreviation: String,
    val version: String,
    val rightToLeft: Boolean,
    val isStrongsDict: Boolean,
    val hasStrongs: Boolean,
    val language: String,
    val category: String,
)

class SqliteVerseBackendState(private val sqliteFile: File): OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData): this(sqliteFile) {
        this.metadata = metadata
    }

    private var _sqlDb: SQLiteDatabase? = null

    val sqlDb: SQLiteDatabase get() = synchronized(this) {
        _sqlDb?.run {
            if (isOpen) this else null
        } ?: run {
            Log.i(TAG, "initDatabase ${sqliteFile.name}")
            val db = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)
            _sqlDb = db
            db
        }
    }

    override fun close() {
        Log.i(TAG, "close database ${sqliteFile.name}")
        _sqlDb?.close()
        _sqlDb = null
    }

    var metadata: SwordBookMetaData? = null

    private val re = Regex("[^a-zA-z0-9]")
    private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

    override fun getBookMetaData(): SwordBookMetaData {
        return metadata?: synchronized(this) {
            val db = this.sqlDb
            val dbFile = File(db.path!!)
            val categoryAbbreviation = dbFile.nameWithoutExtension.substringAfterLast('.', "")
            val category = when(categoryAbbreviation) {
                "bbl" -> "Biblical Texts"
                "cmt" -> "Commentaries"
                "dct" -> "Lexicons / Dictionaries"
                else -> "Illegal"
            }
            val initials = "MySword-" + sanitizeModuleName(dbFile.nameWithoutExtension)

            val data = db.rawQuery("select * from details", null).use {
                it.moveToFirst()
                val names = it.columnNames.map { n -> n.lowercase() }
                val titleColumn = names.indexOf("title")
                val descriptionColumn = names.indexOf("description")
                val abbreviationColumn = names.indexOf("abbreviation")
                val versionColumn = names.indexOf("version")
                val rightToLeftColumn = names.indexOf("rightToLeft")
                val strongColumn = names.indexOf("strong")
                val languageColumn = names.indexOf("language")

                fun getString(columnNum: Int, default: String = ""): String  =
                    when(columnNum) {
                        -1 -> default
                        else -> it.getString(columnNum) ?: default
                    }

                fun getBoolean(columnNum: Int): Boolean =
                    when(columnNum) {
                        -1 -> false
                        else -> it.getInt(columnNum) == 1
                    }

                MySwordModuleInfo(
                    initials = initials,
                    title = getString(titleColumn),
                    description = getString(descriptionColumn),
                    abbreviation = getString(abbreviationColumn, initials),
                    version = getString(versionColumn),
                    rightToLeft = getBoolean(rightToLeftColumn),
                    hasStrongs = categoryAbbreviation == "bbl" && getBoolean(strongColumn),
                    language = getString(languageColumn, "eng"),
                    category = category,
                    isStrongsDict = categoryAbbreviation == "dct" && getBoolean(strongColumn),
                    moduleFileName = db.path!!,
                )
            }

            val conf = getConfig(data)
            Log.i(TAG, "Creating MySwordBook metadata $initials $category")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)

            metadata.driver = SqliteSwordDriver()
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
            BookCategory.BIBLE -> "bible"
            BookCategory.COMMENTARY -> "commentary"
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
                val cur = state.sqlDb.rawQuery("select word from dictionary", null)
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
                try {
                    state.sqlDb.rawQuery(
                        "select word from dictionary WHERE _rowid_ = ?",
                        arrayOf(index)
                    ).use { c ->
                        c.moveToNext()
                        val topic = c.getString(0)
                        return DefaultLeafKeyList(topic)
                    }
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Error getting index $index", e)
                    throw IndexOutOfBoundsException("Error getting index $index")
                }
            }
            else -> throw RuntimeException("Per-index lookup unsupported")
        }
    }

    private fun indexOfBible(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        state.sqlDb.rawQuery("select _rowid_ from bible WHERE book = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToMySwordInt[verse.book]}", "${verse.chapter}", "${verse.verse}")).use {
            it.moveToNext() || return -1
            return it.getInt(0)
        }
    }

    private fun indexOfDictionary(that: Key): Int {
        if(that !is DefaultLeafKeyList) return -1;
        val keyName = that.name
        state.sqlDb.rawQuery("select _rowid_ from dictionary WHERE word = ?", arrayOf(keyName)).use {
            it.moveToNext() || return -1
            return it.getInt(0)
        }

    }

    private fun indexOfCommentary(that: Key): Int {
        val verse = KeyUtil.getVerse(that)
        state.sqlDb.rawQuery(
            """select _rowid_ from commentary WHERE book = ? AND 
                            ((chapter = ? AND fromverse <= ? AND
                            toverse >= ?) OR
                            (chapter = ? AND fromverse = ? AND (toverse IS NULL or toverse = 0)))

                            """,
            arrayOf("${bibleBookToMySwordInt[verse.book]}", "${verse.chapter}", "${verse.verse}", "${verse.verse}", "${verse.chapter}", "${verse.verse}")).use {

            it.moveToNext() || return -1
            return it.getInt(0)
        }
    }

    override fun indexOf(that: Key): Int =
        try  {
            when(bookMetaData.bookCategory) {
                BookCategory.BIBLE -> indexOfBible(that)
                BookCategory.COMMENTARY -> indexOfCommentary(that)
                BookCategory.DICTIONARY -> indexOfDictionary(that)
                else -> -1
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Error in indexOf", e)
            -1
        }

    private fun readBible(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        return state.sqlDb.rawQuery(
            "select scripture from bible WHERE book = ? AND chapter = ? AND verse = ?",
            arrayOf("${bibleBookToMySwordInt[verse.book]}", "${verse.chapter}", "${verse.verse}")
        ).use {
            it.moveToNext() || throw IOException("Can't read $key")
            it.getString(0)
        }
    }
    private fun readDictionary(state: SqliteVerseBackendState, key: Key): String {
        if(key !is DefaultLeafKeyList) throw RuntimeException("Invalid key");
        val keyName = key.name
        return state.sqlDb.rawQuery("select data from dictionary WHERE word = ?", arrayOf(keyName)
        ).use {
            it.moveToNext() || throw IOException("Can't read $key")
            it.getString(0)
        }
    }

    private fun readCommentary(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        val fromVerse: Int = if(verse.chapter == 1 && verse.verse == 1) 0 else verse.verse
        val toVerse = verse.verse

        return state.sqlDb.rawQuery(
            """select data from commentary WHERE book = ? AND
                            ((chapter = ? AND fromverse <= ? AND toverse >= ?) OR
                            (chapter = ? AND fromverse = ? AND (toverse IS NULL OR toverse = 0)))
                """,
            arrayOf("${bibleBookToMySwordInt[verse.book]}", "${verse.chapter}", "$toVerse", "$fromVerse", "${verse.chapter}", "$toVerse")
        ).use {
            val result = arrayListOf<String>()
            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
            result
        }.joinToString {"<div>$it</div>"}
    }

    private val strongsMorphRe = Regex("""(\w+)<W([GH])(\d+)><WT([a-zA-Z\d\-]+)( l="([^"]+)")?>""")
    private val strongsRe = Regex("""(\w+)<W([GH]\d+)>(<W([GH]\d+)>)?(<W([GH]\d+)>)?""")
    private val morphRe = Regex("""<WT([a-zA-Z\d\-]+)( l="([^"]+)")?>""")
    private val tagEndRe = Regex("""<(Ts|Fi|Fo|q|e|t|x|h|g)>""")
    private val singleTagRe = Regex("""<(CM|CL|PF\d|Pl\d|Cl|D|wh|wg|wt|br)>""")

    // MySword has weird non-xml tags, so we need to do some transformation here.
    // https://www.mysword.info/modules-format
    private fun transformMySwordTags(mySwordText: String): String = mySwordText
        .replace(strongsMorphRe) { m ->
            val word = m.groups[1]!!.value
            val lang = m.groups[2]!!.value
            val strongsNum = m.groups[3]!!.value
            val morphCode = m.groups[4]!!.value
            "<w lemma=\"strong:${lang}${strongsNum}\" morph=\"strongMorph:${morphCode}\">${word}</w>"
        }
        .replace(strongsRe) { m ->
            val word = m.groups[1]!!.value
            val strongs1 = m.groups[2]!!.value
            val strongs2 = m.groups[4]?.value
            val strongs3 = m.groups[6]?.value
            val strongs = listOf(strongs1, strongs2, strongs3).filterNotNull().joinToString(" ") { "strong:$it" }
            "<w lemma=\"$strongs\">${word}</w>"
        }
        .replace(morphRe) { m ->
            val morphCode = m.groups[1]!!.value
            "<w morph=\"strongMorph:${morphCode}\">${morphCode}</w>"
        }
        .replace(tagEndRe) { m ->
            "</${m.groups[1]!!.value.uppercase()}>"
        }
        .replace(singleTagRe) { m ->
            "<${m.groups[1]!!.value}/>"
        }

    override fun readRawContent(state: SqliteVerseBackendState, key: Key): String = try {
        when (bookMetaData.bookCategory) {
            BookCategory.BIBLE -> transformMySwordTags(readBible(state, key))
            BookCategory.COMMENTARY -> transformMySwordTags(readCommentary(state, key))
            BookCategory.DICTIONARY -> transformMySwordTags(readDictionary(state, key))
            else -> ""
        }
    } catch (e: SQLiteException) {
        throw IOException("Can't read $key", e)
    }
}

val mySwordBible = object: BookType("MySwordBible", BookCategory.BIBLE, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.mybible")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

val mySwordCommentary = object: BookType("MySwordCommentary", BookCategory.COMMENTARY, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.mybible")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

val mySwordDictionary = object: BookType("MySwordDictionary", BookCategory.DICTIONARY, KeyType.LIST) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordDictionary(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val file = File(File(sbmd.location), "module.mybible")
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

fun addMySwordBook(file: File) {
    if(!(file.canRead() && file.isFile)) return
    val state = SqliteVerseBackendState(file)
    val metadata = try { state.bookMetaData } catch (err: SQLiteException) {
        Log.e(TAG, "Failed to load MySword module $file", err)
        return
    }
    if(Books.installed().getBook(metadata.initials) != null) return
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
    return
}

fun addManuallyInstalledMySwordBooks() {
    val dir = File(SharedConstants.modulesDir, "mysword")
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.walkTopDown()) {
        if(f.isFile && f.canRead() && f.path.lowercase().endsWith(".mybible")) {
            addMySwordBook(f)
        }
    }
}

val Book.isManuallyInstalledMySwordBook get() = bookMetaData.getProperty("AndBibleMySwordModule") != null
