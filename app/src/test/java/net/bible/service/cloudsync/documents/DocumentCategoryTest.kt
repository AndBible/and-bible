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

package net.bible.service.cloudsync.documents

import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentCategoryTest {
    @Test fun parsesValidEnumName() {
        assertEquals(BookCategory.BIBLE, parseCategoryName("BIBLE"))
        assertEquals(BookCategory.GENERAL_BOOK, parseCategoryName("GENERAL_BOOK"))
    }

    @Test fun returnsNullForBlankOrNull() {
        assertNull(parseCategoryName(null))
        assertNull(parseCategoryName(""))
        assertNull(parseCategoryName("   "))
    }

    @Test fun returnsNullForUnknownName() {
        assertNull(parseCategoryName("NOT_A_CATEGORY"))
        assertNull(parseCategoryName("Biblical Texts")) // display name, not enum name
    }
}
