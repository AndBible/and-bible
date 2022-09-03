/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword

import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookFilter

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class AcceptableBookTypeFilter : BookFilter {
    /*
     * (non-Javadoc)
     *
     * @see
     * org.crosswire.jsword.book.BookFilter#test(org.crosswire.jsword.book
     * .Book)
     */
    override fun test(book: Book): Boolean {
        val bookCategory = book.bookCategory
        return bookCategory == BookCategory.BIBLE || bookCategory == BookCategory.COMMENTARY || bookCategory == BookCategory.DICTIONARY || bookCategory == BookCategory.GENERAL_BOOK || bookCategory == BookCategory.MAPS || bookCategory == BookCategory.AND_BIBLE
    }
}

class AndBibleAddonFilter : BookFilter {
    /*
     * (non-Javadoc)
     *
     * @see
     * org.crosswire.jsword.book.BookFilter#test(org.crosswire.jsword.book
     * .Book)
     */
    override fun test(book: Book): Boolean {
        return book.bookCategory == BookCategory.AND_BIBLE &&
            (book.bookMetaData.getProperty("AndBibleMinimumVersion") ?: "0").toLong() <= CommonUtils.applicationVersionNumber
    }
}
