package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.reading.SearchFieldImeRequest

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

    /**
     * The pending focus/keyboard instruction for the toolbar field, or `null` when there is none.
     * Set by the transitions below and cleared by [imeRequestHandled] once the UI has acted.
     */
    private val _imeRequest = MutableStateFlow<SearchFieldImeRequest?>(null)
    val imeRequest: StateFlow<SearchFieldImeRequest?> = _imeRequest.asStateFlow()

    /** The UI has focused or released the field; the instruction is spent. */
    fun imeRequestHandled() { _imeRequest.value = null }

    /**
     * The one rule behind every call site: the field is focused with the keyboard up exactly when the
     * phase becomes [ReadingSearchPhase.Form] — "the user is expected to type". Every other phase
     * releases it. The two setters clear each other so a stale opposite instruction can never fire.
     */
    private fun requestFieldFocus() { _imeRequest.value = SearchFieldImeRequest.Focus }
    private fun requestFieldRelease() { _imeRequest.value = SearchFieldImeRequest.Release }

    /**
     * The query the current results belong to, or null if there are none. Deliberately the query itself
     * rather than a boolean: search mode stays active after the sheet is closed, and the entry points that
     * do not go through the toolbar field (Ctrl+F, the device SEARCH key, the drawer) can call [open]
     * again after the query has been edited. A boolean would then serve the previous query's results for
     * the new query.
     */
    private var resultsForQuery: String? = null

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
                requestFieldRelease()
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
                requestFieldFocus()
            }
            resultsForQuery == q -> {
                // Reopening after a back press: serve what we already have rather than re-running.
                _phase.value = ReadingSearchPhase.Results(docId, forEpub)
                _sheetVisible.value = true
                requestFieldRelease()
            }
            else -> runSearch(docId, forEpub, q)
        }
    }

    private fun runSearch(docId: String, forEpub: Boolean, query: String) {
        onRunSearch(docId, query, forEpub)
        resultsForQuery = query
        _phase.value = ReadingSearchPhase.Results(docId, forEpub)
        _sheetVisible.value = true
        requestFieldRelease()
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

    /**
     * Redirects the current search session to prompt for an index of [docId] — a translation OTHER
     * than the one [resolveDoc] would report, as chosen in the results document selector (which can
     * pick any Bible, not just the one being read). Returns `false` and leaves the phase untouched
     * when the session is [ReadingSearchPhase.Closed] (there is nothing to redirect).
     *
     * This is the only place the phase's `docId` is set to something other than what [resolveDoc]
     * reports — everywhere else it flows from `resolveDoc()` via [open]. So from here on, "the
     * phase's `docId`" means "the document THIS SESSION is currently addressing", which after a
     * selector choice is the chosen translation rather than the active window's document. That is
     * exactly what [acceptIndexing] and [onIndexingFinished] already key off (both read `phase.docId`,
     * never `resolveDoc()`), and what `buildSearchRequest`'s `translationIds` fallback wants — so
     * routing through the SAME phase, rather than adding a parallel "index this other document" path,
     * is what lets the existing indexing pipeline serve a document the active window isn't showing.
     */
    fun promptIndexFor(docId: String): Boolean {
        val forEpub = forEpubOf(_phase.value) ?: return false
        _phase.value = ReadingSearchPhase.NeedsIndex(docId, forEpub)
        _sheetVisible.value = true
        requestFieldRelease()
        return true
    }

    /**
     * The toolbar overflow's "Rebuild index": prompt for a rebuild of the document THIS SESSION is
     * addressing. Returns `false` when there is no session (nothing to rebuild).
     *
     * Classic had to start the `SearchIndex` Activity for this (`Search.kt:269-280`); the in-sheet
     * panel that replaced it is one call away, and it already words itself as a rebuild when the
     * document has a working index. So this is a redirect into the existing pipeline, not a feature.
     */
    fun requestRebuildIndex(): Boolean {
        val docId = docIdOf(_phase.value) ?: return false
        return promptIndexFor(docId)
    }

    fun acceptIndexing() {
        val p = _phase.value as? ReadingSearchPhase.NeedsIndex ?: return
        onStartIndexing(p.docId)
        _phase.value = ReadingSearchPhase.Indexing(p.docId, p.forEpub)
        _sheetVisible.value = true
        requestFieldRelease()
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
            requestFieldRelease()
            return
        }
        val q = queries.query.value.trim()
        if (q.isEmpty()) {
            _phase.value = ReadingSearchPhase.Form(p.docId, p.forEpub)
            _sheetVisible.value = false
            requestFieldFocus()
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
        resultsForQuery = null
        _imeRequest.value = null
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
