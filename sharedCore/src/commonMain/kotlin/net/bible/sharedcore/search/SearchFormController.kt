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
    private val persistRecentTerms: (List<String>) -> Unit = {},
    loadRecentTerms: () -> List<String> = { emptyList() },
    private val maxRecentTerms: Int = 10,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.ALL_WORDS)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _bibleSection = MutableStateFlow(SearchBibleSection.ALL)
    val bibleSection: StateFlow<SearchBibleSection> = _bibleSection.asStateFlow()

    private val _selectedTranslationIds = MutableStateFlow<List<String>>(emptyList())
    val selectedTranslationIds: StateFlow<List<String>> = _selectedTranslationIds.asStateFlow()

    private val _availableTranslations = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableTranslations: StateFlow<List<Pair<String, String>>> = _availableTranslations.asStateFlow()

    private val _recentTerms = MutableStateFlow(loadRecentTerms())
    val recentTerms: StateFlow<List<String>> = _recentTerms.asStateFlow()

    /** Add [term] to the front of the recent-terms MRU (trimmed, de-duplicated, capped), and persist. */
    fun recordRecentTerm(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val updated = (listOf(t) + _recentTerms.value.filter { it != t }).take(maxRecentTerms)
        _recentTerms.value = updated
        persistRecentTerms(updated)
    }

    fun setQuery(v: String) { _query.value = v }
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
        query = _query.value,
        searchType = _searchType.value,
        bibleSection = _bibleSection.value,
        translationIds = _selectedTranslationIds.value,
        currentBookName = currentBookName,
    )
}
