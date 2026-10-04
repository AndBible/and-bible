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
import kotlin.test.assertNotNull
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

    /** The types whose row opens a sheet page — hand-maintained, deliberately duplicating
     *  textSettingEditorPageFor's own table so the two can disagree and be caught. */
    private val SHEET_PAGE_TYPES = setOf(
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

    /** Every type edited inline (a switch row) or by navigating away instead of in a sheet.
     *  Hand-maintained and EXHAUSTIVE — list every one explicitly, never derive it by
     *  subtracting SHEET_PAGE_TYPES, or a newly added type lands in both computed sets at
     *  once and the guard below passes vacuously. */
    private val NON_PAGE_TYPES = setOf(
        TextSettingType.JUSTIFY,
        TextSettingType.HYPHENATION,
        TextSettingType.MORPH,
        TextSettingType.FOOTNOTES,
        TextSettingType.FOOTNOTES_INLINE,
        TextSettingType.EXPAND_XREFS,
        TextSettingType.XREFS,
        TextSettingType.REDLETTERS,
        TextSettingType.SECTIONTITLES,
        TextSettingType.VERSENUMBERS,
        TextSettingType.VERSEPERLINE,
        TextSettingType.BOOKMARKS_SHOW,
        TextSettingType.BOOKMARKS_HIDELABELS,
        TextSettingType.MYNOTES,
        TextSettingType.PAGENUMBER,
        TextSettingType.INFINITE_SCROLL,
        TextSettingType.NON_STRONGS_WORD_ITALIC,
        TextSettingType.MARK_AS_READ_BUTTON,
        TextSettingType.TITLE_SCROLL_BUTTON,
        TextSettingType.MEMORIZATION_INDICATORS,
        TextSettingType.AUTO_TRACK_READING,
        TextSettingType.AI_DOC_MARKERS,
        TextSettingType.SCROLL_HELPER_LINES,
        TextSettingType.PAGE_BUTTONS,
        TextSettingType.ORDINALS,
        TextSettingType.SHOW_READING_PROGRESS,
    )

    /**
     * The guard that matters most: a NEW TextSettingType must not silently default to "no
     * editor". Every type belongs to exactly one of the two lists above, so a type added to
     * the enum and to neither list fails here until someone decides which it is.
     */
    @Test fun everyTextSettingTypeHasADecision() {
        assertEquals(
            emptySet(),
            TextSettingType.entries.toSet() - SHEET_PAGE_TYPES - NON_PAGE_TYPES,
            "A TextSettingType is in neither list. Add it to SHEET_PAGE_TYPES or to " +
                "NON_PAGE_TYPES — deliberately — and give textSettingEditorPageFor a branch " +
                "to match if it is a page.",
        )
        assertEquals(
            emptySet(),
            SHEET_PAGE_TYPES intersect NON_PAGE_TYPES,
            "A TextSettingType is in both lists.",
        )
    }

    /** The routing table must agree with the two hand-maintained lists above. */
    @Test fun theRoutingTableAgreesWithTheHandMaintainedLists() {
        SHEET_PAGE_TYPES.forEach {
            assertNotNull(textSettingEditorPageFor(it.name), "expected a page for $it")
        }
        NON_PAGE_TYPES.forEach {
            assertNull(textSettingEditorPageFor(it.name), "expected no page for $it")
        }
    }
}
