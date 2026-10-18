/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.epubSearchRunFor
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchResultsController
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchKind
import net.bible.sharedcore.search.SearchQueryController
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.forEpubOf
import net.bible.sharedcore.search.searchKindFor
import net.bible.sharedcore.search.searchTranslationIds
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.book.Book

/** The search services [ReadingSearchState] builds its controllers over (Koin singletons on Android). */
@kotlin.native.HiddenFromObjC
class ReadingSearchServices(
    val bible: BibleSearchService,
    val epub: EpubSearchService,
    val resultsCache: SearchResultsCache,
    val index: SearchIndexService,
)

/**
 * The reading view's search, in common code: the search *settings* state (word mode, Bible
 * section, translations, the EPUB word mode, whether the settings sheet is open), the session
 * controller, both result controllers, the index-build feed and the run / leave logic, so Android
 * and iOS share them. The settings persist through [AppSettings] under the keys the classic search
 * Activities use, byte for byte.
 *
 * Settings are plain Compose `State` rather than a `SearchFormController`: the reading view's
 * current book moves as the user reads, so the book name is read fresh per search instead.
 *
 * Everything the platform alone can do (the document being read, JSword lookups, showing a result,
 * the job feed) comes in through [platform] and [indexJobs]; [onLeaveFullScreen] is the host's
 * lambda (the toolbar that hosts the search field is dropped in full screen). [scope] is the host's
 * scope (feed handling hops onto it); [resultsScope] runs the result searches (Android passes the
 * activity's `lifecycleScope`, so a destroyed activity cancels a search in flight). [pollPause] is
 * the wait between post-build `indexStatus` reads.
 *
 * The saved-state behaviour is unchanged from the Android host that used to own all of this: every
 * member is held here, in the object that outlives the composition, and nothing is
 * `rememberSaveable`.
 */
