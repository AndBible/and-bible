@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.bookmark

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.BookmarkRecreationState
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.bookmark.BookmarksValue
import net.bible.android.view.activity.bookmark.LabelDraftValue
import net.bible.android.view.activity.bookmark.LabelEditContract
import net.bible.android.view.activity.bookmark.LabelEditMapper
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.bookmark.ManageLabelsValue
import net.bible.android.view.activity.bookmark.ManageLabelsWrites
import net.bible.android.view.activity.bookmark.assignableLabelItems
import net.bible.android.view.activity.bookmark.toLabelItem
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.db.DatabaseContainer
import net.bible.service.common.displayName
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditResult as ControllerLabelEditResult
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.bookmark.defaultLabelName
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.LabelEditResult as NavLabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.installzip.InstallZipOpener
import net.bible.sharedui.bookmark.ManageLabelsHelpDialog
import net.bible.sharedui.bookmark.nav.BookmarkNavDeps
import net.bible.sharedui.bookmark.nav.BookmarksDeps
import net.bible.sharedui.bookmark.nav.LabelEditDeps
import net.bible.sharedui.bookmark.nav.ManageLabelsDeps
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbActionSheet
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectSheet
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings

private const val TAG_MANAGE_LABELS = "ManageLabelsNavHost"
private const val TAG_BOOKMARKS = "BookmarksNavHost"

/** Classic `ManageLabels`' `labels_list_search_mode` preference (STUDYPAD only). */
private const val MANAGE_LABELS_SEARCH_MODE_KEY = "labels_list_search_mode"

/**
 * The memo behind [BookmarksDeps.controllerFor], and the in-flight state of whichever label-manager
 * round trip is open.
 *
 * [pendingAssign] is what makes ONE result channel serve classic's TWO round trips: the assign one
 * (`BookmarksComposeActivity.kt:199-223`) has to write the returned label set onto the bookmarks the
 * user had selected when it started, and the manage one (`:257-271`) only updates the workspace. A
 * `ManageLabelsResult` carries the returned JSON and nothing else, so the DISTINCTION has to be
 * remembered at the point the payload was built -- which is here, the same shape
 * [ManageLabelsSession.pendingEdit] uses for the editor round trip one level down. The bookmark
 * ENTITIES are held rather than their ids because that is what classic held across its own
 * `awaitIntent`; re-resolving ids afterwards would depend on the list not having reloaded meanwhile.
 */
@kotlin.native.HiddenFromObjC
class BookmarksSession {
    lateinit var controller: BookmarksController
    var restoredAssignIds: List<String>? = null
    var pendingAssign: List<BookmarkEntities.BaseBookmarkWithNotes>? = null

    /**
     * CR-5: this list navigated to the label manager and its answer has not been applied yet.
     * Snapshot state, not a plain field: the host-level collector composes once per Activity
     * and must recompose when a request starts, which can only happen after that composition.
     */
    var awaitingManageLabels by mutableStateOf(false)
}

/**
 * One visit to the label manager: the parsed payload, the authoritative `Label` objects, the
 * controller built around them, and the in-flight editor context.
 *
 * [labelsById] is classic's own `labelsById` (`ManageLabelsComposeActivity.kt:109-111`, itself a
 * mirror of classic `ManageLabels.allLabels`): the shared `LabelItem` carries display fields
 * only, not the `displayStyle`/`displayStyleWholeVerse` columns `BookmarkControl
 * .insertOrUpdateLabel` needs at save time, so the real entities have to be kept beside the
 * controller. Seeded broadly (it includes the Unlabeled special label) so any label reached
 * through the editor can be looked up later.
 *
 * [pendingEdit] is the one piece of state classic did NOT need a field for: its `editLabel`
 * awaited the child Activity inside a single coroutine, so the label being edited and whether it
 * was new were simply locals that survived the suspension. The editor is a destination now and
 * the result arrives later, through the channel, so the two have to be remembered across that
 * gap. One nullable field is enough because only one editor can be open at a time -- it is a
 * child destination, not a second window. It is part of the saved-state snapshot ([BookmarkRecreationState]),
 * so a recreation (or process death) restores it with the manager's working set.
 */
@kotlin.native.HiddenFromObjC
class ManageLabelsSession(
    val data: ManageLabelsContract.ManageLabelsData,
    val labelsById: MutableMap<String, BookmarkEntities.Label>,
    val writes: ManageLabelsWrites,
) {
    lateinit var controller: ManageLabelsController
    var pendingEdit: PendingLabelEdit? = null
}

/** The label whose editor is currently open, and whether it was created by this visit. */
@kotlin.native.HiddenFromObjC
class PendingLabelEdit(val label: BookmarkEntities.Label, val isNew: Boolean)

/**
 * The bookmark cluster's deps builder and host baggage: the bookmark list, the label manager and the label
 * editor. Ported from `BookmarksComposeActivity`, `ManageLabelsComposeActivity` and `LabelEditComposeActivity`.
 *
 * `bookmarkResults`, `manageLabelsResults` and `labelEditResults` stay declared in the activity (the reading
 * destination's collectors read them, and the channel guards read those declarations); [navDeps] receives them.
 * Every service sits behind a provider, so constructing the host touches no database.
 */
