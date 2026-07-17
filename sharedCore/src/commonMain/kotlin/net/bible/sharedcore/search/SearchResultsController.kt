package net.bible.sharedcore.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchResultsController(
    private val service: BibleSearchService,
    private val scope: CoroutineScope,
) {
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _results = MutableStateFlow(MultiSearchResults.EMPTY); val results = _results.asStateFlow()
    private val _scriptureShown = MutableStateFlow(true); val scriptureShown = _scriptureShown.asStateFlow()
    private val _toggleVisible = MutableStateFlow(false); val scriptureToggleVisible = _toggleVisible.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _selectedTranslations = MutableStateFlow<List<String>>(emptyList())
    val selectedTranslations = _selectedTranslations.asStateFlow()
    private val _candidates = MutableStateFlow<List<BibleOption>>(emptyList())
    val candidates = _candidates.asStateFlow()

    private var storedRequest: SearchRequest? = null
    private var isStrongsSearch: Boolean = false

    val displayed: StateFlow<List<SwordResultRow>> =
        combine(_results, _scriptureShown) { r, s -> if (s) r.main else r.other }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun run(request: SearchRequest) {
        storedRequest = request
        isStrongsSearch = request.isStrongsSearch
        _selectedTranslations.value = request.translationIds
        _candidates.value = candidateDocuments(request.isStrongsSearch, service.candidateBibles())
        _scriptureShown.value = service.isCurrentlyShowingScripture()
        _toggleVisible.value = service.containsNonScripture()
        _loading.value = true
        scope.launch {
            try { _results.value = service.searchMulti(request) }
            catch (e: Exception) { _error.value = e.message ?: "error" }
            finally { _loading.value = false }
        }
    }

    /**
     * Apply a new translation selection from the results document selector.
     * Persists the choice; if any selected translation lacks an index, defers to
     * [onNeedIndex] (indexing must complete first) and does NOT re-run the search.
     * Otherwise re-runs the stored request with the new translations.
     */
    fun selectTranslations(ids: List<String>, onNeedIndex: (unindexed: List<String>, ids: List<String>) -> Unit) {
        if (ids.isEmpty()) return
        _selectedTranslations.value = ids
        service.persistSelection(ids, isStrongsSearch)
        val unindexed = service.unindexedAmong(ids)
        if (unindexed.isNotEmpty()) {
            onNeedIndex(unindexed, ids)
            return
        }
        val request = (storedRequest ?: return).copy(translationIds = ids)
        storedRequest = request
        _loading.value = true
        scope.launch {
            try { _results.value = service.searchMulti(request) }
            catch (e: Exception) { _error.value = e.message ?: "error" }
            finally { _loading.value = false }
        }
    }

    fun toggleScripture() { _scriptureShown.value = !_scriptureShown.value }
    fun dismissError() { _error.value = null }
}
