package net.bible.sharedcore.search

import net.bible.sharedcore.reading.SearchFieldImeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingSearchControllerTest {

    private class Recorder {
        var unavailable = 0
        var leftFullScreen = 0
        val indexingStarted = mutableListOf<String>()
        val searchesRun = mutableListOf<Triple<String, String, Boolean>>()
    }

    private fun controller(
        doc: SearchDocumentInfo? = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, true),
        r: Recorder = Recorder(),
    ) = r to ReadingSearchController(
        resolveDoc = { doc },
        onUnavailable = { r.unavailable++ },
        onLeaveFullScreen = { r.leftFullScreen++ },
        onStartIndexing = { r.indexingStarted.add(it) },
        onRunSearch = { id, q, epub -> r.searchesRun.add(Triple(id, q, epub)) },
    )

    @Test
    fun openOnAnIndexedBibleEntersTheFormAndLeavesFullScreen() {
        val (r, c) = controller()
        c.open()
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertTrue(c.searchModeActive.value)
        assertFalse(c.sheetVisible.value)
        assertEquals(1, r.leftFullScreen)
    }

    @Test
    fun openWithASeedQueryRunsTheSearchImmediately() {
        // Entry points 7-8 (text selection, Strong's find-all) arrive with a ready query.
        val (r, c) = controller()
        c.open(seedQuery = "light")
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertTrue(c.sheetVisible.value)
        assertEquals(listOf(Triple("KJV", "light", false)), r.searchesRun)
        assertEquals("light", c.queries.query.value)
    }

    @Test
    fun openOnADictionaryReportsUnavailableAndDoesNotEnterSearchMode() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("D", SearchDocumentCategory.DICTIONARY, false, true),
        )
        c.open()
        assertEquals(ReadingSearchPhase.Closed, c.phase.value)
        assertFalse(c.searchModeActive.value)
        assertFalse(c.sheetVisible.value)
        assertEquals(1, r.unavailable)
    }

    @Test
    fun openOnAnUnindexedDocumentShowsTheIndexPromptInTheSheet() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), c.phase.value)
        assertTrue(c.sheetVisible.value)
    }

    @Test
    fun acceptingIndexingStartsItAndMovesToIndexing() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), c.phase.value)
        assertEquals(listOf("KJV"), r.indexingStarted)
    }

    @Test
    fun indexingFinishingSuccessfullyRunsTheSearchAutomatically() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open(seedQuery = "light")
        c.acceptIndexing()
        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals(listOf(Triple("KJV", "light", false)), r.searchesRun)
    }

    @Test
    fun indexingFinishingWithoutAnIndexFallsBackToTheIndexPrompt() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        c.onIndexingFinished(indexDone = false)
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), c.phase.value)
        assertEquals(emptyList(), r.searchesRun)
    }

    @Test
    fun indexingFinishingWithNoQueryYetReturnsToTheForm() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertEquals(emptyList(), r.searchesRun)
    }

    @Test
    fun submitRunsTheSearchRecordsTheTermAndShowsResults() {
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals(listOf(Triple("KJV", "light", false)), r.searchesRun)
        assertEquals(listOf("light"), c.queries.recentTerms.value)
    }

    @Test
    fun submitWithABlankQueryDoesNothing() {
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("  ")
        c.submit()
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertEquals(emptyList(), r.searchesRun)
    }

    @Test
    fun closingSettingsWithAQueryRerunsTheSearch() {
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.settingsClosed()
        assertEquals(2, r.searchesRun.size)
    }

    @Test
    fun closingSettingsWithoutAQueryDoesNotRunASearch() {
        val (r, c) = controller()
        c.open()
        c.settingsClosed()
        assertEquals(emptyList(), r.searchesRun)
    }

    @Test
    fun backClosesTheSheetFirstThenSearchModeAndOnlyThenGivesUpTheBackPress() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()

        assertTrue(c.closeSheet(), "first back must consume the press by closing the sheet")
        assertFalse(c.sheetVisible.value)
        assertTrue(c.searchModeActive.value, "the query stays in the toolbar after the sheet closes")

        assertTrue(c.closeSearchMode(), "second back must consume the press by leaving search mode")
        assertFalse(c.searchModeActive.value)
        assertEquals(ReadingSearchPhase.Closed, c.phase.value)

        assertFalse(c.closeSheet(), "a third back must not be consumed")
        assertFalse(c.closeSearchMode(), "a third back must not be consumed")
    }

    @Test
    fun reopeningAfterClosingTheSheetKeepsTheQueryAndReturnsToResults() {
        // The toolbar search button is how the user gets results back; the query must survive.
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.closeSheet()
        c.open()
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals("light", c.queries.query.value)
        assertEquals(1, r.searchesRun.size, "reopening must serve the existing results, not re-run")
    }

    // ---- F6-C6 / spec D7: leaving search mode resets the session ---------------------------------

    @Test
    fun leavingSearchModeClearsTheQueryButNotTheRecentTerms() {
        val (_, c) = controller()
        c.open(seedQuery = "light")
        c.submit()
        assertEquals(listOf("light"), c.queries.recentTerms.value)
        c.closeSheet()
        c.closeSearchMode()
        assertEquals("", c.queries.query.value)
        assertEquals(listOf("light"), c.queries.recentTerms.value)
    }

    @Test
    fun reopeningAfterLeavingStartsAtTheFormWithTheFieldFocused() {
        val (r, c) = controller()
        c.open(seedQuery = "light")
        c.closeSheet(); c.closeSearchMode()
        r.searchesRun.clear()
        c.open()
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertFalse(c.sheetVisible.value)
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
        assertEquals(emptyList<Triple<String, String, Boolean>>(), r.searchesRun)
    }

    // ---- F6 Task 11: promptIndexFor (index prompt for a translation other than the active one) ---

    /**
     * The results document selector can choose a translation that is not the one being read.
     * `promptIndexFor` must raise `NeedsIndex` for THAT translation, not re-derive it from
     * `resolveDoc()` (which would just report the active window's document again).
     */
    @Test
    fun aChosenTranslationWithoutAnIndexRaisesThePromptForThatTranslation() {
        val (_, c) = controller() // active document is the indexed "KJV"
        c.open()
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value, "sanity")

        val consumed = c.promptIndexFor("ESV")

        assertTrue(consumed)
        assertEquals(ReadingSearchPhase.NeedsIndex("ESV", forEpub = false), c.phase.value)
        assertTrue(c.sheetVisible.value)
    }

    @Test
    fun acceptingThatPromptIndexesTheChosenTranslationNotTheActiveDocument() {
        val (r, c) = controller() // active document is "KJV"
        c.open()
        c.promptIndexFor("ESV")

        c.acceptIndexing()

        assertEquals(ReadingSearchPhase.Indexing("ESV", forEpub = false), c.phase.value)
        assertEquals(listOf("ESV"), r.indexingStarted, "must index the CHOSEN translation, not KJV")
    }

    @Test
    fun theSearchRerunsAgainstTheChosenTranslationOnceItsIndexIsBuilt() {
        val (r, c) = controller()
        c.open(seedQuery = "light") // runs immediately against the indexed active document "KJV"
        c.promptIndexFor("ESV")
        c.acceptIndexing()

        c.onIndexingFinished(indexDone = true)

        assertEquals(ReadingSearchPhase.Results("ESV", forEpub = false), c.phase.value)
        assertEquals(
            listOf(Triple("KJV", "light", false), Triple("ESV", "light", false)),
            r.searchesRun,
            "the waiting query must re-run against the newly-indexed ESV",
        )
    }

    @Test
    fun aFailedBuildFallsBackToThePromptForTheSameTranslation() {
        val (r, c) = controller()
        c.open()
        c.promptIndexFor("ESV")
        c.acceptIndexing()

        c.onIndexingFinished(indexDone = false)

        assertEquals(ReadingSearchPhase.NeedsIndex("ESV", forEpub = false), c.phase.value)
        assertEquals(emptyList(), r.searchesRun)
    }

    @Test
    fun promptIndexForIsIgnoredOutsideASearchSession() {
        val (_, c) = controller()
        // No `open()` — the session is Closed.

        val consumed = c.promptIndexFor("ESV")

        assertFalse(consumed)
        assertEquals(ReadingSearchPhase.Closed, c.phase.value)
        assertFalse(c.sheetVisible.value)
    }

    @Test
    fun reopeningAfterTheQueryChangedRunsTheNewSearchRatherThanServingStaleResults() {
        // Search mode stays active once the sheet is closed, and the entry points that do not go through
        // the toolbar field — Ctrl+F, the device SEARCH key, the drawer — can call open() again after the
        // query has been edited. Tracking only "results exist" would serve the previous query's results
        // under the new query.
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.closeSheet()

        c.queries.setQuery("water")
        c.open()

        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals(
            listOf(Triple("KJV", "light", false), Triple("KJV", "water", false)),
            r.searchesRun,
            "the edited query must be searched, not the one the stale results belong to",
        )
    }

    // ---- F6-B2 / F6-B3: the field's focus and the software keyboard ----

    // B3. Entering search mode with nothing typed is the "user is expected to type" case.
    @Test
    fun openingTheFormRequestsFieldFocus() {
        val (_, c) = controller()
        c.open()
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
    }

    // B3's counter-case, and the reason this is state rather than a line in the composable: the
    // seeded entry points (text-selection "Search …", Strong's find-all) run the search at once, so
    // focusing the field would pop a keyboard over results the user never asked to type into.
    @Test
    fun openingWithASeedQueryReleasesTheFieldInsteadOfFocusingIt() {
        val (_, c) = controller()
        c.open(seedQuery = "light")
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // B2, the reported symptom.
    @Test
    fun submittingReleasesTheField() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.imeRequestHandled()
        c.submit()
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // B2's hard case, and the second reason this is an acknowledged request rather than a flag
    // derived from `phase`: a resubmit from an open results sheet is Results -> Results, so a
    // phase-derived StateFlow would not even emit and the keyboard would stay up.
    @Test
    fun aSecondSubmitFromTheResultsPhaseRequestsReleaseAgain() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        c.imeRequestHandled()
        assertNull(c.imeRequest.value)
        c.queries.setQuery("water")
        c.submit()
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // A blank query never reaches Lucene, so it is not a submit and must not disturb the field.
    @Test
    fun submittingABlankQueryLeavesTheRequestAlone() {
        val (_, c) = controller()
        c.open()
        c.imeRequestHandled()
        c.queries.setQuery("   ")
        c.submit()
        assertNull(c.imeRequest.value)
    }

    // The index prompt is a sheet with buttons; there is nothing to type.
    @Test
    fun theIndexPromptReleasesTheField() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // F6-C6 / spec D7: closeSearchMode() now clears the query (see
    // leavingSearchModeClearsTheQueryButNotTheRecentTerms above), so reopening after a full close no
    // longer serves the old results — it starts fresh at the form and focuses the field. This test
    // used to assert the opposite (Release, because the query used to survive the close); that was
    // the OLD, now-fixed behaviour — updated rather than deleted since it still pins something real:
    // a full close-then-reopen must NOT come back showing stale results.
    @Test
    fun reopeningAfterAFullCloseStartsFreshAtTheFormRatherThanServingStaleResults() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.closeSearchMode()
        c.open()
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
    }

    // Closing the sheet deliberately changes nothing: it hides the results without changing the
    // phase, and popping a keyboard at someone who just closed the results to READ would be hostile.
    @Test
    fun closingTheSheetDoesNotTouchTheRequest() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.imeRequestHandled()
        c.closeSheet()
        assertNull(c.imeRequest.value)
    }

    // The settings sheet re-runs the search on close; that is a search, not an invitation to type.
    @Test
    fun closingTheSettingsSheetReRunsAndReleasesTheField() {
        val (_, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.imeRequestHandled()
        c.settingsClosed()
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // Auto-running after indexing finishes produces results, so the field is released.
    @Test
    fun theAutoRunAfterIndexingReleasesTheField() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open(seedQuery = "light")
        c.acceptIndexing()
        c.imeRequestHandled()
        c.onIndexingFinished(indexDone = true)
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // Final review I1: `onIndexingFinished` is the only IME producer not driven by a gesture on the search
    // UI. Search mode outlives the sheet, so with the sheet closed the user may be typing a note in a
    // WebView editor — grabbing focus there would land their keystrokes in the query.
    @Test
    fun indexingFinishingAfterTheSheetWasClosedDoesNotTouchTheIme() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        c.closeSheet()
        c.imeRequestHandled()
        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertNull(c.imeRequest.value)
    }

    // The milder variant of the same defect: with a query pending the auto-run would have requested
    // `Release`, hiding the note editor's keyboard mid-sentence.
    @Test
    fun theAutoRunAfterIndexingDoesNotTouchTheImeWhenTheSheetWasClosed() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open(seedQuery = "light")
        c.acceptIndexing()
        c.closeSheet()
        c.imeRequestHandled()
        c.onIndexingFinished(indexDone = true)
        // The search still runs and the sheet still rises — only the IME is left alone.
        assertEquals(listOf(Triple("KJV", "light", false)), r.searchesRun)
        assertTrue(c.sheetVisible.value)
        assertNull(c.imeRequest.value)
    }

    // The gate must not disable the feature: with the sheet still up, the form still takes focus.
    @Test
    fun indexingFinishingWhileTheSheetIsStillUpStillFocusesTheForm() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        c.imeRequestHandled()
        c.onIndexingFinished(indexDone = true)
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
    }

    // Redirecting to another translation's index prompt is a prompt, not a form.
    @Test
    fun promptingForAnotherDocumentsIndexReleasesTheField() {
        val (_, c) = controller()
        c.open()
        c.imeRequestHandled()
        c.promptIndexFor("ESV")
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // The acknowledgement is what makes the NEXT edge fire; without it a repeated request is a
    // no-op state write and the composable's LaunchedEffect never relaunches.
    @Test
    fun theAcknowledgementClearsTheRequest() {
        val (_, c) = controller()
        c.open()
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
        c.imeRequestHandled()
        assertNull(c.imeRequest.value)
    }

    // No stale request may survive into the next session.
    @Test
    fun leavingSearchModeClearsTheRequest() {
        val (_, c) = controller()
        c.open()
        c.closeSearchMode()
        assertNull(c.imeRequest.value)
    }

    // Reaches `enterFormOrResults`' cache-hit branch specifically: search mode is still active and
    // `resultsForQuery` still holds this query, so `open()` serves the existing results rather than
    // re-running. `reopeningOnAnAlreadyServedQueryReleasesTheField` cannot reach it —
    // `closeSearchMode()` resets `resultsForQuery`, so it falls through `runSearch` instead.
    @Test
    fun reopeningWithoutLeavingSearchModeServesTheCacheAndReleasesTheField() {
        val (r, c) = controller()
        c.open()
        c.queries.setQuery("light")
        c.submit()
        c.imeRequestHandled()
        c.open()
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
        // The point of the branch: it serves what it has instead of searching again.
        assertEquals(1, r.searchesRun.size)
    }

    // The Critical this round fixed: indexing finishing with no query yet hands over to the FORM, which
    // is the one phase that must focus the field. Before the fix this branch requested nothing, so the
    // `Release` from the index prompt survived into it and the field stayed unfocused — the reported bug,
    // alive in the indexing path.
    @Test
    fun indexingFinishingWithNoQueryFocusesTheFormsField() {
        val (_, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        c.imeRequestHandled()
        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Form("KJV", forEpub = false), c.phase.value)
        assertEquals(SearchFieldImeRequest.Focus, c.imeRequest.value)
    }

    // ---- F6-B5: rebuild index, reachable from the toolbar overflow ----

    // Reuses the existing NeedsIndex pipeline wholesale: the sheet's SearchIndexPanel already renders
    // the rebuild wording off its isRebuild flag, which the host computes from hasIndex(docId) and is
    // therefore true for a working index. No new Activity, no new panel, no new state.
    @Test
    fun requestingARebuildPromptsForTheSessionsOwnDocument() {
        val (_, c) = controller()
        c.open()
        assertTrue(c.requestRebuildIndex())
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), c.phase.value)
        assertTrue(c.sheetVisible.value)
        assertEquals(SearchFieldImeRequest.Release, c.imeRequest.value)
    }

    // It addresses the phase's document, which after a results-selector choice is the CHOSEN
    // translation rather than the one being read — the same rule promptIndexFor already documents.
    @Test
    fun requestingARebuildAfterASelectorChoiceTargetsTheChosenDocument() {
        val (_, c) = controller()
        c.open()
        c.promptIndexFor("ESV")
        assertTrue(c.requestRebuildIndex())
        assertEquals(ReadingSearchPhase.NeedsIndex("ESV", forEpub = false), c.phase.value)
    }

    // Nothing to rebuild when there is no session; must not invent a phase.
    @Test
    fun requestingARebuildWithNoSessionDoesNothing() {
        val (_, c) = controller()
        assertFalse(c.requestRebuildIndex())
        assertEquals(ReadingSearchPhase.Closed, c.phase.value)
    }

    // The Important this round fixed, part one: the sheet is non-modal, so the overflow stays tappable
    // during a build. A rebuild request must not restart or re-prompt — it shows the build already running.
    @Test
    fun requestingARebuildWhileIndexingShowsTheRunningBuildInsteadOfRestartingIt() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open()
        c.acceptIndexing()
        assertEquals(listOf("KJV"), r.indexingStarted)
        c.closeSheet()
        assertTrue(c.requestRebuildIndex())
        assertEquals(ReadingSearchPhase.Indexing("KJV", forEpub = false), c.phase.value)
        assertTrue(c.sheetVisible.value)
        // No second build was started.
        assertEquals(listOf("KJV"), r.indexingStarted)
    }

    // Part two, and the one that pins the real failure mode: overwriting the phase mid-build orphaned
    // `onIndexingFinished`'s `as? Indexing` guard, so the completion of the build that WAS running got
    // swallowed. This asserts the completion still lands.
    @Test
    fun aRebuildRequestMidBuildDoesNotSwallowThatBuildsCompletion() {
        val (r, c) = controller(
            doc = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, indexDone = false),
        )
        c.open(seedQuery = "light")
        c.acceptIndexing()
        c.requestRebuildIndex()
        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), c.phase.value)
        assertEquals(listOf(Triple("KJV", "light", false)), r.searchesRun)
    }

    // ---- F43: EPUB reading-search phase paths (forEpub = true) — never exercised until now,
    // since every test above uses a Bible document and the EPUB branch (searchKindFor's
    // SearchKind.Epub, ReadingSearchController.open's `is SearchKind.Epub -> ...`) is not yet
    // routed to from anywhere in the app. This is coverage for a branch about to go live, added
    // ahead of that switch so a pre-existing defect surfaces now rather than after it ships. ----

    @Test
    fun anIndexedEpubEntersTheFormWithTheEpubFlagSet() {
        val doc = SearchDocumentInfo("TestEpub", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = true)
        val (_, c) = controller(doc = doc)
        c.open()
        assertEquals(ReadingSearchPhase.Form("TestEpub", forEpub = true), c.phase.value)
    }

    @Test
    fun anEpubQueryRunsWithTheEpubFlagSet() {
        val doc = SearchDocumentInfo("TestEpub", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = true)
        val (r, c) = controller(doc = doc)
        c.open("grace")
        assertEquals(listOf(Triple("TestEpub", "grace", true)), r.searchesRun)
        assertEquals(ReadingSearchPhase.Results("TestEpub", forEpub = true), c.phase.value)
    }

    @Test
    fun anUnindexedEpubOffersIndexingWithTheEpubFlagSet() {
        val doc = SearchDocumentInfo("TestEpub", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)
        val (_, c) = controller(doc = doc)
        c.open()
        assertEquals(ReadingSearchPhase.NeedsIndex("TestEpub", forEpub = true), c.phase.value)
    }

    @Test
    fun anEpubIndexBuildCarriesTheFlagThroughIndexingAndCompletion() {
        val doc = SearchDocumentInfo("TestEpub", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)
        val (r, c) = controller(doc = doc)
        c.open()

        c.acceptIndexing()
        assertEquals(ReadingSearchPhase.Indexing("TestEpub", forEpub = true), c.phase.value)
        assertEquals(listOf("TestEpub"), r.indexingStarted)

        c.onIndexingFinished(indexDone = true)
        assertEquals(ReadingSearchPhase.Form("TestEpub", forEpub = true), c.phase.value)
    }
}
