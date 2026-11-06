@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.search

import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.sharedui.search.SEARCH_RECENT_TERMS_KEY
import net.bible.sharedui.search.seedSearchTranslations
import net.bible.sharedui.search.parseSavedSearchTranslations
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.index.isInProgress
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.search.isSearchIndexJob
import net.bible.sharedui.search.nav.EpubSearchFormDeps
import net.bible.sharedui.search.nav.EpubSearchSetup
import net.bible.sharedui.search.nav.IndexOutcome
import net.bible.sharedui.search.nav.IndexTarget
import net.bible.sharedui.search.nav.SearchFormDeps
import net.bible.sharedui.search.nav.SearchFormSetup
import net.bible.sharedui.search.nav.SearchIndexProgressDeps
import net.bible.sharedui.search.nav.SearchIndexPromptDeps
import net.bible.sharedui.search.nav.SearchNavDeps
import net.bible.sharedui.search.nav.SearchSubmission
import net.bible.sharedui.search.nav.TranslationSelection
import net.bible.sharedui.search.nav.toReadingSearchRequest
import net.bible.sharedui.strings.Strings
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus

private const val TAG_SEARCH = "SearchCompose"
private const val TAG_SEARCH_INDEX = "SearchIndexProgCompose"
private const val TAG_EPUB_SEARCH = "EpubSearchCompose"

/**
 * The search cluster's deps builder and host baggage: the Bible search form, the index prompt and its progress
 * screen, and the EPUB search form.
 *
 * Ported from classic SearchComposeActivity / SearchIndexComposeActivity / SearchIndexProgressComposeActivity.
 * Everything that reaches JSword, app settings or the window/link controllers lives here as a lambda on
 * [SearchNavDeps]. Every controller and service sits behind a provider, so constructing the host touches no
 * database.
 */
@kotlin.native.HiddenFromObjC
class SearchHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val appSettingsProvider: () -> AppSettings,
    private val notificationPermission: NotificationPermission,
    private val links: ExternalLinks,
    private val searchControlProvider: () -> SearchControl,
    private val bibleSearchServiceProvider: () -> BibleSearchService,
    private val searchIndexServiceProvider: () -> SearchIndexService,
    private val windowControlProvider: () -> WindowControl,
    private val pageControlProvider: () -> PageControl,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val searchControl: SearchControl by lazy(searchControlProvider)
    private val bibleSearchService: BibleSearchService by lazy(bibleSearchServiceProvider)
    private val searchIndexService: SearchIndexService by lazy(searchIndexServiceProvider)
    private val windowControl: WindowControl by lazy(windowControlProvider)
    private val pageControl: PageControl by lazy(pageControlProvider)

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
        historyBackEnabled = shell.separateHistoryBack,
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
        epubSearch = EpubSearchFormDeps(
            prepare = { prepareEpubSearchForm() },
            loadMode = { loadEpubSearchMode() },
            saveMode = { mode -> saveEpubSearchMode(mode) },
            askBeforeOpeningLink = { links.askBeforeOpening },
            onOpenExternal = { links.open(it) },
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
     * installed Bible as `initials to abbreviation` sorted by abbreviation, and the indexed persisted
     * selection plus the current document (which may itself need an index).
     */
    internal fun loadSearchTranslations(currentDoc: Book? = currentSearchDocument): TranslationSelection {
        val bibles = SwordDocumentFacade.bibles
            .filterIsInstance<SwordBook>()
            .sortedBy { it.abbreviation }
        val isBible = currentDoc?.bookCategory == BookCategory.BIBLE
        val saved = appSettings.getString(SearchControl.SEARCH_TRANSLATIONS_PREF, null)
        val remembered = parseSavedSearchTranslations(saved, bibles.map { it.initials }.toSet())
        // The cheap in-memory index status, not a validation of the index store (this runs on the main
        // thread for every remembered translation); `submitSearch` still validates the whole selection.
        val indexed = if (isBible) {
            bibles.filter { it.initials in remembered && it.indexStatus == IndexStatus.DONE }.map { it.initials }.toSet()
        } else emptySet()
        return TranslationSelection(
            available = bibles.map { it.initials to it.abbreviation },
            selected = seedSearchTranslations(
                currentInitials = currentDoc?.initials,
                isBible = isBible,
                saved = saved,
                indexedBibles = indexed,
            ),
        )
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
            appSettings.setString(SearchControl.SEARCH_TRANSLATIONS_PREF, ids.joinToString(","))
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
        val continuation = request.toReadingSearchRequest(
            decoratedQuery = bibleSearchService.decorate(request),
            documentId = currentSearchDocument?.initials,
        )
        if (!bibleSearchService.validateIndex(request)) {
            val firstUnindexed = request.translationIds.firstOrNull {
                SwordDocumentFacade.getDocumentByInitials(it)?.indexStatus != IndexStatus.DONE
            }
            return SearchSubmission.NeedsIndex(firstUnindexed, continuation)
        }
        return SearchSubmission.Results(continuation)
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
        // Read the job state AFTER attaching, so nothing can slip between the read and the listener.
        // A running job will deliver its own finished event to the listener. With none running, the
        // build this screen waits on already ended before we attached (#2632 re-entry) and its last
        // event is gone: report that settled state once, so `awaitIndexed` routes on the real status.
        onJobs(indexJobSnapshot())
        if (isAllIndexJobsFinished) onJobFinished()
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
            if (job.isSearchIndexJob() && job.isFinished && finishedIndexJobs.add(job)) onJobFinished()
        }
    }

    /** The index sheet filters the generic feed; the download status screen keeps other running jobs. */
    private fun indexJobSnapshot(indexOnly: Boolean = true): List<ProgressJob> {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            if (job.isFinished || (indexOnly && !job.isSearchIndexJob())) continue
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
        onJobs(indexJobSnapshot(indexOnly = false))
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(indexJobSnapshot(indexOnly = false)) }
            }
            override fun workStateChanged(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(indexJobSnapshot(indexOnly = false)) }
            }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    /** Classic's `isAllJobsFinished`. */
    private val isAllIndexJobsFinished: Boolean
        get() {
            val it = JobManager.iterator()
            while (it.hasNext()) {
                val job = it.next()
                if (job.isSearchIndexJob() && !job.isFinished) return false
            }
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
            document?.indexStatus.isInProgress ->
                IndexOutcome.STILL_RUNNING
            isAllIndexJobsFinished -> {
                Log.e(TAG_SEARCH_INDEX, "Index finished but document's index is invalid")
                IndexOutcome.FAILED
            }
            else -> IndexOutcome.STILL_RUNNING
        }
    }

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

}
