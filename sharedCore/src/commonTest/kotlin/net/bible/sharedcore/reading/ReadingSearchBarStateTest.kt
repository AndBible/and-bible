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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingSearchBarStateTest {
    @Test fun submitIsDisabledForAnEmptyQuery() =
        assertFalse(ReadingSearchBarState(query = "").submitEnabled)

    @Test fun submitIsDisabledForABlankQuery() =
        assertFalse(ReadingSearchBarState(query = "   ").submitEnabled)

    @Test fun submitIsEnabledOnceThereIsANonBlankQuery() =
        assertTrue(ReadingSearchBarState(query = "light").submitEnabled)

    // The new field must default to null, so every existing construction site — and every golden
    // that builds a bar state positionally — keeps rendering an unfocused field.
    @Test
    fun theImeRequestDefaultsToNull() {
        assertNull(ReadingSearchBarState(query = "light").imeRequest)
    }

    // ---- 17d C5: which affordance the field's leading icon offers -------------------------------

    @Test
    fun withoutResultsOrHistoryThereIsNoLeadingAction() =
        assertEquals(
            SearchFieldLeadingAction.None,
            ReadingSearchBarState(query = "light").leadingAction,
        )

    @Test
    fun historyIsOfferedWhileThereAreNoResultsToGoBackTo() =
        assertEquals(
            SearchFieldLeadingAction.RecentTerms,
            ReadingSearchBarState(query = "light", recentTerms = listOf("light")).leadingAction,
        )

    // Results win over history — the reason the two are one slot rather than two (spec D6).
    @Test
    fun resultsWinOverHistoryOnceASearchHasRun() =
        assertEquals(
            SearchFieldLeadingAction.ShowResults,
            ReadingSearchBarState(
                query = "light", recentTerms = listOf("light"), resultsAvailable = true,
            ).leadingAction,
        )

    // Clearing the field must not leave a button offering results for a query that is no longer
    // there: the user is starting a new search, and history is what helps with that.
    @Test
    fun clearingTheQueryFallsBackToHistoryEvenWithResultsLoaded() =
        assertEquals(
            SearchFieldLeadingAction.RecentTerms,
            ReadingSearchBarState(
                query = "  ", recentTerms = listOf("light"), resultsAvailable = true,
            ).leadingAction,
        )

    // Both new fields default off, so every existing construction site keeps the history branch.
    @Test
    fun theNewFieldsDefaultOff() {
        val s = ReadingSearchBarState(query = "light")
        assertFalse(s.resultsAvailable)
        assertFalse(s.forEpub)
    }
}
