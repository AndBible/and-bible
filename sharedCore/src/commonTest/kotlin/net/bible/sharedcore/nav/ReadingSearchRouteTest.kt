package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType

class ReadingSearchRouteTest {
    @Test fun roundTripsEverySearchFieldAndReservedCharacters() {
        val request = ReadingSearchRequest(
            query = "Jésus +?#%/&=,",
            documentId = "Doc/ä",
            translationIds = listOf("KJV", "a,b", "עברית"),
            strongsOrigin = true,
            preDecorated = true,
            section = SearchBibleSection.OLD_TESTAMENT,
            type = SearchType.PHRASE,
            epubMode = EpubSearchMode.FTS,
        )
        assertEquals(request, NavRoutes.readReadingSearch(NavRoutes.readingSearch(request)))
    }

    @Test fun preservesEmptyTranslationIdsAndNavigationDecodedArgument() {
        val request = ReadingSearchRequest(translationIds = listOf("", "a,b", ""), query = "%2F")
        val route = NavRoutes.readingSearch(request)
        val argument = NavRoutes.decodeArg(route.substringAfter("request="))
        assertEquals(request, NavRoutes.decodeReadingSearchArgument(argument))
        assertEquals(request, NavRoutes.readReadingSearch(route))
    }

    @Test fun distinguishesAbsentAndEmptyQuery() {
        for (query in listOf(null, "")) {
            val request = ReadingSearchRequest(query = query)
            assertEquals(request, NavRoutes.readReadingSearch(NavRoutes.readingSearch(request)))
        }
    }
}
