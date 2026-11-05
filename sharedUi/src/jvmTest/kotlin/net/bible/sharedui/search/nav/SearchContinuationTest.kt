package net.bible.sharedui.search.nav

import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.reading.host.slots.SearchFixture
import net.bible.sharedui.reading.host.slots.MapSearchSettings
import net.bible.sharedui.reading.host.slots.fakeSearchBook
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchContinuationTest {
    @Test
    fun formPreservesDecorationAndOriginalScopeInReadingHandoff() {
        val request = SearchRequest(
            query = "100% + love & peace", searchType = SearchType.PHRASE,
            bibleSection = SearchBibleSection.CURRENT_BOOK, currentBookName = "John",
            translationIds = listOf("KJV", "ESV"), isStrongsSearch = true,
        )
        val decorated = "+[John] \"100% + love & peace\""
        val route = NavRoutes.readingSearch(request.toReadingSearchRequest(decorated, "KJV"))
        assertEquals(
            ReadingSearchRequest(
                query = decorated, documentId = "KJV", translationIds = listOf("KJV", "ESV"),
                strongsOrigin = true, preDecorated = true,
                section = SearchBibleSection.CURRENT_BOOK, type = SearchType.PHRASE,
            ),
            NavRoutes.readReadingSearch(route),
        )
    }

    private fun assertFormSelectionSurvivesExecution(activeIndexed: Boolean) {
        val f = SearchFixture()
        val active = fakeSearchBook("KJV")
        f.platform.current = active
        f.platform.documents["KJV"] = active
        f.platform.documents["ESV"] = fakeSearchBook("ESV")
        f.platform.documentInfos["KJV"] = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, activeIndexed)
        f.platform.documentInfos["ESV"] = SearchDocumentInfo("ESV", SearchDocumentCategory.BIBLE, false, true)
        f.platform.bibles = listOf("KJV" to "KJV", "ESV" to "ESV")
        if (!activeIndexed) f.bible.unindexed = setOf("KJV")
        try {
            val form = SearchRequest("love", SearchType.PHRASE, SearchBibleSection.ALL, listOf("ESV"), "John")
            val route = NavRoutes.readingSearch(form.toReadingSearchRequest("\"love\"", "KJV"))
            f.state.openRequest(NavRoutes.readReadingSearch(route))
            val executed = assertNotNull(f.state.lastSearchRequestForTest, "ESV-only form must run without prompting to index active KJV")
            assertEquals(listOf("ESV"), executed.translationIds)
            assertEquals("\"love\"", executed.query)
            assertEquals(ReadingSearchPhase.Results("ESV", forEpub = false), f.state.controller.phase.value)
            assertEquals(active, f.platform.current, "handoff must not move the reading window")
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun formSelectionExcludesAnotherIndexedActiveBibleWhenExecuted() = assertFormSelectionSurvivesExecution(true)

    @Test
    fun formSelectionDoesNotPromptToIndexUnselectedActiveBible() = assertFormSelectionSurvivesExecution(false)

    @Test
    fun formTargetPreservesCommentarySelectionAndEmptySelectionFallback() {
        val commentary = SearchRequest("love", SearchType.ANY_WORDS, SearchBibleSection.ALL, listOf("Commentary"), "John")
        assertEquals("Commentary", commentary.toReadingSearchRequest("love", "KJV").documentId)
        assertEquals("KJV", commentary.copy(translationIds = emptyList()).toReadingSearchRequest("love", "KJV").documentId)
    }

    @Test
    fun emptyEpubIndexContinuationPreservesStoredPhraseMode() {
        val settings = MapSearchSettings(mutableMapOf("epubSearch-SearchType" to "PHRASE"))
        val f = SearchFixture(settings = settings)
        f.platform.documents["EPUB"] = fakeSearchBook("EPUB")
        f.platform.documentInfos["EPUB"] = SearchDocumentInfo("EPUB", SearchDocumentCategory.GENERAL_BOOK, true, true)
        try {
            val route = nextRouteAfterIndexing(SearchArgs(null, null, "EPUB", emptyList(), false), isEpub = true)
            f.state.openRequest(NavRoutes.readReadingSearch(route))
            assertEquals("PHRASE", settings.strings["epubSearch-SearchType"])
            assertEquals(EpubSearchMode.PHRASE, f.state.epubSearchMode.value)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun epubFormKeepsEveryModeAndExactQueryAndDocument() {
        for (mode in EpubSearchMode.entries) {
            val route = epubSearchContinuation("100% + \"love & peace\"", mode, "Book / 1")
            assertEquals(
                ReadingSearchRequest(query = "100% + \"love & peace\"", documentId = "Book / 1", epubMode = mode),
                NavRoutes.readReadingSearch(route),
            )
        }
    }

    @Test
    fun legacyIndexedQueryKeepsTranslationsAndStrongsWithoutRedecoration() {
        val args = SearchArgs("+[John] strong:G26 100%", "G26", "KJV", listOf("KJV", "ESV"), true)
        assertEquals(
            ReadingSearchRequest(
                query = args.searchText, documentId = "KJV", translationIds = listOf("KJV", "ESV"),
                strongsOrigin = true, preDecorated = true,
            ),
            NavRoutes.readReadingSearch(nextRouteAfterIndexing(args, isEpub = false)),
        )
    }

    @Test
    fun indexedContinuationPreservesEntireTypedPayloadIncludingEpubMode() {
        val request = ReadingSearchRequest(
            query = "100% + love", documentId = "Book / 1", translationIds = listOf("A,B", "C"),
            strongsOrigin = true, preDecorated = false, section = SearchBibleSection.OLD_TESTAMENT,
            type = SearchType.ANY_WORDS, epubMode = EpubSearchMode.PHRASE,
        )
        val args = SearchArgs(null, null, "Index target", emptyList(), false, request)
        // The index target may be a different unindexed translation; it must not replace the search target.
        assertEquals(request, NavRoutes.readReadingSearch(nextRouteAfterIndexing(args, isEpub = true)))
    }

    @Test
    fun indexingWithoutQueryOpensReadingFormForTheIndexedDocument() {
        for (isEpub in listOf(false, true)) {
            assertEquals(
                ReadingSearchRequest(documentId = "Indexed book"),
                NavRoutes.readReadingSearch(nextRouteAfterIndexing(
                    SearchArgs(null, null, "Indexed book", emptyList(), false), isEpub,
                )),
            )
        }
    }

    @Test
    fun legacyEpubIndexCompletionDoesNotDecorateTheQuery() {
        assertEquals(
            ReadingSearchRequest(query = "a & b 100%", documentId = "EPUB", epubMode = EpubSearchMode.FTS),
            NavRoutes.readReadingSearch(nextRouteAfterIndexing(
                SearchArgs("a & b 100%", null, "EPUB", emptyList(), false), isEpub = true,
            )),
        )
    }
}
