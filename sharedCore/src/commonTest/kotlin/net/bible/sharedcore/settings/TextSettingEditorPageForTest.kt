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

package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextSettingEditorPageForTest {

    @Test fun colorsOpensTheColoursPage() {
        assertEquals(SettingsEditorPage.Colors, textSettingEditorPageFor(TextSettingType.COLORS.name))
    }

    @Test fun theFourNumericAndMarginTypesOpenTheirOwnRowPage() {
        listOf(
            TextSettingType.FONTSIZE,
            TextSettingType.TOPMARGIN,
            TextSettingType.LINE_SPACING,
            TextSettingType.MARGINSIZE,
        ).forEach { type ->
            assertEquals(SettingsEditorPage.Row(type.name), textSettingEditorPageFor(type.name), "type=$type")
        }
    }

    @Test fun theFourListChoiceTypesOpenTheirOwnRowPage() {
        listOf(
            TextSettingType.FONTFAMILY,
            TextSettingType.STRONGS,
            TextSettingType.PAGE_SCROLL_AMOUNT,
            TextSettingType.SCROLL_HELPER_LINE_STYLE,
        ).forEach { type ->
            assertEquals(SettingsEditorPage.Row(type.name), textSettingEditorPageFor(type.name), "type=$type")
        }
    }

    /** These navigate to another screen instead of editing a value, so they are not sheet pages. */
    @Test fun navigatingKeysAreNotSheetPages() {
        assertNull(textSettingEditorPageFor(TextSettingType.BOOKMARKS_HIDELABELS.name))
        assertNull(textSettingEditorPageFor(KEY_OPEN_WORKSPACE_SETTINGS))
        assertNull(textSettingEditorPageFor(KEY_OPEN_GLOBAL_SETTINGS))
    }

    @Test fun anUnknownKeyIsNotASheetPage() {
        assertNull(textSettingEditorPageFor("NOT_A_TEXT_SETTING_TYPE"))
    }

    /**
     * The guard that matters most: a NEW TextSettingType must not silently default to "no editor".
     * Every type is either a sheet page or listed here as a deliberate non-page. Adding a type
     * without deciding fails this test.
     */
    @Test fun everyTextSettingTypeHasADecision() {
        val deliberatelyNotAPage = setOf(TextSettingType.BOOKMARKS_HIDELABELS)
        val undecided = TextSettingType.entries.filter { type ->
            textSettingEditorPageFor(type.name) == null && type !in deliberatelyNotAPage
        }
        // Boolean switch rows are edited inline in the list, never in a sheet — they belong in the
        // expected-null set too, so assert against the CURRENT known set rather than emptiness.
        assertEquals(
            expectedNonPageTypes(),
            undecided.toSet() + deliberatelyNotAPage,
            "A TextSettingType changed its editor classification — update expectedNonPageTypes() " +
                "and textSettingEditorPageFor() together, deliberately.",
        )
    }

    /** Every type that is edited inline (a switch) or by navigating away, not in a sheet. */
    private fun expectedNonPageTypes(): Set<TextSettingType> =
        TextSettingType.entries.toSet() - setOf(
            TextSettingType.COLORS,
            TextSettingType.FONTSIZE,
            TextSettingType.TOPMARGIN,
            TextSettingType.LINE_SPACING,
            TextSettingType.MARGINSIZE,
            TextSettingType.FONTFAMILY,
            TextSettingType.STRONGS,
            TextSettingType.PAGE_SCROLL_AMOUNT,
            TextSettingType.SCROLL_HELPER_LINE_STYLE,
        )
}
