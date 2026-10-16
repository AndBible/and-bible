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
    /** All installed Bibles as selectable options, each flagged whether it carries Strong's numbers. */
    fun candidateBibles(): List<BibleOption>
    /** Persist the results document-selector choice (selected translations + Strong's mode). */
    fun persistSelection(translationIds: List<String>, strongsSearch: Boolean)
    /** The subset of [translationIds] that lack a usable search index. */
    fun unindexedAmong(translationIds: List<String>): List<String>
}

interface SearchIndexService {
    fun hasIndex(docId: String): Boolean
    fun createIndex(docId: String)   // kickoff only; progress = Screen.SearchIndexProgress (Batch 1)

    /**
     * True when the document was indexed by the previous search engine (its legacy index directory still exists)
     * and has no valid new index: the index prompt then explains that the search engine changed.
     */
    fun hasLegacyIndex(docId: String): Boolean = false
}
