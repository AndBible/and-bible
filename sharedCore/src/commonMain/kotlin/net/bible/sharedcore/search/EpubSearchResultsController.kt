package net.bible.sharedcore.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Framework-free EPUB results state; runs the search through the [service] seam off the caller's [scope]. */
class EpubSearchResultsController(
    private val scope: CoroutineScope,
    private val service: EpubSearchService,
    private val onSelect: (keyId: String) -> Unit,
) {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _results = MutableStateFlow<List<EpubResultRow>>(emptyList())
    val results: StateFlow<List<EpubResultRow>> = _results.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun run(docId: String, query: String, mode: EpubSearchMode) {
        _loading.value = true; _error.value = false
        scope.launch {
            try { _results.value = service.searchEpub(docId, query, mode) }
            catch (e: Exception) { _error.value = true }
            finally { _loading.value = false }
        }
    }

    fun select(keyId: String) = onSelect(keyId)
    fun dismissError() { _error.value = false }
}
