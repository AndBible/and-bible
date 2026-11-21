@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.chooser

import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedui.components.AbHelpMenuItem
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import fi.sykero.kdom.Element
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.document.DocumentChanges
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.window.WindowControl
import net.bible.service.download.DownloadManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.hideFromSelector
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade.Companion.readOsisFragment
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.installzip.InstallZipOpener
import net.bible.sharedui.app.ports.DocumentDialogs
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.navigation.nav.ChooseDictionaryWordDeps
import net.bible.sharedui.navigation.nav.ChooseDocumentDeps
import net.bible.sharedui.navigation.nav.ChooserNavDeps
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Key

/**
 * The installed counterpart of a pseudo or catalogue [Book] (`:app`'s `Book.installedDocument`).
 * TODO: duplicate of `Book.installedDocument` in `DocumentBadges.kt:90` (`:app`); drop this copy when that moves to
 * common code.
 */
private val Book.installedDocument get() = Books.installed().getBook(initials)

private const val TAG_CHOOSERS = "ChoosersNavHost"
private const val TAG_CHOOSE_DOCUMENT = "ChooseDocumentNavHost"

/** Per-SCREEN arrangement preference keys, classic `DownloadComposeActivity.kt:113-114`. */
private const val CHOOSE_DOC_ARRANGEMENT_KEY = "chooseDoc.arrangement"
private const val CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY = "chooseDoc.arrangement.remember"
private const val SELECTED_DOCUMENT_FILTER_KEY = "selected_document_filter_no"

/** Classic `maxLettersWholeWords` (`ChooseDictionaryWordComposeActivity:132-138`). */
internal fun maxDictionaryLettersWholeWords(text: String, max: Int = 50): String {
    val words = text.split(' ').toMutableList()
    var result = ""
    while (result.length < max && words.isNotEmpty()) { result += words[0] + ' '; words.removeAt(0) }
    val append = if (words.isNotEmpty()) "..." else ""
    return "$result$append"
}

/** Classic `cleanUpSnippet` (`:126-130`). */
internal fun cleanUpDictionarySnippet(snippet: String, key: String): String {
    var noNewLines = snippet.replace('\n', ' ')
    if (noNewLines.startsWith(key)) noNewLines = noNewLines.substring(key.length)
    return maxDictionaryLettersWholeWords(noNewLines)
}

/** Classic `getEntrySnippet` (`:118-124`). */
internal fun dictionaryEntrySnippet(text: Element, key: String): String {
    text.removeChild("title")
    val entry = text.getChild("entryFree") ?: return cleanUpDictionarySnippet(text.value, key)
    val greekOrHebrew = entry.getChildren("orth")?.map { it.text }?.filter { it != "" }?.joinToString(" - ") ?: ""
    if (greekOrHebrew != "") return greekOrHebrew
    return cleanUpDictionarySnippet(entry.value, key)
}

/**
 * Classic `initialTypeFilter()` (`ChooseDocumentComposeActivity:425-432`) minus its `addons` branch: nothing in the
 * tree puts an `addons` extra on an Intent any more (design §6.1), and
 * `ClassicDocumentSelectionRemovalGuardTest.noCallSiteStillPutsADownloadExtraOnAnIntent` already asserts as much,
 * by a plain CONTAINMENT scan over every shipping source, which is why this comment spells the extra's name out
 * rather than quoting the call it forbids. The branch would be unreachable by construction.
 */
internal fun chooseDocumentInitialTypeFilter(type: String?, settings: AppSettings): DocTypeFilter = when (type) {
    "BIBLE" -> DocTypeFilter.BIBLE
    "COMMENTARY" -> DocTypeFilter.COMMENTARY
    else -> DocTypeFilter.entries.getOrElse(settings.getInt(SELECTED_DOCUMENT_FILTER_KEY, 0)) { DocTypeFilter.ALL }
}

/**
 * The chooser cluster's deps builder and host baggage: dictionary word and choose document. Ported from the classic
 * chooser Activities (the full-screen passage grid is gone, #4169, and the general-book and map key screens, #4172:
 * those keys are chosen in the reading view's quick sheets).
 *
 * `KeyChooserResults` is a frozen `page` contract (A/B boundary), so the result channels stay declared in the
 * activity (the reading destination's collectors read them) and are passed to [navDeps]. Every service sits behind a
 * provider, so constructing the host touches no database.
 */
