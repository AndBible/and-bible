/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.esword

import android.database.sqlite.SQLiteException
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import net.bible.android.SharedConstants
import net.bible.service.sword.SqliteSwordDriver
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
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

private const val TAG = "ESwordBook"

private fun getConfig(data: ESwordModuleInfo): String {
    var conf = """
[${data.initials}]
Description=${data.description}
Abbreviation=${data.abbreviation}
Category=Biblical Texts
AndBibleESwordModule=1
AndBibleDbFile=${data.moduleFileName}
Lang=${data.language}
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=KJVA"""
    if (data.hasStrongs) {
        conf += "\nGlobalOptionFilter = OSISStrongs"
    }
    return conf
}

class ESwordModuleInfo(
    val moduleFileName: String,
    val initials: String,
    val description: String,
    val abbreviation: String,
    val rightToLeft: Boolean,
    val hasStrongs: Boolean,
    val language: String,
)

class SqliteVerseBackendState(private val sqliteFile: File) : OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData) : this(sqliteFile) {
        this.metadata = metadata
    }

    private var _sqlDb: SQLiteDatabase? = null

    val sqlDb: SQLiteDatabase
        get() = synchronized(this) {
            _sqlDb?.run {
                if (isOpen) this else null
            } ?: run {
                Log.i(TAG, "initDatabase ${sqliteFile.name}")
                val db = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)
                _sqlDb = db
                db
            }
        }

    val isBblx: Boolean = sqliteFile.name.lowercase().endsWith(".bblx")

    override fun close() {
        Log.i(TAG, "close database ${sqliteFile.name}")
        _sqlDb?.close()
        _sqlDb = null
    }

    var metadata: SwordBookMetaData? = null
        private set

    private val re = Regex("[^a-zA-Z0-9]")
    private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

    override fun getBookMetaData(): SwordBookMetaData {
        return metadata ?: synchronized(this) {
            val db = this.sqlDb
            val dbFile = File(db.path!!)
            val initials = "ESword-" + sanitizeModuleName(dbFile.nameWithoutExtension)

            val data = db.rawQuery("select * from Details", null).use {
                it.moveToFirst()
                val names = it.columnNames.map { n -> n.lowercase() }

                fun colIdx(vararg candidates: String): Int =
                    candidates.firstNotNullOfOrNull { c -> names.indexOf(c).takeIf { it >= 0 } } ?: -1

                fun getString(columnNum: Int, default: String = ""): String =
                    when (columnNum) {
                        -1 -> default
                        else -> it.getString(columnNum) ?: default
                    }

                fun getBoolean(columnNum: Int): Boolean =
                    when (columnNum) {
                        -1 -> false
                        else -> it.getInt(columnNum) != 0
                    }

                val descriptionCol = colIdx("description", "title")
                val abbreviationCol = colIdx("abbreviation")
                val rightToLeftCol = colIdx("righttoleft")
                val strongCol = colIdx("strong", "strongs")

                ESwordModuleInfo(
                    initials = initials,
                    description = getString(descriptionCol),
                    abbreviation = getString(abbreviationCol, initials),
                    rightToLeft = getBoolean(rightToLeftCol),
                    hasStrongs = getBoolean(strongCol),
                    language = "en",
                    moduleFileName = db.path!!,
                )
            }

            val conf = getConfig(data)
            Log.i(TAG, "Creating ESwordBook metadata $initials")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)
            metadata.driver = SqliteSwordDriver()
            this.metadata = metadata
            return@synchronized metadata
        }
    }

    override fun releaseResources() {
        close()
    }

    private var _lastAccess: Long = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

