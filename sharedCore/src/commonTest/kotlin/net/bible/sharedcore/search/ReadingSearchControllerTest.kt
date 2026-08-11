package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
