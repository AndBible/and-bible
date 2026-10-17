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

package net.bible.service.sword.mydocument

import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.common.next
import net.bible.service.common.prev
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.BookAndKey
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Past-the-end key lookups of a MyDocument backend must throw IndexOutOfBoundsException (like the
 * EPUB backend), so the gen-book next/prev wrap in CommonUtils lands on a real page key instead of
 * a blank one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyDocumentBackendKeysTest {
    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
    private val traverser = mock(BibleTraverser::class.java)

    @Before
    fun setUp() = runTest {
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }
    }

    @After
    fun tearDown() = runTest {
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }
    }

    private suspend fun book(vararg pageKeys: String): SwordGenBook {
        val document = MyDocument(name = "Keys", initials = "MyDoc_Keys")
        dao.insert(document)
        pageKeys.forEachIndexed { i, key ->
            dao.insertPageWithContent(
                MyDocumentPage(
                    documentId = document.id, title = "Title $key", pageKey = key,
                    contentType = MyDocumentContentType.MARKDOWN, orderNumber = i,
                ),
                "content of $key",
            )
        }
        return createMyDocumentBook(document)
    }

    private fun bookAndKey(book: SwordGenBook, pageKey: String) =
        BookAndKey(DefaultLeafKeyList(pageKey, pageKey), book)

    @Test
    fun lastPageNextReturnsARealKeyOfTheBook() = runTest {
        val book = book("page_a", "page_b")
        val next = bookAndKey(book, "page_b").next(testCoreStrings(), traverser)
        assertFalse(next.key.getOsisRef().isBlank())
        assertEquals("page_a", next.key.getOsisRef())
    }

    @Test
    fun firstPagePrevReturnsARealKeyOfTheBook() = runTest {
        val book = book("page_a", "page_b")
        val prev = bookAndKey(book, "page_a").prev(testCoreStrings(), traverser)
        assertFalse(prev.key.getOsisRef().isBlank())
        assertEquals("page_b", prev.key.getOsisRef())
    }

    @Test
    fun singlePageBookWrapsToItself() = runTest {
        val book = book("only")
        assertEquals("only", bookAndKey(book, "only").next(testCoreStrings(), traverser).key.getOsisRef())
        assertEquals("only", bookAndKey(book, "only").prev(testCoreStrings(), traverser).key.getOsisRef())
    }

    @Test
    fun firstToSecondPage() = runTest {
        val book = book("page_a", "page_b")
        assertEquals("page_b", bookAndKey(book, "page_a").next(testCoreStrings(), traverser).key.getOsisRef())
    }

    @Test
    fun getOutOfBoundsThrowsIndexOutOfBounds() = runTest {
        val backend = book("page_a", "page_b").backend as MyDocumentBackend
        for (bad in listOf(2, -1)) {
            assertThrows("get($bad)", IndexOutOfBoundsException::class.java) { backend.get(bad) }
        }
    }
}
