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

    val displayed: StateFlow<List<SwordResultRow>> =
        combine(_results, _scriptureShown) { r, s -> if (s) r.main else r.other }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun run(request: SearchRequest) {
        _scriptureShown.value = service.isCurrentlyShowingScripture()
        _toggleVisible.value = service.containsNonScripture()
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
