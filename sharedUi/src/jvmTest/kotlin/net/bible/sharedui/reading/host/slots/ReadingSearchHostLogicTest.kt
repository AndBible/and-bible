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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo

/**
 * The reading view's search host logic (moved out of `:app`'s `ReadingSearchHostTest` /
 * `ReadingSearchEntryPointsTest`, same assertions, now over fakes instead of a Robolectric host).
 */
class ReadingSearchHostLogicTest {
    private val unindexedBible =
        SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, isEpub = false, indexDone = false)

    // ---- the pure index helpers --------------------------------------------------------------

    /** The case the ~12s cap exists for: the job finishes, `indexStatus` never reaches DONE. */
    @Test
    fun anIndexThatNeverReachesDoneGivesUpAndLandsBackInNeedsIndex() = runTest {
        val runs = mutableListOf<String>()
        val controller = ReadingSearchController(
            resolveDoc = { unindexedBible },
            onUnavailable = {},
            onLeaveFullScreen = {},
            onStartIndexing = {},
            onRunSearch = { _, query, _ -> runs += query },
        )
        controller.queries.setQuery("light")
        controller.open()
        controller.acceptIndexing()
        assertTrue(controller.phase.value is ReadingSearchPhase.Indexing)

        var polls = 0
        val done = awaitIndexDone(poll = IndexPollDecision(), indexDone = { polls++; false }, pause = { })

        assertFalse(done, "an index that never reaches DONE must not be reported as done")
        assertEquals(6, polls, "the cap is six status reads (~12s at the production 2s interval)")
        controller.onIndexingFinished(done)
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), controller.phase.value)
        assertTrue(runs.isEmpty(), "no search may run against a missing index")
    }

    @Test
    fun anIndexThatReachesDoneRunsTheWaitingQuery() = runTest {
        val runs = mutableListOf<String>()
        val controller = ReadingSearchController(
            resolveDoc = { unindexedBible },
            onUnavailable = {},
            onLeaveFullScreen = {},
            onStartIndexing = {},
            onRunSearch = { _, query, _ -> runs += query },
        )
        controller.queries.setQuery("light")
        controller.open()
        controller.acceptIndexing()

        var reads = 0
        val done = awaitIndexDone(poll = IndexPollDecision(), indexDone = { ++reads >= 3 }, pause = { })

        assertTrue(done)
        controller.onIndexingFinished(done)
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), controller.phase.value)
        assertEquals(listOf("light"), runs)
    }

    @Test
    fun aForeignFinishedJobMustNotResolveTheIndexBuildWhileAnotherJobIsStillRunning() {
        assertFalse(shouldResolveIndexBuild(jobFinished = true, everyJobFinished = false))
    }

    @Test
    fun anUnfinishedJobNeverResolvesTheIndexBuild() {
        assertFalse(shouldResolveIndexBuild(jobFinished = false, everyJobFinished = false))
        assertFalse(shouldResolveIndexBuild(jobFinished = false, everyJobFinished = true))
    }

    @Test
    fun aFinishedJobWithNothingElseRunningResolvesTheIndexBuild() {
        assertTrue(shouldResolveIndexBuild(jobFinished = true, everyJobFinished = true))
    }

    @Test
    fun nextSelectorIndexPromptChainsToTheSecondUnindexedTranslationRatherThanRerunning() {
        assertEquals("B", nextSelectorIndexPrompt(listOf("A", "B"), indexDone = true) { listOf("B") })
    }

    @Test
    fun nextSelectorIndexPromptReturnsNullOnceTheSetIsCleanSoTheOrdinaryRerunTakesOver() {
        assertNull(nextSelectorIndexPrompt(listOf("A", "B"), indexDone = true) { emptyList() })
    }

    @Test
    fun nextSelectorIndexPromptReturnsNullOutsideASelectorDrivenRun() {
        var consulted = false
        assertNull(nextSelectorIndexPrompt(null, indexDone = true) { consulted = true; it })
        assertFalse(consulted, "an ordinary submit must not even query unindexedAmong")
    }

    @Test
    fun nextSelectorIndexPromptReturnsNullWhenTheBuildFailed() {
        assertNull(nextSelectorIndexPrompt(listOf("A"), indexDone = false) { it })
    }

    // ---- session flags --------------------------------------------------------------------------

    @Test
    fun leavingSearchModeClearsTheRecentTermsMenuAndTheSettingsSheetFlags() {
        val f = SearchFixture()
        f.state.openSearch()
        f.state.searchRecentMenuOpen.value = true
        f.state.searchSettingsOpen.value = true

        assertTrue(f.state.closeSearchIfOpen(), "first back closes the sheet")
        // An indexed document with an empty query opens into Form (no sheet), so the first back
        // may already have left search mode; the second then reports nothing left to close.
        f.state.closeSearchIfOpen()

        assertFalse(f.state.searchRecentMenuOpen.value, "the recent-terms dropdown must not stay flagged open")
        assertFalse(f.state.searchSettingsOpen.value, "nor the settings sheet")
    }

    @Test
    fun openingSearchRereadsTheRecentTermsWrittenByTheClassicPath() {
        val f = SearchFixture()
        f.settings.setString("search_recent_terms", "water\nlight")

        f.state.openSearch()

        assertEquals(listOf("water", "light"), f.state.controller.queries.recentTerms.value)
    }

    @Test
    fun closingSearchViaTheBackButtonFromAnEmptyFormResetsTheFieldFocusFlag() {
        val f = SearchFixture()
        f.state.openSearch()
        assertFalse(f.state.controller.sheetVisible.value, "sanity: an indexed document with no query opens into Form")
        f.state.searchFieldFocused.value = true

        assertTrue(f.state.closeSearchIfOpen(), "a single back press must leave search mode from an empty form")

        assertFalse(f.state.searchFieldFocused.value, "the field-focus flag must not survive search mode closing")
    }

    @Test
    fun searchUnavailableMessageShownClearsThePendingMessage() {
        val f = SearchFixture()
        f.platform.info = SearchDocumentInfo("Dict", SearchDocumentCategory.DICTIONARY, isEpub = false, indexDone = true)
        f.platform.current = fakeSearchBook("Dict", "A Dictionary")

        f.state.openSearch()
        assertEquals("A Dictionary", f.state.searchUnavailableDocName.value, "sanity")

        f.state.searchUnavailableMessageShown()

        assertNull(f.state.searchUnavailableDocName.value)
    }

    // ---- selector chain -------------------------------------------------------------------------

    @Test
    fun choosingAnUnindexedTranslationInTheSelectorPromptsForThatTranslationNotTheActiveDocument() {
        val f = SearchFixture(bible = FakeBibleSearch(unindexed = setOf("U1", "U2")))
        f.state.openSearch()
        assertTrue(f.state.controller.sheetVisible.value.not(), "sanity: indexed KJV, empty query -> Form")
        f.state.controller.queries.setQuery("light")
        f.state.controller.submit()
        assertTrue(f.state.controller.sheetVisible.value, "sanity: a results session is open")

        f.state.onSearchTranslationsChosen(listOf("U1", "U2"))

        assertEquals(
            ReadingSearchPhase.NeedsIndex("U1", forEpub = false),
            f.state.controller.phase.value,
            "must prompt for the CHOSEN translation, not KJV (the active document)",
        )
        assertEquals(listOf("U1", "U2"), f.state.searchSelectorPendingIds)
    }

    @Test
    fun choosingAFullyIndexedSelectionArmsNoPendingChain() {
        val f = SearchFixture()
        f.state.openSearch()
        f.state.controller.queries.setQuery("light")
        f.state.controller.submit()

        f.state.onSearchTranslationsChosen(listOf("KJV"))

        assertNull(f.state.searchSelectorPendingIds)
    }

    @Test
    fun leavingSearchClearsThePendingSelectorChain() {
        val f = SearchFixture(bible = FakeBibleSearch(unindexed = setOf("U3")))
        f.state.openSearch()
        f.state.controller.queries.setQuery("light")
        f.state.controller.submit()
        f.state.onSearchTranslationsChosen(listOf("U3"))
        assertEquals(listOf("U3"), f.state.searchSelectorPendingIds, "sanity")

        assertTrue(f.state.closeSearchIfOpen(), "first back closes the sheet")
        assertTrue(f.state.closeSearchIfOpen(), "second back leaves search mode")

        assertNull(f.state.searchSelectorPendingIds)
    }

    // ---- Review focus: the index feed ------------------------------------------------------------

    /**
     * Closing the sheet while a build runs and bringing it back must not stack a second progress
     * subscriber, and must not start a second build: the toolbar's rebuild affordance on a running
     * build only raises the sheet again.
     */
    @Test
    fun reopeningTheSheetWhileIndexingKeepsOneProgressSubscriber() {
        val f = SearchFixture()
        f.platform.info = unindexedBible
        f.state.openSearch()
        f.state.controller.acceptIndexing()
        assertEquals(1, f.jobs.subscribers, "sanity: indexing subscribes once")
        assertEquals(listOf("KJV"), f.index.created, "sanity: one build was started")

        assertTrue(f.state.controller.closeSheet(), "the user dismisses the progress sheet")
        assertTrue(f.state.controller.requestRebuildIndex(), "and raises it again from the toolbar")
        assertTrue(f.state.controller.sheetVisible.value)
        assertEquals(1, f.jobs.subscribers, "raising the sheet again must not add a subscriber")
        assertEquals(listOf("KJV"), f.index.created, "nor start a second build")

        // The other way back in: a fresh open (Ctrl+F) then Create again. A second build request is
        // the controller's existing behaviour; the feed must still hold exactly one subscriber.
        f.state.openSearch()
        f.state.controller.acceptIndexing()
        assertEquals(1, f.jobs.subscribers, "re-accepting must reuse the live subscription")
        assertEquals(1, f.jobs.subscribeCalls, "and never subscribe a second time")

        f.state.closeSearchIfOpen(); f.state.closeSearchIfOpen()
        assertEquals(0, f.jobs.subscribers, "leaving search drops the feed")
    }

    // ---- Review focus: result selection ----------------------------------------------------------

    @Test
    fun resultSelectionOpensInTheActiveWindow() {
        val f = SearchFixture()
        val esv = fakeSearchBook("ESV")
        f.platform.documents["ESV"] = esv
        f.state.openSearch()
        f.state.controller.queries.setQuery("light")
        f.state.controller.submit()
        assertTrue(f.state.controller.sheetVisible.value, "sanity: results sheet is up")

        f.state.onSearchResultSelected("Genesis 1:3", "ESV")

        assertEquals(1, f.platform.shown.size, "navigates the active window exactly once")
        assertEquals(esv, f.platform.shown.single().first)
        assertEquals("Genesis 1:3", f.platform.shown.single().second.toString())
        assertFalse(f.state.controller.sheetVisible.value, "the sheet drops so the verse is readable")
        assertTrue(f.state.controller.reopenResultsOnBack(), "and back brings the results list again")
    }

    @Test
    fun resultSelectionForAnUnresolvableTranslationNavigatesNowhere() {
        val f = SearchFixture()
        f.state.openSearch()

        f.state.onSearchResultSelected("Genesis 1:3", "GONE")

        assertTrue(f.platform.shown.isEmpty())
    }
}
