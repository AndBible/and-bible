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

class QuickDocPickerTest {
    private fun row(id: String, lang: String, abbr: String) = QuickDocRow(id, lang, abbr)

    @Test fun emptyListYieldsNone() {
        assertEquals(QuickDocAction.None, QuickDocPicker.action(emptyList(), "x"))
    }
    @Test fun exactlyTwoSwitchesDirectlyToTheNonActive() {
        val rows = listOf(row("kjv", "en", "KJV"), row("esv", "en", "ESV"))
        assertEquals(QuickDocAction.SwitchDirectly("kjv"), QuickDocPicker.action(rows, "esv"))
    }
    @Test fun exactlyTwoWithNeitherActiveSwitchesToTheFirstInLanguageThenAbbreviationOrder() {
        val rows = listOf(row("b", "fi", "B"), row("a", "en", "Z"))
        assertEquals(QuickDocAction.SwitchDirectly("a"), QuickDocPicker.action(rows, "other"))
    }
    @Test fun oneDocumentShowsThePicker() {
        assertEquals(QuickDocAction.ShowPicker, QuickDocPicker.action(listOf(row("a", "en", "A")), "a"))
    }
    @Test fun threeOrMoreShowsThePicker() {
        val rows = listOf(row("b", "fi", "B"), row("a", "en", "A"), row("c", "en", "C"))
        assertEquals(QuickDocAction.ShowPicker, QuickDocPicker.action(rows, "a"))
    }
}
