/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.installedDocument
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.download.DownloadManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.hideFromSelector
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.anySelectedDeletable
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.strings.LocalStrings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.koin.android.ext.android.inject

/**
 * Per-SCREEN preference keys, not shared with Download: sorting a download list by size is a
 * different intent from ordering the reading view's document picker, and changing one must not
 * silently reorder the other.
 */
private const val ARRANGEMENT_KEY = "chooseDoc.arrangement"
private const val ARRANGEMENT_REMEMBER_KEY = "chooseDoc.arrangement.remember"

/**
 * Compose host for the document (bible/commentary/…) chooser — the new-path twin of classic
 * [ChooseDocument]. It loads JSword [Book]s off-main, flattens them to [DocRow]s, and drives the
 * shared [DocumentSelectionController]/[DocumentSelectionScreen]. All JSword side effects
 * (open/delete/about/unlock/search) run behind the controller's seams, host-side.
 *
 * Result contract parity: [onSelect] returns via an Intent whose className is [ChooseDocument]
 * (with the `book` initials extra) — `MainBibleActivity.onActivityResult` dispatches on that
 * className, so it MUST NOT change.
 */
class ChooseDocumentComposeActivity : ActivityBase() {
    private val downloadControl: DownloadControl by inject()
    private val documentControl: DocumentControl by inject()

    /** docId (Book.initials) -> Book, rebuilt on every (re)load. */
    private var booksById: Map<String, Book> = emptyMap()

    private val controller by lazy {
        DocumentSelectionController(
            // Classic ChooseDocument.sortLanguages: alphabetical by display name.
            langComparator = compareBy { it.displayName },
            onSelect = ::handleDocumentSelection,
            onDelete = ::handleDelete,
            onDeleteIndex = ::handleDeleteIndex,
            onAbout = ::handleAbout,
            onUnlock = ::handleUnlock,
            onStickyLanguage = { lang ->
                // Keep a sticky record like classic's lastSelectedLanguage. ChooseDocument itself
                // starts with no language filter (classic setDefaultLanguage = -1), so this is not
                // read back on launch here; stored for parity / potential reuse.
                CommonUtils.settings.setString("selected_language_code", lang?.code)
            },
            // No SIZE (DocRowMapper sets installSizeMb = null here — it is a download-only field)
            // and no RECOMMENDED (this screen never loads the recommended-documents config, so the
            // flag is always false). A criterion with no data is a lie, so it is not offered.
            applicableSortKeys = setOf(DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME,
                DocSortKey.LANGUAGE, DocSortKey.REPOSITORY),
            applicableGroupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY),
            storedArrangement = if (CommonUtils.settings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true))
                CommonUtils.settings.getString(ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = CommonUtils.settings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                CommonUtils.settings.setBoolean(ARRANGEMENT_REMEMBER_KEY, remember)
                CommonUtils.settings.setString(ARRANGEMENT_KEY, encoded)
            },
            // Round 17e-1 final-review fix (I3): debounces the drag-reorder commit (persist +
            // refilter) so it fires once per settled gesture, not once per item swap.
            scope = lifecycleScope,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial document-type filter (classic ChooseDocument.setInitialDocumentType + base "addons").
        controller.setTypeFilter(initialTypeFilter())
        // Pre-seed the free-text search from the "search" extra (classic base initialiseView).
        intent.getStringExtra("search")?.let { controller.setQuery(it); controller.openSearch() }

        val title = getString(R.string.chooseBook)

        lifecycleScope.launch {
            loadDocuments()
        }

