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
package net.bible.sharedcore.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.search.SearchModeController

/**
 * How long [DocumentSelectionController.moveSortCriterion] waits after the LAST swap of a drag
 * gesture before committing (persisting + refiltering) the reordered criteria — see that
 * function's KDoc (round 17e-1 final-review fix I3).
 */
private const val SORT_REORDER_COMMIT_DELAY_MS = 300L

/**
 * Filter, then arrange. Filtering (type / language / search / repository) is separate from
 * ordering because ordering is now user-controlled and lives in [sortDocuments]; before round 17e
 * this function hard-coded a single composite comparator, which [defaultArrangement] reproduces.
 */
fun computeDisplayedDocuments(
    all: List<DocRow>,
    lang: LangOption?,
    type: DocTypeFilter,
    query: String,
    arrangement: DocArrangement = defaultArrangement(DocSortKey.entries.toSet()),
    searchType: DocTypeFilter? = null,
): List<DocRow> =
    sortDocuments(
        all.filter { row ->
            // F56(b): a present search overrides the category filter. `NavRoutes.download` carries
            // no category, so the arm seeds from the PERSISTED `selected_document_filter_no` --
            // whatever the user last picked, in some other session. A targeted search (a
            // `download://` deep link naming one module) has no way to know or change that, so it
            // must not be narrowed by it. Classic behaved the same way; this is a deliberate
            // improvement, not parity. F72: a category picked DURING a search
            // ([DocumentSelectionController.pickTypeFilter]) narrows that search via [searchType];
            // the persisted filter still never applies to one.
            (if (query.isNotBlank()) searchType?.test(row) ?: true else type.test(row)) &&
                (lang == null || row.language.groupingKey == lang.groupingKey || row.category == DocCategory.AND_BIBLE) &&
                // F56(a): `osisId` first, as the @Fts4 DocumentSearch entity had it. Dropping it in
                // 5e0a78051 is what made `download://?initials=X` unable to match anything.
                matchesDocumentQuery(
                    query,
                    listOf(row.osisId, row.abbreviation, row.name, row.language.displayName, row.repository),
                ) &&
                (arrangement.repository == null || row.repository == arrangement.repository)
        },
        arrangement,
    )

/**
 * The document-selection screen's one confirm/error dialog slot (run-2 plan Task 16, appendix rows
 * 7954/8004/8034/8234/9306/9333) -- shared by Download and ChooseDocument, both of which build a
 * [DocumentSelectionController] instance, so writing this ONCE here covers both near-identical
 * host pairs at once. Kept BESIDE the existing [DocumentSelectionController.error] (not merged into
 * it): that field already has multiple writers (a load failure, in both hosts), a different concern
 * from these confirm/error questions.
 *
 * [ConfirmDownload]/[ConfirmDelete]/[Errors] carry an already HOST-FORMATTED message (D5): each
 * needs data this framework-free controller cannot produce itself (an Android string resource, a
 * live `documentControl.canDelete` recheck, a `resources.getQuantityString`), so the host builds the
 * text exactly as it did before this task and hands over the result. [ConfirmDeleteIndex] is the one
 * exception -- its queue is resolved entirely from this controller's own [DocRow] cache (see
 * [deleteIndex]), so it carries only the bare document name and the SCREEN formats the sentence via
 * `Strings.deleteSearchIndexDoc`, an entry that already existed before this task.
 */
