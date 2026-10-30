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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import org.crosswire.jsword.index.IndexStatus

/**
 * The reading view's search host logic (moved out of `:app`'s `ReadingSearchHostTest` /
 * `ReadingSearchEntryPointsTest`, same assertions, now over fakes instead of a Robolectric host).
 */
class ReadingSearchHostLogicTest {
    private val unindexedBible =
        SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, isEpub = false, indexDone = false)

    @Test
    fun realJobFeedShowsOnlyRunningIndexJobsAndRefreshesTheSheetOnWorkEvents() {
        val feed = net.bible.sharedui.host.reading.HostSearchIndexJobs()
        val download = org.crosswire.common.progress.JobManager.createJob("Downloading files")
        val index = org.crosswire.common.progress.JobManager.createJob("CREATE_INDEX-KJV", "Indexing KJV", null)
        val finished = org.crosswire.common.progress.JobManager.createJob("CREATE_INDEX-OLD", "Old index", null)
        val epub = org.crosswire.common.progress.JobManager.createJob("${net.bible.service.sword.epub.EPUB_INDEX_JOB_PREFIX}/tmp/epub", "Indexing EPUB", null)
        val f = SearchFixture(indexJobs = feed)
        try {
            download.beginJob("Download")
            finished.isNotifyUser = false
            finished.done()
            index.beginJob("Indexing")
            index.work = 12
            f.platform.info = unindexedBible
            f.platform.current!!.indexStatus = IndexStatus.CREATING
            f.platform.documents["KJV"] = f.platform.current!!
            f.state.openSearch()
            assertEquals(listOf("Indexing KJV", "Indexing EPUB"), f.state.searchIndexProgress.jobs.value.map { it.label })
            assertEquals(12, f.state.searchIndexProgress.jobs.value.first().percent)

            index.work = 64
            assertEquals(64, f.state.searchIndexProgress.jobs.value.first().percent)
            epub.done()
            // Completion listeners run before JobManager removes a job: the feed must filter isFinished too.
            index.done()
            assertTrue(f.state.searchIndexProgress.jobs.value.isEmpty())
            assertTrue(feed.isEveryJobFinished(), "a download must not count as an active index build")
        } finally {
            f.state.dispose()
            listOf(download, index, finished, epub).forEach { it.isNotifyUser = true; it.done() }
        }
    }

    @Test
    fun reopeningSearchKeepsTheRunningIndexAndItsWaitingQuery() {
        val f = SearchFixture()
        f.platform.info = unindexedBible
        f.platform.documents["KJV"] = f.platform.current!!
        f.state.openSearch("light")
        f.state.controller.acceptIndexing()
        f.state.controller.closeSheet()

        f.state.openSearch()

        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value)
        assertTrue(f.state.searchSheetVisible.value)
        assertEquals("light", f.state.queries.query.value)
        assertEquals(listOf("KJV"), f.index.created, "reopening must not start a second build")
        assertEquals(1, f.jobs.subscribeCalls, "the existing progress feed stays attached")
        f.state.dispose()
    }

    @Test
    fun reopeningAfterLeavingSearchReattachesToTheRunningIndexWithoutStartingAnotherBuild() {
        for (status in listOf(IndexStatus.SCHEDULED, IndexStatus.CREATING)) {
            val f = SearchFixture()
            f.platform.info = unindexedBible
            val book = f.platform.current!!
            f.platform.documents["KJV"] = book
            f.state.openSearch()
            f.state.controller.acceptIndexing()
            book.indexStatus = status
            f.jobs.running = true
            f.state.leaveSearch()
            assertEquals(0, f.jobs.subscribers)

            f.state.openSearch()

            assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value, "$status")
            assertTrue(f.state.searchSheetVisible.value)
            assertEquals(listOf("KJV"), f.index.created, "a $status build must not be restarted")
            assertEquals(1, f.jobs.subscribers, "progress must be observed again")
            f.platform.info = unindexedBible.copy(indexDone = true)
            book.indexStatus = IndexStatus.DONE
            f.jobs.running = false
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), f.state.controller.phase.value)
            assertEquals(0, f.jobs.subscribers)
            f.state.dispose()
        }
    }

    @Test
    fun aNewSearchHostAttachesToAnIndexAlreadyRunningInTheBackground() {
        val f = SearchFixture()
        f.platform.info = unindexedBible
        val book = f.platform.current!!
        book.indexStatus = IndexStatus.CREATING
        f.platform.documents["KJV"] = book
        f.jobs.running = true
        f.jobs.jobs = listOf(ProgressJob("index-kjv", "Indexing KJV", 42, false))

        f.state.openSearch()

        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value)
        assertTrue(f.state.searchSheetVisible.value)
        assertTrue(f.index.created.isEmpty(), "only observe the existing build")
        assertEquals(listOf(ProgressJob("index-kjv", "Indexing KJV", 42, false)), f.state.searchIndexProgress.jobs.value)
        f.jobs.jobs = listOf(ProgressJob("index-kjv", "Indexing KJV", 60, false))
        f.jobs.emit(Any(), finished = false)
        assertEquals(60, f.state.searchIndexProgress.jobs.value.single().percent)
        assertEquals(1, f.jobs.subscribers)
        f.state.dispose()
    }

    @Test
    fun aBuildFinishingBeforeSubscriptionStillResolvesWithoutAnotherEvent() {
        // Even an unrelated running job must not hide this document's completed index.
        for (otherJobRunning in listOf(false, true)) {
            val f = SearchFixture()
            val book = f.platform.current!!
            f.platform.info = unindexedBible
            f.platform.documents["KJV"] = book
            book.indexStatus = IndexStatus.CREATING
            f.jobs.running = true
            f.jobs.beforeSubscribe = {
                book.indexStatus = IndexStatus.DONE
                f.platform.info = unindexedBible.copy(indexDone = true)
                f.jobs.running = otherJobRunning
            }

            f.state.openSearch("light")

            assertTrue(f.state.controller.phase.value is ReadingSearchPhase.Results)
            assertNull(f.state.searchIndexProgress.error.value)
            assertEquals(0, f.jobs.subscribers)
            assertTrue(f.index.created.isEmpty())
            f.state.dispose()
        }
    }

    @Test
    fun aBuildFinishingBeforeSubscriptionWithNoQueryLeavesTheFormWithoutASheet() {
        val f = SearchFixture()
        val book = f.platform.current!!
        f.platform.info = unindexedBible
        f.platform.documents["KJV"] = book
        book.indexStatus = IndexStatus.CREATING
        f.jobs.beforeSubscribe = {
            book.indexStatus = IndexStatus.DONE
            f.platform.info = unindexedBible.copy(indexDone = true)
        }

        f.state.openSearch()

        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), f.state.controller.phase.value)
        assertFalse(f.state.searchSheetVisible.value)
        f.state.dispose()
    }

    @Test
    fun anEpubBuildStillRunningShowsProgressAndResolvesOnCompletion() {
        // The platform reports an EPUB whose build is scheduled/running as not indexed (its FTS table
        // exists before every row is written; `EpubBackendState.isIndexed` owns that rule).
        val f = SearchFixture()
        val book = f.platform.current!!
        val epub = unindexedBible.copy(isEpub = true, indexDone = false)
        f.platform.info = epub
        f.platform.documents["KJV"] = book
        book.indexStatus = IndexStatus.CREATING
        f.jobs.running = true

        f.state.openSearch("light")

        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = true), f.state.controller.phase.value)
        assertEquals(1, f.jobs.subscribers)
        book.indexStatus = IndexStatus.DONE
        f.platform.info = epub.copy(indexDone = true)
        f.jobs.running = false
        f.jobs.emit(Any(), finished = true)
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = true), f.state.controller.phase.value)
        f.state.dispose()
    }

    @Test
    fun aQueuedBuildKeepsWaitingBeyondTheFinishedForeignJobsPollBudget() = runTest {
        val f = SearchFixture(scope = backgroundScope, pollPause = { delay(2_000) })
        val book = f.platform.current!!
        f.platform.info = unindexedBible
        f.platform.documents["KJV"] = book
        book.indexStatus = IndexStatus.SCHEDULED
        f.jobs.running = true
        f.state.openSearch("light")
        f.jobs.running = false
        f.jobs.emit(Any(), finished = true)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value)
        assertNull(f.state.searchIndexProgress.error.value, "a queued index is not a failed index")
        assertEquals(1, f.jobs.subscribers)

        book.indexStatus = IndexStatus.CREATING
        f.jobs.running = true
        f.jobs.emit(Any(), finished = false)
        advanceTimeBy(20_000)
        runCurrent()
        assertNull(f.state.searchIndexProgress.error.value)
        book.indexStatus = IndexStatus.DONE
        f.platform.info = unindexedBible.copy(indexDone = true)
        f.jobs.running = false
        f.jobs.emit(Any(), finished = true)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(f.state.controller.phase.value is ReadingSearchPhase.Results)
        assertEquals(0, f.jobs.subscribers)
        assertTrue(f.index.created.isEmpty())
        f.state.dispose()
    }

    @Test
    fun aStuckRunningIndexStatusEventuallyGivesUpWithAnError() = runTest {
        // A build that crashed or was cancelled can leave SCHEDULED/CREATING behind with no job.
        val f = SearchFixture(scope = backgroundScope, pollPause = { delay(2_000) })
        val book = f.platform.current!!
        f.platform.info = unindexedBible
        f.platform.documents["KJV"] = book
        book.indexStatus = IndexStatus.CREATING
        f.jobs.running = false

        f.state.openSearch("light")
        runCurrent()
        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value)

        advanceTimeBy(10 * 60_000)
        runCurrent()

        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), f.state.controller.phase.value)
        assertNotNull(f.state.searchIndexProgress.error.value, "a stuck build is reported, not waited on forever")
        assertEquals(0, f.jobs.subscribers)
        assertTrue(f.index.created.isEmpty())
        f.state.dispose()
    }

    @Test
    fun attachingWhileAJobIsRunningWaitsForItsEventInsteadOfResolving() {
        val f = SearchFixture()
        val book = f.platform.current!!
        f.platform.info = unindexedBible
        f.platform.documents["KJV"] = book
        book.indexStatus = IndexStatus.CREATING
        f.jobs.running = true

        f.state.openSearch("light")

        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), f.state.controller.phase.value)
        assertNull(f.state.searchIndexProgress.error.value)
        assertEquals(1, f.jobs.subscribers)
        f.state.dispose()
    }

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
    fun aRunningStatusWithoutAnyActiveJobStopsWaitingAfterTheStallCap() = runTest {
        var pauses = 0
        val done = awaitIndexDone(
            poll = IndexPollDecision(maxStalledPolls = 5),
            indexDone = { false },
            pause = { pauses++ },
            indexRunning = { true },
            buildActive = { false },
        )
        assertFalse(done)
        assertEquals(4, pauses, "five stalled reads, four pauses between them")
    }

    @Test
    fun aRunningStatusWithAnActiveJobKeepsWaitingUntilTheIndexIsDone() = runTest {
        var pauses = 0
        val done = awaitIndexDone(
            poll = IndexPollDecision(maxPolls = 1, maxStalledPolls = 1),
            indexDone = { pauses >= 500 },
            pause = { pauses++ },
            indexRunning = { pauses < 500 },
            buildActive = { true },
        )
        assertTrue(done)
        assertEquals(500, pauses)
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

    @Test
    fun commentarySearchIgnoresSavedBiblesAndPreservesThemForTheNextBibleSearch() = runTest {
        val f = SearchFixture(scope = backgroundScope)
        f.platform.bibles = listOf("KJV" to "KJV", "ESV" to "ESV")
        f.settings.setString("search_selected_translations", "KJV,ESV")
        val commentary = fakeSearchBook("Kingcomments")
        f.platform.current = commentary
        f.platform.documents["Kingcomments"] = commentary
        f.platform.info = SearchDocumentInfo("Kingcomments", SearchDocumentCategory.COMMENTARY, false, true)

        f.state.openSearch()
        f.state.queries.setQuery("light")
        f.state.controller.submit()

        assertEquals(listOf("Kingcomments"), f.state.lastSearchRequestForTest?.translationIds)
        assertEquals(listOf("Kingcomments"), f.state.searchTranslations.value)
        assertEquals("KJV,ESV", f.settings.strings["search_selected_translations"])
        f.state.leaveSearch()
        f.platform.current = fakeSearchBook("KJV")
        f.platform.info = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, true)

        f.state.openSearch("light")

        assertEquals(listOf("KJV", "ESV"), f.state.lastSearchRequestForTest?.translationIds)
    }

    @Test
    fun commentarySearchDoesNotPromptToIndexSavedBibles() = runTest {
        val f = SearchFixture(bible = FakeBibleSearch(unindexed = setOf("ESV")), scope = backgroundScope)
        f.platform.bibles = listOf("ESV" to "ESV")
        f.settings.setString("search_selected_translations", "ESV")
        val commentary = fakeSearchBook("Kingcomments")
        f.platform.current = commentary
        f.platform.documents["Kingcomments"] = commentary
        f.platform.info = SearchDocumentInfo("Kingcomments", SearchDocumentCategory.COMMENTARY, false, true)

        f.state.openSearch("light")

        assertEquals(ReadingSearchPhase.Results("Kingcomments", forEpub = false), f.state.controller.phase.value)
        assertEquals(listOf("Kingcomments"), f.state.lastSearchRequestForTest?.translationIds)
        assertNull(f.state.searchSelectorPendingIds)
    }

    @Test
    fun unindexedCommentaryPromptsForItselfWithoutRestoringSavedBibles() = runTest {
        val f = SearchFixture(scope = backgroundScope)
        f.settings.setString("search_selected_translations", "KJV")
        f.platform.current = fakeSearchBook("Kingcomments")
        f.platform.info = SearchDocumentInfo("Kingcomments", SearchDocumentCategory.COMMENTARY, false, false)

        f.state.openSearch("light")

        assertEquals(ReadingSearchPhase.NeedsIndex("Kingcomments", forEpub = false), f.state.controller.phase.value)
        assertEquals(listOf("Kingcomments"), f.state.searchTranslations.value)
        assertEquals("KJV", f.settings.strings["search_selected_translations"])
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
