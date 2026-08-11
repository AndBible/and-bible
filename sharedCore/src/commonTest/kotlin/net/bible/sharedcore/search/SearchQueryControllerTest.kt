package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchQueryControllerTest {

    @Test
    fun recordingATermPutsItFirstAndPersists() {
        val persisted = mutableListOf<List<String>>()
        val c = SearchQueryController(persistRecentTerms = { persisted.add(it) })
        c.recordRecentTerm("light")
        c.recordRecentTerm("water")
        assertEquals(listOf("water", "light"), c.recentTerms.value)
        assertEquals(listOf(listOf("light"), listOf("water", "light")), persisted)
    }

    @Test
    fun recordingAnExistingTermMovesItToTheFrontWithoutDuplicating() {
        val c = SearchQueryController(loadRecentTerms = { listOf("a", "b", "c") })
        c.recordRecentTerm("c")
        assertEquals(listOf("c", "a", "b"), c.recentTerms.value)
    }

    @Test
    fun blankTermsAreIgnored() {
        val persisted = mutableListOf<List<String>>()
        val c = SearchQueryController(persistRecentTerms = { persisted.add(it) })
        c.recordRecentTerm("   ")
        assertEquals(emptyList(), c.recentTerms.value)
        assertEquals(emptyList(), persisted)
    }

    @Test
    fun theListIsCappedAtMaxRecentTerms() {
        val c = SearchQueryController(maxRecentTerms = 2)
        c.recordRecentTerm("a")
        c.recordRecentTerm("b")
        c.recordRecentTerm("c")
        assertEquals(listOf("c", "b"), c.recentTerms.value)
    }

    @Test
    fun termsAreTrimmedBeforeStoring() {
        val c = SearchQueryController()
        c.recordRecentTerm("  light  ")
        assertEquals(listOf("light"), c.recentTerms.value)
    }

    @Test
    fun setQueryUpdatesTheQueryFlow() {
        val c = SearchQueryController()
        c.setQuery("light")
        assertEquals("light", c.query.value)
    }
}
