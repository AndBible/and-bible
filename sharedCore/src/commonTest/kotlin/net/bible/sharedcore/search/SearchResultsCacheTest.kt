package net.bible.sharedcore.search

import kotlin.test.*

class SearchResultsCacheTest {
    private fun req(query: String, ids: List<String>) =
        SearchRequest(query, SearchType.ALL_WORDS, SearchBibleSection.ALL, ids, "")
    private fun results(name: String) =
        MultiSearchResults(listOf(SwordResultRow(name, emptyList(), StyledText.plain(name))), emptyList(), 1)

    @Test fun empty_cache_misses() {
        assertNull(SearchResultsCache().get(req("x", listOf("KJV"))))
    }

    @Test fun hit_on_equal_request() {
        val c = SearchResultsCache()
        val r = req("x", listOf("KJV"))
        c.put(r, results("Gen 1:1"))
        assertEquals(listOf("Gen 1:1"), c.get(r)?.main?.map { it.referenceName })
        // Value-equal (not same instance) request also hits.
        assertNotNull(c.get(req("x", listOf("KJV"))))
    }

    @Test fun miss_on_different_request_fields() {
        val c = SearchResultsCache()
        c.put(req("x", listOf("KJV")), results("Gen 1:1"))
        assertNull(c.get(req("y", listOf("KJV"))), "different query misses")
        assertNull(c.get(req("x", listOf("ESV"))), "different translations miss")
    }

    @Test fun clear_empties_the_slot() {
        val c = SearchResultsCache()
        val r = req("x", listOf("KJV"))
        c.put(r, results("Gen 1:1"))
        assertNotNull(c.get(r), "sanity: seeded")
        c.clear()
        assertNull(c.get(r), "a cleared cache must miss the request it was seeded with")
    }

    @Test fun put_overwrites_single_slot() {
        val c = SearchResultsCache()
        c.put(req("x", listOf("KJV")), results("Gen 1:1"))
        c.put(req("y", listOf("KJV")), results("Exo 1:1"))
        assertNull(c.get(req("x", listOf("KJV"))), "only the latest request is retained")
        assertEquals(listOf("Exo 1:1"), c.get(req("y", listOf("KJV")))?.main?.map { it.referenceName })
    }
}
