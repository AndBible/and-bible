package net.bible.sharedcore.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpubSearchResultsControllerTest {
    @Test fun finalFixNewestBookOwnsRowsAndSelectionAfterReverseCompletion() = runTest(UnconfinedTestDispatcher()) {
        val old = CompletableDeferred<List<EpubResultRow>>()
        val newest = CompletableDeferred<List<EpubResultRow>>()
        val service = object : EpubSearchService {
            override fun isIndexed(docId: String) = true
            override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode) = withContext(NonCancellable) {
                if (docId == "A") old.await() else newest.await()
            }
        }
        val selected = mutableListOf<Pair<String, Int>>()
        val c = EpubSearchResultsController(backgroundScope, service) { key, ordinal -> selected += key to ordinal }
        val expected = row.copy(keyId = "B:chapter2", ordinal = 22)
        c.run("A", "old", EpubSearchMode.ALL_WORDS)
        c.run("B", "new", EpubSearchMode.PHRASE)
        newest.complete(listOf(expected))
        old.complete(listOf(row.copy(keyId = "A:chapter1")))
        assertEquals(listOf(expected), c.results.value, "late A must not be selectable as B's rows")
        c.results.value.single().let { c.select(it.keyId, it.ordinal) }
        assertEquals(listOf("B:chapter2" to 22), selected)
        assertFalse(c.loading.value)
        assertFalse(c.error.value)
    }

    @Test fun finalFixStaleFailureCannotEndNewLoadingOrShowError() = runTest(UnconfinedTestDispatcher()) {
        val old = CompletableDeferred<Unit>()
        val newest = CompletableDeferred<List<EpubResultRow>>()
        val service = object : EpubSearchService {
            override fun isIndexed(docId: String) = true
            override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow> =
                withContext(NonCancellable) {
                    if (docId == "A") { old.await(); error("old failed") } else newest.await()
                }
        }
        val c = EpubSearchResultsController(backgroundScope, service) { _, _ -> }
        try {
            c.run("A", "old", EpubSearchMode.ALL_WORDS)
            c.run("B", "new", EpubSearchMode.ALL_WORDS)
            old.complete(Unit)
            assertTrue(c.loading.value)
            assertFalse(c.error.value)
        } finally { newest.complete(emptyList()) }
    }

    @Test fun finalFixClearRetiresPendingSearch() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<List<EpubResultRow>>()
        val service = object : EpubSearchService {
            override fun isIndexed(docId: String) = true
            override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode) =
                withContext(NonCancellable) { gate.await() }
        }
        val c = EpubSearchResultsController(backgroundScope, service) { _, _ -> }
        c.run("A", "hit", EpubSearchMode.ALL_WORDS)
        c.clear()
        gate.complete(listOf(row))
        assertTrue(c.results.value.isEmpty(), "a closed session must not regain late rows")
        assertFalse(c.loading.value)
        assertFalse(c.error.value)
    }

    private val row = EpubResultRow("Gen.1.1", 11, "Genesis 1:1", StyledText(listOf(StyledRun("hit", true))))
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
        val c = EpubSearchResultsController(backgroundScope, FakeService(listOf(row)), onSelect = { _, _ -> })
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertEquals(listOf(row), c.results.value)
        assertFalse(c.loading.value)
        assertFalse(c.error.value)
    }

    @Test fun run_failure_sets_error() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(emptyList(), fail = true), onSelect = { _, _ -> })
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertTrue(c.error.value)
        assertFalse(c.loading.value)
    }

    @Test fun select_forwards_keyId_and_ordinal() = runTest(UnconfinedTestDispatcher()) {
        val picked = mutableListOf<Pair<String, Int>>()
        val c = EpubSearchResultsController(
            backgroundScope, FakeService(emptyList()),
            onSelect = { keyId, ordinal -> picked.add(keyId to ordinal) },
        )
        c.select("Gen.1.1", 11)
        assertEquals(listOf("Gen.1.1" to 11), picked)
    }

    @Test fun dismissError_clears_error() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(emptyList(), fail = true), onSelect = { _, _ -> })
        c.run("book", "hit", EpubSearchMode.PHRASE)
        assertTrue(c.error.value)
        c.dismissError()
        assertFalse(c.error.value)
    }

    // F6-C6 / spec D7: leaving search mode drops the rows the session produced.
    @Test
    fun clearEmptiesTheRowsAndTheErrorFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = EpubSearchResultsController(backgroundScope, FakeService(listOf(row)), onSelect = { _, _ -> })
        c.run("EPUB", "grace", EpubSearchMode.ALL_WORDS)
        assertTrue(c.results.value.isNotEmpty())
        c.clear()
        assertEquals(emptyList<EpubResultRow>(), c.results.value)
        assertFalse(c.error.value)
        assertFalse(c.loading.value)
    }
}