sealed interface DocumentSelectionDialog {
    data object None : DocumentSelectionDialog
    data class ConfirmDownload(val message: String) : DocumentSelectionDialog
    data class ConfirmDelete(val message: String) : DocumentSelectionDialog
    data class ConfirmDeleteIndex(val docName: String) : DocumentSelectionDialog
    data class Errors(val title: String, val message: String) : DocumentSelectionDialog
    /** Task 23: classic `askIfWantToProceed()`'s three-way question (`NavHostComposeActivity
     *  :7605-7623`), answered through [DocumentSelectionController.askProceed] rather than the
     *  generic [DocumentSelectionController.confirmDialog]/[DocumentSelectionController.dismissDialog]
     *  pair -- see that function's KDoc for why. No payload: the title/message/option words are all
     *  static app copy, so they live in `Strings.kt` rather than being host-formatted like
     *  [ConfirmDownload]/[ConfirmDelete]/[Errors]. */
    data object ProceedWithDownload : DocumentSelectionDialog
    /** Task 23: classic `warnUserBooksNotDownloaded()`'s (`NavHostComposeActivity:8100-8114`)
     *  inflated-ListView summary, now a plain confirm-only dialog. [text] is host-built HTML (D5):
     *  the host resolves each not-found book's display name off `swordDocumentInfoDao`, this
     *  framework-free controller cannot. */
    data class BooksNotDownloaded(val text: String) : DocumentSelectionDialog
}

/** [DocumentSelectionController.askProceed]'s answer (Task 23). `null` (the suspend call's return
 *  type, not a member here) is Cancel -- the classic neutral button, back press or scrim tap. */
enum class ProceedAnswer { YES, DONT_ASK_AGAIN }

/**
 * Framework-free controller ported from DocumentSelectionBase's filter/sort/multi-select surface.
 * The host loads the Book list off-main, flattens to DocRow, and pushes via [setDocuments]. All
 * JSword side effects (open/delete/about/unlock) happen behind the injected seams.
 */
