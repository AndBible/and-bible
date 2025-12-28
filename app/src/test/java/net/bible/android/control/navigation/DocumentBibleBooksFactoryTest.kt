/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.navigation

import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.junit.Before
import org.crosswire.jsword.book.Books
import kotlin.Throws
import org.hamcrest.CoreMatchers
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.lang.Exception

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//@Ignore("Until ESV comes back")
class DocumentBibleBooksFactoryTest {
    private var documentBibleBooksFactory: DocumentBibleBooksFactory? = null
    private var esv: AbstractPassageBook? = null
    @Before
    fun setup() {
        documentBibleBooksFactory = DocumentBibleBooksFactory()
        esv = Books.installed().getBook("ESV2011") as AbstractPassageBook
    }

    @After
    fun tearDown() {
        // ensure it is in the list after removal by some tests
        Books.installed().addBook(esv)
    }

    @Test
    @Throws(Exception::class)
    fun initialise_shouldInstallBookChangeListenersToResetCache() {
        documentBibleBooksFactory!!.initialise()
        Assert.assertThat(documentBibleBooksFactory!!.size, CoreMatchers.equalTo(0))
        documentBibleBooksFactory!!.getBooksFor(esv!!)
        Assert.assertThat(documentBibleBooksFactory!!.size, CoreMatchers.equalTo(1))
        Books.installed().removeBook(esv)
        Assert.assertThat(documentBibleBooksFactory!!.size, CoreMatchers.equalTo(0))
    }

    @Throws(Exception::class)
    @Test
    fun getDocumentBibleBooksFor()
    {
        val esvBibleBooks = documentBibleBooksFactory!!.getBooksFor(
            esv!!
        )
        Assert.assertThat(documentBibleBooksFactory!!.size, CoreMatchers.equalTo(1))
        Assert.assertThat(true, CoreMatchers.`is`(esvBibleBooks.contains(BibleBook.GEN)))
    }

    @Throws(Exception::class)
    @Test
    fun getBooksFor()
    {
        val esvBibleBooks = documentBibleBooksFactory!!.getBooksFor(
            esv!!
        )
        Assert.assertThat(true, CoreMatchers.`is`(esvBibleBooks.contains(BibleBook.GEN)))
    }
}
