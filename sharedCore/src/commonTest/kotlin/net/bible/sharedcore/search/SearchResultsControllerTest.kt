package net.bible.sharedcore.search

import kotlinx.coroutines.CompletableDeferred
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
    // F91 (fix batch 3 §2.1.2): while a new query loads, the sheet must not show the previous
    // query's count and rows as if they were this query's.
    @Test fun a_new_run_does_not_show_the_previous_runs_results_while_it_loads() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<MultiSearchResults>()
        var calls = 0
        val slow = object : BibleSearchService by fake {
            override suspend fun searchMulti(request: SearchRequest): MultiSearchResults {
                calls++
                return if (calls == 1) {
                    MultiSearchResults(main = listOf(row("Gen 1:1")), other = emptyList(), total = 5001)
                } else {
                    gate.await()
                }
            }
        }
        val c = SearchResultsController(slow, backgroundScope)
        c.run(SearchRequest("lord", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertEquals(5001, c.results.value.total, "sanity: the first run completed")

        c.run(SearchRequest("shepherd", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertTrue(c.loading.value, "sanity: the second run is still loading")
        assertEquals(0, c.results.value.total, "F91: a loading run must not carry the previous count")
        assertTrue(c.displayed.value.isEmpty(), "F91: nor the previous rows")

        gate.complete(MultiSearchResults(main = emptyList(), other = emptyList(), total = 3))
        assertEquals(3, c.results.value.total)
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

    // F26: a shared SearchResultsCache lets a re-created controller (history-revert Back) reuse the
    // last result instead of re-searching.
    @Test fun run_identical_request_on_new_controller_hits_shared_cache() = runTest(UnconfinedTestDispatcher()) {
        val cache = SearchResultsCache()
        val req = SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), "")
        val c1 = SearchResultsController(fake, backgroundScope, cache)
        c1.run(req)
        assertEquals(1, fake.searchCount)
        // New controller instance (activity recreate), same shared cache + identical request:
        val c2 = SearchResultsController(fake, backgroundScope, cache)
        c2.run(req)
        assertEquals(1, fake.searchCount, "identical request must be served from cache, not re-searched")
        assertEquals(listOf("Gen 1:1"), c2.displayed.value.map { it.referenceName })
        assertFalse(c2.loading.value)
    }

    @Test fun run_different_request_misses_shared_cache() = runTest(UnconfinedTestDispatcher()) {
        val cache = SearchResultsCache()
        val c1 = SearchResultsController(fake, backgroundScope, cache)
        c1.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertEquals(1, fake.searchCount)
        val c2 = SearchResultsController(fake, backgroundScope, cache)
        c2.run(SearchRequest("y", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertEquals(2, fake.searchCount, "a different query must re-run and overwrite the single cache slot")
    }

    @Test fun selectTranslations_rerun_result_is_cached() = runTest(UnconfinedTestDispatcher()) {
        val cache = SearchResultsCache()
        val c1 = SearchResultsController(fake, backgroundScope, cache)
        c1.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        c1.selectTranslations(listOf("ESV")) { _, _ -> }
        val countAfterRerun = fake.searchCount
        // Recreate: identical re-selected request is served from cache.
        val c2 = SearchResultsController(fake, backgroundScope, cache)
        c2.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("ESV"), ""))
        assertEquals(countAfterRerun, fake.searchCount)
    }

    /**
     * F44 fix round I2 — what a search COVERED and what the user has SELECTED are two different
     * lists, and only the second may be persisted. Here the search covers `["KJV", "NASB"]` (B4
     * appended the active window's indexed document) while the user's own selection is `["KJV"]`.
     * The results document selector renders `selectedTranslations` as its checked set and hands
     * exactly that back on confirm, where [SearchResultsController.selectTranslations] persists it —
     * so publishing the merged list here would silently save "NASB" as part of the user's selection
     * through a second channel, contradicting the spec's D4.
     */
    @Test fun run_publishes_the_user_selection_so_a_confirm_cannot_persist_an_appended_document() =
        runTest(UnconfinedTestDispatcher()) {
            val c = SearchResultsController(fake, backgroundScope)
            c.run(
                SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV", "NASB"), ""),
                userSelection = listOf("KJV"),
            )
            assertEquals(listOf("KJV"), c.selectedTranslations.value)
            assertEquals(
                listOf("KJV", "NASB"), fake.lastRequest?.translationIds,
                "the SEARCH itself must still cover the appended document",
            )
            // The user opens the selector and presses OK on what it shows, changing nothing.
            c.selectTranslations(c.selectedTranslations.value) { _, _ -> }
            assertEquals(listOf("KJV"), fake.persistedIds, "an auto-appended document must never be persisted")
        }

    @Test fun run_defaults_the_user_selection_to_the_searched_list() = runTest(UnconfinedTestDispatcher()) {
        // The standalone results Activity searches exactly what the user chose, so the default holds.
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV", "ESV"), ""))
        assertEquals(listOf("KJV", "ESV"), c.selectedTranslations.value)
    }

    // F6-C6 / spec D7: leaving search mode drops the rows the session produced. The persisted
    // translation selection and the candidate list are NOT session state and must survive.
    @Test fun clearEmptiesTheRowsAndTheErrorFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = SearchResultsController(fake, backgroundScope)
        c.run(SearchRequest("x", SearchType.ALL_WORDS, SearchBibleSection.ALL, listOf("KJV"), ""))
        assertEquals(listOf("Gen 1:1"), c.displayed.value.map { it.referenceName })
        c.clear()
        assertEquals(MultiSearchResults.EMPTY, c.results.value)
        assertNull(c.error.value)
        assertFalse(c.loading.value)
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
