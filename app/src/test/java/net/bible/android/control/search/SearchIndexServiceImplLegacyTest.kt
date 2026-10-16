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
package net.bible.android.control.search

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.search.SearchIndexService
import net.bible.service.sword.mybible.addMyBibleBook
import net.bible.service.sword.mybible.bibleBookToMyBibleInt
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The legacy Lucene directory is the "engine changed" signal (K6 task 16): it survives until the book's new index
 * is DONE or the book is deleted, and the startup sweep removes only the directories of books that are not installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchIndexServiceImplLegacyTest {
    private val kjv get() = Books.installed().getBook("KJV")!!
    private val service: SearchIndexService get() = GlobalContext.get().get()
    private val tempFiles = mutableListOf<File>()

    private fun makeLegacy(dir: File) {
        dir.mkdirs()
        File(dir, "segments.gen").writeText("lucene")
    }

    @Before fun clean() { FtsTestSupport.dropIndex(kjv) }

    @After fun tearDown() {
        FtsTestSupport.dropIndex(kjv)
        tempFiles.forEach { it.deleteRecursively() }
    }

    @Test fun legacyDirectoryWithoutNewIndexIsTheEngineChangedSignal() {
        assertFalse(service.hasLegacyIndex("KJV"))
        makeLegacy(FtsTestSupport.legacyDir(kjv))
        assertTrue(service.hasLegacyIndex("KJV"))
        assertEquals(IndexStatus.UNDONE, kjv.indexStatus)
        assertFalse(service.hasIndex("KJV"))
        assertFalse(service.hasLegacyIndex("NoSuchBook"))
    }

    @Test fun legacyDirectoryIsGoneOnceTheRebuildIsDone() {
        val legacy = FtsTestSupport.legacyDir(kjv)
        makeLegacy(legacy)
        // service.createIndex only schedules the build on the app scope; run the same build the scope would
        FtsTestSupport.manager.scheduleIndexCreation(kjv)
        assertEquals(IndexStatus.DONE, kjv.indexStatus)
        assertFalse(legacy.exists())
        assertFalse(service.hasLegacyIndex("KJV"))
        assertTrue(service.hasIndex("KJV"))
    }

    @Test fun aBookWithoutADriverFallsBackToItsIndexStatus() {
        fun fake(status: IndexStatus): org.crosswire.jsword.book.Book {
            val md = org.mockito.Mockito.mock(org.crosswire.jsword.book.BookMetaData::class.java)
            org.mockito.Mockito.`when`(md.driverName).thenReturn(null)
            val b = org.mockito.Mockito.mock(org.crosswire.jsword.book.Book::class.java)
            org.mockito.Mockito.`when`(b.bookMetaData).thenReturn(md)
            org.mockito.Mockito.`when`(b.indexStatus).thenReturn(status)
            return b
        }
        assertTrue(hasUsableSearchIndex(fake(IndexStatus.DONE)))
        assertFalse(hasUsableSearchIndex(fake(IndexStatus.UNDONE)))
    }

    @Test fun orphanSweepKeepsTheLegacyDirectoryOfAnInstalledNonSwordBook() {
        val dir = Files.createTempDirectory("legacy-keep").toFile().also { tempFiles += it }
        val file = File(dir, "legacykeep.SQLite3")
        openSqlite(file.path).use { c ->
            c.exec("CREATE TABLE info (name TEXT, value TEXT)")
            c.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
        }
        addMyBibleBook(file)
        val book = Books.installed().getBook("MyBible-legacykeep")!!
        val legacy = FtsTestSupport.legacyDir(book).also { tempFiles += it }
        makeLegacy(legacy)
        try {
            // the keep list must be built after the manual books are registered (see
            // SwordEnvironmentInitialisation.deleteOrphanLegacyIndexesInBackground)
            FtsTestSupport.manager.deleteOrphanLegacyLuceneIndexes(Books.installed().getBooks())
            assertTrue(legacy.exists())
            FtsTestSupport.manager.deleteOrphanLegacyLuceneIndexes(Books.installed().getBooks().filter { it != book })
            assertFalse(legacy.exists())
        } finally {
            Books.installed().removeBook(book)
        }
    }

    @Test fun orphanSweepDeletesOnlyDirectoriesOfBooksThatAreNotInstalled() {
        val legacy = FtsTestSupport.legacyDir(kjv)
        val orphan = File(legacy.parentFile, "NoLongerInstalled")
        val otherDriver = File(legacy.parentFile.parentFile, "OldDriver/Gone")
        makeLegacy(legacy); makeLegacy(orphan); makeLegacy(otherDriver)
        tempFiles += orphan; tempFiles += otherDriver.parentFile
        val deleted = FtsTestSupport.manager.deleteOrphanLegacyLuceneIndexes(Books.installed().getBooks())
        assertEquals(2, deleted)
        assertTrue(legacy.exists())
        assertFalse(orphan.exists())
        assertFalse(otherDriver.parentFile.exists())
    }

    @Test fun deletingTheDocumentDeletesItsLegacyDirectory() {
        val dir = Files.createTempDirectory("legacy-del").toFile().also { tempFiles += it }
        val genesis = bibleBookToMyBibleInt.getValue(BibleBook.GEN)
        val file = File(dir, "legacydel.SQLite3")
        openSqlite(file.path).use { c ->
            c.exec("CREATE TABLE info (name TEXT, value TEXT)")
            c.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
            c.exec("INSERT INTO verses VALUES ($genesis, 1, 1, 'In the beginning')")
        }
        addMyBibleBook(file)
        val book = Books.installed().getBook("MyBible-legacydel")!!
        val legacy = FtsTestSupport.legacyDir(book).also { tempFiles += it }
        makeLegacy(legacy)
        assertTrue(FtsTestSupport.manager.hasLegacyLuceneIndex(book))
        try {
            SwordDocumentFacade.deleteDocument(book)
        } finally {
            Books.installed().getBook("MyBible-legacydel")?.let { Books.installed().removeBook(it) }
        }
        assertFalse(legacy.exists())
    }
}
