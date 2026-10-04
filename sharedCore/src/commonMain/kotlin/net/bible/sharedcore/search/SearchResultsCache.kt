package net.bible.sharedcore.search

/**
 * Single-entry cache of the most recent SWORD multi-translation search result (F26).
 *
 * The Compose results host is re-created from its saved intent whenever the history manager reverts
 * to it on Back (e.g. after tapping through to a verse and pressing Back), and its `onCreate` would
 * otherwise re-run the whole Lucene query even though the request is identical. Injected as a DI
 * singleton so it outlives any single controller/activity instance; a cache hit lets the results
 * reappear instantly instead of re-searching.
 *
 * Only the last request is kept — a genuinely new query has a different [SearchRequest] and misses,
 * overwriting the single slot, so it never serves stale results for a different search.
 */
class SearchResultsCache {
    private var request: SearchRequest? = null
    private var results: MultiSearchResults? = null

    /** The cached results for [forRequest], or null if the last cached request differs. */
    fun get(forRequest: SearchRequest): MultiSearchResults? =
        if (request == forRequest) results else null

    fun put(forRequest: SearchRequest, value: MultiSearchResults) {
        request = forRequest
        results = value
    }

    /**
     * Empties the single slot, so the next [get] misses whatever was cached.
     *
     * Production never needs this (a genuinely new request misses and overwrites by itself); it
     * exists for **test isolation**: this cache is a DI *singleton*, so a test that seeds it — see
     * `ReadingSearchEntryPointsTest`, which seeds a marker result to prove which `SearchRequest` the
     * host actually built — would otherwise leave a JVM-global entry behind for every later test in
     * the same JVM to hit.
     */
    fun clear() {
        request = null
        results = null
    }
}
