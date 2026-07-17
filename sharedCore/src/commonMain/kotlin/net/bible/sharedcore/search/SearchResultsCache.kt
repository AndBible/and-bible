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
}
