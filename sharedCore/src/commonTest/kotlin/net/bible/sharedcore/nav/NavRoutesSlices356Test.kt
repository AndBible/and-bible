package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavRoutesSlices356Test {

    @Test
    fun dailyReadingOmitsAbsentArguments() {
        // The host branches on containsKey, not on null: "no plan argument at all" must stay
        // distinguishable from "plan present but empty", or a bare open calls setReadingPlan("").
        assertEquals("readingPlan/day", NavRoutes.dailyReading(plan = null, day = null))
        assertEquals("readingPlan/day?plan=NIV", NavRoutes.dailyReading(plan = "NIV", day = null))
        assertEquals("readingPlan/day?plan=NIV&day=3", NavRoutes.dailyReading(plan = "NIV", day = 3))
        assertEquals("readingPlan/day?day=3", NavRoutes.dailyReading(plan = null, day = 3))
    }

    @Test
    fun listArgumentsRoundTrip() {
        val ids = listOf("KJV", "ESV", "NIV")
        assertEquals(ids, NavRoutes.decodeList(NavRoutes.encodeList(ids)))
        assertEquals(emptyList(), NavRoutes.decodeList(NavRoutes.encodeList(emptyList())))
    }

    @Test
    fun listArgumentsDropBlanks() {
        assertEquals(listOf("KJV"), NavRoutes.decodeList(NavRoutes.encodeList(listOf("KJV", "", " "))))
    }

    @Test
    fun searchResultsCarriesEveryArgumentTheChainNeeds() {
        // D3: there is no bundle pass-through in route-world. Every argument the classic
        // SearchIndex -> SearchIndexProgress -> SearchResults chain forwarded must be nameable.
        val route = NavRoutes.searchResults(
            searchText = "strong:G26",
            highlightText = "G26",
            searchDocument = "KJV",
            selectedTranslations = listOf("KJV", "ESV"),
            isStrongsSearch = true,
        )
        assertTrue(route.startsWith("search/results?"))
        for (name in listOf(
            NavRoutes.ARG_SEARCH_TEXT, NavRoutes.ARG_SEARCH_HIGHLIGHT_TEXT,
            NavRoutes.ARG_SEARCH_DOCUMENT, NavRoutes.ARG_SELECTED_TRANSLATIONS,
            NavRoutes.ARG_IS_STRONGS_SEARCH,
        )) assertTrue(route.contains("$name="), "route is missing $name: $route")
    }

    @Test
    fun freeTextSearchArgumentsAreEncoded() {
        // A decorated query contains spaces and '+' — both must survive the round trip.
        val q = "+\"in the beginning\" -foo"
        val route = NavRoutes.searchResults(searchText = q, searchDocument = "KJV")
        assertTrue(!route.contains(" "), "route must not contain a raw space: $route")
        val encoded = route.substringAfter("${NavRoutes.ARG_SEARCH_TEXT}=").substringBefore("&")
        assertEquals(q, NavRoutes.decodeArg(encoded))
    }

    @Test
    fun everyPatternRegistersEveryArgumentItsBuilderCanEmit() {
        // A pattern that forgets an argument silently drops it at navigate time.
        fun argsIn(s: String) = Regex("[?&]([A-Za-z]+)=").findAll(s).map { it.groupValues[1] }.toSet()
        assertTrue(argsIn(NavRoutes.SEARCH_RESULTS_PATTERN).containsAll(
            argsIn(NavRoutes.searchResults("q", "h", "KJV", listOf("KJV"), true))))
        assertTrue(argsIn(NavRoutes.SEARCH_INDEX_PATTERN).containsAll(
            argsIn(NavRoutes.searchIndex("q", "h", "KJV", listOf("KJV"), true))))
        assertTrue(argsIn(NavRoutes.DAILY_READING_PATTERN).containsAll(
            argsIn(NavRoutes.dailyReading("NIV", 3))))
    }

    // ——— readDailyReading: the inverse of dailyReading (whole-branch review M5) ———————————————

    @Test
    fun readDailyReadingRoundTripsEveryArgumentCombination() {
        // The four shapes dailyReading() can emit, each read back as the pair that built it.
        assertEquals(null to null, NavRoutes.readDailyReading(NavRoutes.dailyReading()))
        assertEquals("NIV" to null, NavRoutes.readDailyReading(NavRoutes.dailyReading(plan = "NIV")))
        assertEquals(null to 3, NavRoutes.readDailyReading(NavRoutes.dailyReading(day = 3)))
        assertEquals("NIV" to 3, NavRoutes.readDailyReading(NavRoutes.dailyReading(plan = "NIV", day = 3)))
    }

    @Test
    fun readDailyReadingDecodesAPlanCodeContainingASpace() {
        // Plan codes are FILENAMES (ReadingPlanTextFileDao.userPlanCodes), so a space is ordinary.
        // Without the decodeArg step the caller would hand "My%20Plan" to setReadingPlan(), which
        // writes a non-existent plan code into the READING_PLAN preference and then throws.
        val route = NavRoutes.dailyReading(plan = "My Plan", day = 7)
        assertTrue(route.contains("plan=My%20Plan"), "the builder must percent-encode the space: $route")
        assertEquals("My Plan" to 7, NavRoutes.readDailyReading(route))
    }

    @Test
    fun readDailyReadingRoundTripsAPlanCodeContainingAPercent() {
        // The escape character itself: "100%" encodes to "100%25", and a parser that split on '%'
        // naively (or skipped decoding) would hand back "100%25" — or throw on the "25" it read as
        // a continuation. The whole point of decodeArg's two-hex-digit require is that this case
        // survives it.
        val route = NavRoutes.dailyReading(plan = "100% Plan", day = 1)
        assertTrue(route.contains("plan=100%25%20Plan"), "unexpected encoding: $route")
        assertEquals("100% Plan" to 1, NavRoutes.readDailyReading(route))
    }

    @Test
    fun readDailyReadingTreatsAnEmptyValueAsAbsent() {
        // Matches the navigation library, whose query-parameter regex is `(.+?)`: `plan=` does not
        // match, so the argument falls back to its null default. Returning "" instead would reach
        // setReadingPlan("") and wipe the preference.
        assertEquals(null to null, NavRoutes.readDailyReading("readingPlan/day?plan=&day="))
        assertEquals(null to 3, NavRoutes.readDailyReading("readingPlan/day?plan=&day=3"))
    }

    @Test
    fun readDailyReadingIgnoresMalformedAndUnknownQueryParts() {
        // A bare flag with no '=' is skipped rather than throwing; an argument this route does not
        // own is simply not read.
        assertEquals("NIV" to 3, NavRoutes.readDailyReading("readingPlan/day?plan=NIV&flag&day=3&other=x"))
        // A non-numeric day reads as absent, never as an exception.
        assertEquals("NIV" to null, NavRoutes.readDailyReading("readingPlan/day?plan=NIV&day=notANumber"))
    }
}
