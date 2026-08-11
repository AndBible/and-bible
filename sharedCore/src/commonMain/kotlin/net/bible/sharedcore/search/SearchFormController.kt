package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the state of the Search form (query text, search type, Bible section and the
 * selected/available translations) and builds a [SearchRequest] from it. StateFlow-based,
 * mirroring the navigation controllers so the host and :sharedUi observe it the same way.
 */
class SearchFormController(
    private val currentBookName: String,
    private val persistTranslations: (List<String>) -> Unit,
    // The three recent-terms parameters are plain constructor parameters, not properties: they are
    // consumed once by the `queries` initializer below, and keeping them as `private val` would store
    // the same lambda and cap twice per search session. The signature is unchanged either way.
    persistRecentTerms: (List<String>) -> Unit = {},
    loadRecentTerms: () -> List<String> = { emptyList() },
    maxRecentTerms: Int = 10,
) {
    /** Query text and the recent-terms MRU, shared with EPUB search by composition. */
    private val queries = SearchQueryController(
        persistRecentTerms = persistRecentTerms,
        loadRecentTerms = loadRecentTerms,
        maxRecentTerms = maxRecentTerms,
    )

    val query: StateFlow<String> get() = queries.query
    val recentTerms: StateFlow<List<String>> get() = queries.recentTerms

    private val _searchType = MutableStateFlow(SearchType.ALL_WORDS)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _bibleSection = MutableStateFlow(SearchBibleSection.ALL)
    val bibleSection: StateFlow<SearchBibleSection> = _bibleSection.asStateFlow()

    private val _selectedTranslationIds = MutableStateFlow<List<String>>(emptyList())
    val selectedTranslationIds: StateFlow<List<String>> = _selectedTranslationIds.asStateFlow()

    private val _availableTranslations = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableTranslations: StateFlow<List<Pair<String, String>>> = _availableTranslations.asStateFlow()

    /** Add [term] to the front of the recent-terms MRU (trimmed, de-duplicated, capped), and persist. */
    fun recordRecentTerm(term: String) = queries.recordRecentTerm(term)

    fun setQuery(v: String) = queries.setQuery(v)
    fun setSearchType(v: SearchType) { _searchType.value = v }
    fun setBibleSection(v: SearchBibleSection) { _bibleSection.value = v }
    fun setAvailableTranslations(v: List<Pair<String, String>>) { _availableTranslations.value = v }

    fun setTranslations(ids: List<String>) {
        _selectedTranslationIds.value = ids
        persistTranslations(ids)
    }

    /** Restore a saved selection without re-persisting it. */
    fun seedTranslations(ids: List<String>) { _selectedTranslationIds.value = ids }

    fun buildRequest() = SearchRequest(
        query = queries.query.value,
        searchType = _searchType.value,
        bibleSection = _bibleSection.value,
        translationIds = _selectedTranslationIds.value,
        currentBookName = currentBookName,
    )
}
