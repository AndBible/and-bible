/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory

private const val CATEGORY_PROPERTY = "Category"
private const val ANDBIBLE_CATEGORY_NORMALIZED = "andbible"

private fun String.normalizedCategoryValue(): String {
    return filterNot(Char::isWhitespace).lowercase()
}

val Book.isAndBibleCategory: Boolean
    get() {
        if (bookCategory == BookCategory.AND_BIBLE) {
            return true
        }

        val rawCategory = bookMetaData.getProperty(CATEGORY_PROPERTY) ?: return false
        return rawCategory.normalizedCategoryValue() == ANDBIBLE_CATEGORY_NORMALIZED
    }

