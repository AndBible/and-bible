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

package net.bible.service.sword.mybible

import androidx.sqlite.SQLiteException
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The MyBible reader against minimal real `.SQLite3` files, opened read-only by the bundled SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyBibleBookReaderTest {
    private lateinit var dir: File
    private lateinit var state: SqliteVerseBackendState
    private val states = mutableListOf<SqliteVerseBackendState>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mybible-test").toFile()
    }

    @After
    fun tearDown() {
        states.forEach { it.close() }
        dir.deleteRecursively()
    }

    private val kjv = Versifications.instance().getVersification("KJV")
    private fun verse(book: BibleBook, chapter: Int, verse: Int) = Verse(kjv, book, chapter, verse)

    private fun module(name: String, vararg sql: String): File {
        val file = File(dir, name)
        openSqlite(file.path).use { c -> sql.forEach { c.exec(it) } }
        return file
    }

    private fun backend(file: File): SqliteBackend {
        state = SqliteVerseBackendState(file).also { states.add(it) }
        return SqliteBackend(state, state.bookMetaData)
    }

    private val genesis = bibleBookToMyBibleInt.getValue(BibleBook.GEN)

    private fun bible(vararg extra: String) = module(
        "kjv.SQLite3",
        "CREATE TABLE info (name TEXT, value TEXT)",
        "INSERT INTO info VALUES ('description', 'King James')",
        "INSERT INTO info VALUES ('language', 'fi')",
        "INSERT INTO info VALUES ('strong_numbers', 'true')",
        "CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)",
        "INSERT INTO verses VALUES ($genesis, 1, 1, 'In the beginning')",
        "INSERT INTO verses VALUES ($genesis, 1, 2, 'And the earth')",
        "CREATE TABLE stories (book_number INTEGER, chapter INTEGER, verse INTEGER, title TEXT)",
        "INSERT INTO stories VALUES ($genesis, 1, 1, 'Creation')",
        "INSERT INTO stories VALUES ($genesis, 1, 1, '<x/>')",
        *extra,
    )

    @Test
    fun metadataComesFromInfoAndTables() {
        val md = backend(bible()).bookMetaData
        assertEquals("MyBible-kjv", md.initials)
        assertEquals(BookCategory.BIBLE, md.bookCategory)
        assertEquals("King James", md.getProperty("Description"))
        assertEquals("fi", md.getProperty("Lang"))
        assertEquals("kjv", md.getProperty("Abbreviation"))
        assertEquals("OSISStrongs", md.getProperty("GlobalOptionFilter"))
        assertNull(md.getProperty("Feature"))
    }

    @Test
    fun missingInfoRowsFallBackToDefaults() {
        val md = backend(module("bare.SQLite3", "CREATE TABLE info (name TEXT, value TEXT)", "CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")).bookMetaData
        assertEquals("", md.getProperty("Description"))
        assertEquals("en", md.getProperty("Lang"))
        assertNull(md.getProperty("GlobalOptionFilter"))
    }

    @Test
    fun wordsOfChristIsDetectedFromInfoFlagOrJTag() {
        assertEquals("WordsOfChrist", backend(bible("INSERT INTO info VALUES ('is_red_letter', '1')")).bookMetaData.getProperty("Feature"))
        val tagged = module(
            "tag.SQLite3",
            "CREATE TABLE info (name TEXT, value TEXT)",
            "CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)",
            "INSERT INTO verses VALUES ($genesis, 1, 1, 'He said <J>Follow</J>')",
        )
        assertEquals("WordsOfChrist", backend(tagged).bookMetaData.getProperty("Feature"))
    }

    @Test
    fun verseTextGetsStoryTitlesAddedAroundIt() {
        val b = backend(bible())
        assertEquals(
            "<title canonical=\"false\">Creation</title>In the beginning<x/>",
            b.readRawContent(state, verse(BibleBook.GEN, 1, 1)),
        )
        assertEquals("And the earth", b.readRawContent(state, verse(BibleBook.GEN, 1, 2)))
        assertThrows(IOException::class.java) { b.readRawContent(state, verse(BibleBook.GEN, 9, 9)) }
    }

    @Test
    fun cardinalityAndIndexOf() {
        val b = backend(bible())
        assertEquals(2, b.cardinality)
        assertEquals(2, b.indexOf(verse(BibleBook.GEN, 1, 2)))
        assertEquals(-1, b.indexOf(verse(BibleBook.GEN, 9, 9)))
    }

    @Test
    fun commentaryRowsAreJoinedInOrder() {
        val file = module(
            "notes.commentaries.SQLite3",
            "CREATE TABLE info (name TEXT, value TEXT)",
            "CREATE TABLE commentaries (book_number INTEGER, chapter_number_from INTEGER, verse_number_from INTEGER, chapter_number_to INTEGER, verse_number_to INTEGER, text TEXT)",
            "INSERT INTO commentaries VALUES ($genesis, 1, 2, 1, 2, 'second')",
            "INSERT INTO commentaries VALUES ($genesis, 1, 1, 1, 3, 'first')",
            "INSERT INTO commentaries VALUES ($genesis, 2, 1, 2, 1, 'elsewhere')",
        )
        val b = backend(file)
        assertEquals(BookCategory.COMMENTARY, b.bookMetaData.bookCategory)
        assertEquals("<div>first</div>, <div>second</div>", b.readRawContent(state, verse(BibleBook.GEN, 1, 2)))
        assertEquals(1, b.indexOf(verse(BibleBook.GEN, 1, 2)))
        assertEquals(-1, b.indexOf(verse(BibleBook.GEN, 3, 3)))
    }

    @Test
    fun dictionaryIteratesGetsIndexesAndReads() {
        val file = module(
            "words.dictionary.SQLite3",
            "CREATE TABLE info (name TEXT, value TEXT)",
            "CREATE TABLE dictionary (topic TEXT, definition TEXT)",
            "INSERT INTO dictionary VALUES ('alpha', 'first')",
            "INSERT INTO dictionary VALUES ('beta', 'second')",
        )
        val b = backend(file)
        assertEquals(BookCategory.DICTIONARY, b.bookMetaData.bookCategory)
        assertEquals(listOf("alpha", "beta"), b.iterator().asSequence().map { it.name }.toList())
        assertEquals("beta", b.get(2).name)
        assertEquals(2, b.indexOf(DefaultLeafKeyList("beta")))
        assertEquals(-1, b.indexOf(DefaultLeafKeyList("gamma")))
        assertEquals("second", b.readRawContent(state, DefaultLeafKeyList("beta")))
        assertThrows(IndexOutOfBoundsException::class.java) { b.get(99) }
    }

    @Test
    fun theModuleIsOpenedReadOnly() {
        val b = backend(bible())
        assertEquals(2, b.cardinality)
        assertThrows(SQLiteException::class.java) {
            state.sqlDb.exec("INSERT INTO verses VALUES (1, 1, 3, 'x')")
        }
        assertEquals(2, b.cardinality)
    }
}
