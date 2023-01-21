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
import org.crosswire.jsword.book.Books
import org.hamcrest.CoreMatchers
import org.crosswire.jsword.versification.BibleBook
import org.junit.Assert
import org.junit.Test

class DocumentBibleBooksTest {
    //@Ignore("Until ESV comes back")
    @Test
    fun testContains() {
        val esv = Books.installed().getBook("ESV2011") as AbstractPassageBook
        val esvBibleBooks = DocumentBibleBooks(esv)
        Assert.assertThat(true, CoreMatchers.`is`(esvBibleBooks.contains(BibleBook.GEN)))
        Assert.assertThat(true, CoreMatchers.`is`(esvBibleBooks.contains(BibleBook.OBAD)))
        Assert.assertThat(false, CoreMatchers.`is`(esvBibleBooks.contains(BibleBook.PR_AZAR)))
    }
}