@kotlin.native.HiddenFromObjC
class ChooserHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val appSettingsProvider: () -> AppSettings,
    private val documentDialogs: DocumentDialogs,
    private val platform: ChooserHostPlatform,
    private val openInstallZip: InstallZipOpener,
    private val windowControlProvider: () -> WindowControl,
    private val documentControlProvider: () -> DocumentControl,
    private val downloadControlProvider: () -> DownloadControl,
    private val fakeBookFactoryProvider: () -> FakeBookFactory,
    private val coreStringsProvider: () -> CoreStrings,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val windowControl: WindowControl by lazy(windowControlProvider)
    private val documentControl: DocumentControl by lazy(documentControlProvider)
    private val downloadControl: DownloadControl by lazy(downloadControlProvider)
    private val fakeBookFactory: FakeBookFactory by lazy(fakeBookFactoryProvider)

    /** The chooser graph's deps; the channels are the activity's (see the class kdoc). */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(
        navController: NavHostController,
        keyChooserResults: NavResultChannel<KeyChooserResult>,
        documentResults: NavResultChannel<DocumentResult>,
    ): ChooserNavDeps = ChooserNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        keyChooserResults = keyChooserResults,
        documentResults = documentResults,
        chooseDictionaryWord = ChooseDictionaryWordDeps(
            title = strings.dictionaryTitle,
            hint = strings.search,
            controllerFor = { onResult -> chooseDictionaryWordControllerFor(onResult) },
            loadRows = { loadChooseDictionaryRows() },
            loadSnippet = { keyId -> chooseDictionarySnippet(keyId) },
        ),
        chooseDocument = ChooseDocumentDeps(
            title = strings.chooseDocumentTitle,
            controllerFor = { initialTypeFilter, onResult -> chooseDocumentControllerFor(initialTypeFilter, onResult) },
            initialTypeFilter = { type -> chooseDocumentInitialTypeFilter(type, appSettings) },
            persistTypeFilter = { filter -> appSettings.setInt(SELECTED_DOCUMENT_FILTER_KEY, filter.ordinal) },
            loadDocuments = { loadChooseDocumentsOnEntry() },
            topBarActions = { ChooseDocumentOverflowMenu() },
        ),
    )

    // ——— ChooseDictionaryWord ————————————————————————————————————————————————————————————————————

    /** Classic `ChooseDictionaryWordComposeActivity.page` (`:49`). */
    private val chooseDictionaryPage get() = windowControl.activeWindowPageManager.currentDictionary

    /**
     * One dictionary-chooser ENTRY's baggage: the global key list [loadChooseDictionaryRows] resolves off-main,
     * whose index is the `DictRow.keyId` (classic `:51-52`).
     *
     * A session rather than host fields, for [ChooseDocumentSession]'s reason: the destination is per-entry, so a
     * host field shared by two live entries would let a popped-back controller resolve its `keyId` against the
     * other entry's list. A chooser could close its list over its controller instead; this one cannot, because [loadChooseDictionaryRows] WRITES the list and [chooseDictionarySnippet] reads it, and both
     * are deps slots with frozen signatures that carry no controller.
     */
    private class ChooseDictionarySession {
        var keys: List<Key> = emptyList()
    }

    /** The CURRENT entry's session: reassigned by [chooseDictionaryWordControllerFor], read by the two seams below. */
    private var chooseDictionarySession: ChooseDictionarySession? = null

    /** Null where classic finished immediately with NO result -- `page.currentDocument == null` (`:72`). Unlike
     *  the removed general-book and map key choosers there is no fallback key to hand back. */
    private fun chooseDictionaryWordControllerFor(
        onResult: (KeyChooserResult) -> Unit,
    ): ChooseDictionaryWordController? {
        if (chooseDictionaryPage.currentDocument == null) return null
        val session = ChooseDictionarySession()
        chooseDictionarySession = session
        return ChooseDictionaryWordController(
            onSelect = { keyId ->
                val key = session.keys.getOrNull(keyId.toIntOrNull() ?: -1)
                    ?: return@ChooseDictionaryWordController
                // Classic `:58-63`: the dictionary shares the general-book key result shape.
                onResult(
                    KeyChooserResult(
                        key = key.getOsisRef(),
                        book = chooseDictionaryPage.currentDocument?.initials,
                    ),
                )
            },
        )
    }

    /**
     * Classic's `lifecycleScope.launch { withContext(Dispatchers.IO) { page.cachedGlobalKeyList } }` (`:77-85`).
     * NULL means the load failed; the arm turns a null into the `controller.showError()` classic's own `catch`
     * called.
     */
    private suspend fun loadChooseDictionaryRows(): List<DictRow>? {
        // No session means no controller was built for this entry, i.e. the arm never reached its load effect --
        // unreachable by construction, and a null here is the arm's "failed" signal.
        val session = chooseDictionarySession ?: return null
        return try {
            session.keys = withContext(Dispatchers.IO) {
                chooseDictionaryPage.cachedGlobalKeyList ?: emptyList()
            }
            session.keys.mapIndexed { i, k -> DictRow(i.toString(), k.getName()) }
        } catch (e: CancellationException) {
            // Classic ran this in `lifecycleScope`, where leaving the screen simply cancelled the job. In-graph it
            // runs in the arm's `LaunchedEffect`, whose job is cancelled on every navigate-away -- and
            // `CancellationException` IS an `Exception`, so without this rethrow the generic catch below would
            // swallow the cancellation, log a bogus error and hand the arm a null, which is its "the load FAILED"
            // signal and calls the NON-suspending `controller.showError()`.
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CHOOSERS, "Error creating dictionary key list", e)
            null
        }
    }

    /** Classic `snippetFor` (`:110-116`) plus its `withContext` call-site wrapper (`:100`). */
    private suspend fun chooseDictionarySnippet(keyId: String): String = withContext(Dispatchers.IO) {
        val keys = chooseDictionarySession?.keys ?: return@withContext ""
        val key = keys.getOrNull(keyId.toIntOrNull() ?: -1) ?: return@withContext ""
        val book: Book = chooseDictionaryPage.currentDocument ?: return@withContext ""
        val text = try { readOsisFragment(book, key, coreStringsProvider()) } catch (e: OsisError) { e.xml }
        dictionaryEntrySnippet(text, key.toString())
    }

    // ——— ChooseDocument ——————————————————————————————————————————————————————————————————————————

    /**
     * Classic `ChooseDocumentComposeActivity`'s `booksById` field (`:89-90`) plus the controller it is rebuilt
     * alongside. A SESSION per entry, not a host-memoised one (see `ChooseDocumentDeps.controllerFor`): the picker
     * is opened over and over from the reading view and classic gave every open a fresh Activity, so a memoised
     * controller would carry the previous open's filter, search and selection into the next.
     */
    private class ChooseDocumentSession {
        lateinit var controller: DocumentSelectionController

        /** docId (`Book.initials`) -> Book, rebuilt on every (re)load. */
        var booksById: Map<String, Book> = emptyMap()

        /** The documents a delete confirmation is waiting on. */
        var pendingDelete: List<Book> = emptyList()
    }

    /** The CURRENT entry's session. Reassigned by [chooseDocumentControllerFor], read by every seam below. */
    private var chooseDocumentSession: ChooseDocumentSession? = null

    /** Classic's controller construction (`:92-124`), with `onSelect` routed into the graph's channel instead of
     *  `setResult`+`finish`. */
    private fun chooseDocumentControllerFor(
        initialTypeFilter: DocTypeFilter,
        onResult: (DocumentResult) -> Unit,
    ): DocumentSelectionController {
        val session = ChooseDocumentSession()
        session.controller = DocumentSelectionController(
            // Classic ChooseDocument.sortLanguages: alphabetical by display name.
            langComparator = compareBy { it.displayName },
            onSelect = { docId -> handleChooseDocumentSelection(docId, onResult) },
            onDelete = { ids -> handleChooseDocumentDelete(ids) },
            onConfirmDelete = { handleChooseDocumentDeleteConfirmed(session) },
            onConfirmDeleteIndex = { docId -> handleChooseDocumentDeleteIndexConfirmed(session, docId) },
            onAbout = { docId -> handleChooseDocumentAbout(docId) },
            onUnlock = { docId -> handleChooseDocumentUnlock(docId) },
            onStickyLanguage = { lang ->
                // Classic `:101-106`: a sticky record like classic's lastSelectedLanguage, not read back on launch
                // here (this screen starts with no language filter).
                appSettings.setString("selected_language_code", lang?.code)
            },
            // No SIZE and no RECOMMENDED: this screen has neither datum (classic `:107-112`).
            applicableSortKeys = setOf(
                DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME,
                DocSortKey.LANGUAGE, DocSortKey.REPOSITORY,
            ),
            applicableGroupKeys = listOf(
                DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY,
            ),
            storedArrangement = if (appSettings.getBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, true))
                appSettings.getString(CHOOSE_DOC_ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = appSettings.getBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                appSettings.setBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, remember)
                appSettings.setString(CHOOSE_DOC_ARRANGEMENT_KEY, encoded)
            },
            scope = shell.hostScope,
        )
        chooseDocumentSession = session
        // Classic `onCreate:130`, immediately after construction.
        session.controller.setTypeFilter(initialTypeFilter)
        return session.controller
    }

    /** Classic's `loadDocuments()` (`:236-258`). */
    private suspend fun loadChooseDocuments() {
        val session = chooseDocumentSession ?: return
        try {
            val books = withContext(Dispatchers.Default) {
                SwordDocumentFacade.documents + fakeBookFactory.pseudoDocuments.filterNot { it.hideFromSelector }
            }
            val rows = withContext(Dispatchers.Default) { platform.docRows(books) }
            session.booksById = books.associateBy { it.initials }
            session.controller.setDocuments(rows)
        } catch (e: CancellationException) {
            // See [loadChooseDictionaryRows]: classic's `lifecycleScope` job simply died on leaving, but this one
            // runs in the arm's `LaunchedEffect` and is cancelled on every navigate-away. `showError()` does not
            // suspend, so swallowing the cancellation here would paint an error over a screen the user has
            // already left.
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error loading documents", e)
            session.controller.showError()
        }
    }

    /** Classic `handleDocumentSelection` (`:262-278`) with `setResult`+`finish` replaced by the channel: the unlock
     *  gate and its reload are unchanged. */
    private fun handleChooseDocumentSelection(docId: String, onResult: (DocumentResult) -> Unit) {
        val book = chooseDocumentSession?.booksById?.get(docId) ?: return
        if (book.bookCategory == BookCategory.AND_BIBLE) return
        shell.hostScope.launch(Dispatchers.Main) {
            if (book.isLocked && !documentDialogs.awaitUnlock(book.initials)) {
                loadChooseDocuments()
                return@launch
            }
            Log.i(TAG_CHOOSE_DOCUMENT, "Book selected:" + book.initials)
            onResult(DocumentResult(book = book.initials))
        }
    }

    /** Classic `handleDelete` (`:287-321`), including its one-dialog-for-the-whole-selection shape and the
     *  per-document `canDelete` re-check inside the loop (the lastBible guard). */
    private fun handleChooseDocumentDelete(ids: Set<String>) {
        val session = chooseDocumentSession ?: return
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

    /** The confirmed delete: the loop, the per-document tidy-up and the installed-changed notification complete in
     *  the app scope; this keeps the tail in order after them. */
    private fun handleChooseDocumentDeleteConfirmed(session: ChooseDocumentSession) {
        val deletable = session.pendingDelete
        // deleteDocument suspends (it clears the backup row); launch, and keep the tail in order after it.
        shell.hostScope.launch {
            val result = documentControl.deleteDocuments(deletable.map { it.installedDocument })
            for ((_, e) in result.failures) {
                Log.e(TAG_CHOOSE_DOCUMENT, "Deleting document crashed", e)
                platform.showError(e)
            }
            if (result.skipped) UserMessages.toast(strings.cantDeleteDocument)
            loadChooseDocuments()
        }
    }

    private fun handleChooseDocumentDeleteIndexConfirmed(session: ChooseDocumentSession, docId: String) {
        val document = session.booksById[docId] ?: return
        try {
            Log.i(TAG_CHOOSE_DOCUMENT, "Deleting index:$document")
            SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Deleting index crashed", e)
            platform.showError(e)
        }
    }

    /** Classic `handleAbout` (`:344-360`): reload the SBMD (retaining repo/BadDocument), then show. */
    private fun handleChooseDocumentAbout(docId: String) {
        val document = chooseDocumentSession?.booksById?.get(docId) ?: return
        try {
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            val badDocument = sbmd.getProperty("BadDocument")
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            sbmd.putProperty("BadDocument", badDocument)
            documentDialogs.showAbout(document.initials)
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error expanding SwordBookMetaData for $document", e)
            platform.showError(e)
        }
    }

    /** Classic `handleUnlock` (`:363-369`). */
    private fun handleChooseDocumentUnlock(docId: String) {
        val document = chooseDocumentSession?.booksById?.get(docId) ?: return
        shell.hostScope.launch(Dispatchers.Main) {
            documentDialogs.awaitUnlock(document.initials)
            loadChooseDocuments()
        }
    }

    /**
     * Classic `OverflowMenu()` (`:373-392`) -- Download / Backup modules / Install zip. Download and Install zip are
     * both destinations of this graph and are reached in-graph ([onChooseDocumentDownload],
     * [onChooseDocumentInstallZip]).
     */
    @Composable
    private fun ChooseDocumentOverflowMenu() {
        AbOverflowMenu(contentDescription = null) { close ->
            AbHelpMenuItem(DocsLink.DOCUMENTS, close)
            AbMenuItem(
                text = strings.chooseDocumentDownload,
                onClick = { close(); onChooseDocumentDownload() },
                icon = { platform.OverflowIcon(ChooserOverflowItem.DOWNLOAD) },
            )
            AbMenuItem(
                text = strings.chooseDocumentBackup,
                onClick = { close(); platform.backupModules() },
                icon = { platform.OverflowIcon(ChooserOverflowItem.BACKUP) },
            )
            AbMenuItem(
                text = strings.installZipTitle,
                onClick = { close(); onChooseDocumentInstallZip() },
                icon = { platform.OverflowIcon(ChooserOverflowItem.INSTALL_ZIP) },
            )
        }
    }

    /**
     * Classic `onDownload()` (`:394-408`), as an IN-GRAPH hop (slice 8 final review, finding 2). Classic's
     * `awaitIntent` aimed at this `singleTop` host itself was answered with a synthetic cancel before anything was
     * downloaded. An in-graph hop has no result to await, so the follow-up is ARMED here and performed by
     * [loadChooseDocumentsOnEntry] when the chooser composes again on the way back.
     */
    private fun onChooseDocumentDownload() {
        try {
            if (downloadControl.checkDownloadOkay()) {
                pendingChooseDocumentDownloadReturn = true
                shell.navigate(NavRoutes.download())
            }
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error opening download", e)
            platform.showError(e)
        }
    }

    /** Armed by [onChooseDocumentDownload], consumed by [loadChooseDocumentsOnEntry]. */
    private var pendingChooseDocumentDownloadReturn = false

    /**
     * The chooser's per-entry load ([ChooseDocumentDeps.loadDocuments]). Every (re)composition of the chooser entry
     * runs it, so a return from the Download hop lands here: classic's documents-changed broadcast
     * (`DocumentChanges.notifyInstalledChanged`) runs first, then the reload.
     */
    private suspend fun loadChooseDocumentsOnEntry() {
        if (pendingChooseDocumentDownloadReturn) {
            pendingChooseDocumentDownloadReturn = false
            DocumentChanges.notifyInstalledChanged()
        }
        loadChooseDocuments()
    }

    /** Classic `onInstallZip()` (`:414-421`). Slice 8 D2: in-graph, answered through the install-zip opener (M5). */
    private fun onChooseDocumentInstallZip() {
        openInstallZip(null, emptyList()) {
            DocumentChanges.notifyInstalledChanged()
            shell.hostScope.launch { loadChooseDocuments() }
        }
    }
}
