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

package net.bible.service.sword.mysword

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import androidx.sqlite.SQLiteException
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The MySword reader against minimal real `.mybible` files, opened read-only by the bundled SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MySwordBookTest {
    private lateinit var dir: File
    private val states = mutableListOf<SqliteVerseBackendState>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mysword-test").toFile()
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
        val state = SqliteVerseBackendState(file).also { states.add(it) }
        return SqliteBackend(state, state.bookMetaData)
    }

    private fun bible() = module(
        "kjvx.bbl.mybible",
        "CREATE TABLE Details (Title TEXT, Description TEXT, Abbreviation TEXT, Version TEXT, Strong INTEGER, Language TEXT)",
        "INSERT INTO Details VALUES ('The KJV', 'King James', 'KJVX', '1.0', 1, 'eng')",
        "CREATE TABLE Bible (Book INTEGER, Chapter INTEGER, Verse INTEGER, Scripture TEXT)",
        "INSERT INTO Bible VALUES (1, 1, 1, 'In the beginning<WH7225> God<CM>')",
        "INSERT INTO Bible VALUES (1, 1, 2, 'And the earth was without form')",
        "INSERT INTO Bible VALUES (2, 3, 4, 'Exodus text')",
    )

    @Test
    fun metadataComesFromDetailsTableAndFileName() {
        val md = backend(bible()).bookMetaData
        assertEquals("MySword-kjvx_bbl", md.initials)
        assertEquals(BookCategory.BIBLE, md.bookCategory)
        assertEquals("King James", md.getProperty("Description"))
        assertEquals("KJVX", md.getProperty("Abbreviation"))
        assertEquals("eng", md.getProperty("Lang"))
    }

    @Test
    fun missingDetailsColumnsFallBackToDefaults() {
        val file = module(
            "bare.bbl.mybible",
            "CREATE TABLE Details (Title TEXT)",
            "INSERT INTO Details VALUES (NULL)",
            "CREATE TABLE Bible (Book INTEGER, Chapter INTEGER, Verse INTEGER, Scripture TEXT)",
        )
        val md = backend(file).bookMetaData
        assertEquals("MySword-bare_bbl", md.initials)
        assertEquals("MySword-bare_bbl", md.getProperty("Abbreviation"))
        assertEquals("eng", md.getProperty("Lang"))
    }

    @Test
    fun readsVerseTextWithMySwordTagsTransformed() {
        val b = backend(bible())
        assertEquals("In the <w lemma=\"strong:H7225\">beginning</w> God<CM/>", b.readRawContent(b.state, verse(BibleBook.GEN, 1, 1)))
        assertEquals("Exodus text", b.readRawContent(b.state, verse(BibleBook.EXOD, 3, 4)))
    }

    @Test
    fun missingVerseIsAnIOException() {
        val b = backend(bible())
        assertThrows(IOException::class.java) { b.readRawContent(b.state, verse(BibleBook.GEN, 9, 9)) }
    }

    @Test
    fun cardinalityAndIndexOf() {
        val b = backend(bible())
        assertEquals(3, b.cardinality)
        assertEquals(2, b.indexOf(verse(BibleBook.GEN, 1, 2)))
        assertEquals(-1, b.indexOf(verse(BibleBook.GEN, 9, 9)))
    }

    @Test
    fun commentaryJoinsMatchingRowsInDivs() {
        val file = module(
            "notes.cmt.mybible",
            "CREATE TABLE Details (Title TEXT)",
            "INSERT INTO Details VALUES ('Notes')",
            "CREATE TABLE Commentary (Book INTEGER, Chapter INTEGER, FromVerse INTEGER, ToVerse INTEGER, Data TEXT)",
            "INSERT INTO Commentary VALUES (1, 1, 1, 3, 'range note')",
            "INSERT INTO Commentary VALUES (1, 1, 2, NULL, 'single note')",
            "INSERT INTO Commentary VALUES (1, 2, 1, 1, 'other chapter')",
        )
        val b = backend(file)
        assertEquals(BookCategory.COMMENTARY, b.bookMetaData.bookCategory)
        assertEquals("<div>range note</div>, <div>single note</div>", b.readRawContent(b.state, verse(BibleBook.GEN, 1, 2)))
        assertEquals(1, b.indexOf(verse(BibleBook.GEN, 1, 1)))
        assertEquals(-1, b.indexOf(verse(BibleBook.GEN, 3, 3)))
    }

    private fun dictionary() = module(
        "words.dct.mybible",
        "CREATE TABLE Details (Title TEXT, Strong INTEGER)",
        "INSERT INTO Details VALUES ('Words', 1)",
        "CREATE TABLE Dictionary (Word TEXT, Data TEXT)",
        "INSERT INTO Dictionary VALUES ('alpha', 'first <Fi>letter<Fi>')",
        "INSERT INTO Dictionary VALUES ('beta', 'second')",
    )

    @Test
    fun dictionaryIteratesGetsIndexesAndReads() {
        val b = backend(dictionary())
        assertEquals(BookCategory.DICTIONARY, b.bookMetaData.bookCategory)
        assertEquals(listOf("alpha", "beta"), b.iterator().asSequence().map { it.name }.toList())
        assertEquals("beta", b.get(2).name)
        assertEquals(2, b.indexOf(DefaultLeafKeyList("beta")))
        assertEquals(-1, b.indexOf(DefaultLeafKeyList("gamma")))
        assertEquals("second", b.readRawContent(b.state, DefaultLeafKeyList("beta")))
        assertThrows(IndexOutOfBoundsException::class.java) { b.get(99) }
    }

    @Test
    fun theModuleIsOpenedReadOnly() {
        val b = backend(bible())
        assertEquals(3, b.cardinality)
        assertThrows(SQLiteException::class.java) {
            b.state.sqlDb.exec("INSERT INTO Bible VALUES (1, 1, 3, 'x')")
        }
        assertEquals(3, b.cardinality)
    }

    private fun walModule() = module(
        "wal.bbl.mybible",
        "PRAGMA journal_mode=WAL",
        "CREATE TABLE Details (Title TEXT)",
        "INSERT INTO Details VALUES ('Wal')",
        "CREATE TABLE Bible (Book INTEGER, Chapter INTEGER, Verse INTEGER, Scripture TEXT)",
        "INSERT INTO Bible VALUES (1, 1, 1, 'wal text')",
    )

    @Test
    fun aWalModeFileThatIsReadOnlyInAWritableFolderReads() {
        val file = walModule()
        file.setReadOnly()
        val b = backend(file)
        assertEquals("wal text", b.readRawContent(b.state, verse(BibleBook.GEN, 1, 1)))
    }

    /**
     * Documents (does not endorse) a SQLite limit: a WAL-mode file with no -shm/-wal beside it cannot be
     * opened read-only when the folder is not writable (SQLITE_READONLY_CANTINIT, 1544).
     */
    @Test
    fun aWalModeFileInAReadOnlyFolderCannotBeRead() {
        val file = walModule()
        dir.setReadOnly()
        try {
            val e = assertThrows(SQLiteException::class.java) { backend(file) }
            assertTrue(e.message, e.message!!.contains("1544") || e.message!!.contains("readonly"))
        } finally {
            dir.setWritable(true)
        }
    }
}
