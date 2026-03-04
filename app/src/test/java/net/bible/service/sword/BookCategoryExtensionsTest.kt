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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.download.FakeBookFactory
import org.crosswire.jsword.book.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookCategoryExtensionsTest {

    @Test
    fun `isAndBibleCategory should accept AndBible without space`() {
        val book = createBook("ABNoSpace", "AndBible")
        assertTrue(book.isAndBibleCategory)
    }

    @Test
    fun `isAndBibleCategory should accept And Bible with space`() {
        val book = createBook("ABWithSpace", "And Bible")
        assertTrue(book.isAndBibleCategory)
    }

    @Test
    fun `isAndBibleCategory should reject non addon categories`() {
        val book = createBook("GeneralBook", "Generic Books")
        assertFalse(book.isAndBibleCategory)
    }

    @Test
    fun `isAndBibleCategory should be locale independent`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("tr", "TR"))
        try {
            val book = createBook("ABUpper", "ANDBIBLE")
            assertTrue(book.isAndBibleCategory)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `AndBibleAddonFilter should accept AndBible category alias`() {
        val filter = AndBibleAddonFilter()
        val book = createBook("AliasAddon", "AndBible")
        assertTrue(filter.test(book))
    }

    @Test
    fun `AndBibleAddonFilter should still enforce minimum version`() {
        val filter = AndBibleAddonFilter()
        val book = createBook("FutureAddon", "AndBible", "9223372036854775807")
        assertFalse(filter.test(book))
    }

    private fun createBook(initials: String, category: String, minimumVersion: String = "0"): Book {
        val conf = """
[$initials]
Description=$initials
Abbreviation=$initials
Category=$category
Encoding=UTF-8
AndBibleMinimumVersion=$minimumVersion
        """.trimIndent()
        return FakeBookFactory.createFakeRepoBook(initials, conf, null)
    }
}
