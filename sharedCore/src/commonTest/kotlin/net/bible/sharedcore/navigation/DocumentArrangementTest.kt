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

class DocumentArrangementTest {
    private val en = LangOption("en", "English", "en")

    private fun row(
        id: String,
        cat: DocCategory = DocCategory.BIBLE,
        status: DocInstallStatus = DocInstallStatus.NOT_INSTALLED,
        lang: LangOption = en,
        repo: String = "CrossWire",
        recommended: Boolean = false,
        sizeMb: Double? = null,
    ) = DocRow(id, "osis-$id", id, "name $id", lang, repo, cat, status, 0, recommended,
        badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = sizeMb)

    @Test fun status_rank_orders_downloading_then_upgrade_then_installed_then_missing() {
        assertEquals(0, row("a", status = DocInstallStatus.BEING_INSTALLED).sortStatusRank)
        assertEquals(1, row("a", status = DocInstallStatus.UPGRADE_AVAILABLE).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.INSTALLED).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.ERROR_DOWNLOADING).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.INSTALL_CANCELLED).sortStatusRank)
        assertEquals(3, row("a", status = DocInstallStatus.NOT_INSTALLED).sortStatusRank)
    }

    @Test fun docRow_exposes_the_fields_the_row_headline_starts_with() {
        val r = row("KJV", sizeMb = 2.5)
        assertEquals("KJV", r.sortName)            // the headline reads "<abbreviation> <name>"
        assertEquals("name KJV", r.sortSecondaryName)
        assertEquals("English", r.sortLanguage)
        assertEquals("CrossWire", r.sortRepository)
        assertEquals(2_621_440L, r.sortSizeBytes)  // 2.5 MiB
    }

    @Test fun null_install_size_stays_null_rather_than_becoming_zero() {
        assertEquals(null, row("a", sizeMb = null).sortSizeBytes)
    }

    @Test fun category_rank_keeps_the_classic_order_including_its_gap_at_three() {
        assertEquals(
            listOf(0, 1, 2, 4, 5, 6, 7, 8),
            listOf(DocCategory.BIBLE, DocCategory.COMMENTARY, DocCategory.DICTIONARY,
                DocCategory.GENERAL_BOOK, DocCategory.MAPS, DocCategory.AND_BIBLE,
                DocCategory.OTHER, null).map { docCategoryRank(it) },
        )
    }
}
