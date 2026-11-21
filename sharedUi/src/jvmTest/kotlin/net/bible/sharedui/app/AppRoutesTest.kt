package net.bible.sharedui.app

import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AppRoutesTest {
    @Test
    fun savedRouteWins() {
        assertEquals("settings", navHostStartRoute("settings", "download", onMissing = { fail() }))
    }

    @Test
    fun intentRouteNext() {
        assertEquals("download", navHostStartRoute(null, "download", onMissing = { fail() }))
    }

    @Test
    fun aRestoredLegacySearchFormStartRouteOpensReadingInsteadOfCrashingTheGraph() {
        val stale = "search/form?searchText=love&searchType=PHRASE&searchSection=ALL&bibleBook=John"
        assertEquals(NavRoutes.READING, navHostStartRoute(stale, null, onMissing = { fail() }))
        assertEquals(NavRoutes.READING, navHostStartRoute(null, stale, onMissing = { fail() }))
        assertEquals(NavRoutes.READING, navHostStartRoute("search/form", null, onMissing = { fail() }))
    }

    @Test
    fun retiredRouteRedirectMatchesTheWholeBaseOnly() {
        assertEquals(NavRoutes.EPUB_SEARCH, withoutRetiredRoute(NavRoutes.EPUB_SEARCH))
        assertEquals("search/formal", withoutRetiredRoute("search/formal"))
        assertEquals("settings?x=search/form", withoutRetiredRoute("settings?x=search/form"))
    }

    /** #4174: a restored stale start route naming the removed progress screen must not crash the NavHost. */
    @Test
    fun theRemovedProgressStatusRouteOpensReading() {
        val stale = "documents/progress"
        assertEquals(NavRoutes.READING, withoutRetiredRoute(stale))
        assertEquals(NavRoutes.READING, navHostStartRoute(null, stale, onMissing = { fail() }))
        assertEquals(NavRoutes.READING, navHostStartRoute(stale, null, onMissing = { fail() }))
        assertEquals("documents/progressive", withoutRetiredRoute("documents/progressive"))
    }

    @Test
    fun missingDefaultsToReadingAndReports() {
        var missing = 0
        assertEquals(NavRoutes.READING, navHostStartRoute(null, null) { missing++ })
        assertEquals(1, missing)
    }

    @Test
    fun uninitialisedRoutes() {
        assertTrue(routeStartsUninitialised("${NavRoutes.WELCOME}?x=1"))
        assertTrue(routeStartsUninitialised(NavRoutes.BACKUP))
        assertFalse(routeStartsUninitialised(NavRoutes.READING))
        assertFalse(routeStartsUninitialised(null))
    }

    @Test
    fun readingWithoutUsableBibleGoesToWelcomeAndLogsOnce() {
        val logs = mutableListOf<String>()
        assertEquals(NavRoutes.WELCOME, gatedReadingRoute("reading", { false }, logs::add))
        assertEquals(1, logs.size)
    }

    @Test
    fun readingWithUsableBibleIsUnchanged() {
        val logs = mutableListOf<String>()
        assertEquals("reading?x=1", gatedReadingRoute("reading?x=1", { true }, logs::add))
        assertTrue(logs.isEmpty())
    }

    @Test
    fun nonReadingRouteIsNeverGated() {
        assertEquals("settings", gatedReadingRoute("settings", { false }, { fail() }))
    }

    @Test
    fun dailyReadingRouteMatchesOnlyTheQueryBoundary() {
        val base = NavRoutes.dailyReading()
        assertTrue(isDailyReadingRoute(base))
        assertTrue(isDailyReadingRoute("$base?x=1"))
        assertFalse(isDailyReadingRoute(NavRoutes.READING_PLAN_DAY_LIST))
    }
}
