package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingSearchControllerTargetTest {
    private fun bible(id: String) = SearchDocumentInfo(id, SearchDocumentCategory.BIBLE, isEpub = false, indexDone = true)
    private fun epub(id: String) = SearchDocumentInfo(id, SearchDocumentCategory.GENERAL_BOOK, isEpub = true, indexDone = true)

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
