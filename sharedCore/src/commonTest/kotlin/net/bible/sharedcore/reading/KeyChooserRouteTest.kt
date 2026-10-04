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
import kotlin.test.assertNull

class KeyChooserRouteTest {
    @Test fun theGridPagesGoToTheGridSheet() {
        listOf(KeyChooserPage.BIBLE, KeyChooserPage.COMMENTARY, KeyChooserPage.MY_NOTE).forEach { page ->
            assertEquals(
                ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid),
                KeyChooserRoute.sheetFor(page),
                "$page is grid-shaped",
            )
        }
    }

    @Test fun mapsAndPlainGeneralBooksGetTheirOwnSheets() {
        assertEquals(ReadingQuickSheet.KeyChooser(KeyChooserKind.Map), KeyChooserRoute.sheetFor(KeyChooserPage.MAP))
        assertEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.GeneralBook),
            KeyChooserRoute.sheetFor(KeyChooserPage.GENERAL_BOOK),
        )
    }

    @Test fun theFourHardCasesKeepTheirActivity() {
        listOf(
            KeyChooserPage.DICTIONARY,                      // AbSearchField + IME
            KeyChooserPage.GENERAL_BOOK_MY_DOCUMENT,        // search mode, row overflow, 3 dialogs
            KeyChooserPage.GENERAL_BOOK_STUDY_PAD,          // launches ManageLabels
            KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT,     // launches ChooseDocument
        ).forEach { page ->
            assertNull(KeyChooserRoute.sheetFor(page), "$page must keep its full screen (spec §4.6)")
        }
    }

    @Test fun everyPageIsDecided() {
        KeyChooserPage.entries.forEach { KeyChooserRoute.sheetFor(it) } // must not throw
        assertEquals(9, KeyChooserPage.entries.size, "the eight startKeyChooser destinations plus MY_NOTE")
    }
}
