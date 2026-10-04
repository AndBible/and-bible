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
 * A one-shot instruction to the toolbar's search field about focus and the software keyboard.
 *
 * One nullable enum rather than two booleans, for two reasons: `Focus` and `Release` are then mutually
 * exclusive by construction, and the host's `combine` over the bar-state inputs stays inside Kotlin's
 * five-flow overloads.
 *
 * It is an INSTRUCTION, not a level — the composable acts on it and then acknowledges, which returns it
 * to `null`. That is what lets the same instruction fire twice: submitting again from an open results
 * sheet is a `Results -> Results` transition, so anything derived from the phase would not even emit.
 * Same shape as the reading host's existing `searchUnavailableDocNameState` + `onSearchUnavailableMessageShown`.
 */
enum class SearchFieldImeRequest { Focus, Release }

/**
 * Everything the reading toolbar's search mode renders, as plain data (F6 Task 4).
 *
 * The recent-terms list and whether its menu is open are state-IN rather than remembered inside the
 * composable, so both stay unit-testable: an expanded `DropdownMenu` cannot be photographed (it
 * hangs Roborazzi), which makes the golden useless as the regression test for it.
 *
 * A `null` instance of this class — the default on both `ReadingToolbar` and `ReadingViewScreen` —
 * means "not in search mode", so the normal toolbar path and its goldens are untouched.
 */
data class ReadingSearchBarState(
    val query: String,
    val recentTerms: List<String> = emptyList(),
    val recentMenuOpen: Boolean = false,
    val imeRequest: SearchFieldImeRequest? = null,
    /** There are results to go back to — the toolbar's leading icon becomes the results button. */
    val resultsAvailable: Boolean = false,
    /** This session is searching an EPUB, which decides which help text the ⋮ menu shows. */
    val forEpub: Boolean = false,
) {
    /**
     * A blank query must not reach Lucene: classic `SearchControl.validateQuery` rejects it, and an
     * empty search would clear the results the user is still looking at.
     */
    val submitEnabled: Boolean get() = query.isNotBlank()

    /**
     * Which affordance the field's leading icon offers. The two are wanted in disjoint situations —
     * history when there is nothing to go back to, results once a query has been run (spec D6) — and
     * the toolbar row has no slot for both.
     */
    val leadingAction: SearchFieldLeadingAction get() = when {
        resultsAvailable && query.isNotBlank() -> SearchFieldLeadingAction.ShowResults
        recentTerms.isNotEmpty() -> SearchFieldLeadingAction.RecentTerms
        else -> SearchFieldLeadingAction.None
    }
}

/** @see ReadingSearchBarState.leadingAction */
enum class SearchFieldLeadingAction { None, RecentTerms, ShowResults }
