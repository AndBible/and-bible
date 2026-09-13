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

/** Which of the three sheet-hosted key choosers is showing (spec §4.6). */
enum class KeyChooserKind { Grid, Map, GeneralBook }

/**
 * Which quick sheet — if any — is open over the reading view.
 *
 * ONE nullable state of this type, not four independent booleans. Every case is a modal
 * `ModalBottomSheet`, so two open at once is the sheet-over-sheet violation round 14a's spec §5
 * banned; modelling them as one sum type makes that unrepresentable instead of arbitrated. It is
 * also why [ReadingOverlay] gains a single `QuickSheet` member rather than four, and why
 * `ComposeReadingViewHost` needs one new mount point rather than four.
 */
sealed interface ReadingQuickSheet {
    /** Round 15b: the reading-view History list (no fuller counterpart, so no footer row). */
    object History : ReadingQuickSheet

    /** Round 15b: switch workspace; the full selector is one footer row away. */
    object Workspaces : ReadingQuickSheet

    /** Round 15b Plan B: switch document; `ChooseDocument` is one footer row away. */
    object Documents : ReadingQuickSheet

    /**
     * Round 15b Plan B: the three key choosers simple enough for a sheet.
     *
     * [navigateToVerse] is meaningful only for [KeyChooserKind.Grid], where it decides whether
     * picking a chapter finishes or opens a third, verse step. It is a property of the OPENING, not
     * a global preference read inside the sheet, because the two openings disagree (nav-graph slice
     * 7 spec §6.1.1): the reading view's own title tap follows the user's `navigate_to_verse_pref`,
     * while the JS reference chooser (`BibleJavascriptInterface.refChooserDialog`) always drills to
     * verse level — it has to return a verse, and that preference defaults to off. Deciding it at
     * the call site is what keeps the JS chooser from silently stopping at chapter level.
     */
    data class KeyChooser(
        val kind: KeyChooserKind,
        val navigateToVerse: Boolean = false,
    ) : ReadingQuickSheet
}
