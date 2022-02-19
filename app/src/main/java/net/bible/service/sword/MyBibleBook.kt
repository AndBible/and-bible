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
import android.util.Log
import net.bible.android.BibleApplication
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook.*
import org.crosswire.jsword.versification.system.Versifications

import java.io.File
import java.io.IOException

val intToBibleBook = mapOf(
    10 to GEN,
    20 to EXOD,
    30 to LEV,
    40 to NUM,
    50 to DEUT,
    60 to JOSH,
    70 to JUDG,
    80 to RUTH,
    90 to SAM1,
    100 to SAM2,
    110 to KGS1,
    120 to KGS2,
    //180 to
    130 to CHR1,
    140 to CHR2,
    //145 to
    150 to EZRA,
    160 to NEH,
    //165 to
    170 to TOB,
    190 to ESTH,
    192 to ADD_ESTH,
    220 to JOB,
    230 to PS,
    240 to PROV,
    250 to ECCL,
    260 to SONG,
    270 to WIS,
    280 to SIR,
    290 to ISA,
    300 to JER,
    305 to PR_AZAR,
    310 to LAM,
    315 to EP_JER,
    320 to BAR,
    //323 to
    325 to SUS,
    330 to EZEK,
    340 to DAN,
    345 to ADD_DAN,
    350 to HOS,
    360 to JOEL,
    370 to AMOS,
    380 to OBAD,
    390 to JONAH,
    400 to MIC,
    410 to NAH,
    420 to HAB,
    430 to ZEPH,
    440 to HAG,
    450 to ZECH,
    460 to MAL,
    462 to MACC1,
    464 to MACC2,
    466 to MACC3,
    467 to MACC4,
    468 to ESD2,
    470 to MATT,
    480 to MARK,
    490 to LUKE,
    500 to JOHN,
    510 to ACTS,
    660 to JAS,
    670 to PET1,
    680 to PET2,
    690 to JOHN1,
    700 to JOHN2,
    710 to JOHN3,
    720 to JUDE,
    520 to ROM,
    530 to COR1,
    540 to COR2,
    550 to GAL,
    560 to EPH,
    570 to PHIL,
    580 to COL,
    590 to THESS1,
    600 to THESS2,
    610 to TIM1,
    620 to TIM2,
    630 to TITUS,
    640 to PHLM,
    650 to HEB,
    730 to REV,
    780 to EP_LAO,
)

val bibleBookToInt = intToBibleBook.toList().associate { (k, v) -> v to k }

private fun getConfig(abbreviation: String, description: String, language: String, category: String): String = """
[mybible-$abbreviation]
Description=$description
Abbreviation=$abbreviation
Category=$category
AndBibleMyBibleModule=1
Lang=$language
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=KJV"""

const val TAG = "MyBibleBook"

class SqliteVerseBackendState(sqliteFile: File): OpenFileState {
    val sqlDb: SQLiteDatabase = SQLiteDatabase.openDatabase(sqliteFile.path, null, SQLiteDatabase.OPEN_READONLY)

    override fun close() = sqlDb.close()

    var hasStories: Boolean = false

    override fun getBookMetaData(): SwordBookMetaData {
        val initials = File(sqlDb.path).nameWithoutExtension
        val description = sqlDb.rawQuery("select value from info where name = ?", arrayOf("description")).use {
            it.moveToFirst()
            it.getString(0)
        }
        val language = sqlDb.rawQuery("select value from info where name = ?", arrayOf("language")).use {
            it.moveToFirst()
            it.getString(0)
        }

        val tables = sqlDb.rawQuery("select name from sqlite_master where type = 'table' AND name not like 'sqlite_%'", null).use {
            val names = arrayListOf<String>()
            while(it.moveToNext()) {
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
        Log.i(TAG, "Adding MyBibleBook $initials, $description $language $category")
        return SwordBookMetaData(conf.toByteArray(), "mybible-$initials")
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
        val v11n = Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
        val book = intToBibleBook[bookNum]
        val chapter = cur.getInt(1)
        val verse = cur.getInt(2)
        cur.close()
        return Verse(v11n, book, chapter, verse)
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
        Log.i(TAG, "Trying to read $key")
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

fun addMyBibleBooks() {
    val dir = File(BibleApplication.application.getExternalFilesDir(null), "mybible")
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()?: emptyArray()) {
        val state = SqliteVerseBackendState(f)
        val metadata = state.bookMetaData
        val backend = SqliteBackend(state, metadata)
        val book =SwordBook(metadata, backend)
        Books.installed().addBook(book)
    }
}
