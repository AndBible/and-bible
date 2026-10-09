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

package net.bible.service.sword.epub

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** The EPUB FTS5 search index on the real bundled SQLite (FTS5 included). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubSearchTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("epub-search-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun search() = EpubSearch(File(dir, "search.sqlite3"))

    @Test
    fun indexLifecycleIsReportedByIsIndexed() {
        val s = search()
        assertFalse(s.isIndexed)
        s.createTable()
        assertTrue(s.isIndexed)
        s.deleteIndex()
        assertFalse(s.isIndexed)
        s.deleteIndex() // DROP TABLE IF EXISTS: idempotent
    }

    @Test
    fun searchFindsContentWithFragmentOrdinalAndHighlightedSnippet() {
        val s = search()
        s.createTable()
        s.addContent("In the beginning was the Word", 7L, 3)
        s.addContent("Grace and peace to you", 8L, 11)
        val hits = s.search("peace")
        assertEquals(listOf(EpubSearchResult(8L, 11, "Grace and <b>peace</b> to you")), hits)
        assertEquals(emptyList<EpubSearchResult>(), s.search("absent"))
        assertEquals(1, s.search("word").size)
    }

    @Test
    fun addContentReturnsARowIdAndKeepsFragIdAsANumber() {
        val s = search()
        s.createTable()
        assertEquals(1L, s.addContent("one", 5_000_000_000L, 1))
        assertEquals(5_000_000_000L, s.search("one").single().fragId)
    }

    @Test
    fun searchAfterReopeningReadsThePersistedIndex() {
        search().apply { createTable(); addContent("persisted text", 1L, 2) }
        val reopened = search()
        assertTrue(reopened.isIndexed)
        assertEquals(1, reopened.search("persisted").size)
    }

    @Test
    fun invalidMatchSyntaxSurfacesAsAnException() {
        val s = search()
        s.createTable()
        assertThrows(androidx.sqlite.SQLiteException::class.java) { s.search("\"unbalanced") }
    }

    @Test
    fun anUnopenableFileDegradesToNotIndexedAndNoResults() {
        val s = EpubSearch(File(File(dir, "missing-folder"), "search.sqlite3"))
        assertFalse(s.isIndexed)
        assertEquals(emptyList<EpubSearchResult>(), s.search("x"))
        assertEquals(null, s.addContent("x", 1L, 1))
    }
}
