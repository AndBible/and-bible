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

    /**
     * The MRU store is shared with the classic/EPUB search Activities, and the reading-view host
     * outlives any single search — so a term recorded elsewhere must be picked up, not clobbered by
     * the host's stale in-memory copy.
     */
    @Test
    fun reloadRecentTermsPicksUpTermsWrittenToTheStoreElsewhere() {
        var stored = listOf("light")
        val c = SearchQueryController(
            persistRecentTerms = { stored = it },
            loadRecentTerms = { stored },
        )
        assertEquals(listOf("light"), c.recentTerms.value)

        stored = listOf("water", "light") // as if the classic Activity had searched "water"
        c.reloadRecentTerms()
        assertEquals(listOf("water", "light"), c.recentTerms.value)

        // And a term recorded after the reload builds on the fresh list rather than dropping "water".
        c.recordRecentTerm("fire")
        assertEquals(listOf("fire", "water", "light"), c.recentTerms.value)
        assertEquals(listOf("fire", "water", "light"), stored)
    }

    @Test
    fun setQueryUpdatesTheQueryFlow() {
        val c = SearchQueryController()
        c.setQuery("light")
        assertEquals("light", c.query.value)
    }
}