class SqliteBackend(
    private val state: SqliteVerseBackendState,
    metadata: SwordBookMetaData
) : AbstractKeyBackend<SqliteVerseBackendState>(metadata) {

    override fun initState(): SqliteVerseBackendState {
        Log.i(TAG, "initState")
        state.sqlDb
        return state
    }

    override fun getCardinality(): Int {
        state.sqlDb.rawQuery("select count(*) from Bible", null).use { cur ->
            cur.moveToNext()
            return cur.getInt(0)
        }
    }

    override fun get(index: Int): Key {
        throw RuntimeException("Per-index lookup unsupported for e-Sword Bible")
    }

    private fun verseParams(verse: Verse): Array<String> {
        val bookNum = bibleBookToESwordInt[verse.book]
            ?: throw IOException("Unmapped Bible book: ${verse.book}")
        return arrayOf("$bookNum", "${verse.chapter}", "${verse.verse}")
    }

    override fun indexOf(that: Key): Int = try {
        val verse = KeyUtil.getVerse(that)
        state.sqlDb.rawQuery(
            "select _rowid_ from Bible WHERE Book = ? AND Chapter = ? AND Verse = ?",
            verseParams(verse)
        ).use {
            it.moveToNext() || return -1
            it.getInt(0)
        }
    } catch (e: SQLiteException) {
        Log.e(TAG, "Error in indexOf", e)
        -1
    } catch (_: IOException) {
        -1
    }

    private fun readBible(state: SqliteVerseBackendState, key: Key): String {
        val verse = KeyUtil.getVerse(key)
        return state.sqlDb.rawQuery(
            "select Scripture from Bible WHERE Book = ? AND Chapter = ? AND Verse = ?",
            verseParams(verse)
        ).use {
            it.moveToNext() || throw IOException("Can't read $key")
            it.getString(0) ?: ""
        }
    }

    override fun readRawContent(state: SqliteVerseBackendState, key: Key): String = try {
        val raw = readBible(state, key)
        if (state.isBblx) convertRtfToOsis(raw) else raw
    } catch (e: SQLiteException) {
        throw IOException("Can't read $key", e)
    }
}

val eSwordBible = object : BookType("ESwordBible", BookCategory.BIBLE, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val filePath = sbmd.getProperty("AndBibleDbFile")
        val file = File(filePath)
        val state = SqliteVerseBackendState(file, sbmd)
        return SqliteBackend(state, sbmd)
    }
}

fun addESwordBook(file: File) {
    if (!(file.canRead() && file.isFile)) return
    val state = SqliteVerseBackendState(file)
    val metadata = try {
        state.bookMetaData
    } catch (err: SQLiteException) {
        Log.e(TAG, "Failed to load e-Sword module $file", err)
        return
    }
    if (Books.installed().getBook(metadata.initials) != null) return
    val backend = SqliteBackend(state, metadata)
    val book = SwordBook(metadata, backend)

    if (IndexManagerFactory.getIndexManager().isIndexed(book)) {
        metadata.indexStatus = IndexStatus.DONE
    } else {
        metadata.indexStatus = IndexStatus.UNDONE
    }

    Books.installed().addBook(book)
}

fun addManuallyInstalledESwordBooks() {
    val dir = File(SharedConstants.modulesDir, "esword")
    if (!(dir.isDirectory && dir.canRead())) return

    val files = dir.listFiles() ?: return
    for (f in files) {
        if (f.isFile && f.canRead()) {
            val lower = f.name.lowercase()
            if (lower.endsWith(".bblx") || lower.endsWith(".bbli")) {
                addESwordBook(f)
            }
        }
    }
}

val Book.isManuallyInstalledESwordBook
    get() = bookMetaData.getProperty("AndBibleESwordModule") != null

/**
 * Convert e-Sword RTF content to OSIS XML, preserving key formatting.
 *
 * Handles bold (\b/\b0), italic (\i/\i0), line breaks (\line, \par),
 * superscript (\super/\nosupersub), and Unicode/hex escapes.
 * Strips all other RTF control words and groups.
 */
