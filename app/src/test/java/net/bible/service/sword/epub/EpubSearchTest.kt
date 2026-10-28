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

import okio.Path.Companion.toOkioPath
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

    private fun search() = EpubSearch(File(dir, "search.sqlite3").toOkioPath())

    @Test
    fun indexLifecycleIsReportedByIsIndexed() {
        val s = search()
        assertFalse(s.isIndexed)
        s.createTable()
        assertFalse("created but not filled and committed", s.isIndexed)
        s.commitIndex()
        assertTrue(s.isIndexed)
        s.deleteIndex()
        assertFalse(s.isIndexed)
        s.deleteIndex() // DROP TABLE IF EXISTS: idempotent
    }

    /** #3876: a build killed between creating the table and filling it must not look like a finished index. */
    @Test
    fun anUncommittedBuildIsNotIndexedEvenAfterReopening() {
        search().apply { createTable(); addContent("half built", 1L, 1) } // process "killed" here
        val reopened = search()
        assertFalse(reopened.isIndexed)
    }

    @Test
    fun aLeftoverBuildIsDiscardedByTheNextBuild() {
        search().apply { createTable(); addContent("leftover text", 1L, 1) }
        val s = search()
        s.createTable()
        s.addContent("fresh text", 2L, 2)
        s.commitIndex()
        assertTrue(s.isIndexed)
        assertEquals(emptyList<EpubSearchResult>(), s.search("leftover"))
        assertEquals(listOf(2L), s.search("fresh").map { it.fragId })
    }

    @Test
    fun aRebuildReplacesTheCommittedIndexOnlyWhenItIsCommitted() {
        val s = search()
        s.createTable(); s.addContent("old text", 1L, 1); s.commitIndex()
        s.createTable(); s.addContent("new text", 2L, 2)
        assertTrue("the old index serves until the new one is committed", s.isIndexed)
        assertEquals(1, s.search("old").size)
        s.commitIndex()
        assertEquals(emptyList<EpubSearchResult>(), s.search("old"))
        assertEquals(1, search().search("new").size)
    }

    @Test
    fun deleteIndexAlsoDropsAnUncommittedBuild() {
        val s = search()
        s.createTable(); s.addContent("pending", 1L, 1)
        s.deleteIndex()
        assertThrows(Exception::class.java) { s.addContent("more", 1L, 1) }
    }

    @Test
    fun searchFindsContentWithFragmentOrdinalAndHighlightedSnippet() {
        val s = search()
        s.createTable()
        s.addContent("In the beginning was the Word", 7L, 3)
        s.addContent("Grace and peace to you", 8L, 11)
        s.commitIndex()
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
        s.commitIndex()
        assertEquals(5_000_000_000L, s.search("one").single().fragId)
    }

    @Test
    fun searchAfterReopeningReadsThePersistedIndex() {
        search().apply { createTable(); addContent("persisted text", 1L, 2); commitIndex() }
        val reopened = search()
        assertTrue(reopened.isIndexed)
        assertEquals(1, reopened.search("persisted").size)
    }

    @Test
    fun invalidMatchSyntaxSurfacesAsAnException() {
        val s = search()
        s.createTable()
        s.commitIndex()
        assertThrows(androidx.sqlite.SQLiteException::class.java) { s.search("\"unbalanced") }
    }

    @Test
    fun anUnopenableFileCannotSilentlyAcceptIndexMutations() {
        val s = EpubSearch(File(File(dir, "missing-folder"), "search.sqlite3").toOkioPath())
        assertThrows(IllegalStateException::class.java) { s.createTable() }
        assertThrows(IllegalStateException::class.java) { s.addContent("x", 1L, 1) }
        assertThrows(IllegalStateException::class.java) { s.commitIndex() }
    }

    @Test
    fun anUnopenableFileDegradesToNotIndexedAndNoResults() {
        val s = EpubSearch(File(File(dir, "missing-folder"), "search.sqlite3").toOkioPath())
        assertFalse(s.isIndexed)
        assertEquals(emptyList<EpubSearchResult>(), s.search("x"))
        assertThrows(IllegalStateException::class.java) { s.addContent("x", 1L, 1) }
    }
}
