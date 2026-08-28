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

/**
 * The shapes `CurrentPage.startKeyChooser` can dispatch to. The four `GENERAL_BOOK_*` cases mirror
 * the branch inside `CurrentGeneralBookPage.startKeyChooser` (`:79-109`); the host maps its JSword
 * page onto one of these so no JSword type crosses into `:sharedCore`.
 */
enum class KeyChooserPage {
    BIBLE, COMMENTARY, MY_NOTE,
    DICTIONARY, MAP,
    GENERAL_BOOK,
    GENERAL_BOOK_STUDY_PAD, GENERAL_BOOK_MY_DOCUMENT, GENERAL_BOOK_MULTI_DOCUMENT,
}

/**
 * Which key choosers open as a quick sheet, and which keep their full screen (spec §4.6).
 *
 * Stated as a pure function rather than an `if` chain in the host for the reason
 * [ReadingOverlayExclusion] is: the branch is easy to extend wrongly, and the wrong extension —
 * routing the dictionary here — would put an `AbSearchField` and the IME inside a bottom sheet,
 * which is exactly what round 14a's spec §3 group 4 refused to do.
 *
 * Returns null for "use the existing Activity".
 */
object KeyChooserRoute {
    fun sheetFor(page: KeyChooserPage): ReadingQuickSheet.KeyChooser? = when (page) {
        KeyChooserPage.BIBLE,
        KeyChooserPage.COMMENTARY,
        KeyChooserPage.MY_NOTE -> ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid)

        KeyChooserPage.MAP -> ReadingQuickSheet.KeyChooser(KeyChooserKind.Map)
        KeyChooserPage.GENERAL_BOOK -> ReadingQuickSheet.KeyChooser(KeyChooserKind.GeneralBook)

        // A search field plus the IME inside a sheet.
        KeyChooserPage.DICTIONARY,
        // Search mode, a per-row overflow and three dialogs.
        KeyChooserPage.GENERAL_BOOK_MY_DOCUMENT,
        // Launches ManageLabels.
        KeyChooserPage.GENERAL_BOOK_STUDY_PAD,
        // Launches ChooseDocument.
        KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT -> null
    }
}
