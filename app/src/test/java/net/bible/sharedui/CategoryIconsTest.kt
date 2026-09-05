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
package net.bible.sharedui

import net.bible.android.activity.R
import net.bible.sharedcore.navigation.DocCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the classic per-category icon mapping ([categoryDrawableRes]) against the classic
 * `BookCategory.imageResource` (`download/DocumentBadges.kt`). Every category must resolve to the
 * bespoke vector drawable, with the two book-shaped categories (GENERAL_BOOK / OTHER) sharing
 * `ic_book_24dp`.
 */
class CategoryIconsTest {
    @Test
    fun mapsEachCategoryToClassicDrawable() {
        assertEquals(R.drawable.ic_bible_24dp, categoryDrawableRes(DocCategory.BIBLE))
        assertEquals(R.drawable.ic_commentary, categoryDrawableRes(DocCategory.COMMENTARY))
        assertEquals(R.drawable.ic_dictionary_24dp, categoryDrawableRes(DocCategory.DICTIONARY))
        assertEquals(R.drawable.ic_map_black_24dp, categoryDrawableRes(DocCategory.MAPS))
        assertEquals(R.drawable.ic_book_24dp, categoryDrawableRes(DocCategory.GENERAL_BOOK))
        assertEquals(R.drawable.ic_addon_24dp, categoryDrawableRes(DocCategory.AND_BIBLE))
        assertEquals(R.drawable.ic_book_24dp, categoryDrawableRes(DocCategory.OTHER))
    }

    @Test
    fun everyCategoryResolvesToANonZeroDrawable() {
        // A 0 resource id would silently render nothing on-device; ensure the when is exhaustive
        // and every branch points at a real drawable.
        DocCategory.entries.forEach { category ->
            assertEquals(
                "category $category must map to a real drawable",
                true,
                categoryDrawableRes(category) != 0,
            )
        }
    }
}
