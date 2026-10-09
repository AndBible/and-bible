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

import androidx.sqlite.SQLiteException
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The e-Sword reader against minimal real `.bblx` files, opened read-only by the bundled SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ESwordBookReaderTest {
    private lateinit var dir: File
    private val states = mutableListOf<SqliteVerseBackendState>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("esword-test").toFile()
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

    private lateinit var state: SqliteVerseBackendState

    private fun backend(file: File): SqliteBackend {
        state = SqliteVerseBackendState(file).also { states.add(it) }
        return SqliteBackend(state, state.getBookMetaData())
    }

    private fun bible(name: String = "esv.bblx") = module(
        name,
        "CREATE TABLE Details (Description TEXT, Abbreviation TEXT, Strong INTEGER)",
        "INSERT INTO Details VALUES ('English Standard', 'ESV', 1)",
        "CREATE TABLE Bible (Book INTEGER, Chapter INTEGER, Verse INTEGER, Scripture TEXT)",
        "INSERT INTO Bible VALUES (1, 1, 1, '\\cf1 In the beginning \\b God\\b0 ')",
        "INSERT INTO Bible VALUES (1, 1, 2, NULL)",
        "INSERT INTO Bible VALUES (2, 3, 4, 'Exodus text')",
    )

    @Test
    fun metadataComesFromDetailsTable() {
        val md = backend(bible()).bookMetaData
        assertEquals("ESword-esv", md.initials)
        assertEquals(BookCategory.BIBLE, md.bookCategory)
        assertEquals("English Standard", md.getProperty("Description"))
        assertEquals("ESV", md.getProperty("Abbreviation"))
        assertEquals("OSISStrongs", md.getProperty("GlobalOptionFilter"))
    }

    @Test
    fun descriptionFallsBackToTitleColumnAndDefaultsApply() {
        val file = module(
            "titled.bblx",
            "CREATE TABLE Details (Title TEXT)",
            "INSERT INTO Details VALUES ('Only a title')",
            "CREATE TABLE Bible (Book INTEGER, Chapter INTEGER, Verse INTEGER, Scripture TEXT)",
        )
        val md = backend(file).bookMetaData
        assertEquals("Only a title", md.getProperty("Description"))
        assertEquals("ESword-titled", md.getProperty("Abbreviation"))
        assertEquals(null, md.getProperty("GlobalOptionFilter"))
    }

    @Test
    fun readsBblxVersesConvertedFromRtf() {
        val b = backend(bible())
        assertEquals("In the beginning <hi type=\"bold\">God</hi>", b.readRawContent(state, verse(BibleBook.GEN, 1, 1)))
    }

    @Test
    fun nonBblxTextIsReturnedRawAndNullScriptureIsEmpty() {
        val b = backend(bible("plain.bbl"))
        assertEquals("Exodus text", b.readRawContent(state, verse(BibleBook.EXOD, 3, 4)))
        assertEquals("", b.readRawContent(state, verse(BibleBook.GEN, 1, 2)))
    }

    @Test
    fun missingVerseIsAnIOException() {
        val b = backend(bible())
        assertThrows(IOException::class.java) { b.readRawContent(state, verse(BibleBook.GEN, 9, 9)) }
    }

    @Test
    fun cardinalityAndIndexOf() {
        val b = backend(bible())
        assertEquals(3, b.getCardinality())
        assertEquals(3, b.indexOf(verse(BibleBook.EXOD, 3, 4)))
        assertEquals(-1, b.indexOf(verse(BibleBook.GEN, 9, 9)))
    }

    @Test
    fun theModuleIsOpenedReadOnly() {
        val b = backend(bible())
        assertEquals(3, b.getCardinality())
        assertThrows(SQLiteException::class.java) {
            state.sqlDb.exec("INSERT INTO Bible VALUES (1, 1, 3, 'x')")
        }
        assertEquals(3, b.getCardinality())
    }
}