@kotlin.native.HiddenFromObjC
class ReadingSearchState(
    private val settings: AppSettings,
    val scope: CoroutineScope,
    internal val platform: ReadingSearchPlatform,
    private val indexJobs: SearchIndexJobs,
    services: ReadingSearchServices,
    internal val strings: () -> Strings,
    private val onLeaveFullScreen: () -> Unit,
    resultsScope: CoroutineScope = scope,
    private val pollPause: suspend () -> Unit = { delay(SEARCH_INDEX_POLL_INTERVAL_MS) },
) {
    private val bibleSearchService: BibleSearchService = services.bible
    internal val searchIndexService: SearchIndexService = services.index

    val searchType: MutableState<SearchType> = mutableStateOf(SearchType.ALL_WORDS)
    val searchSection: MutableState<SearchBibleSection> = mutableStateOf(SearchBibleSection.ALL)
    val searchTranslations: MutableState<List<String>> = mutableStateOf(emptyList())

    /** The Bibles the settings sheet offers, as (initials, abbreviation). */
    val searchAvailableTranslations: MutableState<List<Pair<String, String>>> = mutableStateOf(emptyList())

    /** Whether the modal search-settings sheet is open. */
    val searchSettingsOpen: MutableState<Boolean> = mutableStateOf(false)

    /**
     * The EPUB word mode, seeded from the key the standalone EPUB search Activities share. The
     * backing flow is private: callers go through [persistEpubSearchMode] so the settings key is
     * always written too. Re-read with [reloadEpubSearchMode] on every search open, because the
     * store is shared and this value is only a starting point.
     */
    private val _epubSearchMode = MutableStateFlow(loadEpubSearchMode())
    val epubSearchMode: StateFlow<EpubSearchMode> = _epubSearchMode.asStateFlow()

    /** Persists the word mode via the classic wire format ([toClassicSearchTypeName]). */
    fun persistEpubSearchMode(mode: EpubSearchMode) {
        _epubSearchMode.value = mode
        settings.setString(EPUB_SEARCH_TYPE_KEY, mode.toClassicSearchTypeName())
    }

    /** Re-reads the EPUB word mode from the shared settings key. */
    fun reloadEpubSearchMode() {
        _epubSearchMode.value = loadEpubSearchMode()
    }

    private fun loadEpubSearchMode(): EpubSearchMode =
        epubSearchModeFromClassicName(settings.getString(EPUB_SEARCH_TYPE_KEY))

    /** Applies and persists a translation choice made in the settings sheet (comma-joined, as classic). */
    fun setSearchTranslations(ids: List<String>) {
        searchTranslations.value = ids
        settings.setString(SEARCH_TRANSLATIONS_KEY, ids.joinToString(","))
    }

    /** The persisted translation choice, restricted to [installed] initials; empty when none is saved. */
    fun loadPersistedTranslations(installed: Set<String>): List<String> {
        val saved = settings.getString(SEARCH_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split(",").filter { it in installed }
    }

    // ---- the search session ---------------------------------------------------------------------

    /**
     * Query text + the recent-terms MRU. Persistence is under the SAME settings key the search
     * Activities use, **newline**-separated because a query may contain a comma, unlike the
     * translation list, which is comma-joined.
     */
    val queries = SearchQueryController(
        persistRecentTerms = { terms -> settings.setString(SEARCH_RECENT_TERMS_KEY, terms.joinToString("\n")) },
        loadRecentTerms = { loadRecentSearchTerms() },
    )

    private fun loadRecentSearchTerms(): List<String> {
        val saved = settings.getString(SEARCH_RECENT_TERMS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split("\n").filter { it.isNotBlank() }
    }

    /** Whether the toolbar field's recent-terms dropdown is open (state-IN, see [ReadingSearchBarState]). */
    val searchRecentMenuOpen = MutableStateFlow(false)

    /**
     * Whether the toolbar's search field currently holds focus. Read by the Android activity to
     * decide whether the IME padding applies: keying that on search mode being ACTIVE would suppress
     * the padding for a WebView note editor opened while search mode is still on.
     */
    val searchFieldFocused = MutableStateFlow(false)

    /**
     * Which result rows are expanded, and the result list's scroll position: both held HERE rather
     * than remembered inside the sheet's composition, so they survive the sheet being closed and
     * reopened. The scroll position is a `mutableStateOf` holder so [runSearch] can replace it (a
     * new query must not inherit the previous query's offset), and the sheet reads it inside
     * composition.
     */
    val searchResultsExpanded = mutableStateMapOf<String, Boolean>()
    val searchResultsListState = mutableStateOf(LazyListState())

    /**
     * The SWORD results themselves. [controller] deliberately does not own results; it owns the
     * session. The cache is the shared F26 same-request cache.
     */
    val searchResults = SearchResultsController(services.bible, resultsScope, services.resultsCache)

    /**
     * The EPUB results, the counterpart of [searchResults]: no result cache (F26 is SWORD-only) and
     * no candidate/translation state, because an EPUB search targets exactly one document.
     */
    val epubSearchResults = EpubSearchResultsController(
        scope = resultsScope,
        service = services.epub,
        onSelect = ::onEpubSearchResultSelected,
    )

    /** Index-build progress rows; only `onHide` differs from the Activity path: it closes the sheet. */
    val searchIndexProgress = SearchIndexProgressController(onHide = { controller.closeSheet() })

    private val _searchUnavailableDocName = MutableStateFlow<String?>(null)

    /**
     * Non-null while a "cannot be searched" message is waiting to be shown: the display name of the
     * document `onUnavailable` fired for. The reading view turns it into a snackbar and calls
     * [searchUnavailableMessageShown] to clear it.
     */
    val searchUnavailableDocName: StateFlow<String?> = _searchUnavailableDocName.asStateFlow()

    /** Clears [searchUnavailableDocName] once the snackbar has started showing it. */
    fun searchUnavailableMessageShown() {
        _searchUnavailableDocName.value = null
    }

    /**
     * The search session state machine. Every effect it needs is injected here, which keeps the
     * machine itself platform-free and host-testable; see [ReadingSearchController].
     */
    val controller = ReadingSearchController(
        resolveDoc = { platform.documentInfo(platform.currentDocument()) },
        onUnavailable = { _searchUnavailableDocName.value = platform.currentDocument()?.name.orEmpty() },
        onLeaveFullScreen = onLeaveFullScreen,
        onStartIndexing = { docId -> startSearchIndexing(docId) },
        // Every document type reaches here: an EPUB runs `runEpubSearch`, everything else the SWORD `runSearch`.
        onRunSearch = { docId, query, forEpub -> if (forEpub) runEpubSearch(docId, query) else runSearch(docId, query) },
        // F100: an unindexed translation in the persisted selection prompts, and arms the Task 11
        // chain ([searchSelectorPendingIds]) for the whole selection, so every unindexed one is built
        // before the search runs, exactly as a results-selector choice does.
        firstUnindexedInSelection = {
            val selection = searchTranslations.value
            bibleSearchService.unindexedAmong(selection).firstOrNull()?.also { searchSelectorPendingIds = selection }
        },
        // An F100 prompt that was abandoned must not leave its chain armed for an unrelated later build.
        onSelectionPromptDropped = { searchSelectorPendingIds = null },
        // Typing a reference ("1 joh 3 16") jumps there in the active window. In-window navigation: no history pop.
        tryOpenReference = { query -> platform.tryOpenReference(query) },
        onReferenceOpened = { leaveSearch() },
        queries = queries,
    )

    /** Phase-derived inputs for [searchBar], pre-combined so [searchBarCore] stays inside `combine`'s typed overload. */
    private data class SearchBarPhaseInfo(val resultsAvailable: Boolean, val forEpub: Boolean)

    private val searchBarPhaseInfo: StateFlow<SearchBarPhaseInfo> = controller.phase
        .map { p ->
            SearchBarPhaseInfo(
                // Deliberately not `&& !sheetVisible`: while the sheet is open the button merely
                // re-raises an already-raised sheet, whereas gating on visibility would make the
                // leading icon change identity every time the sheet is dragged.
                resultsAvailable = p is ReadingSearchPhase.Results,
                forEpub = forEpubOf(p) == true,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, SearchBarPhaseInfo(false, false))

    /** The query/history/IME half of [searchBar]: everything that is not derived from the phase. */
    private val searchBarCore: StateFlow<ReadingSearchBarState?> = combine(
        controller.searchModeActive,
        queries.query,
        queries.recentTerms,
        searchRecentMenuOpen,
        controller.imeRequest,
    ) { active, query, recentTerms, recentMenuOpen, imeRequest ->
        if (!active) null
        else ReadingSearchBarState(
            query = query,
            recentTerms = recentTerms,
            recentMenuOpen = recentMenuOpen,
            imeRequest = imeRequest,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** What the toolbar renders in search mode, or `null` when search mode is off. */
    val searchBar: StateFlow<ReadingSearchBarState?> =
        combine(searchBarCore, searchBarPhaseInfo) { core, info ->
            core?.copy(resultsAvailable = info.resultsAvailable, forEpub = info.forEpub)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The toolbar search row's callbacks. Every one goes to [controller], [queries] or this state's
     * own menu/settings flags; none touches the pane subtree.
     */
    val searchBarCallbacks = ReadingSearchBarCallbacks(
        onQueryChange = { queries.setQuery(it) },
        onSubmit = { controller.submit() },
        onRecentTermsOpen = { searchRecentMenuOpen.value = true },
        onRecentTermsDismiss = { searchRecentMenuOpen.value = false },
        // Picking a recent term fills the field, it does not search by itself.
        onRecentTermSelected = { term ->
            searchRecentMenuOpen.value = false
            queries.setQuery(term)
        },
        onOpenSettings = { searchSettingsOpen.value = true },
        onClose = { leaveSearch() },
        onImeRequestHandled = { controller.imeRequestHandled() },
        onFieldFocusChanged = { searchFieldFocused.value = it },
        onRebuildIndex = { controller.requestRebuildIndex() },
        onShowResults = { controller.showResults() },
    )

    /** Whether the results sheet is up; feeds the reading view's bottom-sheet scaffold. */
    val searchSheetVisible: StateFlow<Boolean> get() = controller.sheetVisible

    // ---- index feed state -------------------------------------------------------------------------

    /** The ONE live job-feed subscription (null while no build is being watched). */
    private var stopIndexFeed: (() -> Unit)? = null
    private val searchIndexFinishedJobs = HashSet<Any>()
    private var searchIndexPoll: IndexPollDecision? = null
    private var searchIndexDocument: Book? = null

    // ---- entry points -------------------------------------------------------------------------------

    /**
     * Opens search for the active window's document. [seedQuery] is for the entry points that
     * bypass the form (text-selection "Search ...", Strong's find-all) and runs immediately.
     *
     * [preDecorated] marks [seedQuery] as ALREADY run through `SearchControl.decorateSearchString`
     * (the text-selection entry point) rather than a raw user query. Strong's find-all does NOT set
     * this: it goes through [openSearchStrongs], which needs its own flag.
     */
    fun openSearch(seedQuery: String? = null, preDecorated: Boolean = false) {
        refreshSearchTranslations()
        // The MRU store is shared with the classic/EPUB search Activities and this state outlives any
        // single search, so it is re-read here (a search performed there meanwhile would otherwise be
        // clobbered by this state's stale in-memory list on its next `recordRecentTerm`).
        queries.reloadRecentTerms()
        // Same for the EPUB word mode: a key the two standalone EPUB search Activities also write.
        reloadEpubSearchMode()
        // BOTH one-shot flags are keyed to the query TEXT, which a seedless open does not change, so
        // only a call that brings a new seed may replace them (Strong's find-all -> results ->
        // seedless re-entry -> settings close must re-run the very same `strong:H430` text as Strong's).
        if (seedQuery != null) {
            // Stored TRIMMED: the controller and [buildSearchRequest] compare against the trimmed
            // query, and entry point 7's seed always carries a leading space.
            searchPreDecoratedQuery = seedQuery.trim().takeIf { preDecorated }
            searchStrongsQuery = null
        }
        controller.open(seedQuery)
    }

    /**
     * Strong's find-all. [ref] is the RAW Strong's number/reference: [buildSearchRequest] forces
     * ANY_WORDS for it and decorates `strong:$ref` exactly once. [translationIds] is the Strong's
     * Bible selection the caller already resolved.
     *
     * Returns whether it actually opened the search; `false` means the caller must fall back to its
     * own route. It declines when the active window's document is an EPUB (Strong's find-all is a
     * Bible concept) and when the active document cannot be searched at all.
     */
    fun openSearchStrongs(ref: String, translationIds: List<String>): Boolean {
        val current = platform.currentDocument()
        if (platform.documentInfo(current)?.isEpub == true) return false
        if (searchKindFor(platform.documentInfo(current)) == SearchKind.Unavailable) return false
        refreshSearchTranslations()
        queries.reloadRecentTerms()
        searchTranslations.value = translationIds
        val query = "strong:$ref"
        searchStrongsQuery = query.trim()
        searchPreDecoratedQuery = null
        controller.open(query)
        return true
    }

    /**
     * The three-stage back for search, in one call: after a result tap the first press brings the
     * results list back; then a press closes the results/index sheet (keeping the query and the
     * results); the last leaves search mode. Returns whether the press was consumed.
     */
    fun closeSearchIfOpen(): Boolean {
        if (controller.closeSheet()) return true
        if (controller.reopenResultsOnBack()) return true
        if (controller.closeSearchMode()) {
            onSearchModeClosed()
            return true
        }
        return false
    }

    /** Leaves search entirely (the toolbar's close affordance, and the index prompt's Cancel). */
    fun leaveSearch() {
        controller.closeSheet()
        if (controller.closeSearchMode()) onSearchModeClosed()
    }

    /**
     * Common tail of leaving search mode: drops the index feed, both one-shot decoration flags, the
     * pending selector-index chain and the two per-open UI flags, so a later [openSearch] never
     * inherits a stale override, and re-entering search mode does not come up with the recent-terms
     * dropdown already down or the settings sheet already open.
     */
    private fun onSearchModeClosed() {
        stopSearchIndexFeed()
        searchPreDecoratedQuery = null
        searchStrongsQuery = null
        searchSelectorPendingIds = null
        searchRecentMenuOpen.value = false
        searchSettingsOpen.value = false
        // The rows belong to the session that just ended. The query is cleared by the controller.
        searchResults.clear()
        epubSearchResults.clear()
        // Every exit from search mode routes through here, which is why the field's focus flag is
        // reset HERE and not at the call sites: the live back-button path would otherwise leave it
        // true with no field on screen, and the activity keys its IME padding on it.
        searchFieldFocused.value = false
    }

    /**
     * Reloads the Bibles offered by the settings sheet and re-seeds the selection from the
     * persisted choice, falling back to the document being read. Called on every [openSearch],
     * since the read document changes.
     */
    private fun refreshSearchTranslations() {
        val bibles = platform.installedBibles()
        searchAvailableTranslations.value = bibles
        val fallback = platform.currentDocument()?.initials?.let { listOf(it) } ?: emptyList()
        searchTranslations.value = loadPersistedTranslations(bibles.map { it.first }.toSet()).ifEmpty { fallback }
    }

    /**
     * Set for the duration of a query seeded already-decorated by the caller (the text-selection
     * "Search..." action): [buildSearchRequest] then forces IDENTITY decorators (ANY_WORDS + ALL)
     * instead of the live settings-sheet values, so re-decorating reproduces the caller's string
     * rather than decorating it twice. Matches the query text, not a boolean, so editing the query
     * drops it with no explicit reset. Cleared when search mode closes.
     */
    private var searchPreDecoratedQuery: String? = null

    /**
     * Set for the duration of a Strong's find-all query: [buildSearchRequest] compares
     * `query == searchStrongsQuery` to force ANY_WORDS and set [SearchRequest.isStrongsSearch], so a
     * re-run from the selector or the settings sheet keeps behaving like a Strong's search while
     * editing the query drops the flag on its own. Cleared when search mode closes.
     */
    private var searchStrongsQuery: String? = null

    /**
     * The FULL set of translation ids chosen in the results document selector (or the persisted
     * selection, for F100), held only while the index-prompt chain [onSearchIndexWorkEvent] drives
     * is running for them; `null` in every other flow. Cleared in [onSearchModeClosed].
     */
    var searchSelectorPendingIds: List<String>? = null
        private set

    /** The request the most recent [runSearch] built (test-only read; the results controller keeps it private). */
    var lastSearchRequestForTest: SearchRequest? = null
        private set

    // ---- running a search ----------------------------------------------------------------------------

    /**
     * Builds the SWORD request for [query] against [docId]. Both one-shot modes force ANY_WORDS and
     * the ALL section (pre-decorated because that is the identity decoration, Strong's because that
     * is what classic chose). Public for tests; production reaches it via [runSearch].
     */
    fun buildSearchRequest(docId: String, query: String): SearchRequest {
        // Both sides trimmed: `query` arrives trimmed from the controller, but a seed from entry
        // point 7 carries a leading space, and a direct caller may hand an untrimmed query.
        val trimmedQuery = query.trim()
        val preDecorated = trimmedQuery == searchPreDecoratedQuery
        val strongsSearch = trimmedQuery == searchStrongsQuery
        return SearchRequest(
            query = query,
            searchType = if (preDecorated || strongsSearch) SearchType.ANY_WORDS else searchType.value,
            bibleSection = if (preDecorated || strongsSearch) SearchBibleSection.ALL else searchSection.value,
            // The persisted selection, plus the document this search is running for when that
            // document is already indexed (F44/B4). The flag is read for `docId`: after
            // `promptIndexFor` the phase addresses a translation the active window is not showing.
            translationIds = searchTranslationIds(
                persisted = searchTranslations.value,
                activeDocId = docId,
                activeIsIndexed = platform.documentInfo(platform.documentByInitials(docId))?.indexDone == true,
            ),
            currentBookName = platform.currentBookName(),
            isStrongsSearch = strongsSearch,
        )
    }

    /** Runs a SWORD query: new rows are new rows, so no stale expansion state or scroll offset survives. */
    fun runSearch(docId: String, query: String) {
        searchResultsExpanded.clear()
        // Only a genuine (re-)run reaches here (reopening the sheet for the query the results already
        // belong to is served by the controller without calling back), so the F25 scroll survival is
        // untouched.
        searchResultsListState.value = LazyListState()
        val request = buildSearchRequest(docId, query)
        lastSearchRequestForTest = request
        // The searched list and the USER's selection are different things (F44 fix round, I2): the
        // results sheet's selector must show, and persist on confirm, only what the user chose, not
        // the auto-appended active document. `ifEmpty` covers the case with no user selection at all.
        searchResults.run(request, userSelection = searchTranslations.value.ifEmpty { request.translationIds })
    }

    /**
     * An EPUB query, the counterpart of [runSearch]. Resets the sheet's scroll for the same reason;
     * deliberately does NOT clear [searchResultsExpanded] (EPUB rows carry no expansion state).
     */
    fun runEpubSearch(docId: String, query: String) {
        searchResultsListState.value = LazyListState()
        // The text-selection entry point seeds an ALREADY Lucene-decorated query, meaningless to
        // FTS5; the flag is matched the same trimmed, self-clearing way as on the SWORD path.
        val params = epubSearchRunFor(
            query = query,
            preDecorated = query.trim() == searchPreDecoratedQuery,
            storedMode = epubSearchMode.value,
        )
        epubSearchResults.run(docId, params.query, params.mode)
    }

    /**
     * An EPUB result row tap. The row's [keyId] is the `BookAndKey.osisRef`
     * (`"<initials>:<fragmentId>"`); [epubKeyFor] strips the initials prefix and re-resolves the
     * inner osisRef, re-attaching [ordinal] so the reader lands on the hit. There is no Activity to
     * start or finish: this only navigates the active window and drops the sheet.
     */
    fun onEpubSearchResultSelected(keyId: String, ordinal: Int) {
        val docId = (controller.phase.value as? ReadingSearchPhase.Results)?.docId
        if (docId == null) {
            Log.w(TAG, "onEpubSearchResultSelected: dropped '$keyId' - no Results phase to resolve a docId from")
            return
        }
        val book = platform.documentByInitials(docId)
        if (book == null) {
            Log.w(TAG, "onEpubSearchResultSelected: dropped '$keyId' - '$docId' is not an installed document")
            return
        }
        try {
            val key = epubKeyFor(book, docId, keyId, ordinal)
            platform.showInActiveWindow(book, key)
            controller.onResultOpened()
        } catch (e: Exception) {
            Log.e(TAG, "onEpubSearchResultSelected: bad key '$keyId' in $docId", e)
        }
    }

    /**
     * A SWORD result row tap. Resolves the key pair and navigates the active window directly:
     * navigation is complete at that call, and there is no Activity to bring to front or finish.
     * Hides the sheet via [ReadingSearchController.onResultOpened] (not the whole session), which
     * also arms the F83 reopen on the next back press; search mode and the `Results` phase stay.
     */
    fun onSearchResultSelected(referenceName: String, translationId: String?) {
        val book = resolveSearchResultBook(translationId)
        if (book == null) {
            Log.w(TAG, "onSearchResultSelected: dropped '$referenceName' - no book resolved for translationId '$translationId'")
            return
        }
        try {
            val key = book.getKey(referenceName)
            platform.showInActiveWindow(book, key)
            controller.onResultOpened()
        } catch (e: Exception) {
            Log.e(TAG, "onSearchResultSelected: bad key '$referenceName' in ${book.initials}", e)
        }
    }

    /** "Open results in a window": every displayed match as one multi-document in the links window, then drop the sheet. */
    fun openSearchResultsInWindow() {
        val list = BookAndKeyList()
        for (row in searchResults.displayed.value) {
            for (match in row.matches) {
                val book = resolveSearchResultBook(match.translationId) ?: continue
                val key = try {
                    book.getKey(row.referenceName)
                } catch (e: Exception) {
                    Log.e(TAG, "openSearchResultsInWindow: bad key '${row.referenceName}' in ${book.initials}", e)
                    continue
                }
                list.addAll(BookAndKey(key, book))
            }
        }
        platform.showInLinksWindow(list)
        controller.closeSheet()
    }

    /** The target book by initials, falling back to the first selected translation. */
    private fun resolveSearchResultBook(translationId: String?): Book? =
        translationId?.let { platform.swordBookByInitials(it) }
            ?: searchResults.selectedTranslations.value.firstOrNull()?.let { platform.swordBookByInitials(it) }

    /**
     * The results document selector's choice: [SearchResultsController.selectTranslations] persists
     * the choice and re-runs, except when it holds a translation with no usable index, in which case
     * the session is redirected to prompt for the FIRST unindexed one and [searchSelectorPendingIds]
     * remembers the whole set so [onSearchIndexWorkEvent] can chain to any others.
     */
    fun onSearchTranslationsChosen(ids: List<String>) {
        searchTranslations.value = ids
        searchResults.selectTranslations(ids) { unindexed, chosenIds ->
            searchSelectorPendingIds = chosenIds
            controller.promptIndexFor(unindexed.first())
        }
    }

    /**
     * Whether the settings sheet must show the EPUB word-mode row rather than the Bible form,
     * decided from the LIVE target (what `settingsClosed()` will then apply), not from the phase.
     */
    fun searchSettingsForEpub(): Boolean =
        platform.documentInfo(platform.currentDocument())?.isEpub == true

    /** The active window's book name, for the Bible settings form. */
    fun currentBookName(): String = platform.currentBookName()

    // ---- the index build -------------------------------------------------------------------------------

    /**
     * Starts an index build and begins feeding [searchIndexProgress] from the job feed. The
     * subscription lives only while a build is being watched: it is made here (at most one, however
     * often the sheet is closed and the build prompted again) and removed by [stopSearchIndexFeed]
     * once the build resolves or search mode closes.
     */
    private fun startSearchIndexing(docId: String) {
        searchIndexDocument = platform.documentByInitials(docId)
        searchIndexPoll = IndexPollDecision()
        searchIndexFinishedJobs.clear()
        // This state owns one progress controller for its lifetime, so a previous build's failure the
        // user walked away from must not raise its error dialog over this brand-new prompt.
        searchIndexProgress.reset()
        // Subscribe FIRST, then build: the first work events of a fast build must have a listener.
        if (stopIndexFeed == null) {
            stopIndexFeed = indexJobs.subscribe { job, isFinished -> onSearchIndexWorkEvent(job, isFinished) }
        }
        searchIndexService.createIndex(docId)
        refreshSearchIndexJobs()
    }

    private fun stopSearchIndexFeed() {
        stopIndexFeed?.invoke()
        stopIndexFeed = null
    }

    /**
     * Drops the job-feed subscription. The platform feed (`JobManager` on Android) is process-wide,
     * so an in-flight index build would otherwise keep this state alive through it after its owner
     * is gone.
     */
    fun dispose() = stopSearchIndexFeed()

    /**
     * A work event of the job feed. Its listener fires off the main thread, so everything is hopped
     * onto [scope] (Main). The blocking poll of the Activity is a coroutine pause around the pure
     * [IndexPollDecision]; what happens after a successful build is
     * [ReadingSearchController.onIndexingFinished]'s decision.
     */
    private fun onSearchIndexWorkEvent(job: Any, isFinished: () -> Boolean) {
        scope.launch {
            refreshSearchIndexJobs()
            // The feed is GLOBAL: `job` is any job (a module download finishing mid-build must not
            // resolve the index build early), so the gate is "no job is still running".
            if (!shouldResolveIndexBuild(isFinished(), indexJobs.isEveryJobFinished())) return@launch
            if (!searchIndexFinishedJobs.add(job)) return@launch
            val poll = searchIndexPoll ?: return@launch
            val indexDone = awaitIndexDone(
                poll = poll,
                // Through the platform's document mapping, NOT a bare `indexStatus` read: the flag lies for EPUBs.
                indexDone = { searchIndexDocument?.let { platform.documentInfo(it)?.indexDone } == true },
                pause = pollPause,
            )
            // Re-read rather than reuse the gate's answer: the poll may have straddled a new job's start.
            if (!indexDone && indexJobs.isEveryJobFinished()) searchIndexProgress.showError()
            stopSearchIndexFeed()
            // A selection holding more than one unindexed translation chains to the next one instead
            // of letting `onIndexingFinished` re-run against only the just-built translation.
            val next = nextSelectorIndexPrompt(searchSelectorPendingIds, indexDone) { bibleSearchService.unindexedAmong(it) }
            if (next != null) {
                controller.promptIndexFor(next, keepInterceptedSearch = true)
                return@launch
            }
            searchSelectorPendingIds = null
            controller.onIndexingFinished(indexDone)
        }
    }

    private fun refreshSearchIndexJobs() {
        searchIndexProgress.setJobs(indexJobs.snapshot())
    }

    internal companion object {
        const val SEARCH_TRANSLATIONS_KEY = "search_selected_translations"
        const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"
        const val TAG = "ComposeReadingViewHost"
    }
}

/** `SearchIndexProgressComposeActivity`'s post-finish poll interval; its ~4 s "no tasks" reveal is deliberately not reproduced. */
internal const val SEARCH_INDEX_POLL_INTERVAL_MS = 2_000L
