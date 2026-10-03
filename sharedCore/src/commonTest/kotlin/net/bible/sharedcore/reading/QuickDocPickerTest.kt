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

package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.bible.sharedcore.navigation.DocCategory

class QuickDocPickerTest {
    private fun row(id: String, lang: String, abbr: String) = QuickDocRow(id, "$abbr ($lang)", lang, abbr, DocCategory.BIBLE)

    @Test fun emptyListYieldsNone() {
        assertEquals(QuickDocAction.None, QuickDocPicker.action(emptyList(), "x"))
    }
    @Test fun exactlyTwoSwitchesDirectlyToTheNonActive() {
        val rows = listOf(row("kjv", "en", "KJV"), row("esv", "en", "ESV"))
        assertEquals(QuickDocAction.SwitchDirectly("kjv"), QuickDocPicker.action(rows, "esv"))
    }
    @Test fun threeOrMoreShowsPopupSortedByLanguageThenAbbreviation() {
        val rows = listOf(row("b", "fi", "B"), row("a", "en", "A"), row("c", "en", "C"))
        val action = QuickDocPicker.action(rows, "a")
        assertTrue(action is QuickDocAction.ShowPopup)
        val items = (action as QuickDocAction.ShowPopup).items
        assertEquals(listOf("a", "c", "b"), items.map { it.id }) // en:A, en:C, fi:B
    }
    @Test fun currentDocIsDisabledButVisibleInPopup() {
        val rows = listOf(row("a", "en", "A"), row("b", "en", "B"), row("c", "en", "C"))
        val items = (QuickDocPicker.action(rows, "b") as QuickDocAction.ShowPopup).items
        assertEquals(false, items.first { it.id == "b" }.enabled)
        assertTrue(items.filter { it.id != "b" }.all { it.enabled })
    }
    @Test
    fun `the popup carries each row's category through the sort`() {
        val rows = listOf(
            QuickDocRow("ESV", "English Standard Version", "en", "ESV", DocCategory.BIBLE),
            QuickDocRow("MHC", "Matthew Henry", "en", "MHC", DocCategory.COMMENTARY),
            QuickDocRow("EAST", "Easton", "en", "EAST", DocCategory.DICTIONARY),
        )
        val action = QuickDocPicker.action(rows, activeId = "ESV")
        val items = (action as QuickDocAction.ShowPopup).items
        assertEquals(
            listOf(DocCategory.DICTIONARY, DocCategory.BIBLE, DocCategory.COMMENTARY),
            items.map { it.category },
        )
    }
}
