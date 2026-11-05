package net.bible.sharedcore.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Framework-free EPUB results state; runs the search through the [service] seam off the caller's [scope]. */
class EpubSearchResultsController(
    private val scope: CoroutineScope,
    private val service: EpubSearchService,
    private val onSelect: (keyId: String, ordinal: Int) -> Unit,
) {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _results = MutableStateFlow<List<EpubResultRow>>(emptyList())
    val results: StateFlow<List<EpubResultRow>> = _results.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L

    private fun retireSearch(): Long {
        generation++
        searchJob?.cancel()
        searchJob = null
        return generation
    }

    fun run(docId: String, query: String, mode: EpubSearchMode) {
        val owner = retireSearch()
        _results.value = emptyList()
        _loading.value = true; _error.value = false
        searchJob = scope.launch {
            try {
                val rows = service.searchEpub(docId, query, mode)
                if (owner == generation) _results.value = rows
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (owner == generation) _error.value = true
            } finally {
                if (owner == generation) _loading.value = false
            }
        }
    }

    fun select(keyId: String, ordinal: Int) = onSelect(keyId, ordinal)
    fun dismissError() { _error.value = false }

    /** Drop the rows: the search session has ended (spec D7). */
    fun clear() {
        retireSearch()
        _results.value = emptyList()
        _error.value = false
        _loading.value = false
    }
}
