package net.bible.sharedcore.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultsControllerTest {
    private fun row(name: String) = SwordResultRow(name, emptyList(), StyledText.plain(name))
    private val fake = object : BibleSearchService {
        var toggleVisible = true; var showingScripture = true
        var bibles = listOf(
            BibleOption("KJV", "KJV", hasStrongs = true),
            BibleOption("ESV", "ESV", hasStrongs = false),
        )
        var unindexed = emptyList<String>()
        var lastRequest: SearchRequest? = null
        var searchCount = 0
        var persistedIds: List<String>? = null
        var persistedStrongs: Boolean? = null
        override fun validateIndex(request: SearchRequest) = true
        override fun decorate(request: SearchRequest) = request.query
        override suspend fun searchMulti(request: SearchRequest): MultiSearchResults {
            lastRequest = request; searchCount++
            return MultiSearchResults(main = listOf(row("Gen 1:1")), other = listOf(row("Sir 1:1")), total = 2)
        }
        override fun containsNonScripture() = toggleVisible
        override fun isCurrentlyShowingScripture() = showingScripture
        override fun candidateBibles() = bibles
        override fun persistSelection(translationIds: List<String>, strongsSearch: Boolean) {
            persistedIds = translationIds; persistedStrongs = strongsSearch
        }
        override fun unindexedAmong(translationIds: List<String>) =
            translationIds.filter { it in unindexed }
    }
    // UnconfinedTestDispatcher runs the launched search eagerly (the fake never suspends), so
    // state is settled by the time the assertions read it; backgroundScope hosts the eternal
    // `displayed` stateIn collector so runTest cancels it (no UncompletedCoroutinesError).
    @Test fun run_populates_and_shows_main_partition() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertEquals(listOf("Gen 1:1"), c.displayed.value.map { it.referenceName })
        assertTrue(c.scriptureToggleVisible.value)
        assertFalse(c.loading.value)
    }
    @Test fun toggle_switches_to_other_partition() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        c.toggleScripture()
        assertEquals(listOf("Sir 1:1"), c.displayed.value.map { it.referenceName })
    }

    @Test fun run_records_selection_and_candidates() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), "", isStrongsSearch = true))
        assertEquals(listOf("KJV"), c.selectedTranslations.value)
        assertEquals(candidateDocuments(true, fake.candidateBibles()), c.candidates.value)
        // strongs → only the hasStrongs bible
        assertEquals(listOf("KJV"), c.candidates.value.map { it.id })
    }

    @Test fun run_candidates_plain_offers_all() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), "", isStrongsSearch = false))
        assertEquals(candidateDocuments(false, fake.candidateBibles()), c.candidates.value)
        assertEquals(listOf("KJV", "ESV"), c.candidates.value.map { it.id })
    }

    @Test fun selectTranslations_all_indexed_reruns_search() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), "", isStrongsSearch = true))
        val countAfterRun = fake.searchCount
        var needIndexCalled = false
        c.selectTranslations(listOf("ESV")) { _, _ -> needIndexCalled = true }
        assertEquals(listOf("ESV"), c.selectedTranslations.value)
        assertEquals(listOf("ESV"), fake.persistedIds)
        assertEquals(true, fake.persistedStrongs)
        assertFalse(needIndexCalled)
        assertEquals(countAfterRun + 1, fake.searchCount)
        assertEquals(listOf("ESV"), fake.lastRequest?.translationIds)
        // re-run copies the stored request, preserving other fields
        assertEquals(true, fake.lastRequest?.isStrongsSearch)
        assertEquals("x", fake.lastRequest?.query)
    }

    @Test fun selectTranslations_empty_is_noop() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), "", isStrongsSearch = true))
        val countAfterRun = fake.searchCount
        fake.persistedIds = null; fake.persistedStrongs = null
        var needIndexCalled = false
        c.selectTranslations(emptyList()) { _, _ -> needIndexCalled = true }
        // Unchecking everything then pressing OK must not clobber the selection or run an empty search.
        assertNull(fake.persistedIds)
        assertFalse(needIndexCalled)
        assertEquals(countAfterRun, fake.searchCount)
        assertEquals(listOf("KJV"), c.selectedTranslations.value)
    }

    @Test fun selectTranslations_unindexed_calls_onNeedIndex_and_does_not_rerun() = runTest(UnconfinedTestDispatcher()) {
        fake.unindexed = listOf("ESV")
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        val countAfterRun = fake.searchCount
        var reportedUnindexed: List<String>? = null
        var reportedIds: List<String>? = null
        c.selectTranslations(listOf("KJV", "ESV")) { unindexed, ids ->
            reportedUnindexed = unindexed; reportedIds = ids
        }
        assertEquals(listOf("ESV"), reportedUnindexed)
        assertEquals(listOf("KJV", "ESV"), reportedIds)
        assertEquals(listOf("KJV", "ESV"), c.selectedTranslations.value)
        assertEquals(listOf("KJV", "ESV"), fake.persistedIds)
        // NOT re-run
        assertEquals(countAfterRun, fake.searchCount)
    }
}