fun convertRtfToOsis(rtf: String): String {
    if (rtf.isEmpty()) return ""
    // Quick check: if it doesn't look like RTF, return as-is
    if (!rtf.startsWith("\\") && !rtf.startsWith("{")) return rtf

    val result = StringBuilder(rtf.length)
    var i = 0
    val len = rtf.length
    var braceDepth = 0
    var skipGroup = false
    var skipGroupDepth = 0
    var bold = false
    var italic = false
    var superscript = false

    fun closeFormattingTags() {
        if (superscript) { result.append("</hi>"); superscript = false }
        if (bold) { result.append("</hi>"); bold = false }
        if (italic) { result.append("</hi>"); italic = false }
    }

    while (i < len) {
        val ch = rtf[i]

        when {
            ch == '{' -> {
                braceDepth++
                // Check if this is a group we should skip (fonttbl, colortbl, etc.)
                if (i + 1 < len && rtf[i + 1] == '\\') {
                    val groupStart = i + 1
                    val groupEnd = minOf(groupStart + 20, len)
                    val preview = rtf.substring(groupStart, groupEnd)
                    if (preview.startsWith("\\fonttbl") ||
                        preview.startsWith("\\colortbl") ||
                        preview.startsWith("\\stylesheet") ||
                        preview.startsWith("\\*\\")
                    ) {
                        skipGroup = true
                        skipGroupDepth = braceDepth
                    }
                }
                i++
            }

            ch == '}' -> {
                if (skipGroup && braceDepth == skipGroupDepth) {
                    skipGroup = false
                }
                braceDepth--
                i++
            }

            skipGroup -> {
                i++
            }

            ch == '\\' -> {
                i++
                if (i >= len) break

                when (rtf[i]) {
                    // Escaped characters
                    '\\' -> { result.append('\\'); i++ }
                    '{' -> { result.append('{'); i++ }
                    '}' -> { result.append('}'); i++ }
                    '\n' -> { i++ } // escaped newline = ignore
                    '\r' -> { i++ }

                    // Hex escape: \'XX
                    '\'' -> {
                        i++
                        if (i + 1 < len) {
                            val hex = rtf.substring(i, i + 2)
                            try {
                                result.append(hex.toInt(16).toChar())
                            } catch (_: NumberFormatException) {
                                // skip
                            }
                            i += 2
                        }
                    }

                    // Unicode escape: \uN? (N = signed decimal, ? = ASCII fallback char)
                    'u' -> {
                        if (i + 1 < len && (rtf[i + 1].isDigit() || rtf[i + 1] == '-')) {
                            i++ // skip 'u'
                            val numStart = i
                            if (rtf[i] == '-') i++
                            while (i < len && rtf[i].isDigit()) i++
                            val num = rtf.substring(numStart, i).toIntOrNull()
                            if (num != null) {
                                val codePoint = if (num < 0) num + 65536 else num
                                result.append(codePoint.toChar())
                            }
                            // Skip the ASCII fallback character
                            if (i < len && rtf[i] != '\\' && rtf[i] != '{' && rtf[i] != '}') i++
                        } else {
                            // Not a unicode escape, treat as unknown control word starting with 'u'
                            while (i < len && rtf[i].isLetter()) i++
                            // skip numeric parameter
                            if (i < len && (rtf[i] == '-' || rtf[i].isDigit())) {
                                if (rtf[i] == '-') i++
                                while (i < len && rtf[i].isDigit()) i++
                            }
                            if (i < len && rtf[i] == ' ') i++
                        }
                    }

                    else -> {
                        // Parse control word
                        val wordStart = i
                        while (i < len && rtf[i].isLetter()) i++
                        val word = rtf.substring(wordStart, i)

                        // Parse optional numeric parameter
                        val numStart = i
                        if (i < len && (rtf[i] == '-' || rtf[i].isDigit())) {
                            if (rtf[i] == '-') i++
                            while (i < len && rtf[i].isDigit()) i++
                        }
                        val param = if (i > numStart) rtf.substring(numStart, i).toIntOrNull() else null

                        // Skip space delimiter after control word
                        if (i < len && rtf[i] == ' ') i++

                        when (word) {
                            "b" -> {
                                if (param == 0) {
                                    if (bold) { result.append("</hi>"); bold = false }
                                } else {
                                    if (!bold) { result.append("<hi type=\"bold\">"); bold = true }
                                }
                            }
                            "i" -> {
                                if (param == 0) {
                                    if (italic) { result.append("</hi>"); italic = false }
                                } else {
                                    if (!italic) { result.append("<hi type=\"italic\">"); italic = true }
                                }
                            }
                            "super" -> {
                                if (!superscript) {
                                    result.append("<hi type=\"super\">")
                                    superscript = true
                                }
                            }
                            "nosupersub" -> {
                                if (superscript) {
                                    result.append("</hi>")
                                    superscript = false
                                }
                            }
                            "line" -> result.append("<lb/>")
                            "par" -> result.append("<lb/>")
                            // All other control words are silently ignored
                            // (viewkind, uc, nowidctlpar, tx, cf, lang, f, fs, sa, sl, slmult, ul, ulnone, etc.)
                        }
                    }
                }
            }

            // Regular text character
            else -> {
                // XML-escape special characters
                when (ch) {
                    '<' -> result.append("&lt;")
                    '>' -> result.append("&gt;")
                    '&' -> result.append("&amp;")
                    else -> result.append(ch)
                }
                i++
            }
        }
    }

    closeFormattingTags()
    return result.toString().trim()
}
