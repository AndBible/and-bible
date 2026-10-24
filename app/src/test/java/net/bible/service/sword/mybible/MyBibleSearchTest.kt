/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import okio.Path.Companion.toOkioPath
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * K6 review focus 3: books that are not SWORD modules but are `SwordBook`s over an SQLite backend (MyBible Bible and
 * verse-keyed commentary) are indexed and found by the FTS5 index like any other verse-keyed book.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyBibleSearchTest {
    private lateinit var dir: File
    private val books = mutableListOf<Book>()
    private val genesis = bibleBookToMyBibleInt.getValue(BibleBook.GEN)

    @Before fun setUp() { dir = Files.createTempDirectory("mybible-search").toFile() }

    @After fun tearDown() {
        books.forEach {
            IndexManagerFactory.getIndexManager().deleteIndex(it)
            Books.installed().removeBook(it)
        }
        dir.deleteRecursively()
    }

    private fun install(name: String, vararg sql: String): Book {
        val file = File(dir, name)
        openSqlite(file.path).use { c -> sql.forEach { c.exec(it) } }
        addMyBibleBook(file.toOkioPath())
        val initials = "MyBible-" + sanitizeModuleName(name.removeSuffix(".SQLite3"))
        return Books.installed().getBook(initials)!!.also { books += it }
    }

    private fun index(book: Book) {
        IndexManagerFactory.getIndexManager().scheduleIndexCreation(book)
        assertEquals(IndexStatus.DONE, book.indexStatus)
    }

    private fun verses(book: Book, query: String) = book.find(query)!!.map { it.getName() }

    @Test fun mybibleBibleIsIndexedAndSearchable() {
        val bible = install(
            "searchbible.SQLite3",
            "CREATE TABLE info (name TEXT, value TEXT)",
            "INSERT INTO info VALUES ('language', 'en')",
            "CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)",
            "INSERT INTO verses VALUES ($genesis, 1, 1, 'In the beginning God created the heaven and the earth')",
            "INSERT INTO verses VALUES ($genesis, 1, 2, 'And the earth was without form')",
        )
        index(bible)
        assertEquals(listOf("Genesis 1:1"), verses(bible, "beginning"))
        assertEquals(listOf("Genesis 1:1", "Genesis 1:2"), verses(bible, "earth"))
        assertEquals(listOf("Genesis 1:2"), verses(bible, "earth AND without"))
        assertEquals(listOf("Genesis 1:1"), verses(bible, "\"the beginning God\""))
        assertEquals(listOf("Genesis 1:2"), verses(bible, "form*"))
        assertTrue(verses(bible, "absent").isEmpty())
    }

    @Test fun verseKeyedMybibleCommentaryIsIndexedAndSearchable() {
        val commentary = install(
            "searchnotes.commentaries.SQLite3",
            "CREATE TABLE info (name TEXT, value TEXT)",
            "CREATE TABLE commentaries (book_number INTEGER, chapter_number_from INTEGER, verse_number_from INTEGER, chapter_number_to INTEGER, verse_number_to INTEGER, text TEXT)",
            "INSERT INTO commentaries VALUES ($genesis, 1, 1, 1, 1, 'Creation ex nihilo')",
            "INSERT INTO commentaries VALUES ($genesis, 1, 2, 1, 2, 'The formless deep')",
        )
        index(commentary)
        assertEquals(listOf("Genesis 1:1"), verses(commentary, "nihilo"))
        assertEquals(listOf("Genesis 1:2"), verses(commentary, "deep"))
    }
}
