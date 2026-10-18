package net.bible.sharedui.app.search

import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.service.download.FakeBookFactory
import net.bible.service.history.HistoryManager
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchResultsController
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.search.nav.EpubSearchFormDeps
import net.bible.sharedui.search.nav.EpubSearchResultsDeps
import net.bible.sharedui.search.nav.EpubSearchSetup
import net.bible.sharedui.search.nav.EpubSearchTarget
import net.bible.sharedui.search.nav.IndexOutcome
import net.bible.sharedui.search.nav.IndexTarget
import net.bible.sharedui.search.nav.SearchFormDeps
import net.bible.sharedui.search.nav.SearchFormSetup
import net.bible.sharedui.search.nav.SearchIndexProgressDeps
import net.bible.sharedui.search.nav.SearchIndexPromptDeps
import net.bible.sharedui.search.nav.SearchNavDeps
import net.bible.sharedui.search.nav.SearchResultsDeps
import net.bible.sharedui.search.nav.SearchSubmission
import net.bible.sharedui.search.nav.TranslationSelection
import net.bible.sharedui.strings.Strings
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.index.search.SearchType as JSwordSearchType

private const val TAG_SEARCH = "SearchCompose"
private const val TAG_SEARCH_INDEX = "SearchIndexProgCompose"
private const val TAG_SEARCH_RESULTS = "SearchResultsCompose"
private const val TAG_EPUB_SEARCH = "EpubSearchCompose"
private const val TAG_EPUB_SEARCH_RESULTS = "EpubSearchResultsCompose"

internal const val SEARCH_SELECTED_TRANSLATIONS_KEY = "search_selected_translations"
internal const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

/**
 * The search cluster's deps builder and host baggage: the Bible search form, the index prompt and its progress
 * screen, the Bible results, and the EPUB search form and results.
 *
 * Ported from classic SearchComposeActivity / SearchIndexComposeActivity / SearchIndexProgressComposeActivity.
 * Everything that reaches JSword, app settings or the window/link controllers lives here as a lambda on
 * [SearchNavDeps]. Every controller and service sits behind a provider, so constructing the host touches no
 * database.
 *
 * [bookByInitials] and [showInReadingOverride] are seams for tests: they default to the JSword facade and the active
 * window's page manager.
 */
class SearchHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val appSettingsProvider: () -> AppSettings,
    private val userNotifierProvider: () -> UserNotifier,
    private val notificationPermission: NotificationPermission,
    private val links: ExternalLinks,
    private val searchControlProvider: () -> SearchControl,
    private val bibleSearchServiceProvider: () -> BibleSearchService,
    private val searchIndexServiceProvider: () -> SearchIndexService,
    private val windowControlProvider: () -> WindowControl,
    private val pageControlProvider: () -> PageControl,
    private val searchResultsCacheProvider: () -> SearchResultsCache,
    private val epubSearchServiceProvider: () -> EpubSearchService,
    private val linkControlProvider: () -> LinkControl,
    private val fakeBookFactoryProvider: () -> FakeBookFactory,
    /** The ACTIVITY's history manager (the history traversal is per host instance). */
    private val historyManagerProvider: () -> HistoryManager,
    private val bookByInitials: (String) -> Book? = { initials ->
        SwordDocumentFacade.getDocumentByInitials(initials)?.takeIf { it is SwordBook }
    },
    private val showInReadingOverride: ((Book, Key) -> Unit)? = null,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val searchControl: SearchControl by lazy(searchControlProvider)
    private val bibleSearchService: BibleSearchService by lazy(bibleSearchServiceProvider)
    private val searchIndexService: SearchIndexService by lazy(searchIndexServiceProvider)
    private val windowControl: WindowControl by lazy(windowControlProvider)
    private val pageControl: PageControl by lazy(pageControlProvider)
    private val searchResultsCache: SearchResultsCache by lazy(searchResultsCacheProvider)
    private val epubSearchService: EpubSearchService by lazy(epubSearchServiceProvider)
    private val linkControl: LinkControl by lazy(linkControlProvider)
    private val fakeBookFactory: FakeBookFactory by lazy(fakeBookFactoryProvider)

    /** Makes [book] at [key] the active window's page. */
    private fun showInReading(book: Book, key: Key) {
        val override = showInReadingOverride
        if (override != null) override(book, key)
        else windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }

    /** Classic `SearchComposeActivity`'s `windowControl.activeWindowPageManager.currentPage.currentDocument`. */
    private val currentSearchDocument get() = windowControl.activeWindowPageManager.currentPage.currentDocument

    /**
     * The graph's search deps. Built on every host launch, so every slot is a lambda over a provider;
     * [navController] is unused today and kept so that `AppHosts.graphs` can treat every host the same way.
     */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(navController: NavHostController): SearchNavDeps = SearchNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        setHistoryRoute = { owner, route -> shell.setHistoryRoute(owner, route) },
        clearHistoryRoute = { owner -> shell.clearHistoryRoute(owner) },
        goBackInHistory = { shell.goBackInHistory() },
        searchForm = SearchFormDeps(
            prepare = { restoredBibleBook -> prepareSearchForm(restoredBibleBook) },
            loadTranslations = { loadSearchTranslations() },
            controllerFor = { currentBookName -> buildSearchFormController(currentBookName) },
            submit = { request -> submitSearch(request) },
        ),
        searchIndex = SearchIndexPromptDeps(
            resolve = { searchDocument -> resolveIndexTarget(searchDocument) },
            createIndex = { documentId -> searchIndexService.createIndex(documentId) },
            title = strings.searchIndex,
        ),
        searchIndexProgress = SearchIndexProgressDeps(
            // Classic SearchIndexProgressComposeActivity.kt:82, which is a suspend call: the host owns the
            // scope so the graph's deps slot can stay a plain () -> Unit.
            requestNotificationPermission = {
                shell.hostScope.launch { notificationPermission.request() }
            },
            observeJobs = { onJobs, onJobFinished -> observeIndexJobs(onJobs, onJobFinished) },
            awaitIndexed = { documentId -> awaitIndexed(documentId) },
            title = strings.searchIndex,
        ),
        searchResults = SearchResultsDeps(
            controllerFor = { buildSearchResultsController() },
            isScriptureReference = { searchText -> isScriptureReference(searchText) },
            openScriptureReference = { searchText -> openScriptureReference(searchText) },
            title = { total, selectedCount -> strings.multiSearchResults(total, selectedCount) },
            showError = { showSearchError() },
            openReference = { referenceName, translationId, selectedTranslations ->
                openSearchResult(referenceName, translationId, selectedTranslations)
            },
            openResultsInWindow = { rows, selectedTranslations ->
                openSearchResultsInAWindow(rows, selectedTranslations)
            },
        ),
        epubSearch = EpubSearchFormDeps(
            prepare = { prepareEpubSearchForm() },
            loadMode = { loadEpubSearchMode() },
            saveMode = { mode -> saveEpubSearchMode(mode) },
            modeWireName = { mode -> mode.toClassicSearchTypeName() },
            askBeforeOpeningLink = { links.askBeforeOpening },
            onOpenExternal = { links.open(it) },
        ),
        epubSearchResults = EpubSearchResultsDeps(
            resolve = { searchDocument -> resolveEpubSearchTarget(searchDocument) },
            modeFromWireName = { name -> epubSearchModeFromClassicName(name) },
            controllerFor = { documentId -> buildEpubSearchResultsController(documentId) },
            logSearch = { documentId, searchText, mode ->
                Log.i(TAG_EPUB_SEARCH_RESULTS, "Searching '$searchText' ($mode) in $documentId")
            },
            title = { resultCount, documentAbbreviation ->
                epubSearchResultsTitle(resultCount, documentAbbreviation)
            },
            showError = { showSearchError() },
        ),
    )

    // --- Search cluster host baggage -----------------------------------------------------------

    /**
     * Classic `SearchComposeActivity.onCreate`'s preamble: the `search-last-used` stamp, the "nothing to search"
     * gate (null here == classic's immediate `finish()`) and the two strings the form needs. [restoredBibleBook]
     * is the route's own `bibleBook` argument, which classic read from its history-restore extra before falling
     * back to the current book name.
     */
    internal fun prepareSearchForm(restoredBibleBook: String?): SearchFormSetup? {
        Log.i(TAG_SEARCH, "Displaying Compose search view")
        appSettings.setLong("search-last-used", currentTimeMillis())
        val currentDoc = currentSearchDocument ?: return null
        return SearchFormSetup(
            title = strings.searchIn(currentDoc.abbreviation),
            currentBookName = restoredBibleBook ?: searchControl.currentBookName,
        )
    }

    /**
     * Classic's `onCreate` seed AND its `onResume` re-seed in one call (they read the same two things): every
     * installed Bible as `initials to abbreviation` sorted by abbreviation, and the persisted selection falling
     * back to the current document.
     */
    internal fun loadSearchTranslations(): TranslationSelection {
        val bibles = SwordDocumentFacade.bibles
            .filterIsInstance<SwordBook>()
            .sortedBy { it.abbreviation }
        val saved = loadSelectedSearchTranslations()
        val fallback = currentSearchDocument?.initials?.let { listOf(it) } ?: emptyList()
        return TranslationSelection(
            available = bibles.map { it.initials to it.abbreviation },
            selected = saved.ifEmpty { fallback },
        )
    }

    /** Classic Search.loadSelectedTranslations: only initials that still resolve to a Bible. */
    private fun loadSelectedSearchTranslations(): List<String> {
        val saved = appSettings.getString(SEARCH_SELECTED_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        val available = SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().map { it.initials }.toSet()
        return saved.split(",").filter { it in available }
    }

    /** Classic Search.loadRecentTerms (F22): newline-separated, since a query may contain commas. */
    private fun loadRecentSearchTerms(): List<String> {
        val saved = appSettings.getString(SEARCH_RECENT_TERMS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split("\n").filter { it.isNotBlank() }
    }

    /** The form's controller: host-side only because all three of its lambdas reach app settings. */
    internal fun buildSearchFormController(currentBookName: String) = SearchFormController(
        currentBookName = currentBookName,
        persistTranslations = { ids ->
            appSettings.setString(SEARCH_SELECTED_TRANSLATIONS_KEY, ids.joinToString(","))
        },
        persistRecentTerms = { terms ->
            appSettings.setString(SEARCH_RECENT_TERMS_KEY, terms.joinToString("\n"))
        },
        loadRecentTerms = { loadRecentSearchTerms() },
    )

    /**
     * Classic `SearchComposeActivity.onSubmit`'s JSword half. It returns the DECISION rather than navigating:
     * both of classic's arms were `startActivity(...); finish()`, and in the graph both are
     * `navController.navigate(...)`, which only the graph can do -- see [SearchSubmission].
     */
    internal fun submitSearch(request: SearchRequest): SearchSubmission {
        if (!bibleSearchService.validateIndex(request)) {
            // The first selected translation without a usable index (classic parity).
            val firstUnindexed = request.translationIds.firstOrNull {
                SwordDocumentFacade.getDocumentByInitials(it)?.indexStatus != IndexStatus.DONE
            }
            return SearchSubmission.NeedsIndex(firstUnindexed)
        }
        return SearchSubmission.Results(
            decoratedQuery = bibleSearchService.decorate(request),
            // Section-less highlight query (classic Search.onSearch -> SEARCH_HIGHLIGHT_TEXT).
            highlightText = searchControl.highlightSearchString(
                request.query,
                request.searchType.toClassicJSwordSearchType(),
            ),
            // Classic passed the CURRENT PAGE's document here, not one of the selected translations.
            searchDocument = currentSearchDocument?.initials,
            translationIds = request.translationIds,
        )
    }

    /** :sharedCore SearchType -> classic JSword SearchType (kept consistent with BibleSearchServiceImpl). */
    private fun SearchType.toClassicJSwordSearchType(): JSwordSearchType = when (this) {
        SearchType.ALL_WORDS -> JSwordSearchType.ALL_WORDS
        SearchType.ANY_WORDS -> JSwordSearchType.ANY_WORDS
        SearchType.PHRASE -> JSwordSearchType.PHRASE
    }

    /**
     * Classic `SearchIndexComposeActivity.documentToIndex` (`:44-49`) plus its `hasIndex` read. Null == classic's
     * "nothing to index -> finish()". The resolved initials are what the graph overrides the next hop's
     * `searchDocument` with, so an argument-free prompt still hands the progress destination a concrete document.
     */
    internal fun resolveIndexTarget(searchDocument: String?): IndexTarget? {
        val doc =
            if (!searchDocument.isNullOrEmpty()) SwordDocumentFacade.getDocumentByInitials(searchDocument)
            else pageControl.currentPageManager.currentPage.currentDocument
        doc ?: return null
        return IndexTarget(
            documentId = doc.initials,
            documentName = doc.name,
            isRebuild = searchIndexService.hasIndex(doc.initials),
            engineChanged = searchIndexService.hasLegacyIndex(doc.initials),
        )
    }

    /**
     * Classic's `finishedJobs: HashSet<Progress>` -- the once-per-job de-duplication, keyed on JSword `Progress`
     * identities, which is why it cannot move into the graph.
     */
    private val finishedIndexJobs = HashSet<Progress>()

    /**
     * Classic `SearchIndexProgressComposeActivity`'s `onResume`/`onPause` PAIR (`:80-96`) as one call returning
     * its own undo: the initial `refreshJobs()`, then `JobManager.addWorkListener`, and the returned lambda is
     * `JobManager.removeWorkListener`. The graph drives it from a `LifecycleResumeEffect`, so the removal happens
     * on pause AND on disposal -- leaking the listener would leak the destination it closes over.
     */
    internal fun observeIndexJobs(
        onJobs: (List<ProgressJob>) -> Unit,
        onJobFinished: () -> Unit,
    ): () -> Unit {
        onJobs(indexJobSnapshot())
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onIndexWorkEvent(ev, onJobs, onJobFinished)
            override fun workStateChanged(ev: WorkEvent) = onIndexWorkEvent(ev, onJobs, onJobFinished)
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    /** Classic's `uiHandler.post`: deferred to the main thread, never run inline on JSword's worker thread. */
    private fun onIndexWorkEvent(
        ev: WorkEvent,
        onJobs: (List<ProgressJob>) -> Unit,
        onJobFinished: () -> Unit,
    ) {
        shell.hostScope.launch(Dispatchers.Main) {
            onJobs(indexJobSnapshot())
            val job = ev.job
            if (job.isFinished && finishedIndexJobs.add(job)) onJobFinished()
        }
    }

    /** Classic's `refreshJobs()` snapshot. */
    private fun indexJobSnapshot(): List<ProgressJob> {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            snapshot.add(
                ProgressJob(
                    id = job.hashCode().toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        return snapshot
    }

    /**
     * `PROGRESS_STATUS_PATTERN`'s `observeJobs`: classic `ProgressStatusComposeActivity`'s `onResume`/`onPause`
     * PAIR (`:82-98`) as one call, reusing [indexJobSnapshot] -- the snapshot it builds is generic over JSword's
     * `JobManager`, not index-specific. Unlike [observeIndexJobs], there is no `onJobFinished` callback and no
     * `finishedIndexJobs` de-duplication: [net.bible.sharedui.download.nav.ProgressStatusDeps] has no "indexing
     * finished" routing to drive, only a job-list refresh. Public because the download cluster still uses it
     * from the activity until its own lift.
     */
    fun observeProgressStatusJobs(onJobs: (List<ProgressJob>) -> Unit): () -> Unit {
        onJobs(indexJobSnapshot())
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(indexJobSnapshot()) }
            }
            override fun workStateChanged(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(indexJobSnapshot()) }
            }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    /** Classic's `isAllJobsFinished`. */
    private val isAllIndexJobsFinished: Boolean
        get() {
            val it = JobManager.iterator()
            while (it.hasNext()) if (!it.next().isFinished) return false
            return true
        }

    /**
     * Classic `jobFinished`'s wait (`:132-137`) -- "give the document up to 12 secs to reload: the Progress
     * declares itself finished before the index status has been changed" -- and nothing else. Six rounds of a
     * 2 second pause, off the main thread; what it returns is only what the routing decision needs, the decision
     * itself stays in the graph.
     */
    internal suspend fun awaitIndexed(documentId: String?): IndexOutcome = withContext(Dispatchers.IO) {
        val document = SwordDocumentFacade.getDocumentByInitials(documentId)
        var attempts = 0
        while ((document == null || IndexStatus.DONE != document.indexStatus) && attempts++ < 6) {
            delay(2000)
        }
        when {
            IndexStatus.DONE == document?.indexStatus -> {
                Log.i(TAG_SEARCH_INDEX, "Index created")
                if (document.isEpub) IndexOutcome.INDEXED_EPUB else IndexOutcome.INDEXED
            }
            isAllIndexJobsFinished -> {
                Log.e(TAG_SEARCH_INDEX, "Index finished but document's index is invalid")
                IndexOutcome.FAILED
            }
            else -> IndexOutcome.STILL_RUNNING
        }
    }

    // --- Search RESULTS host baggage (classic SearchResultsComposeActivity) -----------------------

    /** Classic's `SearchResultsController(bibleSearchService, lifecycleScope, searchResultsCache)`. */
    internal fun buildSearchResultsController(): SearchResultsController {
        Log.i(TAG_SEARCH_RESULTS, "Displaying Compose search results view")
        return SearchResultsController(bibleSearchService, shell.hostScope, searchResultsCache)
    }

    /**
     * The DECIDING half of classic `SearchResultsComposeActivity.kt:90-95`'s ref short circuit: does this search
     * text parse as a plain scripture reference? Pure -- `LinkControl.resolveRef` reads
     * `WindowControl.defaultBibleDoc` and calls `SwordContentFacade.resolveRef`, and neither mutates anything --
     * which is what lets the graph ask it during composition, as classic asked it before `setContent`.
     */
    internal fun isScriptureReference(searchText: String): Boolean =
        linkControl.resolveRef(searchText) != null

    /**
     * The ACTING half: open the reference in the reading view and undo the one history item that opening it
     * pushes.
     *
     * **Why ONE pop, where classic did two.** Classic popped `{this results screen's item, the launcher's item}`,
     * and in the graph the second of those does not exist:
     *
     *  - `ActivityBase.startActivity` makes a direct `HistoryManager.recordIfCreated` call only when the `open val`
     *    `integrateWithHistoryManager` is true, and this host deliberately does not override it (the activity's
     *    `setHistoryRoute` drives the `var` instead), so it is permanently false. And the in-graph
     *    `Search form -> results` hop is a `navigate`, not a `startActivity`, so there is nothing to gate anyway:
     *    **no launcher item is ever pushed for these screens.** Classic's second pop would therefore eat the entry
     *    BELOW -- the `KeyHistoryItem` `MainBibleActivity` pushed for the user's reading position when it launched
     *    the search. Every "the search text was actually a reference" jump would silently lose the way back.
     *  - The one item that IS pushed comes from this very call: `tryToOpenRef` -> `showLink(forceOpenHere = true)`
     *    -> `setCurrentDocumentAndKey` -> `setKey` -> a direct `HistoryManager.recordIfCreated` call ->
     *    `HistoryManager.createHistoryItem`, which for this host builds an `IntentHistoryItem` from
     *    `isIntegrateWithHistoryManager` + the activity's `intentForHistoryList`. The graph publishes this
     *    destination's route immediately before calling this, precisely so that item exists and names the results
     *    screen -- so exactly one pop removes exactly what classic's first pop removed, and the reading position
     *    below it survives.
     *
     * The `canGoBack()` guard is not decoration: `HistoryManager.popHistoryItem` is a bare `Stack.pop()`, which
     * throws on an empty stack -- and the stack it pops is the ACTIVE window's, which `showLink` can have changed
     * under us when the user has "open links in a new window" configured. Classic had the same hazard, twice over.
     */
    internal fun openScriptureReference(searchText: String) {
        if (!linkControl.tryToOpenRef(searchText)) return
        val history = historyManagerProvider()
        if (history.canGoBack()) {
            history.popHistoryItem()
        }
    }

    /** Classic's error path (`:126-136`): a transient toast, then out -- never a dialog. */
    private fun showSearchError() {
        userNotifierProvider().showInfo(strings.errorExecutingSearch)
    }

    /**
     * Classic `resolveBook` (`:166-168`): the row's own translation, else the first of the current selection.
     * [selectedTranslations] is passed in because the graph owns that state now (it is the results controller's
     * live selection), where classic kept a mutable field.
     */
    private fun resolveSearchResultBook(
        translationId: String?,
        selectedTranslations: List<String>,
    ): Book? =
        translationId?.let { bookByInitials(it) }
            ?: selectedTranslations.firstOrNull()?.let { bookByInitials(it) }

    /**
     * Classic `onSelect` (`:171-183`), unchanged apart from the dropped `intent.putExtra(LIST_POSITION, ...)`: the
     * list position now lives in the destination's own `rememberSaveable`, so there is no intent left to write it
     * onto.
     *
     * The target is the reading destination, reached through [HostShell.openReading]. On Android that is the host
     * starting ITSELF by Intent with `CLEAR_TOP or SINGLE_TOP`, which the manifest's `singleTop` delivers to
     * `onNewIntent` on this same instance; nothing here awaits a RESULT, so the one thing an Intent to self cannot
     * do (bring an answer back to the caller) is not asked of it.
     */
    internal fun openSearchResult(
        referenceName: String,
        translationId: String?,
        selectedTranslations: List<String>,
    ) {
        val book = resolveSearchResultBook(translationId, selectedTranslations) ?: return
        try {
            val key = book.getKey(referenceName)
            showInReading(book, key)
            shell.openReading()
        } catch (e: Exception) {
            Log.e(TAG_SEARCH_RESULTS, "Could not resolve key '$referenceName' in ${book.initials}", e)
        }
    }

    /** Classic `openResultsInAWindow` (`:208-224`) minus its `finish()`, which is the graph's pop. */
    internal fun openSearchResultsInAWindow(rows: List<SwordResultRow>, selectedTranslations: List<String>) {
        val list = BookAndKeyList()
        for (row in rows) {
            for (match in row.matches) {
                val book = resolveSearchResultBook(match.translationId, selectedTranslations) ?: continue
                val key = try {
                    book.getKey(row.referenceName)
                } catch (e: Exception) {
                    Log.e(TAG_SEARCH_RESULTS, "openResultsInAWindow: bad key '${row.referenceName}' in ${book.initials}", e)
                    continue
                }
                list.addAll(BookAndKey(key, book))
            }
        }
        linkControl.showLink(fakeBookFactory.multiDocument, list)
    }

    // --- EPUB search host baggage (classic EpubSearch/EpubSearchResultsComposeActivity) -----------

    /**
     * Classic `EpubSearchComposeActivity.onCreate`'s preamble. Null is the one deliberate difference: classic
     * wrote `currentPage.currentDocument!!` and would have thrown.
     */
    internal fun prepareEpubSearchForm(): EpubSearchSetup? {
        Log.i(TAG_EPUB_SEARCH, "Displaying Compose EPUB search view")
        appSettings.setLong("search-last-used", currentTimeMillis())
        val doc = pageControl.currentPageManager.currentPage.currentDocument
        if (doc == null) {
            Log.w(TAG_EPUB_SEARCH, "No current document to search in; leaving the EPUB search form")
            return null
        }
        return EpubSearchSetup(
            title = strings.searchIn(doc.abbreviation),
            documentId = doc.initials,
        )
    }

    /** The persisted word-mode, through the shared classic wire format (`EpubSearchModeWire.kt`). */
    private fun loadEpubSearchMode(): EpubSearchMode =
        epubSearchModeFromClassicName(appSettings.getString(EPUB_SEARCH_TYPE_KEY))

    private fun saveEpubSearchMode(mode: EpubSearchMode) {
        appSettings.setString(EPUB_SEARCH_TYPE_KEY, mode.toClassicSearchTypeName())
    }

    /**
     * Classic `EpubSearchResultsComposeActivity.onCreate`'s document resolution and its two guards (`:205-227`) as
     * one call. Null == "not installed, or not an epub" (classic's `Log.e` + `finish()`); the `!!` on the current
     * Bible's document is a `?: return null` for the same reason [prepareEpubSearchForm]'s is.
     */
    internal fun resolveEpubSearchTarget(searchDocument: String?): EpubSearchTarget? {
        // Classic `EpubSearchResultsComposeActivity.onCreate`'s FIRST line (`:63`), see [prepareEpubSearchForm]
        // and [buildSearchResultsController] for the two siblings.
        Log.i(TAG_EPUB_SEARCH_RESULTS, "Displaying Compose EPUB search results view")
        val docId =
            if (searchDocument.isNullOrEmpty())
                windowControl.activeWindowPageManager.currentBible.currentDocument?.initials
                    ?: run {
                        Log.e(TAG_EPUB_SEARCH_RESULTS, "No searchDocument and no current Bible document; aborting")
                        return null
                    }
            else searchDocument
        val doc = Books.installed().getBook(docId)
        if (doc == null || !doc.isEpub) {
            Log.e(TAG_EPUB_SEARCH_RESULTS, "Document ${doc?.name} is not an epub; aborting")
            return null
        }
        return if (!epubSearchService.isIndexed(docId)) EpubSearchTarget.NeedsIndex(docId)
        else EpubSearchTarget.Ready(documentId = docId, documentAbbreviation = doc.abbreviation)
    }

    /** The EPUB results controller, with classic's `onSelect` (`:280-291`) already bound to [documentId]. */
    internal fun buildEpubSearchResultsController(documentId: String) = EpubSearchResultsController(
        shell.hostScope,
        epubSearchService,
        onSelect = { keyId, ordinal -> openEpubSearchResult(documentId, keyId, ordinal) },
    )

    /** Classic `EpubSearchResults.onSelect`: re-resolve the hit's key, then open the reading view. */
    private fun openEpubSearchResult(documentId: String, keyId: String, ordinal: Int) {
        val book = Books.installed().getBook(documentId) ?: return
        try {
            val key = epubKeyFor(book, documentId, keyId, ordinal)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            shell.openReading()
        } catch (e: Exception) {
            Log.e(TAG_EPUB_SEARCH_RESULTS, "Could not resolve key '$keyId' in $documentId", e)
        }
    }

    /**
     * Classic's results title INCLUDING its "+" overflow affordance (`:253-257`): the service caps at
     * `MAX_SEARCH_RESULTS + 1`, so a size over the cap is detectable and shown as "5000+".
     */
    internal fun epubSearchResultsTitle(resultCount: Int, documentAbbreviation: String): String {
        val resultAmount =
            if (resultCount > SearchControl.MAX_SEARCH_RESULTS) "${SearchControl.MAX_SEARCH_RESULTS}+"
            else resultCount.toString()
        return strings.searchWithResults2(resultAmount, documentAbbreviation)
    }
}
