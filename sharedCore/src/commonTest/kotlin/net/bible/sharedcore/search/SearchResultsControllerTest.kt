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
        override fun validateIndex(request: SearchRequest) = true
        override fun decorate(request: SearchRequest) = request.query
        override suspend fun searchMulti(request: SearchRequest) =
            MultiSearchResults(main = listOf(row("Gen 1:1")), other = listOf(row("Sir 1:1")), total = 2)
        override fun containsNonScripture() = toggleVisible
        override fun isCurrentlyShowingScripture() = showingScripture
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
}
