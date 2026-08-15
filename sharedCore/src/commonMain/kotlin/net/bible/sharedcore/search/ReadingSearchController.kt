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

    /** What the rows the hosts' result controllers are currently holding were produced by. */
    private data class ResultsKey(val query: String, val docId: String, val forEpub: Boolean)

    /**
     * What the current results belong to, or null if there are none. Deliberately the query itself
     * rather than a boolean: search mode stays active after the sheet is closed, and the entry points that
     * do not go through the toolbar field (Ctrl+F, the device SEARCH key, the drawer) can call [open]
     * again after the query has been edited. A boolean would then serve the previous query's results for
     * the new query.
     *
     * The DOCUMENT is part of the key for the same reason (F44 fix round, I1): since B3 the target is
     * re-resolved on every entry, so the same query can be re-entered against a different document —
     * search a Bible, press back once (the sheet closes, search mode stays active), tap the EPUB pane
     * and trigger the same query again. Keyed on the query alone, [enterFormOrResults] would then have
     * built `Results(epubDocId, forEpub = true)` WITHOUT running a search, and the sheet would render
     * the EPUB branch over the EPUB controller's empty/stale rows while the header counted the Bible's
     * hits. The cache is served only when the query AND the resolved target both match; anything else
     * runs the search.
     */
    private var lastResults: ResultsKey? = null

    /**
     * True while a [ReadingSearchPhase.NeedsIndex] prompt addresses a document chosen in the results
     * document selector rather than the active window's — see [promptIndexFor]. Such a prompt must
     * survive a window switch, or the user's explicit choice would be overwritten by whatever pane
     * they tapped next.
     */
    private var indexPromptIsExplicit = false

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
        indexPromptIsExplicit = false
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
            lastResults == ResultsKey(q, docId, forEpub) -> {
                // Reopening after a back press: serve what we already have rather than re-running.
                _phase.value = ReadingSearchPhase.Results(docId, forEpub)
                _sheetVisible.value = true
                requestFieldRelease()
            }
            else -> runSearch(docId, forEpub, q)
        }
    }

    private fun runSearch(docId: String, forEpub: Boolean, query: String, touchIme: Boolean = true) {
        onRunSearch(docId, query, forEpub)
        lastResults = ResultsKey(query, docId, forEpub)
        _phase.value = ReadingSearchPhase.Results(docId, forEpub)
        _sheetVisible.value = true
        // `touchIme = false` only from the asynchronous indexing completion — see onIndexingFinished.
        if (touchIme) requestFieldRelease()
    }

    /**
     * Submit from the toolbar field (IME action or the submit button). Blank queries are ignored.
     *
     * The target is resolved HERE, not read back out of the phase: search mode outlives a window
     * switch, so the document to search is whatever the active window shows at the moment the user
     * submits (F44/B3). The phase's own docId is what a RESULT SET belongs to, which is a different
     * question and is set by [runSearch]. The one phase that is NOT retargeted is an index build in
     * flight — see [keepIndexingInFlight].
     */
    fun submit() {
        if (_phase.value == ReadingSearchPhase.Closed) return
        val q = queries.query.value.trim()
        if (q.isEmpty()) return
        if (keepIndexingInFlight()) return
        when (val kind = searchKindFor(resolveDoc())) {
            SearchKind.Unavailable -> onUnavailable()
            is SearchKind.NeedsIndex -> {
                queries.recordRecentTerm(q)
                promptIndexImplicitly(kind.docId, kind.forEpub)
            }
            is SearchKind.Bible -> { queries.recordRecentTerm(q); runSearch(kind.docId, false, q) }
            is SearchKind.Epub -> { queries.recordRecentTerm(q); runSearch(kind.docId, true, q) }
        }
    }

    /**
     * An index build already running governs the session until it finishes (spec §9: the RECORDED
     * document, not the live target, governs a build in flight). [submit] and [settingsClosed] would
     * otherwise overwrite [ReadingSearchPhase.Indexing] with results for another document, and
     * [onIndexingFinished]'s `as? Indexing` guard would then early-return — silently dropping the
     * completed build's automatic search, with the user left looking at results they did not ask for.
     *
     * What the user sees instead: the progress panel stays exactly where it is, raised into view (the
     * sheet may have been dismissed with a back press), and the search they typed runs by itself the
     * moment the build completes — that is [onIndexingFinished]'s existing "a query is already
     * waiting" path, which reads the very query they just submitted. Returns true when it consumed
     * the call.
     *
     * The term is deliberately NOT recorded as a recent term here: nothing has been searched yet, and
     * `onIndexingFinished`'s automatic run does not record one either.
     */
    private fun keepIndexingInFlight(): Boolean {
        if (_phase.value !is ReadingSearchPhase.Indexing) return false
        _sheetVisible.value = true
        requestFieldRelease()
        return true
    }

    /**
     * An index prompt derived from the ACTIVE window's document rather than chosen in the results
     * document selector, so [indexPromptIsExplicit] must be cleared (F44 fix round, M1): the flag is
     * sticky otherwise — set by [promptIndexFor], cleared only by [open]/[closeSearchMode] — so after
     * one use of the results document selector, no prompt for the rest of the session would ever
     * follow the active window again ([activeDocumentChanged] refuses to refresh an "explicit" one).
     */
    private fun promptIndexImplicitly(docId: String, forEpub: Boolean) {
        _phase.value = ReadingSearchPhase.NeedsIndex(docId, forEpub)
        _sheetVisible.value = true
        requestFieldRelease()
        indexPromptIsExplicit = false
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
        indexPromptIsExplicit = true
        return true
    }

    /**
     * The toolbar overflow's "Rebuild index": prompt for a rebuild of the document THIS SESSION is
     * addressing. Returns `false` when there is no session (nothing to rebuild); when a build is
     * already running it raises the sheet on the in-progress build instead of restarting it.
     *
     * Classic had to start the `SearchIndex` Activity for this (`Search.kt:269-280`); the in-sheet
     * panel that replaced it is one call away, and it already words itself as a rebuild when the
     * document has a working index. So this is a redirect into the existing pipeline, not a feature.
     */
    fun requestRebuildIndex(): Boolean {
        val p = _phase.value
        // Already building: show the build in progress rather than restarting it. The sheet is a
        // NON-modal `BottomSheetScaffold`, so the toolbar's overflow stays tappable during indexing —
        // and overwriting the phase to `NeedsIndex` here would orphan `onIndexingFinished`'s
        // `as? Indexing` guard. The real build's completion would be swallowed, the sheet would sit on
        // the prompt, and accepting again would start a SECOND concurrent build. Every phase-related
        // transition names its IME instruction explicitly rather than relying on what a predecessor
        // left standing, and this one is no exception: the sheet is showing progress, nothing is to be
        // typed, so release.
        if (p is ReadingSearchPhase.Indexing) {
            _sheetVisible.value = true
            requestFieldRelease()
            return true
        }
        val docId = docIdOf(p) ?: return false
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
     * The active window, or the document shown in it, changed while search mode is open. The phases
     * that describe what will be searched NEXT follow it; the ones that describe work already done
     * or in flight do not (F44/B3):
     *
     * - [ReadingSearchPhase.Form] and a non-explicit [ReadingSearchPhase.NeedsIndex] re-resolve;
     * - [ReadingSearchPhase.Results] keeps the document its rows came from, so a result tap still
     *   opens the document the hit was found in;
     * - [ReadingSearchPhase.Indexing] keeps the document whose build is running;
     * - an [SearchKind.Unavailable] new document leaves the session untouched — there is nothing to
     *   show for it, and [submit] reports it honestly if the user then searches.
     */
    fun activeDocumentChanged() {
        val p = _phase.value
        val refreshable = p is ReadingSearchPhase.Form ||
            (p is ReadingSearchPhase.NeedsIndex && !indexPromptIsExplicit)
        if (!refreshable) return
        when (val kind = searchKindFor(resolveDoc())) {
            SearchKind.Unavailable -> Unit
            is SearchKind.NeedsIndex -> _phase.value = ReadingSearchPhase.NeedsIndex(kind.docId, kind.forEpub)
            is SearchKind.Bible -> _phase.value = ReadingSearchPhase.Form(kind.docId, forEpub = false)
            is SearchKind.Epub -> _phase.value = ReadingSearchPhase.Form(kind.docId, forEpub = true)
        }
    }

    /**
     * Called when the indexing job reports done. On success the search runs automatically if a query is
     * already waiting; otherwise the form takes over. On failure we fall back to the prompt rather than
     * pretending the document is searchable.
     */
    fun onIndexingFinished(indexDone: Boolean) {
        val p = _phase.value as? ReadingSearchPhase.Indexing ?: return
        // This is the only IME-instruction producer that is NOT driven by a gesture on the search UI —
        // a JSword WorkListener fires it whenever the build happens to finish. Search mode deliberately
        // outlives the sheet, so by then the user may be typing a note in a WebView editor. Touching the
        // IME at all would then steal their focus or hide their keyboard (spec §4's constraint, arriving
        // through the focus channel rather than the padding one). If they are no longer watching this
        // session, the session changes phase silently.
        val stillWatching = _sheetVisible.value
        if (!indexDone) {
            _phase.value = ReadingSearchPhase.NeedsIndex(p.docId, p.forEpub)
            if (stillWatching) requestFieldRelease()
            return
        }
        val q = queries.query.value.trim()
        if (q.isEmpty()) {
            _phase.value = ReadingSearchPhase.Form(p.docId, p.forEpub)
            _sheetVisible.value = false
            if (stillWatching) requestFieldFocus()
        } else {
            runSearch(p.docId, p.forEpub, q, touchIme = stillWatching)
        }
    }

    /** The settings sheet closed: re-run if there is something to re-run (spec §2), against the
     *  document the active window shows now — same rule as [submit], including its
     *  [keepIndexingInFlight] exception. */
    fun settingsClosed() {
        if (_phase.value == ReadingSearchPhase.Closed) return
        val q = queries.query.value.trim()
        if (q.isEmpty()) return
        if (keepIndexingInFlight()) return
        when (val kind = searchKindFor(resolveDoc())) {
            SearchKind.Unavailable -> onUnavailable()
            is SearchKind.NeedsIndex -> promptIndexImplicitly(kind.docId, kind.forEpub)
            is SearchKind.Bible -> runSearch(kind.docId, false, q)
            is SearchKind.Epub -> runSearch(kind.docId, true, q)
        }
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
        lastResults = null
        _imeRequest.value = null
        indexPromptIsExplicit = false
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