        setContent {
            AbAppTheme {
                    val strings = LocalStrings.current
                    val loading by controller.loading.collectAsState()
                    val displayed by controller.displayed.collectAsState()
                    val languages by controller.languages.collectAsState()
                    val selectedLanguage by controller.selectedLanguage.collectAsState()
                    val selectedTypeFilter by controller.selectedTypeFilter.collectAsState()
                    val query by controller.query.collectAsState()
                    val resultCount by controller.resultCount.collectAsState()
                    val selectionMode by controller.selectionMode.collectAsState()
                    val selectedIds by controller.selectedIds.collectAsState()
                    val error by controller.error.collectAsState()
                    val searchModeActive by controller.searchModeActive.collectAsState()
                    val grouped by controller.grouped.collectAsState()
                    val arrangement by controller.arrangement.collectAsState()
                    val repositories by controller.repositories.collectAsState()
                    val rememberArrangement by controller.rememberArrangement.collectAsState()
                    val arrangementIsDefault by controller.arrangementIsDefault.collectAsState()

                    val firstSelected = displayed.firstOrNull { it.docId in selectedIds }

                    DocumentSelectionScreen(
                        title = title,
                        downloadMode = false,
                        loading = loading,
                        isRefreshing = false,
                        onRefresh = null,
                        grouped = grouped,
                        languages = languages,
                        selectedLanguage = selectedLanguage,
                        typeFilters = typeFilterLabels(strings),
                        selectedTypeFilter = selectedTypeFilter,
                        query = query,
                        resultCount = strings.docFilterResults(resultCount),
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        error = error,
                        topBarActions = { OverflowMenu() },
                        onQueryChange = controller::setQuery,
                        searchModeActive = searchModeActive,
                        onOpenSearch = controller::openSearch,
                        onCloseSearch = controller::closeSearch,
                        onLanguageChange = controller::setLanguage,
                        onTypeFilterChange = {
                            // Persist like classic's spinner listener so "else last saved" works.
                            CommonUtils.settings.setInt("selected_document_filter_no", it.ordinal)
                            controller.setTypeFilter(it)
                        },
                        arrangement = arrangement,
                        groupKeys = controller.groupKeys,
                        repositories = repositories,
                        rememberArrangement = rememberArrangement,
                        arrangementIsDefault = arrangementIsDefault,
                        onMoveSort = controller::moveSortCriterion,
                        onToggleSortDirection = controller::toggleSortDirection,
                        onGroupByChange = controller::setGroupBy,
                        onRepositoryChange = controller::setRepositoryFilter,
                        onRememberChange = controller::setRememberArrangement,
                        onResetArrangement = controller::resetArrangement,
                        onRowClick = { row ->
                            if (selectionMode) controller.toggle(row.docId) else controller.select(row.docId)
                        },
                        onRowLongClick = { row ->
                            controller.enterSelection()
                            controller.toggle(row.docId)
                        },
                        onDownload = { /* no-op: ChooseDocument is not download mode */ },
                        onCancel = { /* no-op: ChooseDocument is not download mode */ },
                        onSelectionAbout = controller::about,
                        onSelectionDelete = controller::delete,
                        onSelectionDeleteIndex = controller::deleteIndex,
                        onSelectionUnlock = controller::unlock,
                        unlockVisible = firstSelected?.enciphered == true,
                        deleteVisible = anySelectedDeletable(displayed, selectedIds),
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                        onExitSelection = controller::clearSelection,
                    )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back dismisses what is visually on top: the selection bar covers the search bar
        // (AbSelectionScaffold's precedence), so selection goes first. Closing search underneath a
        // visible selection bar would clear the query and re-filter the list invisibly.
        if (controller.selectionMode.value) { controller.clearSelection(); return }
        if (controller.searchModeActive.value) { controller.closeSearch(); return }
        super.onBackPressed()
    }

    // --- Loading / DocRow construction ------------------------------------------------------

    /** (Re)load Books off-main, rebuild the docId->Book map + DocRows, seed the FTS DAO, and push. */
    private suspend fun loadDocuments() {
        try {
            val books = withContext(Dispatchers.Default) {
                SwordDocumentFacade.documents + FakeBookFactory.pseudoDocuments.filterNot { it.hideFromSelector }
            }
            val rows = withContext(Dispatchers.Default) {
                // Round 15b Task 5: the Book -> DocRow mapping (language grouping included) now lives
                // in DocRowMapper, shared with the reading view's document quick sheet so the two
                // cannot drift. One mapper per book list — see its kdoc.
                val mapper = DocRowMapper(downloadControl, books)
                // Round 17e-2: no FTS seeding here any more. The search runs in the controller over
                // the loaded rows (matchesDocumentQuery), which indexes the same four fields the
                // FTS table did. DocumentSearch / TemporaryDatabase stay in the tree because
                // classic DocumentSelectionBase still uses them; Batch Z deletes them whole.
                books.map { mapper.toDocRow(it) }
            }
            booksById = books.associateBy { it.initials }
            controller.setDocuments(rows)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading documents", e)
            controller.showError()
        }
    }

    // --- Controller seams (JSword side effects) ---------------------------------------------

    /** Classic ChooseDocument.handleDocumentSelection. Result className = [ChooseDocument]. */
    private fun handleDocumentSelection(docId: String) {
        val book = booksById[docId] ?: return
        if (book.bookCategory == BookCategory.AND_BIBLE) return
        lifecycleScope.launch(Dispatchers.Main) {
            if (book.isLocked && !CommonUtils.unlockDocument(this@ChooseDocumentComposeActivity, book)) {
                loadDocuments()
                return@launch
            }
            Log.i(TAG, "Book selected:" + book.initials)
            val myIntent = Intent(this@ChooseDocumentComposeActivity, ChooseDocument::class.java)
            myIntent.putExtra("book", book.initials)
            setResult(Activity.RESULT_OK, myIntent)
            finish()
        }
    }

    /**
     * Classic `DocumentSelectionBase.handleDelete`, with one deliberate change: classic — and this
     * port's first copy of it — opened one `AlertDialog` PER selected document, so a three-document
     * selection stacked three dialogs, each naming one document and each triggering its own reload.
     * One dialog now lists every document that will be deleted, and the non-deletable remainder is
     * reported once rather than once per document.
     */
    private fun handleDelete(ids: Set<String>) {
        val selected = ids.mapNotNull { booksById[it] }
        val (deletable, rest) = selected.partition { documentControl.canDelete(it.installedDocument) }
        if (rest.isNotEmpty()) ABEventBus.post(net.bible.android.control.event.ToastEvent(R.string.cant_delete_document))
        if (deletable.isEmpty()) return
        val msg: CharSequence = if (deletable.size == 1) {
            getString(R.string.delete_doc, deletable.single().name)
        } else {
            getString(R.string.delete_docs_confirm) + "\n\n" + deletable.joinToString("\n") { it.name }
        }
        AlertDialog.Builder(this)
            .setMessage(msg).setCancelable(true)
            .setPositiveButton(R.string.yes) { _, _ ->
                // Re-checked per document INSIDE the loop, not just in the partition above:
                // Book.canDelete is `!lastBible && ...`, so with exactly two Bibles installed both
                // pass the partition, and deleting them both would leave zero Bibles — the state the
                // lastBible guard exists to prevent. Deleting one flips the other's flag.
                var skipped = false
                for (document in deletable) {
                    if (!documentControl.canDelete(document.installedDocument)) { skipped = true; continue }
                    try {
                        Log.i(TAG, "Deleting:$document")
                        documentControl.deleteDocument(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG, "Deleting document crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                if (skipped) ABEventBus.post(net.bible.android.control.event.ToastEvent(R.string.cant_delete_document))
                lifecycleScope.launch { loadDocuments() }
                ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
            }
            .setNegativeButton(R.string.no, null)
            .create().show()
    }

    /** Classic DocumentSelectionBase.handleDeleteIndex. */
    private fun handleDeleteIndex(ids: Set<String>) {
        for (document in ids.mapNotNull { booksById[it] }) {
            val msg: CharSequence = getString(R.string.delete_search_index_doc, document.name)
            AlertDialog.Builder(this)
                .setMessage(msg).setCancelable(true)
                .setPositiveButton(R.string.okay) { _, _ ->
                    try {
                        Log.i(TAG, "Deleting index:$document")
                        SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG, "Deleting index crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create().show()
        }
    }

    /** Classic DocumentSelectionBase.handleAbout: reload SBMD (retaining repo/BadDocument), then showAbout. */
    private fun handleAbout(docId: String) {
        val document = booksById[docId] ?: return
        try {
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            val badDocument = sbmd.getProperty("BadDocument")
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            sbmd.putProperty("BadDocument", badDocument)
            lifecycleScope.launch(Dispatchers.Main) {
                CommonUtils.showAbout(this@ChooseDocumentComposeActivity, document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding SwordBookMetaData for $document", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    /** Classic ChooseDocument action-mode unlock: unlockDocument + reload. */
    private fun handleUnlock(docId: String) {
        val document = booksById[docId] ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            CommonUtils.unlockDocument(this@ChooseDocumentComposeActivity, document)
            loadDocuments()
        }
    }

    // --- Overflow menu (classic ChooseDocument.onOptionsItemSelected) -----------------------

    @androidx.compose.runtime.Composable
    private fun OverflowMenu() {
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.download),
                onClick = { close(); onDownload() },
                icon = { Icon(painterResource(R.drawable.ic_file_download_24dp), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.backup_modules2),
                onClick = { close(); onBackup() },
                icon = { Icon(painterResource(R.drawable.ic_backup_black_24dp), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.install_zip),
                onClick = { close(); onInstallZip() },
                icon = { Icon(painterResource(R.drawable.ic_unarchive_white_24dp), contentDescription = null) },
            )
        }
    }

    private fun onDownload() {
        try {
            if (downloadControl.checkDownloadOkay()) {
                val handlerIntent = ScreenLauncher.intentFor(this, Screen.Download)
                lifecycleScope.launch {
                    awaitIntent(handlerIntent)
                    ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
                    loadDocuments()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening download", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun onBackup() {
        lifecycleScope.launch { BackupControl.backupModulesViaIntent(this@ChooseDocumentComposeActivity) }
    }

    private fun onInstallZip() {
        val intent = ScreenLauncher.intentFor(this, Screen.InstallZip)
        lifecycleScope.launch {
            awaitIntent(intent)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
            loadDocuments()
        }
    }

    // --- Extras / mappings ------------------------------------------------------------------

    private fun initialTypeFilter(): DocTypeFilter = when (intent.extras?.getString("type")) {
        "BIBLE" -> DocTypeFilter.BIBLE
        "COMMENTARY" -> DocTypeFilter.COMMENTARY
        else -> if (intent.getBooleanExtra("addons", false)) DocTypeFilter.ADDON
        else DocTypeFilter.entries.getOrElse(
            CommonUtils.settings.getInt("selected_document_filter_no", 0),
        ) { DocTypeFilter.ALL }
    }

    private fun typeFilterLabels(strings: net.bible.sharedui.strings.Strings): List<Pair<DocTypeFilter, String>> = listOf(
        DocTypeFilter.ALL to strings.docTypeAll,
        DocTypeFilter.BIBLE to strings.docTypeBible,
        DocTypeFilter.COMMENTARY to strings.docTypeCommentary,
        DocTypeFilter.DICTIONARY to strings.docTypeDictionary,
        DocTypeFilter.GENERAL_BOOK to strings.docTypeGeneralBook,
        DocTypeFilter.MAPS to strings.docTypeMaps,
        DocTypeFilter.ADDON to strings.docTypeAddon,
    )

    companion object {
        private const val TAG = "ChooseDocumentCompose"
    }
}
