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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocTypeFilterIconCategoryTest {
    @Test fun all_has_no_representative_category() {
        assertNull(DocTypeFilter.ALL.iconCategory)
    }

    @Test fun addon_maps_to_and_bible_not_to_a_same_named_category() {
        // The one non-identity mapping: the filter is called ADDON, the category AND_BIBLE.
        assertEquals(DocCategory.AND_BIBLE, DocTypeFilter.ADDON.iconCategory)
    }

    @Test fun every_concrete_filter_maps_to_the_category_it_filters_on() {
        // Derive the expectation from the filter's own predicate rather than restating the
        // mapping: build a row of each category, and assert that the category the icon claims
        // to represent is one the filter actually accepts.
        for (filter in DocTypeFilter.entries) {
            val category = filter.iconCategory ?: continue
            val probe = DocRow(
                docId = "p", osisId = "p", abbreviation = "p", name = "p",
                language = LangOption("en", "English", "en"), repository = "r",
                category = category, installStatus = DocInstallStatus.NOT_INSTALLED,
                percentDone = 0, recommended = false, badWarn = false, locked = false,
                enciphered = false, canDelete = false, installSizeMb = null,
            )
            assertEquals(true, filter.test(probe), "$filter rejects a ${category} row it claims to represent")
        }
    }

    @Test fun only_all_lacks_a_category() {
        val withoutCategory = DocTypeFilter.entries.filter { it.iconCategory == null }
        assertEquals(listOf(DocTypeFilter.ALL), withoutCategory)
    }
}