@kotlin.native.HiddenFromObjC
class BookmarkHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val icons: LabelIcons,
    private val studyPadExport: StudyPadExportPlatform,
    private val platform: BookmarkHostPlatform,
    private val openInstallZip: InstallZipOpener,
    private val appSettingsProvider: () -> AppSettings,
    private val bookmarksServiceProvider: () -> BookmarksServiceImpl,
    private val bookmarkControlProvider: () -> BookmarkControl,
    private val speakControlProvider: () -> SpeakControl,
    private val windowControlProvider: () -> WindowControl,
    private val manageLabelsServiceProvider: () -> ManageLabelsService,
    private val labelEditServiceProvider: () -> LabelEditService,
    private val fakeBookFactoryProvider: () -> FakeBookFactory,
    private val appScopeProvider: () -> AppCoroutineScope,
    private val orderedLauncherProvider: () -> OrderedLauncher,
    private val userNotifierProvider: () -> UserNotifier,
    restored: BookmarkRecreationState?,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val bookmarksService: BookmarksServiceImpl by lazy(bookmarksServiceProvider)
    private val bookmarkControl: BookmarkControl by lazy(bookmarkControlProvider)
    private val speakControl: SpeakControl by lazy(speakControlProvider)
    private val windowControl: WindowControl by lazy(windowControlProvider)
    private val manageLabelsService: ManageLabelsService by lazy(manageLabelsServiceProvider)
    private val labelEditService: LabelEditService by lazy(labelEditServiceProvider)
    private val fakeBookFactory: FakeBookFactory by lazy(fakeBookFactoryProvider)
    private val appScope: AppCoroutineScope by lazy(appScopeProvider)
    private val orderedLauncher: OrderedLauncher by lazy(orderedLauncherProvider)
    private val userNotifier: UserNotifier by lazy(userNotifierProvider)

    /** Set by [navDeps]: the host's controller and the activity-declared channels the baggage delivers into. */
    private var navController: NavHostController? = null
    private var bookmarkResults: NavResultChannel<BookmarkResult>? = null
    private var manageLabelsResults: NavResultChannel<ManageLabelsResult>? = null

    /**
     * The value snapshot decoded from the saved state: a fallback each destination consumes when it re-composes,
     * and which later saves keep carrying until then. The manager/editor fields are cleared at their exits;
     * [BookmarkRecreationState.list] and [BookmarkRecreationState.documentsSearch] are not, because those roots
     * are never re-entered (one session per host).
     */
    private var restoredBookmarks: BookmarkRecreationState? = restored

    /** The restored snapshot not yet consumed; the My Documents screens (still in the activity) read their parts. */
    val restoredState: BookmarkRecreationState? get() = restoredBookmarks

    /** Replaces the restored snapshot, e.g. clearing the part a destination has consumed. */
    fun updateRestored(transform: (BookmarkRecreationState?) -> BookmarkRecreationState?) {
        restoredBookmarks = transform(restoredBookmarks)
    }

    private var labelEditSession: Pair<String, LabelEditController>? = null

    /** The bookmark list's deps, the label manager's and the label editor's; the channels are the activity's. */
    fun navDeps(
        navController: NavHostController,
        bookmarkResults: NavResultChannel<BookmarkResult>,
        manageLabelsResults: NavResultChannel<ManageLabelsResult>,
        labelEditResults: NavResultChannel<NavLabelEditResult>,
    ): BookmarkNavDeps {
        this.navController = navController
        this.bookmarkResults = bookmarkResults
        this.manageLabelsResults = manageLabelsResults
        return BookmarkNavDeps(
            exitHost = { shell.exitHost() },
            setWindowTitle = { title -> shell.setWindowTitle(title) },
            bookmarkResults = bookmarkResults,
            manageLabelsResults = manageLabelsResults,
            labelEditResults = labelEditResults,
            bookmarks = BookmarksDeps(
                // A HOST-memoised factory, not a per-entry one -- see
                // BookmarksDeps.controllerFor: this controller holds the user's
                // multi-selection, and the label manager they assign it with sits on top
                // of this destination.
                controllerFor = { initialFilterIndex, onSelectBookmark, navigateToManageLabels ->
                    bookmarksControllerFor(initialFilterIndex, onSelectBookmark, navigateToManageLabels)
                },
                // Classic carried this as android:label on the host; one host now serves
                // every cluster. Same string the screen draws in its own top bar.
                title = strings.bookmarksAndMynotesTitle,
                subscribeSyncEvents = { onBookmarksChanged ->
                    platform.subscribeBookmarkSync(onBookmarksChanged)
                },
            ),
            manageLabels = ManageLabelsDeps(
                // A HOST-memoised factory, not a per-entry one — see
                // ManageLabelsDeps.controllerFor: this controller must survive the label
                // editor sitting on top of its destination.
                controllerFor = { data, onEditLabel, onResult ->
                    manageLabelsControllerFor(data, onEditLabel, onResult)
                },
                // Classic's `getString(data.titleId)` (`:170`): the title is chosen by mode, one of four.
                titleFor = { data ->
                    manageLabelsTitle(ManageLabelsContract.ManageLabelsData.fromJSON(data).mode)
                },
                onLabelEditResult = { result -> applyLabelEditResult(result) },
                initialSearchMode = {
                    appSettings.getInt(MANAGE_LABELS_SEARCH_MODE_KEY, SearchMode.NAME_START.ordinal)
                },
                iconSlot = { customIcon, tint, studyPadMode ->
                    icons.ManageLabelIcon(customIcon, tint, studyPadMode)
                },
                actions = { controller -> ManageLabelsActions(controller) },
                searchActions = { controller ->
                    NewLabelIcon(onClick = controller::newLabel)
                },
            ),
            labelEdit = LabelEditDeps(
                // A factory, one controller per back-stack entry — and the place the
                // controller's three outcomes become the nav result's two.
                controllerFor = { data, onResult -> labelEditControllerFor(data, onResult) },
                // Classic carried this as android:label="@string/edit_label" on the
                // host; one host now serves every cluster. Same string the screen draws
                // in its own top bar.
                title = strings.editLabelTitle,
                // Classic's `remember { customIconMap.keys.toList() + null }` — a pure
                // constant, so it is built once here instead of per composition. The
                // trailing null is the "no custom icon" cell.
                iconKeys = icons.iconKeys,
                iconSlot = { name, tint -> icons.LabelIcon(name, tint) },
                actions = { data, state, onSave, onDelete ->
                    LabelEditActions(data, state, onSave, onDelete)
                },
                deletePromptSlot = { prompt, labelName, onConfirm, onDismiss ->
                    LabelEditDeletePrompt(prompt, labelName, onConfirm, onDismiss)
                },
            ),
        )
    }

    /** The label manager's title, by mode (classic's `titleId`, one of four). */
    private fun manageLabelsTitle(mode: ManageLabelsContract.Mode): String = when (mode) {
        ManageLabelsContract.Mode.ASSIGN -> strings.assignLabelsLabel
        ManageLabelsContract.Mode.STUDYPAD -> strings.studyPadsLabel
        ManageLabelsContract.Mode.WORKSPACE -> strings.labelsTitle
        ManageLabelsContract.Mode.HIDELABELS -> strings.bookmarkSettingsHideLabelsTitle
    }

    // --- Bookmarks host baggage ----------------------------------------------------------------
    // Ported from BookmarksComposeActivity (which a later task deletes). Everything here needs a
    // `:app` type the graph cannot see -- the Room bookmark entities behind the rows, BookmarkControl,
    // SpeakControl, the workspace settings, an `Activity` for the CSV chooser, and an
    // `android.app.AlertDialog` -- which is the line that decides what is a deps slot and what is not.

    /**
     * The value snapshot to persist. A manager whose Save has started is not offered again, and the My Documents
     * parts come from the platform (their sessions are still the activity's).
     */
    fun recreationSnapshot(): BookmarkRecreationState {
        // A manager whose Save has started must not be offered again: the restored working set would still
        // hold new=true labels and a second Save would insert them twice.
        val savingManager = manageLabelsSession?.second?.writes?.isStarted == true
        val manager = manageLabelsSession?.takeUnless { savingManager }
        val bookmarks = bookmarksSession
        val editor = labelEditSession
        val documents = platform.documentsRecreation()
        return BookmarkRecreationState(
            filter = bookmarks?.first ?: restoredBookmarks?.filter,
            list = bookmarks?.second?.controller?.snapshot()?.let {
                BookmarksValue.from(it)
            } ?: restoredBookmarks?.list,
            pendingAssignIds = if (bookmarks != null) bookmarks.second.let {
                it.pendingAssign?.map { b -> b.id.toString() } ?: it.restoredAssignIds
            } else restoredBookmarks?.pendingAssignIds,
            awaitingManager = bookmarks?.second?.awaitingManageLabels ?: (restoredBookmarks?.awaitingManager == true),
            managerKey = manager?.first ?: restoredBookmarks?.managerKey.takeIf { manageLabelsSession == null },
            manager = manager?.second?.controller?.snapshot()?.let {
                ManageLabelsValue.from(it)
            } ?: restoredBookmarks?.manager.takeIf { manageLabelsSession == null },
            // Only labels the user changed need the Bundle; every other one is reloaded from the database.
            labels = manager?.second?.let { m ->
                val changedIds = m.controller.resultChanged()
                m.labelsById.filterKeys { it in changedIds }.values.toList()
            } ?: restoredBookmarks?.labels.orEmpty().takeIf { manageLabelsSession == null }.orEmpty(),
            pendingLabel = manager?.second?.pendingEdit?.label ?: if (manageLabelsSession == null) restoredBookmarks?.pendingLabel else null,
            pendingLabelIsNew = manager?.second?.pendingEdit?.isNew ?: (manageLabelsSession == null && restoredBookmarks?.pendingLabelIsNew == true),
            editorKey = editor?.first,
            editorDraft = editor?.second?.state?.value?.let {
                LabelDraftValue.from(it)
            },
            documentsSearch = documents.documentsSearch ?: restoredBookmarks?.documentsSearch,
            pages = documents.pages ?: restoredBookmarks?.pages,
            // The entry belongs to the screen only while it is on top; a stale entry from an earlier visit is not saved.
            cloudFilters = documents.cloudFilters ?: restoredBookmarks?.cloudFilters,
        )
    }

    /**
     * The bookmark list's memoised session, keyed on the route's filter index exactly as
     * [manageLabelsSession] is keyed on its payload -- so a route naming a DIFFERENT label filter can
     * never be served the previous list's controller. (No live edge re-navigates this host to a second
     * bookmarks route today, since `BOOKMARKS_PATTERN` is a root destination reached only as the
     * host's start destination; the key costs one word and removes the trap rather than relying on
     * that staying true.)
     *
     * Unlike [manageLabelsSession] it is never DROPPED, and that is this destination's shape: being a
     * root destination, it has no re-entry for a stale session to be resumed by, and the host dies
     * with it. Dropping it at its exit would be a reload of a list that is about to disappear.
     *
     * Snapshot state, not a plain field: [BookmarksManageLabelsCollector] composes once per Activity
     * and must recompose when a session appears after that composition. A plain field read inside a
     * `?.let` registers no dependency when it is null, which is exactly the launch-on-bookmarks case
     * the comment above `manageLabelsHelpOpen` documents.
     */
    var bookmarksSession: Pair<Int, BookmarksSession>? by mutableStateOf(null)
        private set

    /**
     * Classic `BookmarksComposeActivity`'s `controller` (`:79-91`) plus its `onCreate` side effects
     * (`:95`), memoised on [bookmarksSession] -- see [BookmarksDeps.controllerFor] for why this
     * controller must survive the label manager sitting on top of its destination.
     *
     * The three lambdas that are NOT parameters here are the ones classic also kept private to the
     * host: CSV export/import (they hand `this` to `BookmarkControl`, which wants an `Activity`) and
     * the delete confirmation (an `android.app.AlertDialog`; converting platform dialogs is a
     * separate, queued port goal). Each of them ends by refreshing the controller being built, which
     * is why they close over [BookmarksSession] rather than being deps slots the arm would have to
     * hand a self-reference to.
     */
    private fun bookmarksControllerFor(
        initialFilterIndex: Int,
        onSelectBookmark: (BookmarkResult) -> Unit,
        navigateToManageLabels: (payload: String) -> Unit,
    ): BookmarksController {
        bookmarksSession?.let { (key, existing) -> if (key == initialFilterIndex) return existing.controller }

        // Classic `BookmarksComposeActivity.onCreate` (`:95`). Once per opening of the list, as there.
        appSettings.setLong("bookmarks-last-used", currentTimeMillis())

        val session = BookmarksSession()
        restoredBookmarks?.takeIf { it.filter == initialFilterIndex }?.let {
            session.restoredAssignIds = it.pendingAssignIds
            session.awaitingManageLabels = it.awaitingManager
        }
        session.controller = BookmarksController(
            service = bookmarksService,
            scope = shell.hostScope,
            initialFilterIndex = initialFilterIndex,
            onSelectBookmark = { id, listPosition ->
                val labelNo = session.controller.selectedFilterIndex.value
                shell.hostScope.launch {
                    bookmarkResultFor(id, listPosition, labelNo)?.let(onSelectBookmark)
                }
            },
            onAssignLabels = { ids -> requestAssignLabels(session, ids, navigateToManageLabels) },
            onDeleteSelected = { ids -> confirmDeleteBookmarks(session, ids) },
            onExportCsv = { exportBookmarksCsv(session) },
            onImportCsv = { importBookmarksCsv(session) },
            onManageLabels = { requestManageLabels(session, navigateToManageLabels) },
            restored = restoredBookmarks?.takeIf { it.filter == initialFilterIndex }?.list?.toSnapshot(),
        )
        bookmarksSession = initialFilterIndex to session
        return session.controller
    }

    /**
     * The half of classic `onSelectBookmark` (`BookmarksComposeActivity.kt:165-187`) that decides WHAT
     * the result says; how it LEAVES is the activity's `bookmarkResults` channel. Returns null when there is nothing to
     * report -- an id with no loaded bookmark behind it (classic's `?: return`), or the failure its
     * `try` caught.
     *
     * The `try` is classic's, split with the body it wrapped: what can throw here is the speak lookup
     * and `verseRange.start.osisID`, and what can throw there is the history/`setResult` pair. Both
     * halves keep the same log-and-toast, so a user sees exactly what classic showed them either way.
     *
     * [labelNo] is passed in rather than read off [BookmarksSession] so that this stays a function of
     * what the caller saw: it is `controller.selectedFilterIndex.value` at the moment of the tap,
     * which is what classic put in the extra (`:185`).
     */
    private suspend fun bookmarkResultFor(id: String, listPosition: Int, labelNo: Int): BookmarkResult? {
        val bookmark = bookmarksService.bookmarkById(id) ?: return null
        Log.i(TAG_BOOKMARKS, "Bookmark selected:$bookmark")
        return try {
            if (bookmark is BookmarkEntities.BibleBookmarkWithNotes && bookmarkControl.isSpeakBookmark(bookmark)) {
                speakControl.speakFromBookmark(bookmark)
            }
            // The window title is the HOST WINDOW's, which the arm's LaunchedEffect has already set to
            // this destination's -- the same CharSequence classic read off its own Activity (`:184`).
            val description = shell.windowTitle
            when (bookmark) {
                is BookmarkEntities.BibleBookmarkWithNotes -> BookmarkResult(
                    verse = bookmark.verseRange.start.getOsisID(),
                    description = description,
                    labelNo = labelNo,
                    listPosition = listPosition,
                )
                is BookmarkEntities.GenericBookmarkWithNotes -> BookmarkResult(
                    key = bookmark.key,
                    book = bookmark.book?.initials,
                    ordinal = bookmark.ordinalStart,
                    description = description,
                    labelNo = labelNo,
                    listPosition = listPosition,
                )
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG_BOOKMARKS, "Error on bookmarkSelected", e)
            userNotifier.showInfo(strings.errorOccurred)
            null
        }
    }

    /**
     * Classic `assignLabels` (`BookmarksComposeActivity.kt:199-214`) up to the point it launched the
     * Intent: the Room read that collects every label already on the selected bookmarks, on
     * `Dispatchers.IO` exactly as classic ran it, then the ASSIGN-mode payload. The navigation itself
     * is the arm's, so it is handed back on the MAIN thread -- `navController.navigate` is not
     * thread-safe and classic's `awaitIntent` was reached from a main-dispatched continuation.
     */
    private fun requestAssignLabels(
        session: BookmarksSession,
        ids: List<String>,
        navigateToManageLabels: (payload: String) -> Unit,
    ) {
        shell.hostScope.launch(Dispatchers.IO) {
            val bookmarks = bookmarksService.bookmarksByIds(ids)
            val labels = mutableSetOf<IdType>()
            for (bookmark in bookmarks) {
                labels.addAll(bookmarkControl.labelsForBookmark(bookmark).map { it.id })
            }
            val payload = ManageLabelsContract.ManageLabelsData(
                mode = ManageLabelsContract.Mode.ASSIGN,
                selectedLabels = labels,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON()
            withContext(Dispatchers.Main) {
                session.pendingAssign = bookmarks
                session.restoredAssignIds = null
                session.awaitingManageLabels = true
                navigateToManageLabels(payload)
            }
        }
    }

    /** Classic `manageLabels` (`:257-265`): the WORKSPACE-mode payload, and no pending assignment. */
    private fun requestManageLabels(
        session: BookmarksSession,
        navigateToManageLabels: (payload: String) -> Unit,
    ) {
        shell.hostScope.launch(Dispatchers.Main) {
            val payload = ManageLabelsContract.ManageLabelsData(
                mode = ManageLabelsContract.Mode.WORKSPACE,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON()
            session.pendingAssign = null
            session.restoredAssignIds = null
            session.awaitingManageLabels = true
            navigateToManageLabels(payload)
        }
    }

    /**
     * Both of classic's label-manager continuations, joined at the one channel the arm consumes:
     * `assignLabels`' (`:215-222`) and `manageLabels`' (`:266-270`). Which one runs is decided by the
     * [BookmarksSession.pendingAssign] recorded when the payload was built, not by anything in the
     * result -- see that field's kdoc.
     *
     * There is no `RESULT_OK` guard, and none is missing: classic's was always true on this edge,
     * because the label manager has no cancel path at all (its Back press SAVES) and every exit it
     * has delivers a result. A user who backs out of it has, by classic's own design, saved.
     */
    private fun applyBookmarksManageLabelsResult(result: ManageLabelsResult) {
        val session = bookmarksSession?.second ?: return
        val bookmarks = session.pendingAssign
        val restoredIds = session.restoredAssignIds
        session.pendingAssign = null
        session.restoredAssignIds = null
        session.awaitingManageLabels = false

        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data)
        if (bookmarks != null || restoredIds != null) {
            shell.hostScope.launch(Dispatchers.IO) {
                val targets = bookmarks ?: restoredIds.orEmpty().mapNotNull { id ->
                    bookmarkControl.bibleBookmarkById(IdType(id)) ?: bookmarkControl.genericBookmarkById(IdType(id))
                }
                // The owner hydrates each bookmark and announces the batch once (F54).
                bookmarkControl.changeLabelsForBookmarks(targets, resultData.selectedLabels.toList())
                windowControl.windowRepository.updateRecentLabels(resultData.selectedLabels.toList())
                windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                withContext(Dispatchers.Main) { session.controller.refresh() }
            }
        } else {
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            session.controller.refresh()
        }
    }

    /**
     * Classic `onDelete` (`BookmarksComposeActivity.kt:227-240`), minus the confirmation: the
     * question now lives in `BookmarksController.dialog` (Task 13), and this runs only once the
     * controller's own `confirmDialog()` has confirmed it -- this stays host-side because it needs
     * the Room entities behind the selected ids.
     */
    private fun confirmDeleteBookmarks(session: BookmarksSession, ids: List<String>) {
        val bookmarks = bookmarksService.bookmarksByIds(ids)
        // The delete must complete even if this screen closes at once (app scope); the refresh follows it.
        val write = appScope.launch {
            for (bookmark in bookmarks) {
                bookmarkControl.deleteBookmark(bookmark)
            }
        }
        shell.hostScope.launch {
            write.join()
            session.controller.refresh()
        }
    }

    /** Classic `onExportCsv` (`:244-248`) -- `BookmarkCsvFlow.exportBookmarksToCSV` wants an `Activity`, hence the platform. */
    private fun exportBookmarksCsv(session: BookmarksSession) {
        shell.hostScope.launch {
            val bibleBookmarks = bookmarksService.loadedBookmarks()
                .filterIsInstance<BookmarkEntities.BibleBookmarkWithNotes>()
            platform.exportBookmarksCsv(bibleBookmarks)
            session.controller.refresh()
        }
    }

    /** Classic `onImportCsv` (`:250-253`). */
    private fun importBookmarksCsv(session: BookmarksSession) {
        shell.hostScope.launch(Dispatchers.Main) {
            platform.importBookmarksCsv()
            session.controller.refresh()
        }
    }

    // --- ManageLabels host baggage -------------------------------------------------------------
    // Ported from ManageLabelsComposeActivity (which a later task deletes). Everything here needs a
    // `:app` type the graph cannot see -- `BookmarkEntities.Label` and its style columns, the
    // workspace override DAO, `ManageLabelsContract.ManageLabelsData`, the settings store,
    // icon resources, an `android.app.AlertDialog` -- which is exactly the boundary
    // `ManageLabelsDeps` draws.

    /**
     * The memo behind [ManageLabelsDeps.controllerFor] — see that field's kdoc for WHY the label
     * manager's controller is host-held while every other controller in these graphs is built per
     * back-stack entry. Keyed on the route's payload, and dropped at every exit
     * ([deliverManageLabelsResult]), so a later entry with a different payload — or a re-entry after
     * this one has finished — is seeded afresh rather than resuming a stale working set.
     */
    internal var manageLabelsSession: Pair<String, ManageLabelsSession>? = null

    /** Raised from the ManageLabels overflow; rendered host-level in [Overlays]. */
    internal var manageLabelsHelpOpen by mutableStateOf(false)
    internal var manageLabelsExportOpen by mutableStateOf(false)

    private fun manageLabelsControllerFor(
        data: String,
        onEditLabel: (labelEditPayload: String) -> Unit,
        onResult: (ManageLabelsResult) -> Unit,
    ): ManageLabelsController {
        manageLabelsSession?.let { (key, existing) -> if (key == data) return existing.controller }

        // Restore the absent parent request with this Activity's navigation bindings before results can arrive.
        restoredBookmarks?.filter?.takeIf { bookmarksSession == null }?.let { filter ->
            bookmarksControllerFor(filter,
                { result -> navController?.let { nav -> bookmarkResults?.deliver(nav, result) } },
                { payload -> navController?.navigate(NavRoutes.manageLabels(payload)) },
            )
        }
        val restored = restoredBookmarks?.takeIf { it.managerKey == data }
        val parsed = ManageLabelsContract.ManageLabelsData.fromJSON(data)
        val session = ManageLabelsSession(
            data = parsed,
            labelsById = mutableMapOf(),
            writes = ManageLabelsWrites(bookmarkControl, appScope, barrier = { orderedLauncher.join("workspace") }),
        )
        restored?.let {
            session.labelsById.putAll(it.labels.associateBy { label -> label.id.toString() })
            session.pendingEdit = it.pendingLabel?.let { label -> PendingLabelEdit(label, it.pendingLabelIsNew) }
        }
        // labelsById is filled by the controller's own initial label load (on the host scope, so on
        // Main), from the same entities its rows are built from. An entry an editor round trip already
        // put there wins over the loaded copy.
        val sessionService = object : ManageLabelsService by manageLabelsService {
            override suspend fun assignableLabels(): List<LabelItem> {
                val all = withContext(Dispatchers.IO) { bookmarkControl.assignableLabels() }
                all.forEach { session.labelsById.getOrPut(it.id.toString()) { it } }
                return all.assignableLabelItems()
            }
        }
        // Only STUDYPAD reads the active window: other modes are opened from hosts whose window repository may not
        // be initialised (e.g. a host started on backup), where activeWindow throws.
        val highlightId = if (parsed.mode == ManageLabelsContract.Mode.STUDYPAD) {
            (windowControl.activeWindowPageManager.currentPage.key as? StudyPadKey)?.label?.id?.toString()
        } else null
        session.controller = ManageLabelsController(
            mode = ManageLabelsMapper.toMode(parsed.mode),
            service = sessionService,
            scope = shell.hostScope,
            initialSelected = ManageLabelsMapper.seedSelected(parsed),
            initialAutoAssign = ManageLabelsMapper.seedAutoAssign(parsed),
            initialAutoAssignPrimary = ManageLabelsMapper.seedAutoAssignPrimary(parsed),
            initialBookmarkPrimary = ManageLabelsMapper.seedBookmarkPrimary(parsed),
            highlightLabelId = highlightId,
            // Classic launched an Intent here; the payload is built the same way and the GRAPH turns
            // it into a destination. A label that cannot be resolved navigates nowhere, exactly as
            // classic's `?: return` did.
            onEditLabel = { id -> shell.hostScope.launch { buildLabelEditPayload(session, id)?.let(onEditLabel) } },
            onSelectStudyPad = { id, entryId -> shell.hostScope.launch { selectStudyPad(session, id, entryId, onResult) } },
            onSave = { saveManageLabelsAndExit(session, onResult) },
            onReset = { resetManageLabels(session, onResult) },
            restored = restored?.manager?.toSnapshot(),
        )
        manageLabelsSession = data to session
        return session.controller
    }

    /**
     * Classic `ManageLabelsComposeActivity.onEditLabel` (`:439-497`) up to the point it built the
     * Intent: everything that decides WHAT the editor opens on. Returns the `LabelEditContract
     * .LabelData` JSON the `bookmarks/labelEdit` route carries, or null when the label cannot be
     * resolved (classic's bare `return`).
     */
    private suspend fun buildLabelEditPayload(session: ManageLabelsSession, id: String?): String? {
        val controller = session.controller
        val isNew = id == null
        val label: BookmarkEntities.Label = if (id != null) {
            session.labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return null
        } else {
            BookmarkEntities.Label(new = true).apply { color = manageLabelsService.randomColorArgb() }
        }
        // A new label opened from the toolbar + with no live query must arrive with a real,
        // editable, unique name, or the return path below discards it silently (round 17b).
        val suggestedName = if (isNew) {
            controller.searchText.value.trim().ifBlank {
                defaultLabelName(
                    existing = session.labelsById.values.mapTo(mutableSetOf()) { it.displayName },
                    format = strings.newLabelDefaultName,
                )
            }
        } else {
            null
        }

        // F97 (fix batch 3 §2.3.1): a hide list does not know its workspace, so its editor gets none --
        // no override is read, none is returned, and applyLabelEditResult's override write is skipped.
        val workspaceContext = ManageLabelsMapper.labelEditHasWorkspaceContext(session.data.mode)
        val workspaceOverride = if (!workspaceContext) null else {
            val workspaceId = windowControl.windowRepository.id
            val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
            val existingOverrides = if (!isNew) workspaceDao.labelOverrides(workspaceId) else emptyList()
            existingOverrides.find { it.labelId == label.id } ?: WorkspaceEntities.WorkspaceLabelOverride(
                workspaceId = workspaceId,
                labelId = label.id,
            )
        }

        val labelData = LabelEditContract.LabelData(
            isAssigning = session.data.mode == ManageLabelsContract.Mode.ASSIGN,
            label = label,
            isAutoAssign = controller.resultAutoAssign().contains(label.id.toString()),
            isAutoAssignPrimary = controller.resultAutoAssignPrimary() == label.id.toString(),
            isThisBookmarkPrimary = controller.resultBookmarkPrimary() == label.id.toString(),
            isThisBookmarkSelected = controller.resultSelected().contains(label.id.toString()),
            suggestedName = suggestedName,
            workspaceOverride = workspaceOverride,
            hasWorkspaceContext = workspaceContext,
        )
        if (isNew) {
            when (session.data.mode) {
                ManageLabelsContract.Mode.ASSIGN -> {
                    labelData.isThisBookmarkSelected = true
                    labelData.isThisBookmarkPrimary = true
                }
                ManageLabelsContract.Mode.WORKSPACE -> {
                    labelData.isAutoAssignPrimary = true
                    labelData.isAutoAssign = true
                }
                else -> {}
            }
        }

        session.pendingEdit = PendingLabelEdit(label, isNew)
        return labelData.toJSON()
    }

    /**
     * Classic's `editLabel` continuation (`ManageLabelsComposeActivity.kt:499-551`), field for
     * field, with `awaitIntent`'s `ActivityResult` replaced by the channel's [NavLabelEditResult] —
     * `Cancelled` IS classic's `RESULT_CANCELED`, and `Saved` carries the same `"data"` JSON its
     * `"data"` extra did, including a delete expressed as `delete`/`deleteOrphanedBookmarks` flags
     * on the payload.
     *
     * Called from the `MANAGE_LABELS_PATTERN` arm's `LaunchedEffect` over
     * `BookmarkNavDeps.labelEditResults`. Reads the session rather than taking the controller as a
     * parameter: the reconciliation needs `labelsById` and the in-flight [PendingLabelEdit] too, and
     * those live together or not at all.
     */
    private fun applyLabelEditResult(result: NavLabelEditResult) {
        val session = manageLabelsSession?.second ?: return
        val pending = session.pendingEdit ?: return
        session.pendingEdit = null
        val label = pending.label
        val controller = session.controller

        if (result !is NavLabelEditResult.Saved) return

        val newLabelData = LabelEditContract.LabelData.fromJSON(result.data)

        // A name the user deliberately cleared is still not a label.
        if (newLabelData.label.name.isEmpty() && pending.isNew) return

        if (newLabelData.delete) {
            session.labelsById.remove(label.id.toString())
            controller.applyLabelDeleted(label.id.toString(), newLabelData.deleteOrphanedBookmarks)
        } else {
            val updatedLabel = newLabelData.label
            session.labelsById[updatedLabel.id.toString()] = updatedLabel

            // All four applied unconditionally, as classic did (`ManageLabels.kt:614-634`): the
            // three non-ASSIGN checkboxes are hidden on the editor, so their round-tripped values
            // are unchanged from the seeds and passing them is behaviourally identical.
            controller.applyLabelChanged(
                item = updatedLabel.toLabelItem(),
                selectedFlag = newLabelData.isThisBookmarkSelected,
                autoAssignFlag = newLabelData.isAutoAssign,
                bookmarkPrimaryFlag = newLabelData.isThisBookmarkPrimary,
                autoAssignPrimaryFlag = newLabelData.isAutoAssignPrimary,
            )

            // SU-14 D5: a label that already exists in Room (`!updatedLabel.new`: judged on the entity,
            // not on this edit, because a label created during this visit is still `new` on its second
            // OK) is written the moment the editor's OK arrives, not only at the manager's exit
            // (saveManageLabelsAndExit), so a process kill while the user is still on the list cannot
            // lose an edit the list already shows. The exit write stays (idempotent update). A label
            // that is still new stays staged: it is inserted, and its temporary id remapped, at exit.
            // `insertOrUpdateLabel` emits LabelUpserted itself, so it replaces `notifyLabelChanged`;
            // with an override it runs AFTER the override write, keeping override-before-notification.
            val earlyWrite = !updatedLabel.new

            // Workspace override (classic `ManageLabels.kt:637-649`).
            val returnedOverride = newLabelData.workspaceOverride
            if (returnedOverride != null) {
                val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                orderedLauncher.launch("workspace") {
                    if (returnedOverride.hasOverride) {
                        dao.insertOrUpdateLabelOverride(returnedOverride)
                    } else {
                        dao.deleteLabelOverride(returnedOverride.workspaceId, returnedOverride.labelId)
                    }
                    if (earlyWrite) bookmarkControl.insertOrUpdateLabel(updatedLabel)
                    else bookmarkControl.notifyLabelChanged(updatedLabel)
                    controller.refresh() // Re-derive the indicator after persistence.
                }
            } else if (earlyWrite) {
                orderedLauncher.launch("workspace") { bookmarkControl.insertOrUpdateLabel(updatedLabel) }
            }
        }
    }

    /**
     * Classic `onSelectStudyPad` (`:557-569`): point the active window at the StudyPad, then leave
     * the way every other exit leaves.
     */
    private suspend fun selectStudyPad(
        session: ManageLabelsSession,
        id: String,
        firstMatchEntryId: String?,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        val label = session.labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return
        try {
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(
                fakeBookFactory.journalDocument,
                StudyPadKey(label, entryId = firstMatchEntryId?.let { IdType(it) }),
            )
        } catch (e: Exception) {
            Log.e(TAG_MANAGE_LABELS, "Error on attempt to show journal", e)
            userNotifier.showError(strings.errorOccurred, e)
        }
        saveManageLabelsAndExit(session, onResult)
    }

    /**
     * Classic `saveAndExit` (`ManageLabelsComposeActivity.kt:573-637`), with `setResult`/`finish()`
     * replaced by the channel delivery — which is the whole of the change, because every line of the
     * rest is Room work.
     *
     * That includes classic's `labels_list_search_mode` write (`:576-578`), which stays HERE rather
     * than moving into the arm: every exit this screen has routes through `controller.save()` and so
     * through this function — the up-arrow, Back, and `selectStudyPad`, which ends by calling it —
     * so there is no exit an exit-time write can miss. Only the READ half is a deps lambda, because
     * only the seeding happens inside the arm's composition.
     */
    private fun saveManageLabelsAndExit(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        // Already saving (a second Save tap or a StudyPad pick): leave the running save alone.
        if (session.writes.isStarted) return
        val controller = session.controller

        val deletedIds = controller.resultDeleted()
        val orphanedIds = controller.resultDeletedWithOrphaned()
        val withoutOrphaned = deletedIds.filterNot { orphanedIds.contains(it) }.map { IdType(it) }
        val withOrphaned = orphanedIds.map { IdType(it) }

        val changedIds = controller.resultChanged()
        val toSave = changedIds.filterNot { deletedIds.contains(it) }.mapNotNull { session.labelsById[it] }

        // The list's quick favourite-toggle only flips the controller's own LabelItem copy, so
        // re-apply the controller's current favourite onto the entity before persisting.
        val currentFavourites = controller.currentLabelItems().associate { it.id to it.favourite }
        toSave.forEach { label -> currentFavourites[label.id.toString()]?.let { label.favourite = it } }

        // The writes must land even if this screen is closed right away (app scope); the result is
        // delivered only after they have, on the host's own scope. A second Save (or a StudyPad pick)
        // while they are running is ignored: it would write every new label again and deliver twice.
        val writes = session.writes.start(withoutOrphaned, withOrphaned, toSave) ?: return

        // Classic `ManageLabelsComposeActivity.kt:576-578` (itself classic `ManageLabels`'
        // saveFilteringSettings): STUDYPAD only, because it is the only mode with a content search.
        if (session.data.mode == ManageLabelsContract.Mode.STUDYPAD) {
            appSettings.setInt(MANAGE_LABELS_SEARCH_MODE_KEY, controller.searchMode.value.ordinal)
        }

        shell.hostScope.launch {
            // A failed write was already logged by the app scope's handler; stay on the screen.
            val idRemap = try { writes.await() } catch (e: Exception) { return@launch }

            fun remapSet(ids: Set<String>) = ids.map { idRemap[it] ?: it }.toSet()
            fun remapId(id: String?) = id?.let { idRemap[it] ?: it }

            ManageLabelsMapper.applyResult(
                data = session.data,
                selected = remapSet(controller.resultSelected()),
                autoAssign = remapSet(controller.resultAutoAssign()),
                changed = remapSet(controller.resultChanged()),
                deleted = controller.resultDeleted(),
                deletedWithOrphaned = controller.resultDeletedWithOrphaned(),
                autoAssignPrimary = remapId(controller.resultAutoAssignPrimary()),
                bookmarkPrimary = remapId(controller.resultBookmarkPrimary()),
            )

            deliverManageLabelsResult(session, onResult)
        }
    }

    /**
     * Classic `reset` (`ManageLabelsComposeActivity.kt:656-674`), minus the confirmation: the
     * question ("Do you want to remove all auto-assign labels..." / "...reset setting for hiding...")
     * now lives in `ManageLabelsController.dialog` (Task 13), and this runs only once the
     * controller's own `confirmDialog()` has confirmed it. Unchanged otherwise: WORKSPACE clears the
     * auto-assign set IN PLACE and stays on the list, HIDELABELS flips the payload's `reset` flag and
     * leaves — the same `"data"` extra, because [ManageLabelsMapper.applyReset] sets a FIELD rather
     * than producing a second result shape.
     */
    private fun resetManageLabels(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        when (session.data.mode) {
            ManageLabelsContract.Mode.WORKSPACE -> session.controller.clearAutoAssign()
            ManageLabelsContract.Mode.HIDELABELS -> {
                ManageLabelsMapper.applyReset(session.data)
                deliverManageLabelsResult(session, onResult)
            }
            else -> throw RuntimeException("Illegal value")
        }
    }

    /**
     * The ONE place the label manager leaves. Drops the memoised session first, so that a re-entry
     * (from `Bookmarks`, once that destination exists) builds a fresh controller from a fresh
     * payload rather than resuming a finished one — see [manageLabelsSession].
     */
    private fun deliverManageLabelsResult(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        manageLabelsSession = null
        restoredBookmarks = restoredBookmarks?.copy(
            managerKey = null, manager = null, labels = emptyList(), pendingLabel = null, pendingLabelIsNew = false,
        )
        manageLabelsHelpOpen = false
        manageLabelsExportOpen = false
        onResult(ManageLabelsResult(session.data.toJSON()))
    }

    /**
     * Classic `importStudyPads` (`:308-313`): InstallZip, then a controller refresh. Slice 8 D2: InstallZip is a
     * destination of this host, answered through [openInstallZip] -- never `awaitIntent` (M5).
     */
    private fun importStudyPads(controller: ManageLabelsController) {
        openInstallZip(null, emptyList()) { shell.hostScope.launch(Dispatchers.Main) { controller.refresh() } }
    }

    /**
     * The New (+) icon, in both the normal bar's [ManageLabelsActions] and the search bar's
     * `searchActions` slot — see the arm's comment at that call site for why the search bar needs its
     * own copy. Verbatim from classic (`:334-343`).
     */
    @Composable
    private fun RowScope.NewLabelIcon(onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            icons.ActionIcon(BookmarkActionIcon.NEW, strings.newItem, Modifier.size(AbActionIconSize))
        }
    }

    /**
     * The label manager's top-bar actions, verbatim from classic `ManageLabelsComposeActivity
     * .ManageLabelsActions` (`:345-435`) — including every comment's worth of reasoning about which
     * rows are mode-gated and which icons deliberately differ from classic's XML menu.
     *
     * Takes the whole controller rather than five callbacks: it reads `mode` and `styleTagsVisible`
     * off it as well as commanding it, and classic read them the same way (off the Activity's own
     * `controller` field). The two rows that are NOT controller commands — help and export — flip
     * host state instead, because both raise sheets that are rendered host-level; see
     * [manageLabelsHelpOpen].
     */
    @Composable
    private fun RowScope.ManageLabelsActions(controller: ManageLabelsController) {
        IconButton(onClick = controller::openSearch) {
            icons.ActionIcon(BookmarkActionIcon.SEARCH, strings.search, Modifier.size(AbActionIconSize))
        }
        NewLabelIcon(onClick = { controller.newLabel() })
        val styleTagsVisible by controller.styleTagsVisible.collectAsState()
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = strings.help,
                onClick = { close(); manageLabelsHelpOpen = true },
                icon = { icons.ActionIcon(BookmarkActionIcon.HELP, null, Modifier) },
            )
            if (controller.mode.hasReOrderButton) {
                AbMenuItem(
                    text = strings.reorder,
                    onClick = { close(); controller.reOrder() },
                    icon = { icons.ActionIcon(BookmarkActionIcon.REORDER, null, Modifier) },
                )
            }
            if (controller.mode.styleTagsShown) {
                AbMenuItem(
                    text = strings.showStyleExamples,
                    onClick = { close(); controller.toggleStyleTags() },
                    checkable = true,
                    checked = styleTagsVisible,
                    icon = { icons.ActionIcon(BookmarkActionIcon.STYLE_EXAMPLES, null, Modifier) },
                )
            }
            if (controller.mode.hasResetButton) {
                AbMenuItem(
                    // Two different actions, two different labels (round 17b): WORKSPACE clears the
                    // auto-assign set and stays; HIDELABELS reverts to the inherited value and leaves.
                    text = if (controller.mode == ManageLabelsMode.WORKSPACE) {
                        strings.clearAutoAssignLabels
                    } else {
                        strings.resetGeneric
                    },
                    onClick = { close(); controller.reset() },
                    icon = { icons.ActionIcon(BookmarkActionIcon.UNDO, null, Modifier) },
                )
            }
            // Export/import StudyPads: visible in ALL modes (classic onCreateOptionsMenu parity).
            AbMenuItem(
                text = strings.exportSomething(strings.studyPadsLabel),
                onClick = { close(); manageLabelsExportOpen = true },
                icon = { icons.ActionIcon(BookmarkActionIcon.EXPORT, null, Modifier) },
            )
            AbMenuItem(
                text = strings.importItems(strings.studyPadsLabel),
                onClick = { close(); importStudyPads(controller) },
                icon = { icons.ActionIcon(BookmarkActionIcon.IMPORT, null, Modifier) },
            )
        }
    }

    // --- LabelEdit host baggage ---------------------------------------------------------------
    // Ported from LabelEditComposeActivity (which a later task deletes): the icon renderer, the top-bar
    // actions, the delete confirmations, the discard-changes dialog and the export-destination chooser.
    // Every one of them is here rather than in `BookmarkNavGraph.kt` because it ends in a platform call
    // `commonMain` has no equivalent for, now behind [LabelIcons], [StudyPadExportPlatform] and
    // [BookmarkHostPlatform].

    /**
     * Non-null while the "Export to where?" chooser is awaiting an answer — rendered as an
     * [AbActionSheet] in [Overlays], so the export (via `BackupControl.saveOrShare`) skips its own
     * platform `AlertDialog` and awaits this one instead. Verbatim from
     * classic `LabelEditComposeActivity.kt:89-95`, including the `finally` that clears the field and
     * so closes the sheet.
     */
    internal var destinationRequest: CompletableDeferred<SaveOrShare?>? by mutableStateOf(null)

    /**
     * Asks "save or share?" through the host-level sheet in [Overlays]. Also the chooser of the My Documents page
     * export, which still lives in the activity.
     */
    suspend fun askDestination(): SaveOrShare? {
        val deferred = CompletableDeferred<SaveOrShare?>()
        destinationRequest = deferred
        return try { deferred.await() } finally { destinationRequest = null }
    }

    /**
     * Builds the label editor's controller for one back-stack entry, AND collapses its three
     * outcomes onto [NavLabelEditResult]'s two.
     *
     * The collapse is classic `onFinish` (`LabelEditComposeActivity.kt:234-247`) with `setResult` +
     * `finish()` replaced by `onResult`, and nothing else changed:
     * - [ControllerLabelEditResult.Save] → [NavLabelEditResult.Saved] carrying the updated payload's
     *   JSON, which is exactly what `finishWithData` put in the `"data"` extra.
     * - [ControllerLabelEditResult.Delete] → also [NavLabelEditResult.Saved], because classic's
     *   `Delete` arm ALSO ended at `finishWithData`. It is not a third result shape: it sets
     *   `delete`/`deleteOrphanedBookmarks` on the payload first and then returns the same extra, so
     *   a completed delete IS a saved `LabelData` whose own `delete` flag is set. Collapsing it any
     *   other way would change the contract `ManageLabels` reads back.
     * - [ControllerLabelEditResult.Cancel] → [NavLabelEditResult.Cancelled], classic's bare
     *   `setResult(RESULT_CANCELED)` with no `"data"` extra at all.
     *
     * It stays here rather than in the graph because every line of it needs `LabelEditMapper` and
     * `LabelEditContract.LabelData`, types that embed Room entities and cannot cross into
     * the graph's `commonMain`. [data] is parsed ONCE per controller, not per outcome: the `Delete` arm mutates
     * that instance exactly as classic mutated its `lateinit var data` field.
     */
    private fun labelEditControllerFor(
        data: String,
        onResult: (NavLabelEditResult) -> Unit,
    ): LabelEditController {
        val labelData = LabelEditContract.LabelData.fromJSON(data)
        val restoredDraft = restoredBookmarks?.takeIf { it.editorKey == data }?.editorDraft?.toState()
        return LabelEditController(
            LabelEditMapper.toState(labelData),
            labelEditService,
            shell.hostScope,
            restoredDraft,
        ) { result ->
            labelEditSession = null
            restoredBookmarks = restoredBookmarks?.copy(editorKey = null, editorDraft = null)
            when (result) {
                is ControllerLabelEditResult.Save ->
                    onResult(NavLabelEditResult.Saved(LabelEditMapper.applyToData(labelData, result.state).toJSON()))
                is ControllerLabelEditResult.Delete -> {
                    labelData.delete = true
                    labelData.deleteOrphanedBookmarks = result.deleteOrphaned
                    onResult(NavLabelEditResult.Saved(LabelEditMapper.applyToData(labelData, result.state).toJSON()))
                }
                ControllerLabelEditResult.Cancel -> onResult(NavLabelEditResult.Cancelled)
            }
        }.also { labelEditSession = data to it }
    }

    /**
     * Classic `shareLabel` (`LabelEditComposeActivity.kt:227-232`): applies the pending (unsaved)
     * edits onto the payload before exporting, so the exported StudyPad matches what is on screen.
     * Takes the route's [data] and the live [state] instead of reading two Activity fields — the
     * editor's payload and controller belong to a back-stack entry now, not to this host.
     */
    private fun shareLabel(data: String, state: LabelEditState) {
        val current = LabelEditMapper.applyToData(LabelEditContract.LabelData.fromJSON(data), state)
        shell.hostScope.launch {
            studyPadExport.export(listOf(current.label), ::askDestination)
        }
    }

    /**
     * The label editor's top-bar actions, verbatim from classic `LabelEditComposeActivity
     * .LabelEditActions` (`:158-183`). [onSave] and [onDelete] are the GRAPH-owned controller's, so
     * they arrive as parameters; [data] and [state] are the two halves [shareLabel] needs.
     */
    @Composable
    private fun RowScope.LabelEditActions(
        data: String,
        state: LabelEditState,
        onSave: () -> Unit,
        onDelete: () -> Unit,
    ) {
        IconButton(onClick = onSave) {
            icons.ActionIcon(BookmarkActionIcon.CHECK, strings.okay, Modifier.size(AbActionIconSize))
        }
        if (!state.isSpecialLabel) {
            IconButton(onClick = onDelete) {
                icons.ActionIcon(BookmarkActionIcon.DELETE, strings.deleteLabel, Modifier.size(AbActionIconSize))
            }
        }
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = strings.export,
                onClick = { close(); shareLabel(data, state) },
                icon = { icons.ShareIcon() },
            )
        }
    }

    /**
     * Classic `DeletePromptDialog` (`LabelEditComposeActivity.kt:190-224`), with the controller's
     * two calls hoisted into [onConfirm]/[onDismiss] parameters. [labelName] comes from the LIVE
     * controller state (not the route payload's name, which `LabelEditMapper.applyToData` only syncs
     * at save/delete/share time) — otherwise a name typed but not yet saved would show stale here.
     *
     * This member only resolves strings and hands them to the top-level [LabelEditDeletePromptContent],
     * which does the actual rendering and is driven directly by a compose-ui test.
     */
    @Composable
    private fun LabelEditDeletePrompt(
        prompt: DeletePrompt,
        labelName: String,
        onConfirm: (deleteOrphaned: Boolean) -> Unit,
        onDismiss: () -> Unit,
    ) {
        LabelEditDeletePromptContent(
            prompt = prompt,
            title = strings.deleteLabelConfirmation(labelName),
            orphanedMessage = when (prompt) {
                is DeletePrompt.Orphaned -> strings.confirmDeleteOrphanedBookmarks(prompt.count)
                DeletePrompt.Confirm -> null
            },
            deleteBothLabel = strings.deleteLabelAndBookmarks,
            deleteOnlyLabel = strings.deleteLabelOnly,
            cancelLabel = strings.cancel,
            confirmLabel = strings.yes,
            dismissLabel = strings.no,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }

    // --- Overlays ------------------------------------------------------------------------------

    /**
     * Host-level collectors and sheets of the bookmark cluster, outside the NavHost; composed by
     * `AppHosts.Overlays`.
     */
    @Composable
    fun Overlays() {
        // CR-5: the bookmark list's claim on a label-manager answer. Host-level, outside the
        // NavHost, for InstallZipHost.Overlays' reason: the arm that asked is disposed while
        // the label manager is on top, and the settings arm (which shares this channel) may be
        // the resumed destination when the answer arrives. An arm-local consume would miss it,
        // leave it pending, and apply it stale the next time the list composed.
        BookmarksManageLabelsCollector()

        // Host-level, deliberately OUTSIDE the NavHost: the export runs in the host scope
        // and outlives the destination that started it, so a chooser scoped
        // to that destination's composition could be disposed mid-await and leave the
        // export hanging on a deferred nobody can complete. Verbatim from classic
        // `LabelEditComposeActivity.kt:128-153`, including the no-Cancel-row shape (round
        // 14a G2.9): dismissing IS "chose nothing", completing with null.
        // The label manager's help dialog and StudyPad-export multiselect, host-level for
        // `destinationRequest`'s reason above and for one of their own: both are raised from
        // the ManageLabels top-bar overflow, which is a `RowScope` slot inside the nav
        // graph's top app bar — a sheet composed there would be a child of the bar's own
        // layout.
        //
        // ORDER IS LOAD-BEARING, and this is the correction of a justification that used to
        // stand here claiming the opposite. The two booleans are read UNCONDITIONALLY, with
        // the plain-field [manageLabelsSession] looked up INSIDE each branch — never the
        // other way round. `manageLabelsSession` is NOT snapshot state, so a scope that can
        // only reach the booleans through a `?.let` on it registers no dependency on them
        // whenever the field is null at the moment this lambda composes. This lambda
        // composes ONCE per Activity (NavHost's own recompositions do not invalidate it),
        // and when the host is launched on `bookmarks` and the user navigates to the label
        // manager from there (`MenuCommandHandler.kt:207`), the field IS null then — so the
        // overflow's Help and Export StudyPads items would flip a boolean nobody observes
        // and open nothing. Reading the state first makes the subscription unconditional;
        // the plain-field read inside is safe because it runs only when a boolean is true,
        // and only a LIVE ManageLabels destination's overflow can make one true.
        // `ManageLabelsOverflowSubscriptionTest` proves both halves — do not re-nest these.
        if (manageLabelsHelpOpen) {
            manageLabelsSession?.second?.let { session ->
                ManageLabelsHelpDialog(
                    mode = session.controller.mode,
                    title = manageLabelsTitle(session.data.mode),
                    // Classic showed this only for WORKSPACE and HIDE, the two scoped
                    // settings (`ManageLabelsComposeActivity.kt:254-263`).
                    scopeSentence = if (
                        session.controller.mode == ManageLabelsMode.WORKSPACE ||
                        session.controller.mode == ManageLabelsMode.HIDELABELS
                    ) {
                        strings.settingScope(
                            if (session.data.isWindow) strings.settingScopeWindow else strings.settingScopeWorkspace,
                        )
                    } else null,
                    // STUDYPAD keeps its own playlist; the other three modes use the
                    // Labels & Bookmarks one. Don't collapse this to one URL.
                    readMoreUrl = platform.helpReadMoreUrl(studyPad = session.controller.mode == ManageLabelsMode.STUDYPAD),
                    onDismiss = { manageLabelsHelpOpen = false },
                )
            }
        }

        // Classic `ManageLabelsComposeActivity.kt:218-248` (the export_studypads menu
        // handler): a multiselect over every assignable label, then the export for
        // the chosen ones. The `manageLabelsSession != null` half keeps the old gate ("only
        // while a label manager is live") without putting the session read in FRONT of the
        // boolean — see the note above; `&&` short-circuits left to right, so the state is
        // still read on every composition of this scope.
        if (manageLabelsExportOpen && manageLabelsSession != null) {
            val loadedExportableLabels by produceState<List<BookmarkEntities.Label>?>(null, manageLabelsExportOpen) { value = bookmarkControl.assignableLabels() }
            // Not shown until the list has loaded, so it never opens with an empty option list.
            val exportableLabels = loadedExportableLabels
            if (exportableLabels != null) AbMultiSelectSheet(
                open = true,
                title = strings.exportSomething(strings.studyPadsLabel),
                options = exportableLabels,
                selectedIds = emptyList(),
                idOf = { it.id.toString() },
                labelOf = { it.displayName },
                confirmText = strings.okay,
                dismissText = strings.cancel,
                onConfirm = { ids ->
                    manageLabelsExportOpen = false
                    val selected = exportableLabels.filter { ids.contains(it.id.toString()) }
                    if (selected.isNotEmpty()) {
                        shell.hostScope.launch(Dispatchers.Main) {
                            studyPadExport.export(selected, ::askDestination)
                        }
                    }
                },
                onDismiss = { manageLabelsExportOpen = false },
                selectAllText = strings.selectAll,
                selectNoneText = strings.selectNone,
            )
        }

        destinationRequest?.let { req ->
            AbActionSheet(
                open = true,
                title = strings.exportDestinationTitle,
                message = strings.exportDestinationMessage,
                onDismiss = { req.complete(null) },
            ) {
                AbActionSheetRow(
                    label = strings.shareLabel,
                    onClick = { req.complete(SaveOrShare.SHARE) },
                    icon = { icons.ShareIcon() },
                )
                AbActionSheetRow(
                    label = strings.backupPhoneStorage,
                    onClick = { req.complete(SaveOrShare.SAVE) },
                    icon = { icons.SaveIcon() },
                )
            }
        }
    }

    /**
     * CR-5's consumer, and the only one `manageLabelsResults` has for the bookmark list. The session
     * flag is the source of truth for "this list asked"; the channel's pending is the answer. Both
     * are read UNCONDITIONALLY, for the reason the comment in [Overlays] spells out:
     * this lambda composes once per Activity, and a scope that could reach the flag only through a
     * `?.let` on a plain field would register no dependency whenever the field was null at that one
     * composition. Both the session and the flag are snapshot state, and both are read before the
     * null check, so the subscription is unconditional.
     *
     * An answer the list did not ask for is left in the channel, for whoever did (the settings arm's
     * own gate is that side of the same contract).
     */
    @Composable
    private fun BookmarksManageLabelsCollector() {
        // Read UNCONDITIONALLY, before the null check, for the reason above: a `?.let` that only
        // reached the flag when a session already existed would subscribe to nothing on the
        // composition where the field was null, and a later request would flip a state nobody observes.
        val awaiting = bookmarksSession?.second?.awaitingManageLabels == true
        val results = manageLabelsResults ?: return
        net.bible.sharedui.bookmark.nav.BookmarksManageLabelsCollector(
            results = results,
            awaiting = awaiting,
            onResult = { applyBookmarksManageLabelsResult(it) },
        )
    }
}
