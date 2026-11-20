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
package net.bible.sharedui.reading.host.slots

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings

/** The `truncatedByTime` -> notice wiring of [SearchSheetSlotContent], including the loading state. */
@OptIn(ExperimentalTestApi::class)
class SearchTruncatedNoticeTest {
    private val strings = iosStrings("en")

    private fun results(truncated: Boolean) = MultiSearchResults(
        main = listOf(SwordResultRow("Gen.1.1", emptyList(), StyledText.plain("light"))),
        other = emptyList(),
        total = 1,
        truncatedByTime = truncated,
    )

    /** Runs the sheet over a search whose results arrive only when [gate] is completed. */
    private fun androidx.compose.ui.test.ComposeUiTest.withGate(
        gate: CompletableDeferred<MultiSearchResults>,
        check: () -> Unit,
    ) {
        val fixture = SearchFixture()
        val gated = object : BibleSearchService by fixture.bible {
            override suspend fun searchMulti(request: SearchRequest) = gate.await()
        }
        val state = ReadingSearchState(
            fixture.settings, fixture.scope, fixture.platform, fixture.jobs,
            ReadingSearchServices(gated, FakeEpubSearch(), net.bible.sharedcore.search.SearchResultsCache(), fixture.index),
            onLeaveFullScreen = {},
        )
        try {
            fixture.platform.documents["KJV"] = fixture.platform.current!!
            state.openSearch("light")
            state.session.runSearch("KJV", "light")
            setContent {
                CompositionLocalProvider(LocalStrings provides strings) {
                    MaterialTheme { SearchSheetSlotContent(state.session, strings) }
                }
            }
            check()
        } finally {
            state.dispose()
            fixture.state.dispose()
            fixture.scope.cancel()
        }
    }

    @Test
    fun truncatedResultsShowTheNoticeOnlyOnceLoadingIsDone() = runComposeUiTest {
        val gate = CompletableDeferred<MultiSearchResults>()
        withGate(gate) {
            onNodeWithText(strings.searchResultsTruncated).assertDoesNotExist() // still loading
            gate.complete(results(truncated = true))
            waitForIdle()
            onNodeWithText("Gen.1.1").assertExists()
            onNodeWithText(strings.searchResultsTruncated).assertExists()
        }
    }

    @Test
    fun completeResultsShowNoNotice() = runComposeUiTest {
        val gate = CompletableDeferred<MultiSearchResults>()
        withGate(gate) {
            gate.complete(results(truncated = false))
            waitForIdle()
            onNodeWithText("Gen.1.1").assertExists()
            onNodeWithText(strings.searchResultsTruncated).assertDoesNotExist()
        }
    }

    // Submitting a blank query with the regex switch on must not start a search at all.
    @Test
    fun aBlankQueryWithTheRegexSwitchOnStartsNoSearch() {
        val f = SearchFixture()
        try {
            f.platform.documents["KJV"] = f.platform.current!!
            f.state.openSearch()
            f.state.searchRegex.value = true
            f.state.queries.setQuery("   ")
            f.state.controller.submit()
            kotlin.test.assertNull(f.state.lastSearchRequestForTest)
        } finally {
            f.state.dispose()
        }
    }
}
