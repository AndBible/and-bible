package net.bible.sharedui.app.download

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import net.bible.android.control.document.DocumentChanges
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.document.canDelete
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.LanguageGrouping
import net.bible.android.control.download.repoIdentity
import net.bible.android.control.event.UserMessages
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.download.BadDocumentAction
import net.bible.android.view.activity.download.DocumentConfiguration
import net.bible.android.view.activity.download.DownloadProgressBridge
import net.bible.android.view.activity.download.RowDownloadStatus
import net.bible.android.view.activity.download.installedDocument
import net.bible.android.view.activity.download.isBadDocument
import net.bible.android.view.activity.download.isInstalled
import net.bible.android.view.activity.download.isRecommended
import net.bible.service.db.DatabaseContainer
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.download.DownloadManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.PseudoBook
import net.bible.service.download.RepoFactory
import net.bible.service.download.isPseudoBook
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.ProceedAnswer
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.ui.dialog.plainTextToHtml
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.installzip.InstallZipOpener
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.download.nav.CustomRepositoriesDeps
import net.bible.sharedui.download.nav.CustomRepositoryEditorDeps
import net.bible.sharedui.download.nav.CloudDocumentsDeps
import net.bible.sharedui.download.nav.DownloadDeps
import net.bible.sharedui.download.nav.DownloadNavDeps
import net.bible.sharedui.download.nav.ProgressStatusDeps
import net.bible.sharedui.docCategoryOf
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings
import okio.FileSystem
import okio.Path
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlin.random.Random

private const val TAG_DOWNLOAD = "DownloadNavHost"

/**
 * Per-SCREEN arrangement preference keys, classic `DownloadComposeActivity.kt:113-114`: sorting a download list by
 * size is a different intent from ordering the reading view's document picker, so they are deliberately NOT the
 * chooser's keys.
 */
private const val ARRANGEMENT_KEY = "download.arrangement"
private const val ARRANGEMENT_REMEMBER_KEY = "download.arrangement.remember"
private const val SELECTED_DOCUMENT_FILTER_KEY = "selected_document_filter_no"
private const val DO_NOT_ASK_KEY = "download_do_not_ask"

/** Classic's repository-staleness cache (`DownloadComposeActivity.kt:938-940`). */
private const val REPO_REFRESH_DATE = "repoRefreshDate"
private const val REPO_LIST_STALE_AFTER_DAYS: Long = 1
private const val MILLISECS_IN_DAY = 1000 * 60 * 60 * 24.toLong()

private const val DATA_URL = "https://andbible.github.io/data/"
private const val RECOMMENDED_JSON = "recommended_documents_v2.json"
private const val BAD_DOCS_JSON = "bad_documents.json"
private const val DEFAULT_JSON = "default_documents_v2.json"
private const val PSEUDO_BOOKS_JSON = "pseudo_books.json"

/**
 * The download cluster's deps builder and host baggage: the Download destination, the custom repository list and
 * editor, and Progress status. Ported from the classic `DownloadComposeActivity`, whose line numbers the comments
 * cite. Every line of the original names a JSword type, a network fetch or a `settings` key, which is why it is
 * lambdas of `DownloadDeps` rather than part of the graph.
 *
 * The cloud documents deps are built by [CloudDocumentsHost] and passed to [navDeps]. Every service sits behind a
 * provider, so constructing the host touches no database.
 */
class DownloadHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val platform: DownloadHostPlatform,
    private val notificationPermission: NotificationPermission,
    private val clipboard: ClipboardText,
    private val openInstallZip: InstallZipOpener,
    /** Progress status' running-job feed (the search host owns the index jobs it reports). */
    private val observeProgressJobs: ((List<ProgressJob>) -> Unit) -> () -> Unit,
    private val appSettingsProvider: () -> AppSettings,
    private val userNotifierProvider: () -> UserNotifier,
    private val directoriesProvider: () -> AppDirectories,
    private val environmentProvider: () -> AppEnvironment,
    private val coreStringsProvider: () -> CoreStrings,
    private val downloadHttpProvider: () -> DownloadHttp,
    private val bookInstallWatcherProvider: () -> BookInstallWatcher,
    private val downloadControlProvider: () -> DownloadControl,
    private val documentControlProvider: () -> DocumentControl,
    private val fakeBookFactoryProvider: () -> FakeBookFactory,
    private val customRepositoryServiceProvider: () -> CustomRepositoryService,
    private val documentSyncSettingsProvider: () -> DocumentSyncSettings,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val userNotifier: UserNotifier by lazy(userNotifierProvider)
    private val directories: AppDirectories by lazy(directoriesProvider)
    private val environment: AppEnvironment by lazy(environmentProvider)
    private val coreStrings: CoreStrings by lazy(coreStringsProvider)
    private val downloadHttp: DownloadHttp by lazy(downloadHttpProvider)
    private val bookInstallWatcher: BookInstallWatcher by lazy(bookInstallWatcherProvider)
    private val downloadControl: DownloadControl by lazy(downloadControlProvider)
    private val documentControl: DocumentControl by lazy(documentControlProvider)
    private val fakeBookFactory: FakeBookFactory by lazy(fakeBookFactoryProvider)
    private val customRepositoryService: CustomRepositoryService by lazy(customRepositoryServiceProvider)
    private val documentSyncSettings: DocumentSyncSettings by lazy(documentSyncSettingsProvider)

    private val swordDocumentInfoDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val downloadBookmarksDao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    /**
     * Classic `DownloadComposeActivity`'s own fields (`:139-166`) plus the controller they are memoised
     * alongside -- see [DownloadDeps.controllerFor] for why this whole session must survive `CustomRepositories`
     * sitting on top of its destination.
     */
    private class DownloadSession {
        lateinit var controller: DocumentSelectionController
        lateinit var downloadManager: DownloadManager
        lateinit var repoFactory: RepoFactory
        lateinit var genericFileDownloader: GenericFileDownloader
        val bridge = DownloadProgressBridge()

        /** Network-fetched configs (parity with classic `Ref<…>` fields). */
        var recommendedDocuments: DocumentConfiguration? = null
        var defaultDocuments: DocumentConfiguration? = null
        var badDocuments: DocumentConfiguration? = null
        var pseudoBooks: List<PseudoBook>? = null

        /** docId (`Book.repoIdentity`) -> Book, rebuilt on every (re)load. */
        var booksById: Map<String, Book> = emptyMap()

        /** The full loaded book list (for `findBookByInitials` in the auto-download extras). */
        var allBooks: List<Book> = emptyList()

        /** Current `DocRow`s host-side so progress updates can rebuild only the affected rows. */
        var currentRows: List<DocRow> = emptyList()

        /** groupingKey -> sort rank from `downloadControl.sortLanguages` (RelevantLanguageSorter). */
        var langRank: Map<String, Int> = emptyMap()
        val booksNotFound = ArrayList<String>()

        val hasErrors get() =
            genericFileDownloader.errors.isNotEmpty() || downloadManager.failedRepos.isNotEmpty()

        /**
         * The deletable subset [handleDownloadDelete] partitioned out (a live `documentControl.canDelete` recheck
         * the framework-free controller cannot do), parked here for [handleDownloadDeleteConfirmed]: the question
         * (the controller's `ConfirmDelete` dialog) and the answer are two different calls, and this carries the
         * partition between them without recomputing it (which would re-toast "can't delete" for the SAME
         * non-deletable documents a second time).
         */
        var pendingDelete: List<Book> = emptyList()
    }

    /**
     * `Download` has no re-entry of its own (its only child destination is `CustomRepositories`), so this is built
     * once and never dropped.
     *
     * It is a plain field, so it does NOT survive an Activity recreation (the host dies with the Activity), while
     * the arm's one-shot guards are `rememberSaveable` and DO -- see [sessionToken], which is what keeps the two
     * from disagreeing. The token therefore needs no place in the saved host state.
     */
    private var session: DownloadSession? = null

    /** The live download controller, or null before the destination has asked for one. */
    val currentController: DocumentSelectionController? get() = session?.controller

    /**
     * Identity of the CURRENT [session], handed to the graph as [DownloadDeps.sessionToken] and regenerated ONLY by
     * [downloadControllerFor] when it actually builds a new session. The value must be unique across host
     * instances, not merely within one, or a restored saveable key would match a token a rebuilt session happened
     * to reuse; a counter restarting at zero on every recreation would do exactly that, hence random.
     */
    private var sessionToken: String = newSessionToken()

    private fun newSessionToken(): String = "${currentTimeMillis()}-${Random.nextLong()}-${Random.nextLong()}"

    /**
     * Drives the `firstDownload` OK gate's enabled state: true once >=1 Bible is installed. Latches (classic
     * `okayButtonEnabled` never flips back off), fed by [downloadCompletionListener].
     *
     * A HOST field rather than a session one because [DownloadDeps] is built before any destination has asked for
     * a controller, so the flow has to exist ahead of the session.
     */
    private val downloadHasBible = MutableStateFlow(false)

    /** Mirror of classic FirstDownload's `JobManager` WorkListener: enable OK once a Bible lands. */
    private val downloadCompletionListener = object : WorkListener {
        override fun workProgressed(workEvent: WorkEvent) {
            if (workEvent.job.isFinished) updateHasBible()
        }

        // Never called by JSword in practice, so all the work is done in workProgressed (classic note).
        override fun workStateChanged(workEvent: WorkEvent) {}
    }

    /** Latching check: once a Bible is installed, OK stays enabled (classic `enableOkayButtonIfBibles`). */
    private fun updateHasBible() {
        if (!downloadHasBible.value) {
            downloadHasBible.value = Books.installed().getBooks().any { it.bookCategory == BookCategory.BIBLE }
        }
    }

    /**
     * Armed by [CustomRepositoryController.onChanged] when a repository was added, edited or deleted (F96, classic
     * reloaded after every visit), disarmed by [reloadDownloadCatalogueIfRequested]. Classic awaited that
     * Activity's result and then reloaded (`DownloadComposeActivity.kt:899-905`); an in-graph hop has no result to
     * await, so the "and then reloaded" half is carried here instead of being lost.
     */
    private var pendingDownloadCatalogueReload = false

    /**
     * Classic's own `downloadScope` (`DownloadComposeActivity.kt:167`), deliberately NOT the host scope:
     * `downloadControl.downloadDocument` is a SUSPEND call, so a cancellation between "user tapped" and "job
     * enqueued" would silently drop the download -- and leaving the screen is exactly when that happens.
     */
    private val downloadScope = CoroutineScope(Dispatchers.Default)

    /**
     * The download graph's deps. [repositoryEditorResults] is the activity's channel (its exit is a hard `error`,
     * see there); [cloudDocuments] come from [CloudDocumentsHost] since both destinations share one graph.
     */
    fun navDeps(
        navController: NavHostController,
        repositoryEditorResults: NavResultChannel<RepositoryResult>,
        cloudDocuments: CloudDocumentsDeps,
    ): DownloadNavDeps = DownloadNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        repositoryEditorResults = repositoryEditorResults,
        customRepositories = CustomRepositoriesDeps(
            // A per-entry factory, not host-memoised -- see CustomRepositoriesDeps.controllerFor: this destination
            // is the host's start destination on every live edge, so there is no sitting child for a memoised
            // controller to survive.
            controllerFor = { onDuplicate ->
                CustomRepositoryController(customRepositoryService, shell.hostScope).apply {
                    this.onDuplicate = onDuplicate
                    this.onChanged = { pendingDownloadCatalogueReload = true }
                }
            },
            title = strings.customRepositories,
            onDuplicate = { name -> UserMessages.toast(strings.duplicateCustomRepository(name)) },
        ),
        customRepositoryEditor = CustomRepositoryEditorDeps(
            controllerFor = { initial ->
                CustomRepositoryEditorController(customRepositoryService, shell.hostScope, initial)
            },
            // Classic's manifest gives BOTH Activities in this cluster the same android:label ("Custom
            // repositories"); one host now serves both.
            title = strings.customRepositories,
            initialFor = { id -> customRepositoryEditorInitialFor(id) },
            readClipboard = { clipboard.read() },
        ),
        download = DownloadDeps(
            // Host-memoised, not per-entry: the controller is only half of this screen's state -- the session
            // holds the loaded Books, the repoIdentity maps and the DocRow mirror every seam below reads -- and
            // CustomRepositories sits on top of this destination, disposing its composition. Same shape as
            // myDocumentsControllerFor.
            controllerFor = { initialTypeFilter -> downloadControllerFor(initialTypeFilter) },
            // Read by the arm AFTER controllerFor, so it is the token of whichever session that call returned or
            // built.
            sessionToken = { sessionToken },
            // Classic's manifest label AND the string its screen drew are both @string/download.
            title = strings.downloadTitle,
            topBarActions = { firstDownload -> DownloadOverflowMenu(navController, firstDownload) },
            askIfWantToProceed = { askIfWantToProceedWithDownload() },
            // Classic DownloadComposeActivity.kt:238, a suspend call: the host owns the scope so the graph's deps
            // slot can stay a plain () -> Unit, the same shape ProgressStatusDeps.requestNotificationPermission
            // uses.
            requestNotificationPermission = { shell.hostScope.launch { notificationPermission.request() } },
            refreshCatalogue = { refresh -> refreshDownloadCatalogue(refresh) },
            onAutoDownload = { documentIds, downloadRecommended ->
                handleAutoDownloadExtras(documentIds, downloadRecommended)
            },
            reloadCatalogueIfRequested = { reloadDownloadCatalogueIfRequested() },
            onCancelDownload = { docId -> cancelDownload(docId) },
            hasBible = downloadHasBible,
            subscribeDownloadProgress = { subscribeDownloadProgress() },
            subscribeMonitoring = { firstDownload -> subscribeDownloadMonitoring(firstDownload) },
            persistTypeFilter = { filter -> appSettings.setInt(SELECTED_DOCUMENT_FILTER_KEY, filter.ordinal) },
            initialTypeFilter = { addons -> initialTypeFilter(addons) },
        ),
        progressStatus = ProgressStatusDeps(
            title = strings.progressStatus,
            // Classic ProgressStatusComposeActivity.kt:84, a suspend call: the host owns the scope so the graph's
            // deps slot can stay a plain () -> Unit.
            requestNotificationPermission = { shell.hostScope.launch { notificationPermission.request() } },
            observeJobs = { onJobs -> observeProgressJobs(onJobs) },
        ),
        cloudDocuments = cloudDocuments,
    )

    /**
     * [id] resolves through the same [customRepositoryService] the list uses; `null` (a NEW repository, plan D9)
     * maps to a blank [RepositoryResult] -- classic's `newItem()` -- rather than throwing, and an id that no longer
     * resolves (deleted from under the editor) falls back to the same blank state instead of crashing the editor
     * arm.
     */
    private suspend fun customRepositoryEditorInitialFor(id: Long?): RepositoryResult {
        val repo = id?.let { wanted -> customRepositoryService.list().find { it.id == wanted } }
        return RepositoryResult(repository = repo)
    }

    /**
     * Classic `DownloadComposeActivity`'s `controller` (`:196-227`) and the `onCreate` setup around it
     * (`:229-231`), memoised on [session].
     */
    private fun downloadControllerFor(initialTypeFilter: DocTypeFilter): DocumentSelectionController {
        session?.let { return it.controller }

        val session = DownloadSession()
        session.genericFileDownloader = GenericFileDownloader(
            shell.hostScope,
            appSettings,
            userNotifier,
            coreStrings,
            onErrorsChange = {},
            http = downloadHttp,
            copyFile = { from, to -> platform.copyFile(from, to) },
        )
        session.downloadManager = DownloadManager(
            appSettings,
            bookInstallWatcher,
            session.genericFileDownloader,
            environment,
        ) { }
        session.repoFactory = RepoFactory(session.downloadManager, environment)

        session.controller = DocumentSelectionController(
            // classic sortLanguages order (RelevantLanguageSorter): rank by the precomputed index.
            langComparator = Comparator { a, b ->
                (session.langRank[a.groupingKey] ?: Int.MAX_VALUE)
                    .compareTo(session.langRank[b.groupingKey] ?: Int.MAX_VALUE)
            },
            onSelect = { docId -> handleDownloadSelection(docId) },
            onDelete = { ids -> handleDownloadDelete(ids) },
            onConfirmDownload = { docId -> session.booksById[docId]?.let { doDownload(session, it) } },
            onConfirmDelete = { handleDownloadDeleteConfirmed(session) },
            onConfirmDeleteIndex = { docId -> handleDownloadDeleteIndexConfirmed(session, docId) },
            onAbout = { docId -> handleDownloadAbout(docId) },
            onUnlock = { docId -> handleDownloadUnlock(docId) },
            onStickyLanguage = { lang -> appSettings.setString("selected_language_code", lang?.code) },
            // Every key applies here: this list is the only one with an install size, and the only one that loads
            // the recommended-documents config.
            applicableSortKeys = DocSortKey.entries.toSet(),
            applicableGroupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY),
            storedArrangement = if (appSettings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true))
                appSettings.getString(ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = appSettings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                appSettings.setBoolean(ARRANGEMENT_REMEMBER_KEY, remember)
                appSettings.setString(ARRANGEMENT_KEY, encoded)
            },
            // Round 17e-1 final-review fix (I3): debounces the drag-reorder commit.
            scope = shell.hostScope,
        )
        session.controller.setTypeFilter(initialTypeFilter)
        this.session = session
        // A NEW session, so a new identity: every one-shot the arm stamped against the previous one (in this host
        // or in the one a recreation replaced) is now stale by construction. See [sessionToken].
        sessionToken = newSessionToken()
        return session.controller
    }

    /**
     * Classic `initialTypeFilter()` (`:909-916`) minus its `"type"` extra (plan D1): the tree's only
     * `putExtra("type"` is gone, so only the `addons` branch and the persisted fallback remain.
     */
    internal fun initialTypeFilter(addons: Boolean): DocTypeFilter =
        if (addons) DocTypeFilter.ADDON
        else DocTypeFilter.entries.getOrElse(appSettings.getInt(SELECTED_DOCUMENT_FILTER_KEY, 0)) { DocTypeFilter.ALL }

    /**
     * Classic `askIfWantToProceed()` (`:460-478`), which can answer NO. The question itself is
     * [DocumentSelectionController.askProceed], a Compose dialog driven by `DocumentSelectionDialog
     * .ProceedWithDownload`; this host method keeps exactly what the framework-free `:sharedCore` controller
     * cannot do -- the "download_do_not_ask" pref read/write (D5).
     */
    private suspend fun askIfWantToProceedWithDownload(): Boolean = withContext(Dispatchers.Main) {
        if (appSettings.getBoolean(DO_NOT_ASK_KEY, false)) {
            true
        } else {
            when (session?.controller?.askProceed()) {
                ProceedAnswer.YES -> true
                ProceedAnswer.DONT_ASK_AGAIN -> {
                    appSettings.setBoolean(DO_NOT_ASK_KEY, true)
                    true
                }
                null -> false
            }
        }
    }

    /**
     * Classic's `downloadDocJson()` + `loadDocuments()` + `updateLastRepoRefreshDate()` block (`:239-243` on entry,
     * `:286-297` on pull-to-refresh) as one call.
     *
     * [refresh] means FORCE a repository re-fetch (the gesture). The staleness cache stays here, where `settings`
     * lives: a non-forced call still refreshes when the repo list is old, which is exactly what classic's
     * `val refresh = isRepoBookListOld` did on entry.
     */
    private suspend fun refreshDownloadCatalogue(refresh: Boolean) {
        val session = session ?: return
        downloadDocJson(session)
        val doRefresh = refresh || isRepoBookListOld
        loadDownloadDocuments(session, doRefresh)
        if (doRefresh) appSettings.setLong(REPO_REFRESH_DATE, currentTimeMillis())
    }

    /**
     * Classic `onCustomRepositories()`'s `loadDocuments(true)` (`:904`), verbatim: a FORCED reload, not
     * [refreshDownloadCatalogue]'s staleness-cached one -- adding or editing a repository is exactly what changes
     * the downloadable set, and a `refresh = false` load may serve the day-old cached catalogue. No
     * `downloadDocJson()` either: classic did not re-fetch the JSON configs on this path.
     */
    private suspend fun reloadDownloadCatalogueIfRequested() {
        if (!pendingDownloadCatalogueReload) return
        pendingDownloadCatalogueReload = false
        val session = session ?: return
        loadDownloadDocuments(session, refresh = true)
    }

    /** Classic `downloadDocJson()` (`:406-414`), minus the defaults list -- see [handleAutoDownloadExtras]. */
    private suspend fun downloadDocJson(session: DownloadSession) = coroutineScope {
        awaitAll(
            async { loadRecommendedDocuments(session) },
            async { loadPseudoBooks(session) },
            async { loadBadDocuments(session) },
        )
    }

    /**
     * Fetches [fileName] from the data site into the modules directory and decodes it with [decode]. A failed
     * fetch only reports an error when no earlier copy is readable, and an unreadable target is logged as
     * [missingLog] and answered with null (classic's four `load*` functions, one body).
     */
    private suspend fun <T> fetchConfig(
        session: DownloadSession,
        fileName: String,
        description: String,
        missingLog: String,
        decode: (String) -> T,
    ): T? = withContext(Dispatchers.IO) {
        val source = KUri(DATA_URL + fileName)
        val target = directories.modulesDir / fileName
        session.genericFileDownloader.downloadFile(source, target, description, reportError = !canRead(target))
        if (canRead(target)) {
            decode(FileSystem.SYSTEM.read(target) { readUtf8() })
        } else {
            Log.e(TAG_DOWNLOAD, missingLog)
            null
        }
    }

    private fun canRead(path: Path): Boolean = FileSystem.SYSTEM.exists(path)

    private suspend fun loadRecommendedDocuments(session: DownloadSession) {
        fetchConfig(session, RECOMMENDED_JSON, "Recommendations", "Could not load recommendations") {
            AppJson.decodeFromString(DocumentConfiguration.serializer(), it)
        }?.let { session.recommendedDocuments = it }
    }

    private suspend fun loadBadDocuments(session: DownloadSession) {
        fetchConfig(session, BAD_DOCS_JSON, "Bad documents list", "Could not load bad documents list") {
            AppJson.decodeFromString(DocumentConfiguration.serializer(), it)
        }?.let { session.badDocuments = it }
    }

    private suspend fun loadDefaultDocuments(session: DownloadSession) {
        fetchConfig(session, DEFAULT_JSON, "Defaults", "Could not load default document list") {
            AppJson.decodeFromString(DocumentConfiguration.serializer(), it)
        }?.let { session.defaultDocuments = it }
    }

    private suspend fun loadPseudoBooks(session: DownloadSession) {
        fetchConfig(session, PSEUDO_BOOKS_JSON, "Pseudo books", "Could not load pseudo book list") {
            AppJson.decodeFromString(ListSerializer(PseudoBook.serializer()), it)
        }?.let { session.pseudoBooks = it }
    }

    /**
     * (Re)load downloadable Books off-main, rebuild the docId->Book map and the `DocRow` list, and push them into
     * the controller. Classic `loadDocuments()` (`:485-528`).
     */
    private suspend fun loadDownloadDocuments(session: DownloadSession, refresh: Boolean) {
        try {
            val books = withContext(Dispatchers.Default) {
                session.downloadManager.refreshInstallManager()
                val docs = downloadControl.getDownloadableDocuments(session.repoFactory, refresh)
                if (docs.isNotEmpty()) docs + fakeBookFactory.pseudoDocuments(session.pseudoBooks) else docs
            }
            val rows = withContext(Dispatchers.Default) {
                val grouping = LanguageGrouping(books.mapNotNull { it.language })
                val langByKey: Map<String, LangOption> = grouping.representatives.mapNotNull { lang ->
                    val key = grouping.key(lang) ?: return@mapNotNull null
                    key to LangOption(lang.code ?: "", lang.name, key)
                }.toMap()
                // classic sortLanguages(representatives) order -> groupingKey -> rank index.
                session.langRank = downloadControl.sortLanguages(grouping.representatives)
                    .mapIndexedNotNull { i, lang -> grouping.key(lang)?.let { it to i } }
                    .toMap()
                // Bad documents flagged HIDE are excluded (classic filterDocuments); WARN -> badWarn.
                books.filterNot { it.isBadDocument(session.badDocuments, BadDocumentAction.HIDE) }
                    .map { it.toDocRow(session, grouping, langByKey) }
            }
            session.allBooks = books
            // docId is the opaque repoIdentity ("repo--initials"), unique per repo+initials.
            session.booksById = books.associateBy { it.repoIdentity }
            // Progress events already carry repoIdentity as DocumentStatus.id, and docId == repoIdentity now, so
            // the bridge's repoIdentity -> docId map is the identity.
            session.bridge.setRepoIdentityMap(books.associate { it.repoIdentity to it.repoIdentity })
            session.currentRows = rows
            session.controller.setDocuments(rows)
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error loading download documents", e)
            session.controller.showError()
        }
    }

    private fun Book.toDocRow(
        session: DownloadSession,
        grouping: LanguageGrouping,
        langByKey: Map<String, LangOption>,
    ): DocRow {
        val status = downloadControl.getDocumentStatus(this)
        val key = grouping.key(language) ?: (language.code ?: "")
        val sizeMb = bookMetaData.getProperty(SwordBookMetaData.KEY_INSTALL_SIZE)
            ?.toDoubleOrNull()?.let { it / 1e6 }
        return DocRow(
            docId = repoIdentity,
            osisId = osisID,
            abbreviation = abbreviation,
            name = name,
            language = langByKey[key] ?: LangOption(language.code ?: "", language.name, key),
            repository = getProperty(DownloadManager.REPOSITORY_KEY) ?: "",
            category = docCategoryOf(bookCategory),
            installStatus = status.documentInstallStatus.toDocInstallStatus(),
            percentDone = status.percentDone,
            recommended = isRecommended(session.recommendedDocuments),
            badWarn = isBadDocument(session.badDocuments, BadDocumentAction.WARN),
            locked = isLocked,
            enciphered = isEnciphered,
            canDelete = canDeleteNow(this),
            installSizeMb = sizeMb,
        )
    }

    /**
     * From the INSTALLED copy, not the repository catalogue entry: a repo Book's driver is an installer driver and
     * is never deletable. Re-resolved on every in-place row update, because the installed copy does not exist until
     * the download finishes (classic `canDeleteNow`, `:559-560`).
     */
    private fun canDeleteNow(book: Book?): Boolean =
        runCatching { book?.installedDocument?.canDelete ?: false }.getOrDefault(false)

    /**
     * Classic's `lifecycleScope.launch { bridge.statuses.collect { applyProgress(it) } }` (`:253-255`), started and
     * stopped by the destination instead of by an Activity -- see [DownloadDeps.subscribeDownloadProgress] for why
     * no payload crosses into the graph.
     */
    private fun subscribeDownloadProgress(): () -> Unit {
        val session = session ?: return {}
        val job = shell.hostScope.launch {
            session.bridge.statuses.collect { statusMap -> applyDownloadProgress(session, statusMap) }
        }
        return { job.cancel() }
    }

    /** Classic `onStart`/`onStop` (`:376-394`) as one subscribe/unsubscribe pair. */
    private fun subscribeDownloadMonitoring(firstDownload: Boolean): () -> Unit {
        val session = session ?: return {}
        session.bridge.register(downloadControl.progress)
        downloadControl.startMonitoringDownloads()
        if (firstDownload) {
            updateHasBible()
            JobManager.addWorkListener(downloadCompletionListener)
        }
        return {
            session.bridge.unregister()
            downloadControl.stopMonitoringDownloads()
            if (firstDownload) JobManager.removeWorkListener(downloadCompletionListener)
        }
    }

    /** Classic `applyProgress` (`:565-590`), verbatim apart from the session indirection. */
    private fun applyDownloadProgress(session: DownloadSession, statusMap: Map<String, RowDownloadStatus>) {
        if (statusMap.isEmpty()) return
        // Push each row's live status straight into the controller, which updates it IN PLACE -- no re-sort, no
        // clearSelection() -- unlike setDocuments()/refilter(). Keep the host-side currentRows mirror in sync so a
        // later refreshRowStatus()/setDocuments() doesn't revert the in-progress status.
        var mirror = session.currentRows
        for ((docId, s) in statusMap) {
            // A completed install arrives HERE, not through refreshRowStatus, so this is the path on which
            // canDelete actually flips, and it has to be re-resolved.
            val canDelete = canDeleteNow(session.booksById[docId])
            session.controller.updateDownloadStatus(docId, s.status, s.percentDone, canDelete)
            mirror = mirror.map { row ->
                if (row.docId == docId &&
                    (row.installStatus != s.status || row.percentDone != s.percentDone || row.canDelete != canDelete)
                ) {
                    row.copy(installStatus = s.status, percentDone = s.percentDone, canDelete = canDelete)
                } else row
            }
        }
        session.currentRows = mirror
    }

    /** Refresh one row's status directly from getDocumentStatus (classic `refreshRowStatus`, `:593-617`). */
    private fun refreshDownloadRowStatus(session: DownloadSession, book: Book) {
        val status = downloadControl.getDocumentStatus(book)
        val canDelete = canDeleteNow(book)
        val updated = session.currentRows.map { row ->
            if (row.docId == book.repoIdentity) {
                row.copy(
                    installStatus = status.documentInstallStatus.toDocInstallStatus(),
                    percentDone = status.percentDone,
                    canDelete = canDelete,
                )
            } else row
        }
        if (updated != session.currentRows) {
            session.currentRows = updated
            // updateDownloadStatus() updates the row IN PLACE, never re-sorting/clearing selection.
            session.controller.updateDownloadStatus(
                book.repoIdentity,
                status.documentInstallStatus.toDocInstallStatus(),
                status.percentDone,
                canDelete,
            )
        }
    }

    /** Classic `handleDocumentSelection` (`:622-631`): a selection starts a download, in place. */
    private fun handleDownloadSelection(docId: String) {
        val session = session ?: return
        val book = session.booksById[docId] ?: return
        Log.i(TAG_DOWNLOAD, "Document selected:" + book.initials)
        shell.hostScope.launch {
            try {
                manageDownload(session, book)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG_DOWNLOAD, "Error on attempt to download", e)
                UserMessages.toast(strings.errorDownloading)
            }
        }
    }

    /** Classic `manageDownload` (`:633-654`), including its confirm-before-download dialog. */
    private suspend fun manageDownload(session: DownloadSession, documentToDownload: Book?) {
        if (documentToDownload != null &&
            downloadControl.getDocumentStatus(documentToDownload).documentInstallStatus != DocumentInstallStatus.BEING_INSTALLED &&
            !documentToDownload.isPseudoBook
        ) {
            if (documentToDownload.isInstalled && DatabaseContainer.ready &&
                downloadBookmarksDao.genericBookmarkCountFor(documentToDownload) > 0
            ) {
                if (platform.confirmUpgradeWithBookmarks()) {
                    doDownload(session, documentToDownload)
                }
            } else {
                session.controller.requestDownloadConfirm(
                    documentToDownload.repoIdentity,
                    // M4: this plain-text message (a document's `name` can itself contain "<...>") goes through
                    // AbMessageDialog, which always parses its body as HTML -- I2's helper keeps it literal instead
                    // of silently dropping an unknown "tag".
                    plainTextToHtml(strings.downloadDocumentConfirmPrefix + " " + documentToDownload.name),
                )
            }
        }
    }

    /** Classic `doDownload` (`:656-665`). */
    private fun doDownload(session: DownloadSession, document: Book) = downloadScope.launch(Dispatchers.Main) {
        try {
            downloadControl.downloadDocument(session.repoFactory, document)
            refreshDownloadRowStatus(session, document)
            DocumentChanges.notifyInstalledChanged()
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error on attempt to download", e)
            UserMessages.toast(strings.errorDownloading)
        }
    }

    /** Classic `onCancel` (`:344`): the per-row cancel button, resolved through the session's map. */
    private fun cancelDownload(docId: String) {
        val session = session ?: return
        session.booksById[docId]?.let { downloadControl.cancelDownload(it) }
    }

    /**
     * Classic `handleDelete` (`:673-707`): ONE dialog listing every document that will be deleted, with the
     * non-deletable remainder reported once rather than once per document.
     */
    private fun handleDownloadDelete(ids: Set<String>) {
        val session = session ?: return
        val selected = ids.mapNotNull { session.booksById[it] }
        val (deletable, rest) = selected.partition { documentControl.canDelete(it.installedDocument) }
        if (rest.isNotEmpty()) UserMessages.toast(strings.cantDeleteDocument)
        if (deletable.isEmpty()) return
        val msg: String = if (deletable.size == 1) {
            strings.deleteDoc(deletable.single().name)
        } else {
            strings.deleteDocumentsConfirmation + "\n\n" + deletable.joinToString("\n") { it.name }
        }
        session.pendingDelete = deletable
        session.controller.requestDeleteConfirm(msg)
    }

    /**
     * Task 16 (run-2 plan, NH row 8004): the confirmed half of [handleDownloadDelete] -- the `deletable` list it
     * partitioned, parked on [DownloadSession.pendingDelete]. Unchanged from the classic dialog's positive button,
     * including the per-document `canDelete` RECHECK inside the loop (`Book.canDelete` is `!lastBible && ...`, so
     * deleting one of exactly two installed Bibles flips the other's flag -- this is a genuinely different, later
     * check than the one [handleDownloadDelete] already toasted for).
     */
    private fun handleDownloadDeleteConfirmed(session: DownloadSession) {
        val deletable = session.pendingDelete
        // deleteDocument suspends (it clears the backup row); launch, and keep the tail in order after it.
        shell.hostScope.launch {
            // The loop, the per-document tidy-up and the installed-changed notification complete in the app scope.
            val result = documentControl.deleteDocuments(deletable.map { it.installedDocument })
            for ((_, e) in result.failures) {
                Log.e(TAG_DOWNLOAD, "Deleting document crashed", e)
                userNotifier.showError(strings.errorOccurred, e)
            }
            if (result.skipped) UserMessages.toast(strings.cantDeleteDocument)
            loadDownloadDocuments(session, false)
        }
    }

    /**
     * Task 16 (D8-3 fix, NH row 8034): classic's `handleDeleteIndex` (`:710-727`) opened one stacked platform dialog
     * per selected document; [DocumentSelectionController.deleteIndex] now asks one at a time and calls this ONLY
     * for the document just confirmed -- the delete-index action itself (no `canDelete` check in classic, and none
     * here either) is unchanged.
     */
    private fun handleDownloadDeleteIndexConfirmed(session: DownloadSession, docId: String) {
        val document = session.booksById[docId] ?: return
        try {
            Log.i(TAG_DOWNLOAD, "Deleting index:$document")
            SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Deleting index crashed", e)
            userNotifier.showError(strings.errorOccurred, e)
        }
    }

    /** Classic `handleAbout` (`:730-746`): reload the SBMD (retaining repo/BadDocument), then showAbout. */
    private fun handleDownloadAbout(docId: String) {
        val session = session ?: return
        val document = session.booksById[docId] ?: return
        try {
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            val badDocument = sbmd.getProperty("BadDocument")
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            sbmd.putProperty("BadDocument", badDocument)
            shell.hostScope.launch(Dispatchers.Main) {
                platform.showAbout(document)
            }
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error expanding SwordBookMetaData for $document", e)
            userNotifier.showError(strings.errorOccurred, e)
        }
    }

    /** Classic `handleUnlock` (`:748-756`). */
    private fun handleDownloadUnlock(docId: String) {
        val session = session ?: return
        val document = session.booksById[docId] ?: return
        shell.hostScope.launch(Dispatchers.Main) {
            platform.unlock(document)
            loadDownloadDocuments(session, false)
        }
    }

    /**
     * Classic `handleAutoDownloadExtras` (`:759-786`), with its two Intent extras now route arguments. The defaults
     * list is fetched HERE rather than in [downloadDocJson]: classic fetched it in parallel with the other three but
     * gated on the same `download-recommended` extra this takes, and nothing else reads `defaultDocuments` -- so the
     * fetch follows its one consumer instead of needing a flag [DownloadDeps.refreshCatalogue] does not carry.
     */
    private suspend fun handleAutoDownloadExtras(documentIds: String?, downloadRecommended: Boolean) {
        val session = session ?: return
        // Cleared per run: the session outlives one visit to this destination (a second entry into the singleTop
        // host reuses it), and a stale list would report books that were not found on a PREVIOUS visit in this
        // visit's "books not downloaded" dialog.
        session.booksNotFound.clear()
        withContext(Dispatchers.Main) {
            if (documentIds != null) {
                val booksToDownload: List<SwordDocumentInfo> =
                    AppJson.decodeFromString(ListSerializer(SwordDocumentInfo.serializer()), documentIds)
                downloadRequestedBooks(session, booksToDownload)
                if (session.booksNotFound.size > 0) {
                    warnUserBooksNotDownloaded(session)
                }
            }
            if (!downloadRecommended) return@withContext
            loadDefaultDocuments(session)
            val defaults = session.defaultDocuments ?: return@withContext
            for (l in listOf(
                defaults.bibles["en"], defaults.commentaries["en"], defaults.addons["en"],
                defaults.books["en"], defaults.dictionaries["en"], defaults.maps["en"],
            )) {
                val l2 = l?.map {
                    if (it.contains("::")) {
                        val (initials, repository) = it.split("::")
                        SwordDocumentInfo(initials = initials, repository = repository, language = "en", abbreviation = "", name = "")
                    } else {
                        SwordDocumentInfo(initials = it, repository = "", language = "en", abbreviation = "", name = "")
                    }
                }
                downloadRequestedBooks(session, l2)
            }
        }
    }

    /** Classic `downloadRequestedBooks` (`:789-800`). */
    private fun downloadRequestedBooks(session: DownloadSession, osisIds: List<SwordDocumentInfo>?) {
        osisIds ?: return
        for (it in osisIds) {
            Log.i(TAG_DOWNLOAD, "User request to download $it")
            val book: Book? = findBookByInitials(session, it.initials, if (it.repository == "") null else it.repository)
            if (book != null) {
                doDownload(session, book)
            } else {
                session.booksNotFound.add(it.initials)
            }
        }
    }

    private fun findBookByInitials(session: DownloadSession, initials: String, repository: String?): Book? =
        session.allBooks.find {
            if (repository != null) {
                it.initials == initials && it.getProperty(DownloadManager.REPOSITORY_KEY) == repository
            } else {
                it.initials == initials
            }
        }

    /**
     * Classic `warnUserBooksNotDownloaded` (`:803-821`): an HTML message built here (it needs the DAO) and rendered
     * by [DocumentSelectionController.showBooksNotDownloaded]. The layout's intro text ("The following books could
     * not be downloaded:") sits ABOVE the list, exactly where the old layout put it. The WHOLE assembled string
     * (intro + names) goes through [plainTextToHtml] together, not per-name: user-visible module names can contain
     * `&`/`<`/`>`/newlines that must not corrupt the HTML the rest of the message parses as, and a single pass
     * turns both `\n\n` (intro/list gap) and each `\n` (one name per line) into `<br>`s uniformly.
     */
    private fun warnUserBooksNotDownloaded(session: DownloadSession) {
        val books = session.booksNotFound.toTypedArray()
        shell.hostScope.launch {
            val notInstalled: List<String> = books.mapNotNull { swordDocumentInfoDao.getBook(it)?.name }
            withContext(Dispatchers.Main) {
                val message = strings.downloadDialogMessage + "\n\n" + notInstalled.joinToString("\n")
                session.controller.showBooksNotDownloaded(plainTextToHtml(message))
            }
        }
    }

    /** Classic `isRepoBookListOld` (`:826-831`): not refreshed in a day means old. */
    private val isRepoBookListOld: Boolean
        get() {
            val repoRefreshDate = appSettings.getLong(REPO_REFRESH_DATE, 0)
            return (currentTimeMillis() - repoRefreshDate) / MILLISECS_IN_DAY > REPO_LIST_STALE_AFTER_DAYS
        }

    /**
     * Classic's overflow menu (`:839-876`). `AbMenuItem`, never `DropdownMenuItem`: this file is scanned by
     * `MenuSeamGuardTest`.
     */
    @Composable
    private fun DownloadOverflowMenu(navController: NavHostController, firstDownload: Boolean) {
        AbOverflowMenu(contentDescription = null) { close ->
            if (session?.hasErrors == true) {
                AbMenuItem(
                    text = strings.downloadErrors,
                    onClick = { close(); showDownloadErrors() },
                    icon = { platform.OverflowIcon(DownloadOverflowItem.ERRORS) },
                )
            }
            if (!firstDownload) { // classic FirstDownload hides installZip
                AbMenuItem(
                    text = strings.installZipTitle,
                    onClick = { close(); onInstallZip() },
                    icon = { platform.OverflowIcon(DownloadOverflowItem.INSTALL_ZIP) },
                )
            }
            AbMenuItem(
                text = strings.customRepositories,
                // An in-graph hop, not classic's awaitIntent(Screen.CustomRepositories): that destination lives in
                // THIS graph (Task 3), so an Intent would launch this host at itself. An in-graph hop has no
                // result to await; classic's follow-up `loadDocuments(true)` (:904) is armed by
                // CustomRepositoryController.onChanged (F96), so a view-only visit reloads nothing, and performed
                // by [reloadDownloadCatalogueIfRequested] when the destination composes again.
                onClick = {
                    close()
                    navController.navigate(NavRoutes.customRepositories())
                },
                icon = { platform.OverflowIcon(DownloadOverflowItem.CUSTOM_REPOSITORIES) },
            )
            if (documentSyncSettings.enabled) {
                AbMenuItem(
                    text = strings.documentSyncManageTitle,
                    // An in-graph hop, not classic's awaitIntent(Screen.CloudDocuments): nav-graph slice 4 Task 8
                    // registered CloudDocuments as a destination in THIS graph, so an Intent would launch this host
                    // at itself.
                    onClick = { close(); navController.navigate(NavRoutes.cloudDocuments()) },
                    icon = { platform.OverflowIcon(DownloadOverflowItem.CLOUD_DOCUMENTS) },
                )
            }
        }
    }

    /** Classic `showErrors` (`:878-890`). */
    private fun showDownloadErrors() {
        val session = session ?: return
        var message = ""
        if (session.downloadManager.failedRepos.isNotEmpty()) {
            message += strings.failedRepositoriesMessage(session.downloadManager.failedRepos.joinToString(",\n"))
        }
        if (session.genericFileDownloader.errors.isNotEmpty()) {
            message += strings.failedDownloadsMessage(session.genericFileDownloader.errors.joinToString(",\n"))
        }
        session.controller.showErrors(strings.downloadErrors, message)
    }

    /** Classic `onInstallZip` (`:891-897`). Slice 8 D2: in-graph, answered through the InstallZip opener (M5). */
    private fun onInstallZip() {
        openInstallZip(null, emptyList()) { DocumentChanges.notifyInstalledChanged() }
    }

    private fun DocumentInstallStatus.toDocInstallStatus(): DocInstallStatus = when (this) {
        DocumentInstallStatus.INSTALLED -> DocInstallStatus.INSTALLED
        DocumentInstallStatus.NOT_INSTALLED -> DocInstallStatus.NOT_INSTALLED
        DocumentInstallStatus.BEING_INSTALLED -> DocInstallStatus.BEING_INSTALLED
        DocumentInstallStatus.UPGRADE_AVAILABLE -> DocInstallStatus.UPGRADE_AVAILABLE
        DocumentInstallStatus.ERROR_DOWNLOADING -> DocInstallStatus.ERROR_DOWNLOADING
        DocumentInstallStatus.INSTALL_CANCELLED -> DocInstallStatus.INSTALL_CANCELLED
    }
}