class DocumentSelectionController(
    private val langComparator: Comparator<LangOption>,
    private val onSelect: (String) -> Unit,
    private val onDelete: (Set<String>) -> Unit,
    private val onAbout: (String) -> Unit,
    private val onUnlock: (String) -> Unit,
    private val onStickyLanguage: (LangOption?) -> Unit,
    // Task 16 (D8-3 fix): the actual, one-document action a confirmed ConfirmDownload/ConfirmDelete/
    // ConfirmDeleteIndex runs -- still a host callback (JSword/Android side effects), only the
    // QUESTION moved here. Defaulted so every pre-existing positional test construction keeps
    // compiling unchanged.
    private val onConfirmDownload: (docId: String) -> Unit = {},
    private val onConfirmDelete: () -> Unit = {},
    private val onConfirmDeleteIndex: (docId: String) -> Unit = {},
    private val applicableSortKeys: Set<DocSortKey> = DocSortKey.entries.toSet(),
    private val applicableGroupKeys: List<DocGroupBy> = listOf(DocGroupBy.NONE),
    storedArrangement: String? = null,
    rememberArrangementInitially: Boolean = true,
    private val onArrangementChange: (encoded: String?, remember: Boolean) -> Unit = { _, _ -> },
    // Round 17e-1 final-review fix (I3). Null (the default, and every existing test's choice) makes
    // moveSortCriterion commit synchronously, same as before this fix — a host that cares about the
    // debounced behavior (i.e. every real screen) supplies its lifecycleScope.
    private val scope: CoroutineScope? = null,
) {
    private var all: List<DocRow> = emptyList()
    private var sortReorderCommitJob: Job? = null

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _documents = MutableStateFlow<List<DocRow>>(emptyList())
    val documents: StateFlow<List<DocRow>> = _documents.asStateFlow()
    private val _displayed = MutableStateFlow<List<DocRow>>(emptyList())
    val displayed: StateFlow<List<DocRow>> = _displayed.asStateFlow()
    private val _languages = MutableStateFlow<List<LangOption>>(emptyList())
    val languages: StateFlow<List<LangOption>> = _languages.asStateFlow()
    private val _selectedLanguage = MutableStateFlow<LangOption?>(null)
    val selectedLanguage: StateFlow<LangOption?> = _selectedLanguage.asStateFlow()
    private val _selectedTypeFilter = MutableStateFlow(DocTypeFilter.ALL)
    val selectedTypeFilter: StateFlow<DocTypeFilter> = _selectedTypeFilter.asStateFlow()

    /**
     * F72 (fix batch 3 §2.2.1): a category picked while a query is active. It narrows that search
     * only; the persisted [selectedTypeFilter] never applies to a search (F56(b)) and is not changed
     * by this. Null = the search is unfiltered. Cleared whenever the query becomes blank.
     */
    private var searchTypeFilter: DocTypeFilter? = null

    private val _shownTypeFilter = MutableStateFlow(DocTypeFilter.ALL)

    /** What the type chip shows: the filter that actually applies to [displayed] right now. */
    val shownTypeFilter: StateFlow<DocTypeFilter> = _shownTypeFilter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val searchMode = SearchModeController(onClearQuery = { setQuery("") })
    val searchModeActive: StateFlow<Boolean> = searchMode.active
    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()
    private val _error = MutableStateFlow<ChooserError?>(null)
    val error: StateFlow<ChooserError?> = _error.asStateFlow()
    private val _dialog = MutableStateFlow<DocumentSelectionDialog>(DocumentSelectionDialog.None)
    val dialog: StateFlow<DocumentSelectionDialog> = _dialog.asStateFlow()

    /** [requestDownloadConfirm]'s target, consumed (and cleared) by [confirmDialog]/[dismissDialog].
     *  Not part of [DocumentSelectionDialog.ConfirmDownload] itself -- the screen only needs the
     *  message text, and this id is purely the confirm action's own routing. */
    private var pendingDownloadDocId: String? = null

    /** [askProceed]'s one in-flight call, if any -- completed by [confirmProceed] (an answer) or
     *  [dismissProceed]/disposal (`null`, cancel). Same shape as `AiPromptsController
     *  .importModeDeferred` (Task 14 Step 2). */
    private var proceedDeferred: CompletableDeferred<ProceedAnswer?>? = null

    /**
     * D8-3 fix: classic opened one stacked platform dialog PER selected document. This queue asks
     * one at a time instead -- [deleteIndex] fills it from this controller's OWN [DocRow] cache (no
     * host round trip needed for the ask step, unlike bulk [delete] whose partition needs a live
     * `documentControl.canDelete` recheck the host alone can do), [showNextDeleteIndex] publishes the
     * head as the dialog state, and [confirmDialog]/[dismissDialog] both advance it -- confirm runs
     * that ONE document's action first, dismiss ("Cancel") skips only that one, exactly as the
     * stacked dialogs did before this fix.
     */
    private val deleteIndexQueue = ArrayDeque<DocRow>()

    private val _arrangement = MutableStateFlow(
        decodeArrangement(storedArrangement, applicableSortKeys, applicableGroupKeys.toSet())
    )
    val arrangement: StateFlow<DocArrangement> = _arrangement.asStateFlow()
    private val _rememberArrangement = MutableStateFlow(rememberArrangementInitially)
    val rememberArrangement: StateFlow<Boolean> = _rememberArrangement.asStateFlow()
    private val _repositories = MutableStateFlow<List<String>>(emptyList())
    val repositories: StateFlow<List<String>> = _repositories.asStateFlow()
    private val _grouped = MutableStateFlow<List<DocGroup<DocRow>>>(emptyList())
    val grouped: StateFlow<List<DocGroup<DocRow>>> = _grouped.asStateFlow()
    private val _arrangementIsDefault = MutableStateFlow(
        decodeArrangement(storedArrangement, applicableSortKeys, applicableGroupKeys.toSet()) ==
            defaultArrangement(applicableSortKeys)
    )
    val arrangementIsDefault: StateFlow<Boolean> = _arrangementIsDefault.asStateFlow()

    /** The group keys this screen offers, for the arrangement sheet's radio row. */
    val groupKeys: List<DocGroupBy> get() = applicableGroupKeys

    /**
     * Reorder one sort criterion. [AbReorderableColumn] (in `:sharedUi`) calls this once PER ITEM
     * SWAP during an active drag inside the arrangement sheet — a modal with no visible document
     * list behind it. Doing a full [applyArrangement] (a synchronous persistence write plus a
     * re-sort/re-group of the whole, potentially large, document list) on every swap would be
     * wasted main-thread work for a reorder the user cannot even see yet.
     *
     * [_arrangement] is still updated on every call, immediately, so the sheet's OWN UI (which
     * reads the live order) reflects each swap right away. Only the expensive commit is deferred:
     * with a [scope] supplied, it is debounced — cancelling any pending commit and rescheduling —
     * so it fires once, [SORT_REORDER_COMMIT_DELAY_MS] after the LAST swap, i.e. once the drag
     * settles. Without a [scope] it commits synchronously, matching the pre-fix behavior (the
     * choice every test that does not itself exercise this debounce makes).
     */
    fun moveSortCriterion(from: Int, to: Int) {
        val list = _arrangement.value.sort.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        val next = _arrangement.value.copy(sort = list)
        _arrangement.value = next
        _arrangementIsDefault.value = next == defaultArrangement(applicableSortKeys)
        val liveScope = scope
        if (liveScope == null) {
            applyArrangement(next)
            return
        }
        sortReorderCommitJob?.cancel()
        sortReorderCommitJob = liveScope.launch {
            delay(SORT_REORDER_COMMIT_DELAY_MS)
            applyArrangement(_arrangement.value)
        }
    }

    fun toggleSortDirection(key: DocSortKey) {
        applyArrangement(_arrangement.value.copy(
            sort = _arrangement.value.sort.map { if (it.key == key) it.copy(descending = !it.descending) else it },
        ))
    }

    fun setGroupBy(groupBy: DocGroupBy) = applyArrangement(_arrangement.value.copy(groupBy = groupBy))
    fun setRepositoryFilter(repository: String?) = applyArrangement(_arrangement.value.copy(repository = repository))
    fun resetArrangement() = applyArrangement(defaultArrangement(applicableSortKeys))

    /**
     * The user's "remember these settings" switch. Turning it OFF clears the stored value ONCE
     * (so the next launch really does start from the default) and then stops writing; the live
     * arrangement is untouched, because the switch is about persistence, not about this session.
     */
    fun setRememberArrangement(on: Boolean) {
        _rememberArrangement.value = on
        if (on) onArrangementChange(encodeArrangement(_arrangement.value), true)
        else onArrangementChange(null, false)
    }

    private fun applyArrangement(next: DocArrangement) {
        _arrangement.value = next
        _arrangementIsDefault.value = next == defaultArrangement(applicableSortKeys)
        if (_rememberArrangement.value) onArrangementChange(encodeArrangement(next), true)
        refilter()
    }

    fun setDocuments(all: List<DocRow>) {
        this.all = all
        _documents.value = all
        _loading.value = false
        // dedupe languages by groupingKey (representative = first seen), sort by host comparator
        _languages.value = all.map { it.language }
            .associateBy { it.groupingKey }.values
            .sortedWith(langComparator)
        _repositories.value = all.map { it.repository }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
        refilter()
    }

    /**
     * Update one row's live download status/progress IN PLACE — deliberately without re-sorting,
     * and without clearing an active selection.
     *
     * Classic sorts only in DocumentSelectionBase.filterDocuments() (a spinner/search change, or
     * after populateMasterDocumentList on refresh); DownloadActivity.doDownload issues a bare
     * notifyDataSetChanged(). Re-running computeDisplayed here instead made the row jump to the
     * top the instant it entered BEING_INSTALLED — that is computeDisplayed's first sort key —
     * which reads as the row disappearing from where the user left it. The sort keys are correct
     * and unchanged; they apply at the next re-sort (setDocuments / setLanguage / setTypeFilter /
     * setQuery), exactly as in classic.
     *
     * [canDelete] travels with the status because a finished install CHANGES it: the flag is
     * derived from the installed copy of the document, which does not exist until the download
     * completes. Without it the row kept the deletability it was loaded with, so the delete action
     * stayed hidden on a document the user had just installed until the screen was reopened. It is
     * part of the equality short-circuit below for the same reason — the terminal event can carry
     * a status and percentage the row already shows, and that is exactly the update that matters.
     */
    fun updateDownloadStatus(docId: String, status: DocInstallStatus, percentDone: Int, canDelete: Boolean) {
        val idx = all.indexOfFirst { it.docId == docId }
        if (idx < 0) return
        val cur = all[idx]
        if (cur.installStatus == status && cur.percentDone == percentDone && cur.canDelete == canDelete) return
        val updated = cur.copy(installStatus = status, percentDone = percentDone, canDelete = canDelete)
        all = all.toMutableList().apply { this[idx] = updated }
        _documents.value = all
        val shown = _displayed.value
        val shownIdx = shown.indexOfFirst { it.docId == docId }
        if (shownIdx >= 0) {
            _displayed.value = shown.toMutableList().apply { this[shownIdx] = updated }
        }
        // Mirror the in-place replacement into the grouped view. Deliberately NOT a regroup: a
        // status change can move a row between STATUS groups, and doing that mid-download is the
        // same "the row jumped away from where the user left it" defect the no-re-sort rule exists
        // to prevent. The next real refilter regroups.
        _grouped.value = _grouped.value.map { g ->
            val i = g.rows.indexOfFirst { it.docId == docId }
            if (i < 0) g else g.copy(rows = g.rows.toMutableList().apply { this[i] = updated })
        }
        // resultCount is deliberately NOT recomputed: no predicate in computeDisplayed reads
        // installStatus, so a status change can never add or remove a row from the displayed set.
    }

    /**
     * Round 17e-2: the query filters HERE, over the loaded rows, instead of the host running a
     * Room FTS query and pushing osisIds back. The FTS table indexed FIVE fields -- `osisId`
     * first -- and this matches the same five. The original claim of "these same four short
     * fields, so nothing is lost" was off by exactly one, and that one field is the only thing a
     * `download://?initials=X` deep link supplies (finding F56). The three-character minimum and
     * the per-keystroke IO hop still go with it. `matchesDocumentQuery` is shared with the cloud
     * list, which is what makes the two screens' search behave the same.
     */
    fun setQuery(q: String) { _query.value = q; if (q.isBlank()) searchTypeFilter = null; refilter() }
    fun openSearch() = searchMode.open()
    fun closeSearch() = searchMode.close()
    fun setLanguage(lang: LangOption?) { _selectedLanguage.value = lang; onStickyLanguage(lang); refilter() }
    fun setTypeFilter(f: DocTypeFilter) { _selectedTypeFilter.value = f; refilter() }

    /**
     * The type chip's pick. With a live query it narrows that search only and returns false (the
     * caller must NOT persist it); otherwise it is [setTypeFilter] and returns true.
     */
    fun pickTypeFilter(f: DocTypeFilter): Boolean {
        if (_query.value.isBlank()) {
            setTypeFilter(f)
            return true
        }
        searchTypeFilter = f
        refilter()
        return false
    }

    private fun refilter() {
        clearSelection()
        val out = computeDisplayed(all, _selectedLanguage.value, _selectedTypeFilter.value, _query.value, searchTypeFilter)
        _displayed.value = out
        _grouped.value = groupDocuments(out, _arrangement.value.groupBy)
        _resultCount.value = out.size
        _shownTypeFilter.value =
            if (_query.value.isNotBlank()) searchTypeFilter ?: DocTypeFilter.ALL else _selectedTypeFilter.value
    }

    fun computeDisplayed(
        all: List<DocRow>, lang: LangOption?, type: DocTypeFilter, query: String,
        searchType: DocTypeFilter? = null,
    ): List<DocRow> =
        computeDisplayedDocuments(all, lang, type, query, _arrangement.value, searchType)

    fun enterSelection() { _selectionMode.value = true }
    fun toggle(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun clearSelection() { _selectionMode.value = false; _selectedIds.value = emptySet() }

    fun select(docId: String) = onSelect(docId)

    /** Unchanged trigger: the host still owns the partition (a live `documentControl.canDelete`
     *  recheck + Book access this controller cannot do) and the message text, then calls
     *  [requestDeleteConfirm] with what it built -- see that function's kdoc. */
    fun delete() { onDelete(_selectedIds.value) }

    /** D8-3 fix (see [deleteIndexQueue]'s kdoc): resolves the CURRENT selection against this
     *  controller's own [all], queues them, and asks one at a time. No host round trip for the ask
     *  step -- unlike [delete], deleting a SEARCH INDEX never checked `canDelete` in the classic code
     *  this ports, so there is nothing here only the host can compute. */
    fun deleteIndex() {
        val ids = _selectedIds.value
        deleteIndexQueue.clear()
        deleteIndexQueue.addAll(all.filter { it.docId in ids })
        showNextDeleteIndex()
    }

    private fun showNextDeleteIndex() {
        val next = deleteIndexQueue.firstOrNull()
        _dialog.value = if (next == null) DocumentSelectionDialog.None
            else DocumentSelectionDialog.ConfirmDeleteIndex(next.name)
    }

    fun about() { _selectedIds.value.firstOrNull()?.let(onAbout) }
    fun unlock() { _selectedIds.value.firstOrNull()?.let(onUnlock) }

    /** [manageDownload]'s confirm-before-download question (run-2 plan Task 16, NH row 7954), asked
     *  by the host once it has resolved the target [Book] and built the message; [docId] is stashed
     *  only to route [confirmDialog]'s [onConfirmDownload] call back to the right document. */
    fun requestDownloadConfirm(docId: String, message: String) {
        pendingDownloadDocId = docId
        _dialog.value = DocumentSelectionDialog.ConfirmDownload(message)
    }

    /** The bulk-delete question (NH rows 8004/9306), asked by the host once it has partitioned the
     *  selection into deletable/not-deletable (a live `documentControl.canDelete` recheck) and built
     *  the singular-vs-plural message -- this controller only shows it and waits. */
    fun requestDeleteConfirm(message: String) { _dialog.value = DocumentSelectionDialog.ConfirmDelete(message) }

    /** The download-errors summary (NH row 8234); [title]/[message] are host-built (D5: needs
     *  `resources.getQuantityString`/repo names the host alone has). */
    fun showErrors(title: String, message: String) { _dialog.value = DocumentSelectionDialog.Errors(title, message) }

    /** The books-not-downloaded summary (NH row 8100), Download-only; [text] is host-built HTML --
     *  see [DocumentSelectionDialog.BooksNotDownloaded]'s KDoc. */
    fun showBooksNotDownloaded(text: String) { _dialog.value = DocumentSelectionDialog.BooksNotDownloaded(text) }

    /**
     * Classic `askIfWantToProceed()`'s (`NavHostComposeActivity:7605-7623`) three-way question, now
     * a suspend call over [DocumentSelectionDialog.ProceedWithDownload] -- the `CompletableDeferred`
     * shape `AiPromptsController.chooseImportMode` uses (Task 14 Step 2). Returns `null` on cancel
     * (the classic neutral "Cancel" button, back press, or a scrim tap).
     *
     * The host still owns the "download_do_not_ask" pref (Android `SharedPreferences`, unreachable
     * from this framework-free controller): it checks the pref BEFORE calling this at all, and -- on
     * [ProceedAnswer.DONT_ASK_AGAIN] -- writes it itself, exactly as classic's own
     * `setNegativeButton` did. This call only asks the question and reports which button was tapped.
     *
     * Fix-round-shaped guard (mirrors `chooseImportMode`): a second call while one is already in
     * flight completes the FIRST caller's `await()` with `null` first, so it never hangs forever, and
     * the `finally` covers the destination being disposed (composition torn down) while this suspend
     * call is still in flight -- either way, it only clears state that is still ITS OWN
     * (`proceedDeferred === deferred`), so a fresh call started by a dismissed OLD call's own
     * cleanup running late never has its state wiped out from under it.
     */
    suspend fun askProceed(): ProceedAnswer? {
        proceedDeferred?.complete(null)
        val deferred = CompletableDeferred<ProceedAnswer?>()
        proceedDeferred = deferred
        _dialog.value = DocumentSelectionDialog.ProceedWithDownload
        try {
            return deferred.await()
        } finally {
            if (proceedDeferred === deferred) {
                proceedDeferred = null
                if (_dialog.value == DocumentSelectionDialog.ProceedWithDownload) _dialog.value = DocumentSelectionDialog.None
            }
        }
    }

    /** [AbOptionsDialog][net.bible.sharedui.components.AbOptionsDialog]'s `onSelect`, mapped to an
     *  answer by the screen. A no-op unless [DocumentSelectionDialog.ProceedWithDownload] is actually
     *  showing -- the same guard every other confirm/choose function in this batch uses. */
    fun confirmProceed(answer: ProceedAnswer) {
        if (_dialog.value != DocumentSelectionDialog.ProceedWithDownload) return
        _dialog.value = DocumentSelectionDialog.None
        proceedDeferred?.complete(answer)
        proceedDeferred = null
    }

    /** The dialog's own dismiss (classic's neutral "Cancel" button, back press, or scrim tap). Same
     *  no-op guard as [confirmProceed]. */
    fun dismissProceed() {
        if (_dialog.value != DocumentSelectionDialog.ProceedWithDownload) return
        _dialog.value = DocumentSelectionDialog.None
        proceedDeferred?.complete(null)
        proceedDeferred = null
    }

    fun confirmDialog() {
        when (_dialog.value) {
            is DocumentSelectionDialog.ConfirmDownload -> {
                _dialog.value = DocumentSelectionDialog.None
                pendingDownloadDocId?.let(onConfirmDownload)
                pendingDownloadDocId = null
            }
            is DocumentSelectionDialog.ConfirmDelete -> {
                _dialog.value = DocumentSelectionDialog.None
                onConfirmDelete()
            }
            is DocumentSelectionDialog.ConfirmDeleteIndex -> {
                val doc = deleteIndexQueue.removeFirstOrNull()
                if (doc != null) onConfirmDeleteIndex(doc.docId)
                showNextDeleteIndex()
            }
            is DocumentSelectionDialog.Errors -> _dialog.value = DocumentSelectionDialog.None
            is DocumentSelectionDialog.BooksNotDownloaded -> _dialog.value = DocumentSelectionDialog.None
            // Answered through confirmProceed/dismissProceed instead -- see askProceed's KDoc. Not
            // reached through this generic path in production; kept as a defensive no-op rather than
            // an `else` so a NEW DocumentSelectionDialog case fails to compile here until considered.
            DocumentSelectionDialog.ProceedWithDownload -> {}
            DocumentSelectionDialog.None -> {}
        }
    }

    /** "Cancel". For [DocumentSelectionDialog.ConfirmDeleteIndex] this skips only the CURRENT
     *  document and shows the next one -- classic's per-document stacked dialogs let a Cancel on one
     *  skip just that document, and this fix's whole point (D8-3) is to keep that behavior while
     *  showing them one at a time instead of all stacked. Every other dialog just clears. */
    fun dismissDialog() {
        if (_dialog.value is DocumentSelectionDialog.ConfirmDeleteIndex) {
            deleteIndexQueue.removeFirstOrNull()
            showNextDeleteIndex()
        } else {
            pendingDownloadDocId = null
            _dialog.value = DocumentSelectionDialog.None
        }
    }

    fun showError() { _error.value = ChooserError.FAILED }
    fun dismissError() { _error.value = null }
}
