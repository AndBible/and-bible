package net.bible.sharedcore.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpubSearchResultsControllerTest {
    private val row = EpubResultRow("Gen.1.1", "Genesis 1:1", StyledText(listOf(StyledRun("hit", true))))
    private class FakeService(val out: List<EpubResultRow>, val fail: Boolean = false) : EpubSearchService {
        override fun isIndexed(docId: String) = true
        override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow> {
            if (fail) throw RuntimeException("boom"); return out
        }
    }

    // UnconfinedTestDispatcher runs the launched search eagerly (the fake never suspends), so state
    // is settled by the time the assertions read it; backgroundScope hosts the controller's scope so
    // runTest cancels it (no UncompletedCoroutinesError). Mirrors SearchResultsControllerTest.
    @Test fun run_populates_results_and_clears_loading() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(listOf(row)), onSelect = {})
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertEquals(listOf(row), c.results.value)
        assertFalse(c.loading.value)
        assertFalse(c.error.value)
    }

    @Test fun run_failure_sets_error() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(emptyList(), fail = true), onSelect = {})
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertTrue(c.error.value)
        assertFalse(c.loading.value)
    }

    @Test fun select_forwards_keyId() = runTest(UnconfinedTestDispatcher()) {
        val picked = mutableListOf<String>()
        val c = EpubSearchResultsController(backgroundScope, FakeService(emptyList()), onSelect = { picked.add(it) })
        c.select("Gen.1.1")
        assertEquals(listOf("Gen.1.1"), picked)
    }

    @Test fun dismissError_clears_error() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(emptyList(), fail = true), onSelect = {})
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertTrue(c.error.value)
        c.dismissError()
        assertFalse(c.error.value)
    }
}
