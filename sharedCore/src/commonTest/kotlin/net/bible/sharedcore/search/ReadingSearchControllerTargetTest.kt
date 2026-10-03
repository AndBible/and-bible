package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingSearchControllerTargetTest {
    private fun bible(id: String) = SearchDocumentInfo(id, SearchDocumentCategory.BIBLE, isEpub = false, indexDone = true)
    private fun epub(id: String) = SearchDocumentInfo(id, SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = true)
    private fun unindexedBible(id: String) = SearchDocumentInfo(id, SearchDocumentCategory.BIBLE, isEpub = false, indexDone = false)

    private class Harness {
        var doc: SearchDocumentInfo? = null
        val runs = mutableListOf<Triple<String, String, Boolean>>()
        val controller = ReadingSearchController(
            resolveDoc = { doc },
            onUnavailable = {},
            onLeaveFullScreen = {},
            onStartIndexing = {},
            onRunSearch = { docId, query, forEpub -> runs.add(Triple(docId, query, forEpub)) },
        )
    }

    @Test
    fun submitAfterSwitchingToAnEpubWindowRunsTheEpubSearch() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.open()
        h.doc = epub("Epub-book")            // the user taps the EPUB pane
        h.controller.queries.setQuery("grace")
        h.controller.submit()
        assertEquals(listOf(Triple("Epub-book", "grace", true)), h.runs)
    }

    @Test
    fun submitAfterSwitchingToABibleWindowRunsTheBibleSearch() {
        val h = Harness()
        h.doc = epub("Epub-book")
        h.controller.open()
        h.doc = bible("KJV")
        h.controller.queries.setQuery("grace")
        h.controller.submit()
        assertEquals(listOf(Triple("KJV", "grace", false)), h.runs)
    }

    @Test
    fun resultsKeepTheDocumentTheSearchRanFor() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.queries.setQuery("grace")
        h.controller.open()
        h.doc = epub("Epub-book")            // switching windows must not retarget existing results
        h.controller.activeDocumentChanged()
        assertEquals(ReadingSearchPhase.Results("KJV", false), h.controller.phase.value)
    }

    @Test
    fun theFormFollowsTheActiveWindow() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.open()
        assertEquals(ReadingSearchPhase.Form("KJV", false), h.controller.phase.value)
        h.doc = epub("Epub-book")
        h.controller.activeDocumentChanged()
        assertEquals(ReadingSearchPhase.Form("Epub-book", true), h.controller.phase.value)
    }

    @Test
    fun anIndexBuildInFlightKeepsItsOwnDocument() {
        val h = Harness()
        h.doc = SearchDocumentInfo("Epub-book", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)
        h.controller.open()
        h.controller.acceptIndexing()
        h.doc = bible("KJV")
        h.controller.activeDocumentChanged()
        assertEquals(ReadingSearchPhase.Indexing("Epub-book", true), h.controller.phase.value)
    }

    /**
     * F44 fix round I1 — the ONE place a `Results` phase is created without running a search. Since
     * B3 the target is re-resolved on every entry, so the same query can be re-entered against a
     * different document: search a Bible, press back once (the sheet closes, search mode stays
     * active), tap the EPUB pane, then trigger the same query again (Strong's find-all, Ctrl+F or
     * the hardware SEARCH key all call [ReadingSearchController.open]). Keyed on the query alone, the
     * cache branch produced `Results(epubDocId, forEpub = true)` with NO search run — the sheet then
     * rendered the EPUB branch over the EPUB controller's empty/stale rows while the header counted
     * the Bible's hits.
     */
    @Test
    fun reEnteringTheSameQueryOnAnotherDocumentRunsTheSearchAgain() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.queries.setQuery("grace")
        h.controller.open()
        assertEquals(listOf(Triple("KJV", "grace", false)), h.runs)
        h.controller.closeSheet()            // one back press: sheet closed, search mode still active
        h.doc = epub("Epub-book")            // the user taps the EPUB pane
        h.controller.open()                  // the same query, entered again
        assertEquals(ReadingSearchPhase.Results("Epub-book", true), h.controller.phase.value)
        assertEquals(
            listOf(Triple("KJV", "grace", false), Triple("Epub-book", "grace", true)),
            h.runs,
            "an EPUB results phase must be backed by an EPUB search that actually ran",
        )
    }

    /** The other half of the same rule: re-entry on the SAME document still serves the cached rows. */
    @Test
    fun reEnteringTheSameQueryOnTheSameDocumentDoesNotReRunTheSearch() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.queries.setQuery("grace")
        h.controller.open()
        h.controller.closeSheet()
        h.controller.open()
        assertEquals(ReadingSearchPhase.Results("KJV", false), h.controller.phase.value)
        assertEquals(1, h.runs.size, "reopening after a back press must not re-run the search")
    }

    /**
     * F44 fix round M1 — `indexPromptIsExplicit` was set by `promptIndexFor` and cleared only by
     * `open`/`closeSearchMode`, so after ONE use of the results document selector no index prompt in
     * that session ever followed the active window again.
     */
    @Test
    fun anImplicitIndexPromptClearsTheExplicitFlagLeftByTheResultsSelector() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.open()
        assertTrue(h.controller.promptIndexFor("ESV"))   // the selector's explicit choice
        h.doc = unindexedBible("NASB")                   // the active window now shows an unindexed Bible
        h.controller.queries.setQuery("grace")
        h.controller.submit()                            // an IMPLICIT prompt for the active document
        assertEquals(ReadingSearchPhase.NeedsIndex("NASB", false), h.controller.phase.value)
        h.doc = unindexedBible("NIV")
        h.controller.activeDocumentChanged()
        assertEquals(
            ReadingSearchPhase.NeedsIndex("NIV", false), h.controller.phase.value,
            "an implicit prompt must keep following the active window",
        )
    }

    /**
     * F44 fix round M3 — spec §9: a build in flight is governed by its RECORDED document. `submit()`
     * used to overwrite the `Indexing` phase with results for another document, after which
     * `onIndexingFinished`'s `as? Indexing` guard early-returned and the completed build's automatic
     * search was silently dropped.
     */
    @Test
    fun submitDuringAnIndexBuildLeavesTheBuildInFlight() {
        val h = Harness()
        h.doc = SearchDocumentInfo("Epub-book", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)
        h.controller.open()
        h.controller.acceptIndexing()
        h.doc = bible("KJV")                             // the user taps a Bible pane while it builds
        h.controller.queries.setQuery("grace")
        h.controller.submit()
        assertEquals(ReadingSearchPhase.Indexing("Epub-book", true), h.controller.phase.value)
        assertTrue(h.controller.sheetVisible.value, "the progress panel is raised rather than replaced")
        assertTrue(h.runs.isEmpty(), "nothing may be searched while the build runs")
        h.controller.onIndexingFinished(indexDone = true)
        assertEquals(
            listOf(Triple("Epub-book", "grace", true)), h.runs,
            "the completed build's automatic search must not be dropped",
        )
    }

    @Test
    fun settingsClosedDuringAnIndexBuildLeavesTheBuildInFlight() {
        val h = Harness()
        h.doc = SearchDocumentInfo("Epub-book", SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = false)
        h.controller.open()
        h.controller.acceptIndexing()
        h.doc = bible("KJV")
        h.controller.queries.setQuery("grace")
        h.controller.settingsClosed()
        assertEquals(ReadingSearchPhase.Indexing("Epub-book", true), h.controller.phase.value)
        assertTrue(h.runs.isEmpty())
    }

    @Test
    fun anIndexPromptChosenInTheResultsSelectorIsNotRetargeted() {
        val h = Harness()
        h.doc = bible("KJV")
        h.controller.open()
        assertTrue(h.controller.promptIndexFor("ESV"))
        h.doc = epub("Epub-book")
        h.controller.activeDocumentChanged()
        assertEquals(ReadingSearchPhase.NeedsIndex("ESV", false), h.controller.phase.value)
    }
}
