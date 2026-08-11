package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which surface the reading-view search session is showing. */
sealed interface ReadingSearchPhase {
    data object Closed : ReadingSearchPhase
    data class Form(val docId: String, val forEpub: Boolean) : ReadingSearchPhase
    data class NeedsIndex(val docId: String, val forEpub: Boolean) : ReadingSearchPhase
    data class Indexing(val docId: String, val forEpub: Boolean) : ReadingSearchPhase
    data class Results(val docId: String, val forEpub: Boolean) : ReadingSearchPhase
}

/**
 * The reading-view search session. This replaces the old state model, which was the Intent extras
 * bundle carried across a chain of Activities that each `startActivity(...); finish()`ed. A panel is not
 * an `ActivityBase`, so it loses `HistoryTraversal`/`IntentHistoryItem` — and with it back navigation,
 * the F25 scroll position and form re-open. All three are rebuilt here and in the shells.
 *
 * Deliberately free of coroutines and platform types: every effect is an injected lambda, so the whole
 * machine is host-testable and iOS-clean.
 */
class ReadingSearchController(
    private val resolveDoc: () -> SearchDocumentInfo?,
    private val onUnavailable: () -> Unit,
    private val onLeaveFullScreen: () -> Unit,
    private val onStartIndexing: (docId: String) -> Unit,
    private val onRunSearch: (docId: String, query: String, forEpub: Boolean) -> Unit,
    val queries: SearchQueryController = SearchQueryController(),
) {
    private val _phase = MutableStateFlow<ReadingSearchPhase>(ReadingSearchPhase.Closed)
    val phase: StateFlow<ReadingSearchPhase> = _phase.asStateFlow()

    private val _searchModeActive = MutableStateFlow(false)
    val searchModeActive: StateFlow<Boolean> = _searchModeActive.asStateFlow()

    private val _sheetVisible = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheetVisible.asStateFlow()

    /** True once a search has produced results for the current query, so reopening need not re-run. */
    private var hasResults = false

    /**
     * Opens search for the active window's document. [seedQuery] comes from the entry points that bypass
     * the form (text-selection "Search …", Strong's find-all), and runs immediately.
     */
    fun open(seedQuery: String? = null) {
        val kind = searchKindFor(resolveDoc())
        if (kind is SearchKind.Unavailable) {
            onUnavailable()
            return
        }
        // The toolbar hosts the input, and the toolbar is dropped in fullscreen — so search always
        // leaves it. `fullScreen = false` is idempotent host-side.
        onLeaveFullScreen()
        _searchModeActive.value = true
        if (seedQuery != null) queries.setQuery(seedQuery)

        when (kind) {
            is SearchKind.NeedsIndex -> {
                _phase.value = ReadingSearchPhase.NeedsIndex(kind.docId, kind.forEpub)
                _sheetVisible.value = true
            }
            is SearchKind.Bible -> enterFormOrResults(kind.docId, forEpub = false)
            is SearchKind.Epub -> enterFormOrResults(kind.docId, forEpub = true)
            SearchKind.Unavailable -> Unit // unreachable, handled above
        }
    }

    private fun enterFormOrResults(docId: String, forEpub: Boolean) {
        val q = queries.query.value.trim()
        when {
            q.isEmpty() -> {
                _phase.value = ReadingSearchPhase.Form(docId, forEpub)
                _sheetVisible.value = false
            }
            hasResults -> {
                // Reopening after a back press: serve what we already have rather than re-running.
                _phase.value = ReadingSearchPhase.Results(docId, forEpub)
                _sheetVisible.value = true
            }
            else -> runSearch(docId, forEpub, q)
        }
    }

    private fun runSearch(docId: String, forEpub: Boolean, query: String) {
        onRunSearch(docId, query, forEpub)
        hasResults = true
        _phase.value = ReadingSearchPhase.Results(docId, forEpub)
        _sheetVisible.value = true
    }

    /** Submit from the toolbar field (IME action or the submit button). Blank queries are ignored. */
    fun submit() {
        val p = _phase.value
        val docId = docIdOf(p) ?: return
        val forEpub = forEpubOf(p) ?: return
        val q = queries.query.value.trim()
        if (q.isEmpty()) return
        queries.recordRecentTerm(q)
        runSearch(docId, forEpub, q)
    }

    fun acceptIndexing() {
        val p = _phase.value as? ReadingSearchPhase.NeedsIndex ?: return
        onStartIndexing(p.docId)
        _phase.value = ReadingSearchPhase.Indexing(p.docId, p.forEpub)
        _sheetVisible.value = true
    }

    /**
     * Called when the indexing job reports done. On success the search runs automatically if a query is
     * already waiting; otherwise the form takes over. On failure we fall back to the prompt rather than
     * pretending the document is searchable.
     */
    fun onIndexingFinished(indexDone: Boolean) {
        val p = _phase.value as? ReadingSearchPhase.Indexing ?: return
        if (!indexDone) {
            _phase.value = ReadingSearchPhase.NeedsIndex(p.docId, p.forEpub)
            return
        }
        val q = queries.query.value.trim()
        if (q.isEmpty()) {
            _phase.value = ReadingSearchPhase.Form(p.docId, p.forEpub)
            _sheetVisible.value = false
        } else {
            runSearch(p.docId, p.forEpub, q)
        }
    }

    /** The settings sheet closed: re-run if there is something to re-run (spec §2). */
    fun settingsClosed() {
        val p = _phase.value
        val docId = docIdOf(p) ?: return
        val forEpub = forEpubOf(p) ?: return
        val q = queries.query.value.trim()
        if (q.isEmpty()) return
        runSearch(docId, forEpub, q)
    }

    /** First back press. Returns true if it consumed the press. */
    fun closeSheet(): Boolean {
        if (!_sheetVisible.value) return false
        _sheetVisible.value = false
        return true
    }

    /** Second back press. Returns true if it consumed the press. */
    fun closeSearchMode(): Boolean {
        if (!_searchModeActive.value) return false
        _searchModeActive.value = false
        _phase.value = ReadingSearchPhase.Closed
        hasResults = false
        return true
    }

    private fun docIdOf(p: ReadingSearchPhase): String? = when (p) {
        is ReadingSearchPhase.Form -> p.docId
        is ReadingSearchPhase.NeedsIndex -> p.docId
        is ReadingSearchPhase.Indexing -> p.docId
        is ReadingSearchPhase.Results -> p.docId
        ReadingSearchPhase.Closed -> null
    }

    private fun forEpubOf(p: ReadingSearchPhase): Boolean? = when (p) {
        is ReadingSearchPhase.Form -> p.forEpub
        is ReadingSearchPhase.NeedsIndex -> p.forEpub
        is ReadingSearchPhase.Indexing -> p.forEpub
        is ReadingSearchPhase.Results -> p.forEpub
        ReadingSearchPhase.Closed -> null
    }
}
