package net.bible.sharedui.app.mydocuments

import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.view.activity.bookmark.MyDocumentPagesValue
import net.bible.android.view.activity.bookmark.MyDocumentsSearchValue
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.mydocuments.MyDocumentPagesSnapshot
import net.bible.sharedcore.mydocuments.MyDocumentsController
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.MyDocumentFiles
import net.bible.sharedui.mydocuments.nav.MyDocumentPagesDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings
import kotlin.coroutines.cancellation.CancellationException

private const val TAG_MY_DOCUMENT_PAGES = "MyDocPagesNavHost"
private const val TAG_MY_DOCUMENTS = "MyDocumentsNavHost"
private val MY_DOCUMENT_IMPORT_MIME_TYPES = listOf("text/*")
private val FILE_NAME_CHARS = Regex("[^a-zA-Z0-9._\\- ]")

/**
 * The My Documents cluster's deps builder and host baggage: the document list and the pages-within-a-document
 * editor. Ported from classic MyDocumentsComposeActivity / MyDocumentPagesComposeActivity: every Room, SWORD and
 * file side effect stays host-side (the file I/O behind [MyDocumentFiles]).
 *
 * Constructing the host touches no database; the DAO is read only when a screen loads or an export runs.
 */
class MyDocumentsHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val documentPicker: DocumentPicker,
    private val files: MyDocumentFiles,
    private val userNotifierProvider: () -> UserNotifier,
    /** The documents list search the bookmark recreation state carried, applied by the first load. */
    private val restoredDocumentsSearch: () -> MyDocumentsSearchValue?,
    /** The pages screen state the bookmark recreation state carried, applied by the first load. */
    private val restoredPages: () -> MyDocumentPagesValue?,
    /** Drops [restoredPages] once applied: later visits to a Pages screen start fresh. */
    private val consumeRestoredPages: () -> Unit,
    restored: MyDocumentsRecreationState? = null,
    /** Seam for tests: replaces the database-backed folder export. Receives the pending ids, kind, batch flag and folder. */
    private val exportSeam: (suspend (ids: List<String>, pages: Boolean, batch: Boolean, tree: DocumentRef) -> Unit)? = null,
) {
    private val userNotifier: UserNotifier by lazy(userNotifierProvider)
    private val myDocumentDao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    /** What the export waits for while the folder picker is open; saved across recreation. */
    private val pendingExports = PendingMyDocumentExports()

    /** Identifies the newest export pick; an older one that finishes afterwards must not touch [pendingExports]. */
    private var exportTicket = 0

    /** True while the documents import's file picker is open; saved so the name prompt still follows after recreation. */
    private var importPicking = false

    /** The state to persist across recreation. The empty snapshot means "nothing to restore". */
    fun recreationSnapshot() = pendingExports.snapshot().copy(importPicking = importPicking)

    /**
     * Restores what the previous host instance was waiting for and resumes it. The platform redelivers a pick that
     * was open at recreation (see [DocumentPicker.resumeDocumentTree]); an export then runs as if the pick had
     * happened in this instance, and a cancelled pick clears silently.
     */
    fun restore(state: MyDocumentsRecreationState?) {
        pendingExports.restoreFrom(state)
        importPicking = state?.importPicking ?: false
        if (pendingExports.isPending) {
            val ticket = ++exportTicket
            shell.hostScope.launch { completeExportPick(ticket, documentPicker.resumeDocumentTree()) }
        }
        if (importPicking) {
            shell.hostScope.launch { completeImportPick(documentPicker.resumeOpenDocuments()) }
        }
    }

    // --- Recreation snapshot (read by the bookmark host) -----------------------------------------

    /** The documents list's search state, or null when it is not loaded. */
    fun documentsSearchValue(): MyDocumentsSearchValue? =
        myDocumentsSession?.takeIf { it.loaded }?.controller?.searchSnapshot()?.let {
            MyDocumentsSearchValue(it.query, it.searchOpen, it.selectedInitials)
        }

    /** The pages screen's state; only while that screen is on top ([onPagesRoute]), a stale session is not saved. */
    fun pagesValue(onPagesRoute: Boolean): MyDocumentPagesValue? =
        myDocumentPagesSession?.takeIf { it.loaded && onPagesRoute }?.let { session ->
            val snapshot = session.controller.snapshot()
            val idByToken = myDocumentPagesEntityByLong.mapValues { it.value.id.toString() }
            MyDocumentPagesValue(
                session.documentId, snapshot.query, snapshot.searchOpen,
                snapshot.selection.mapNotNull { idByToken[it] }.toSet(),
            )
        }

    /**
     * Builds the cluster's deps. The result channels stay with the host: the reading destination's collectors read
     * them. [beforeSwitchDocument] is the pages screen's "switch document" edge, which needs the reading results.
     */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(
        navController: NavHostController,
        myDocumentPagesResults: NavResultChannel<MyDocumentPagesResult>,
        myDocumentsResults: NavResultChannel<MyDocumentsResult>,
        beforeSwitchDocument: () -> Unit,
    ): MyDocumentsNavDeps {
        // Shared local vals rather than two independent copies of "launch the import picker" / "export this page" /
        // "export these pages": both MyDocumentPagesDeps' own top-level fields AND the MyDocumentPagesController
        // myDocumentPagesControllerFor builds are wired to the SAME instances.
        val onImportMyDocumentPage: () -> Unit = { importMyDocumentPages() }
        val onExportMyDocumentPage: (Long) -> Unit = { id -> exportMyDocumentPage(id) }
        val onExportSelectedMyDocumentPages: (List<Long>) -> Unit = { ids -> launchMyDocumentPagesExport(ids) }
        // Same shape one level up: MyDocumentsDeps' own onImport/onExport/onExportSelected AND the
        // MyDocumentsController myDocumentsControllerFor builds are wired to the SAME instances.
        val onImportMyDocument: () -> Unit = { importMyDocuments() }
        val onExportMyDocument: (Long) -> Unit = { id -> launchMyDocumentsExport(listOf(id), batch = false) }
        val onExportSelectedMyDocuments: (List<Long>) -> Unit = { ids -> launchMyDocumentsExport(ids, batch = true) }
        return MyDocumentsNavDeps(
            exitHost = { shell.exitHost() },
            setWindowTitle = { title -> shell.setWindowTitle(title) },
            myDocumentPagesResults = myDocumentPagesResults,
            myDocumentsResults = myDocumentsResults,
            myDocumentPages = MyDocumentPagesDeps(
                controllerFor = { documentId, documentInitials, onResult ->
                    myDocumentPagesControllerFor(
                        documentId, documentInitials, onResult,
                        onImport = onImportMyDocumentPage,
                        onExport = onExportMyDocumentPage,
                        onExportSelected = onExportSelectedMyDocumentPages,
                    )
                },
                titleFor = { documentName -> strings.myDocumentPagesTitle(documentName) },
                onImport = onImportMyDocumentPage,
                onExportSelected = onExportSelectedMyDocumentPages,
                onExportPage = onExportMyDocumentPage,
                beforeSwitchDocument = beforeSwitchDocument,
            ),
            myDocuments = MyDocumentsDeps(
                controllerFor = { onResult -> myDocumentsControllerFor(onResult) },
                title = strings.myDocumentsTitle,
                onImport = onImportMyDocument,
                onExport = onExportMyDocument,
                onExportSelected = onExportSelectedMyDocuments,
                importNamePrompt = myDocumentImportNamePrompt,
                onConfirmImport = ::confirmImportMyDocument,
                onDismissImport = ::dismissImportMyDocument,
                routeForPages = { id ->
                    myDocumentsSession?.entityByLong?.get(id)?.let { doc ->
                        NavRoutes.myDocumentPages(
                            documentId = doc.id.toString(),
                            documentInitials = doc.initials,
                            documentName = doc.name,
                        )
                    }
                },
            ),
        )
    }

    // --- MyDocumentPages host baggage ------------------------------------------------------------

    /**
     * The bits of the CURRENTLY open `MyDocumentPages` session the file flows need to reach: the stable
     * load-index-Long -> Room-entity map `myDocumentPagesControllerFor` rebuilds on every entry, so imports/exports
     * can resolve the ids the controller hands them back to a real row. Only one `MyDocumentPages` destination is
     * ever composed at a time (it has no child destination of its own), so this single mutable map is exactly
     * classic's per-Activity-instance `entityByLong` field.
     */
    private var myDocumentPagesEntityByLong: Map<Long, MyDocumentPage> = emptyMap()

    /** The `MyDocumentPages` entry currently built; replaced by every [myDocumentPagesControllerFor] call. */
    private class MyDocumentPagesSession(val documentId: String) {
        lateinit var controller: MyDocumentPagesController
        /** True once the first load ran, i.e. the controller holds real state worth snapshotting. */
        var loaded = false
    }
    private var myDocumentPagesSession: MyDocumentPagesSession? = null

    /**
     * The current session's own `addPage`, captured so an imported page, which arrives from a picker coroutine
     * outside the arm's own closures, can be added to the right controller. Refreshed by every
     * [myDocumentPagesControllerFor] call.
     */
    private var addMyDocumentPageToList: ((title: String, contentType: MyDocumentContentType, content: String) -> Unit)? = null

    /** Remember the picked pages by PERSISTENT id (see [PendingMyDocumentExports]) and open the folder picker. */
    internal fun launchMyDocumentPagesExport(tokens: List<Long>) {
        val ids = tokens.mapNotNull { myDocumentPagesEntityByLong[it]?.id?.toString() }
        if (ids.isEmpty()) return reportLostMyDocumentExport()
        pendingExports.restoreFrom(null)
        pendingExports.pageIds = ids
        val ticket = ++exportTicket
        shell.hostScope.launch { completeExportPick(ticket, documentPicker.openDocumentTree()) }
    }

    /**
     * Applies a folder pick to the pending export: runs it, or drops it silently when the pick was cancelled. A
     * folder with nothing left to export is reported, never ignored.
     */
    internal fun completeExportPick(ticket: Int, tree: DocumentRef?) {
        // A newer pick took over the pending state: this one was superseded and must leave it alone.
        if (ticket != exportTicket) return
        // Resolved by persistent id from the DB, not through a session: after a recreation (picker still open)
        // the session is rebuilt and may not be loaded.
        val batch = pendingExports.documentsBatch
        val pageIds = pendingExports.takePageIds()
        val documentIds = pendingExports.takeDocumentIds()
        pendingExports.documentsBatch = false
        if (tree == null) return
        when {
            exportSeam != null && (pageIds.isNotEmpty() || documentIds.isNotEmpty()) ->
                shell.hostScope.launch { exportSeam.invoke(pageIds.ifEmpty { documentIds }, pageIds.isNotEmpty(), batch, tree) }
            pageIds.isNotEmpty() -> exportMyDocumentPagesToFolder(pageIds, tree)
            documentIds.isEmpty() -> reportLostMyDocumentExport()
            batch -> exportMyDocumentsToFolder(documentIds, tree)
            else -> exportMyDocumentByIdToFolder(documentIds.first(), tree)
        }
    }

    /** One page per picked file, in filename order, like the documents-side import. */
    private fun importMyDocumentPages() {
        shell.hostScope.launch {
            val refs = documentPicker.openDocuments(MY_DOCUMENT_IMPORT_MIME_TYPES, multiple = true)
            if (refs.isEmpty()) return@launch
            val addPage = addMyDocumentPageToList
            for (ref in refs.sortedBy { files.displayName(it) ?: "" }) importMyDocumentPageFile(ref, addPage)
        }
    }

    /** Import a single text file as a new page. */
    private suspend fun importMyDocumentPageFile(
        ref: DocumentRef,
        addPage: ((title: String, contentType: MyDocumentContentType, content: String) -> Unit)?,
    ) {
        try {
            val page = files.importPageFile(ref) ?: return
            addPage?.invoke(page.title, page.contentType, page.content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_MY_DOCUMENT_PAGES, "Failed to import file", e)
            userNotifier.showInfo(strings.errorOccurred)
        }
    }

    /** Export one page through the save-or-share flow of [MyDocumentFiles.exportPage]. */
    private fun exportMyDocumentPage(id: Long) {
        val page = myDocumentPagesEntityByLong[id] ?: return
        shell.hostScope.launch {
            val pageWithContent = withContext(Dispatchers.IO) { myDocumentDao.pageByIdWithContent(page.id) } ?: return@launch
            files.exportPage(pageWithContent)
        }
    }

    /** Export several pages into one chosen folder. */
    private fun exportMyDocumentPagesToFolder(ids: List<String>, tree: DocumentRef) {
        shell.hostScope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    ids.mapNotNull { myDocumentDao.pageByIdWithContent(IdType.fromString(it)) }
                }
                if (!files.exportPagesToFolder(pages, tree)) return@launch
                userNotifier.showInfo(strings.myDocumentExportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENT_PAGES, "Failed to export pages", e)
                userNotifier.showInfo(strings.errorOccurred)
            }
        }
    }

    /**
     * Builds the pages-within-a-document editor's controller for one destination entry. Mirror of classic
     * `MyDocumentPagesComposeActivity`'s `onCreate` + `reload` + `applyChanges` + `addPageToList`, all of which
     * needed Room and therefore could not live in the graph.
     *
     * [onImport]/[onExport]/[onExportSelected] are the SAME lambdas [MyDocumentPagesDeps] exposes at the top
     * level, so there is exactly one definition of "launch the import picker" / "export this page" / "export these
     * pages", not two copies that could drift.
     */
    private fun myDocumentPagesControllerFor(
        documentId: String,
        documentInitials: String,
        onResult: (MyDocumentPagesResult) -> Unit,
        onImport: () -> Unit,
        onExport: (id: Long) -> Unit,
        onExportSelected: (ids: List<Long>) -> Unit,
    ): MyDocumentPagesController {
        val docId = IdType(documentId)
        myDocumentPagesEntityByLong = emptyMap()

        lateinit var controller: MyDocumentPagesController
        val session = MyDocumentPagesSession(documentId)
        myDocumentPagesSession = session

        fun nextLongId(): Long = (myDocumentPagesEntityByLong.keys.maxOrNull() ?: -1L) + 1L

        /** Mirror of classic `addPageToList`: insert, assign the next stable Long id, notify the controller. */
        fun addPageToList(title: String, contentType: MyDocumentContentType, content: String) {
            val pageId = IdType()
            val page = MyDocumentPage(
                id = pageId,
                documentId = docId,
                title = title,
                pageKey = "page_$pageId",
                contentType = contentType,
                orderNumber = controller.totalCount.value,
            )
            MyDocumentBookManager.launcher.launch("mydocument-pages-$docId") {
                myDocumentDao.insertPageWithContent(page, content)
            }
            val id = nextLongId()
            myDocumentPagesEntityByLong = myDocumentPagesEntityByLong + (id to page)
            val ct = if (contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN
            controller.addPage(MyDocPageItem(id, title, ct, isAiGenerated = false))
        }
        addMyDocumentPageToList = ::addPageToList

        /**
         * Mirror of classic `applyChanges`: delete removed pages, persist reorders/renames, always refresh the
         * SWORD book, and post `MyDocumentChange.AiDocPages` for the deletions.
         */
        suspend fun applyChanges(ordered: List<MyDocPageItem>, changed: Set<Long>, deleted: Set<Long>, pages: Map<Long, MyDocumentPage>) {
            val removedPages = deleted.mapNotNull { pages[it] }
            run {
                removedPages.forEach { p ->
                    myDocumentDao.pageById(p.id)?.let { myDocumentDao.deletePageWithContent(it) }
                }
            }
            val toUpdate = ArrayList<MyDocumentPage>()
            ordered.forEachIndexed { index, item ->
                val p = pages[item.id]?.copy() ?: return@forEachIndexed
                p.orderNumber = index
                p.title = item.name
                if (item.id in changed) { p.updatedAt = currentTimeMillis(); toUpdate.add(p) }
            }
            if (toUpdate.isNotEmpty()) myDocumentDao.updatePages(toUpdate)
            // Classic always refreshes: new pages are inserted directly to the DB in
            // addPageToList() without going through `changed`, so the SWORD book would otherwise be
            // stale.
            MyDocumentBookManager.refreshDocument(documentInitials)
            val deletedIds = deleted.mapNotNull { pages[it]?.id }
            if (deletedIds.isNotEmpty()) MyDocumentBookManager.notifyAiDocPagesChanged(MyDocumentChange.AiDocPages(deletedPageIds = deletedIds))
        }

        controller = MyDocumentPagesController(
            onOpenPage = { id ->
                // Mirror of classic `openPage`/`returnWithPage`: auto-save-on-leave rather than
                // classic's save-changes prompt, then refresh the SWORD book's key map (a snapshot)
                // before the caller resolves the returned pageKey against it.
                myDocumentPagesEntityByLong[id]?.let { page ->
                    if (controller.dirty.value) controller.save()
                    MyDocumentBookManager.launcher.launch("mydocument-pages-$docId") {
                        MyDocumentBookManager.refreshDocument(documentInitials)
                        withContext(Dispatchers.Main) {
                            if (shell.isHostAlive) {
                                onResult(MyDocumentPagesResult.Selected(documentInitials, page.pageKey))
                            }
                        }
                    }
                }
            },
            onImport = onImport,
            onExport = onExport,
            onCreatePage = { name, type ->
                val ct = if (type == ContentType.HTML) MyDocumentContentType.HTML else MyDocumentContentType.MARKDOWN
                addPageToList(name, ct, "")
            },
            onExportSelected = onExportSelected,
            onSave = { ordered, changed, deleted ->
                val pages = myDocumentPagesEntityByLong.mapValues { it.value.copy() }
                MyDocumentBookManager.launcher.launch("mydocument-pages-$docId") {
                    applyChanges(ordered, changed, deleted, pages)
                }
            },
        )

        session.controller = controller
        shell.hostScope.launch {
            // The old screen's dispose-time autosave runs under this key; read only after it, or a
            // recreation with unsaved edits loads pre-save rows and a later Save writes them back.
            MyDocumentBookManager.launcher.join("mydocument-pages-$docId")
            val list = withContext(Dispatchers.IO) { myDocumentDao.pagesForDocument(docId) }
            myDocumentPagesEntityByLong = list.mapIndexed { i, p -> i.toLong() to p }.toMap()
            // Selection is saved by persistent page id: the load-order tokens change when pages are reordered or deleted.
            val restoredPagesValue = restoredPages()?.takeIf { it.documentId == documentId }
            val tokenById = list.mapIndexed { i, p -> p.id.toString() to i.toLong() }.toMap()
            controller.setPages(
                list.mapIndexed { i, p ->
                    MyDocPageItem(
                        id = i.toLong(),
                        name = p.title,
                        contentType = if (p.contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN,
                        isAiGenerated = p.sourcePromptId != null,
                    )
                },
                restored = restoredPagesValue?.let { r ->
                    MyDocumentPagesSnapshot(
                        r.query, r.searchOpen, r.selectedPageIds.mapNotNull { tokenById[it] }.toSet(),
                    )
                },
            )
            // Consumed: later visits to a Pages screen of this host start fresh, like classic.
            if (this@MyDocumentsHost.restoredPages() != null) consumeRestoredPages()
            session.loaded = true
        }

        return controller
    }

    // --- MyDocuments host baggage ----------------------------------------------------------------

    /**
     * Classic `MyDocumentsComposeActivity`'s `entityByLong` plus the controller it is memoised alongside -- see
     * [MyDocumentsDeps.controllerFor]'s kdoc for why this whole session must survive `MyDocumentPages` sitting on
     * top of its destination, the same shape the bookmark host's session uses for the label manager.
     */
    private class MyDocumentsSession {
        lateinit var controller: MyDocumentsController
        var entityByLong: Map<Long, MyDocument> = emptyMap()
        /** True once the first load ran, i.e. the controller holds real state worth snapshotting. */
        var loaded = false
    }

    /**
     * `MyDocuments` is a root destination with no re-entry: reached only as the host's start destination, so this
     * is built once and never dropped.
     */
    private var myDocumentsSession: MyDocumentsSession? = null

    /**
     * Classic `MyDocumentsComposeActivity`'s `controller` plus its `onCreate` `reload()`, memoised on
     * [myDocumentsSession] -- see [MyDocumentsDeps.controllerFor] for why this controller must survive the pages
     * editor sitting on top of its destination.
     *
     * [onResult] is threaded through for shape parity with [myDocumentPagesControllerFor], but nothing below
     * actually calls it: `onOpen` is a no-op here (the arm's own `onOpen`, built from
     * [MyDocumentsDeps.routeForPages], handles the drill-down instead, since only the arm has the
     * `NavHostController`), and the `Selected` result reaches the documents channel through the pages child's own
     * channel rather than through this controller.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun myDocumentsControllerFor(onResult: (MyDocumentsResult) -> Unit): MyDocumentsController {
        myDocumentsSession?.let { return it.controller }

        val session = MyDocumentsSession()

        /**
         * Mirror of classic `applyChanges`: delete removed documents (and their pages, CASCADE), persist
         * reorders/renames/descriptions, and post `MyDocumentChange.AiDocPages` for the deletions.
         */
        fun applyMyDocumentsChanges(ordered: List<MyDocItem>, changed: Set<Long>, deleted: Set<Long>) {
            val removedDocs = deleted.mapNotNull { session.entityByLong[it] }
            // The entity edits are in-memory and immediate, so the screen that follows already sees them.
            val toUpdate = ArrayList<MyDocument>()
            ordered.forEachIndexed { index, item ->
                val doc = session.entityByLong[item.id] ?: return@forEachIndexed
                doc.orderNumber = index
                doc.name = item.name
                doc.description = item.description.ifEmpty { null }
                if (item.id in changed) { doc.updatedAt = currentTimeMillis(); toUpdate.add(doc) }
            }
            // Must-complete: the user's Save must reach the database even if this host is destroyed
            // right after it, so it runs in the application scope rather than the host scope.
            // The ordered "mydocument-sync" key keeps two quick Saves in order, and keeps them behind a sync's own writes.
            MyDocumentBookManager.launcher.launch("mydocument-sync") {
                val deletedPageIds = removedDocs.flatMap { doc -> myDocumentDao.pagesForDocument(doc.id).map { it.id } }
                val freshDocs = removedDocs.mapNotNull { myDocumentDao.documentById(it.id) }
                // JSword's Activator and SwordGenBook are not thread-safe: registration changes stay on Main.
                withContext(Dispatchers.Main) { freshDocs.forEach { MyDocumentBookManager.unregisterDocument(it.initials) } }
                freshDocs.forEach { myDocumentDao.delete(it) }
                if (toUpdate.isNotEmpty()) myDocumentDao.updateDocuments(toUpdate)
                if (deletedPageIds.isNotEmpty()) withContext(Dispatchers.Main) {
                    MyDocumentBookManager.notifyAiDocPagesChanged(MyDocumentChange.AiDocPages(deletedPageIds = deletedPageIds))
                }
            }
        }

        session.controller = MyDocumentsController(
            // See this function's own kdoc: the real "open" handling lives in the arm, not here.
            onOpen = {},
            onImport = { importMyDocuments() },
            onExport = { id -> launchMyDocumentsExport(listOf(id), batch = false) },
            onCreate = { name -> createMyDocument(session, name) },
            onExportSelected = { ids -> launchMyDocumentsExport(ids, batch = true) },
            onSave = { ordered, changed, deleted -> applyMyDocumentsChanges(ordered, changed, deleted) },
        )
        myDocumentsSession = session

        shell.hostScope.launch {
            // The old composition queues its must-complete autosave on disposal. Re-read only
            // after that ordered work; replaying old changed/deleted tokens would apply it twice.
            MyDocumentBookManager.launcher.join("mydocument-sync")
            val docs = withContext(Dispatchers.IO) { myDocumentDao.allDocuments() }
            session.entityByLong = docs.mapIndexed { i, d -> i.toLong() to d }.toMap()
            session.controller.setDocuments(
                docs.mapIndexed { i, d ->
                    MyDocItem(
                        id = i.toLong(),
                        initials = d.initials,
                        name = d.name,
                        description = d.description ?: "",
                        isAiGenerated = d.sourcePromptId != null,
                        canDelete = MyDocumentBookManager.canDeleteDocument(d),
                    )
                },
                restored = restoredDocumentsSearch()?.toSnapshot(),
            )
            session.loaded = true
        }

        return session.controller
    }

    /** Mirror of classic `createNewDocument`: insert + register a fresh empty document, then add it to the session's list. */
    private fun createMyDocument(session: MyDocumentsSession, name: String) {
        MyDocumentBookManager.launcher.launch("mydocument-create") {
            val initials = MyDocumentBookManager.generateInitials(name)
            val newDoc = MyDocument(name = name, initials = initials, orderNumber = withContext(Dispatchers.Main) { session.controller.totalCount.value })
            myDocumentDao.insert(newDoc)
            withContext(Dispatchers.Main) {
                MyDocumentBookManager.registerDocument(newDoc)
                if (!shell.isHostAlive || myDocumentsSession !== session) return@withContext
                val id = (session.entityByLong.keys.maxOrNull() ?: -1L) + 1L
                session.entityByLong = session.entityByLong + (id to newDoc)
                session.controller.addDocument(MyDocItem(id, initials, name, "", isAiGenerated = false, canDelete = true))
            }
        }
    }

    /** A folder was picked but the export target is gone: never drop it silently. */
    private fun reportLostMyDocumentExport() {
        Log.e(TAG_MY_DOCUMENTS, "Export folder picked but no pending export target survived")
        userNotifier.showInfo(strings.errorOccurred)
    }

    /** Picked files awaiting a user-entered name; [myDocumentImportNamePrompt] is shown while non-null. */
    private var pendingMyDocumentImportRefs: List<DocumentRef>? = null

    /**
     * Classic's `importNamePrompt` Compose state as a host flow -- see [MyDocumentsDeps.importNamePrompt]'s kdoc for
     * why it cannot be `remember`ed in the arm.
     */
    internal val myDocumentImportNamePrompt = MutableStateFlow<String?>(null)

    private fun importMyDocuments() {
        importPicking = true
        shell.hostScope.launch { completeImportPick(documentPicker.openDocuments(MY_DOCUMENT_IMPORT_MIME_TYPES, multiple = true)) }
    }

    private fun completeImportPick(refs: List<DocumentRef>) {
        importPicking = false
        // Parity with classic showImportNameDialog: don't import immediately -- stash the files
        // and open the import name dialog pre-filled with the same default name; import runs on
        // the user's confirm.
        if (refs.isNotEmpty()) {
            pendingMyDocumentImportRefs = refs
            myDocumentImportNamePrompt.value =
                strings.newDocumentName((myDocumentsSession?.entityByLong?.size ?: 0) + 1)
        }
    }

    /** Import name dialog confirmed: run the import with the user's chosen name, then clear pending state. */
    private fun confirmImportMyDocument(name: String) {
        val refs = pendingMyDocumentImportRefs
        myDocumentImportNamePrompt.value = null
        pendingMyDocumentImportRefs = null
        if (refs != null) importMyDocumentFromFiles(name, refs)
    }

    /** Import name dialog dismissed: drop the pending files without importing. */
    private fun dismissImportMyDocument() {
        myDocumentImportNamePrompt.value = null
        pendingMyDocumentImportRefs = null
    }

    /** Remember the picked documents by PERSISTENT id (see [PendingMyDocumentExports]) and open the folder picker. */
    internal fun launchMyDocumentsExport(tokens: List<Long>, batch: Boolean) {
        val ids = tokens.mapNotNull { myDocumentsSession?.entityByLong?.get(it)?.id?.toString() }
        if (ids.isEmpty()) return reportLostMyDocumentExport()
        pendingExports.restoreFrom(null)
        pendingExports.documentIds = ids
        pendingExports.documentsBatch = batch
        val ticket = ++exportTicket
        shell.hostScope.launch { completeExportPick(ticket, documentPicker.openDocumentTree()) }
    }

    /**
     * Import selected text files as a new document under the user-entered [documentName]. Ported from classic
     * `MyDocumentsComposeActivity.importFromFiles` (`Dispatchers.IO` body).
     */
    private fun importMyDocumentFromFiles(documentName: String, refs: List<DocumentRef>) {
        val session = myDocumentsSession ?: return
        MyDocumentBookManager.launcher.launch("mydocument-create") {
            try {
                val entries = files.readDocumentFiles(refs)

                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) { userNotifier.showInfo(strings.myDocumentImportEmptySelection) }
                    return@launch
                }

                val initials = MyDocumentBookManager.generateInitials(documentName)
                val newDocument = MyDocument(
                    name = documentName,
                    initials = initials,
                    orderNumber = session.controller.totalCount.value,
                )
                myDocumentDao.insert(newDocument)

                for ((index, entry) in entries.withIndex()) {
                    val contentType = when {
                        entry.fileName.endsWith(".html", true)
                            || entry.fileName.endsWith(".htm", true) -> MyDocumentContentType.HTML
                        else -> MyDocumentContentType.MARKDOWN
                    }
                    val rawName = entry.fileName.substringBeforeLast(".")
                    val title = rawName.replace(Regex("^\\d+-"), "").trim()
                        .ifEmpty { strings.newPageName(index + 1) }

                    val pageId = IdType()
                    val page = MyDocumentPage(
                        id = pageId,
                        documentId = newDocument.id,
                        title = title,
                        pageKey = "page_$pageId",
                        contentType = contentType,
                        orderNumber = index,
                    )
                    myDocumentDao.insertPageWithContent(page, entry.content)
                }

                withContext(Dispatchers.Main) {
                    MyDocumentBookManager.registerDocument(newDocument)
                    if (!shell.isHostAlive || myDocumentsSession !== session) return@withContext
                    val id = (session.entityByLong.keys.maxOrNull() ?: -1L) + 1L
                    session.entityByLong = session.entityByLong + (id to newDocument)
                    session.controller.addDocument(
                        MyDocItem(id, initials, documentName, "", isAiGenerated = false, canDelete = true),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENTS, "Failed to import files", e)
                withContext(Dispatchers.Main) { userNotifier.showInfo(strings.errorOccurred) }
            }
        }
    }

    /**
     * Resolve the single pending document by persistent id (the session may not be loaded after a recreate) and
     * export it; an unresolvable id shows the generic error toast.
     */
    private fun exportMyDocumentByIdToFolder(id: String, tree: DocumentRef) {
        shell.hostScope.launch {
            val document = withContext(Dispatchers.IO) { myDocumentDao.documentById(IdType.fromString(id)) }
            if (document == null) reportLostMyDocumentExport()
            else exportMyDocumentToFolder(document, tree)
        }
    }

    /** Export one document's pages to a chosen tree folder. */
    private suspend fun exportMyDocumentToFolder(document: MyDocument, tree: DocumentRef) {
        try {
            val pages = withContext(Dispatchers.IO) { myDocumentDao.pagesWithContentForDocument(document.id) }
            if (pages.isEmpty()) return
            if (!files.exportPagesToFolder(pages, tree)) return
            userNotifier.showInfo(strings.myDocumentExportSuccess)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_MY_DOCUMENTS, "Failed to export document", e)
            userNotifier.showInfo(strings.errorOccurred)
        }
    }

    /**
     * Export several documents into one chosen folder, each into its OWN subdirectory -- two documents can hold
     * same-named pages, and a flat batch export would collide.
     */
    private fun exportMyDocumentsToFolder(ids: List<String>, tree: DocumentRef) {
        shell.hostScope.launch {
            try {
                val exports = withContext(Dispatchers.IO) {
                    ids.mapNotNull { id ->
                        val document = myDocumentDao.documentById(IdType.fromString(id)) ?: return@mapNotNull null
                        // Fetch first, and skip a page-less document BEFORE creating its subdirectory --
                        // otherwise a batch containing an empty document leaves a stray empty folder.
                        val pages = myDocumentDao.pagesWithContentForDocument(document.id)
                        if (pages.isEmpty()) return@mapNotNull null
                        val folderName = document.name
                            .replace(FILE_NAME_CHARS, "")
                            .take(50)
                            .ifEmpty { document.initials }
                        DocumentFolderExport(folderName, pages)
                    }
                }
                if (!files.exportDocumentsToFolder(exports, tree)) return@launch
                userNotifier.showInfo(strings.myDocumentExportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENTS, "Failed to export documents", e)
                userNotifier.showInfo(strings.errorOccurred)
            }
        }
    }

    // Last member on purpose: restoring may resume a pick at once, and that touches every property above.
    init {
        restore(restored)
    }
}
