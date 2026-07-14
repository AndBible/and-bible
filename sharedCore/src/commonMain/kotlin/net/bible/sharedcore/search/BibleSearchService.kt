package net.bible.sharedcore.search

interface BibleSearchService {
    /** True if every requested translation has a usable index. */
    fun validateIndex(request: SearchRequest): Boolean
    /** Wraps SearchControl.decorateSearchString (section terms, strong: protection, SearchType.decorate). */
    fun decorate(request: SearchRequest): String
    /** Runs the JSword Lucene search, groups + partitions main/other, builds per-row StyledText highlighting. */
    suspend fun searchMulti(request: SearchRequest): MultiSearchResults
    /** True if the current document contains non-scripture (deuterocanonical) → scripture toggle visible. */
    fun containsNonScripture(): Boolean
    /** Whether the results view starts showing scripture (parity with isCurrentlyShowingScripture). */
    fun isCurrentlyShowingScripture(): Boolean
}

interface SearchIndexService {
    fun hasIndex(docId: String): Boolean
    fun createIndex(docId: String)   // kickoff only; progress = Screen.SearchIndexProgress (Batch 1)
}
