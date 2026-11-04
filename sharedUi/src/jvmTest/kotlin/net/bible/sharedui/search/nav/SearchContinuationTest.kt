package net.bible.sharedui.search.nav

import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
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
                ReadingSearchRequest(documentId = "Indexed book", epubMode = if (isEpub) EpubSearchMode.FTS else null),
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
