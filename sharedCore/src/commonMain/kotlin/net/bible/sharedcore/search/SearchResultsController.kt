package net.bible.sharedcore.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchResultsController(
    private val service: BibleSearchService,
    private val scope: CoroutineScope,
    private val cache: SearchResultsCache = SearchResultsCache(),
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

    /**
     * Runs [request] and publishes it to the results sheet.
     *
     * [userSelection] is what the results document selector shows as checked, and therefore what a
     * confirm in that selector hands back to [selectTranslations] — which PERSISTS it. It defaults to
     * the searched list, which is right for every caller whose searched list IS the user's choice
     * (the standalone results Activity). The reading view is not one of them: since F44/B4 it appends
     * the active window's document to the persisted selection, and publishing that merged list here
     * would silently turn an auto-appended document into part of the user's SAVED selection through a
     * second channel — contradicting the spec's D4 ("the persisted selection is the user's own; the
     * active document is only added to the SEARCH"), leaving the settings sheet's picker (which reads
     * the persisted list) disagreeing with this one, and, for a commentary, checking a document that
     * is not even among the selector's candidates and so vanishes on the next confirm.
     */
    fun run(request: SearchRequest, userSelection: List<String> = request.translationIds) {
        storedRequest = request
        isStrongsSearch = request.isStrongsSearch
        _selectedTranslations.value = userSelection
        _candidates.value = candidateDocuments(request.isStrongsSearch, service.candidateBibles())
        _scriptureShown.value = service.isCurrentlyShowingScripture()
        _toggleVisible.value = service.containsNonScripture()

        // F26: a history-revert recreate re-runs onCreate with the SAME request; serve the cached
        // result instantly instead of re-executing the Lucene search.
        val cached = cache.get(request)
        if (cached != null) {
            _results.value = cached
            _loading.value = false
            return
        }
        // F91: a new query must not show the previous query's count and rows while it loads.
        _results.value = MultiSearchResults.EMPTY
        _loading.value = true
        scope.launch {
            try {
                val r = service.searchMulti(request)
                _results.value = r
                cache.put(request, r)
            }
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
        // F91: a new query must not show the previous query's count and rows while it loads.
        _results.value = MultiSearchResults.EMPTY
        _loading.value = true
        scope.launch {
            try {
                val r = service.searchMulti(request)
                _results.value = r
                cache.put(request, r)
            }
            catch (e: Exception) { _error.value = e.message ?: "error" }
            finally { _loading.value = false }
        }
    }

    fun toggleScripture() { _scriptureShown.value = !_scriptureShown.value }
    fun dismissError() { _error.value = null }

    /** Drop the rows: the search session has ended (spec D7). The persisted translation selection
     *  and the candidate list are NOT session state and stay. */
    fun clear() {
        _results.value = MultiSearchResults.EMPTY
        _error.value = null
        _loading.value = false
    }
}
