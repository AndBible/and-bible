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
package net.bible.android.view.activity.download

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.document.canDelete
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.LanguageGrouping
import net.bible.android.control.download.repoIdentity
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.DocumentSearch
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.DocumentConfiguration
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.PseudoBook
import net.bible.android.view.activity.base.installedDocument
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.CommonUtils.settings
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.download.DownloadManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.RepoFactory
import net.bible.service.download.isPseudoBook
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.AbTheme
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.koin.android.ext.android.inject
import java.io.File
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Compose host for the Download screen — the new-path twin of classic [DownloadActivity]. It reuses
 * the SAME shared [DocumentSelectionController]/[DocumentSelectionScreen] built in Plan A, running
 * them in `downloadMode = true` (install sizes shown, pull-to-refresh live, per-row download/cancel).
 *
 * All the download-specific orchestration ported from classic [DownloadActivity] lives here: the
 * network JSON fetches ([downloadDocJson]), the notification permission, the "do you want to
 * proceed" gate, staleness caching ([isRepoBookListOld]), the auto-download extras, and the live
 * per-row progress via [DownloadProgressBridge]. The heavy JSword side effects run behind the
 * controller seams, host-side.
 *
 * Unlike ChooseDocument this is a browse/download-**in-place** screen: [handleDocumentSelection]
 * starts a download rather than returning a result Intent.
 */
open class DownloadComposeActivity : ActivityBase() {
    private val downloadControl: DownloadControl by inject()
    private val documentControl: DocumentControl by inject()

    private val dao get() = DatabaseContainer.instance.downloadDocumentsDb.documentSearchDao()
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val bookmarksDao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    private lateinit var downloadManager: DownloadManager
    private lateinit var repoFactory: RepoFactory
    private val genericFileDownloader = GenericFileDownloader(this) { }

    private val hasErrors get() = genericFileDownloader.errors.isNotEmpty() || downloadManager.failedRepos.isNotEmpty()

    // Network-fetched configs (parity with classic Ref<…> fields).
    private var recommendedDocuments: DocumentConfiguration? = null
    private var defaultDocuments: DocumentConfiguration? = null
    private var badDocuments: DocumentConfiguration? = null
    private var pseudoBooks: List<PseudoBook>? = null

    /** docId (Book.repoIdentity) -> Book, rebuilt on every (re)load. repoIdentity is unique per repo+initials. */
    private var booksById: Map<String, Book> = emptyMap()
    /** The full loaded book list (for findBookByInitials in the auto-download extras). */
    private var allBooks: List<Book> = emptyList()
    /** Current DocRows host-side so progress updates can rebuild only the affected rows. */
    private var currentRows: List<DocRow> = emptyList()
    /** Current FTS search result set (osisIds) to preserve across progress/refresh pushes. */
    private var currentSearchIds: Set<String>? = null
    /** groupingKey -> sort rank from downloadControl.sortLanguages (RelevantLanguageSorter). */
    private var langRank: Map<String, Int> = emptyMap()

    private val booksNotFound = ArrayList<String>()

    private val bridge = DownloadProgressBridge()
    private val refreshing = MutableStateFlow(false)

    private val downloadScope = CoroutineScope(Dispatchers.Default)

    private val downloadDefaults get() = intent.extras?.getBoolean("download-recommended") == true

    /** FirstDownload onboarding variant (classic [FirstDownload]): shows an OK gate + hides installZip. */
    private val firstDownload get() = intent.getBooleanExtra(EXTRA_FIRST_DOWNLOAD, false)

    /**
     * Drives the OK button's enabled state in [firstDownload] mode: true once ≥1 Bible is installed.
     * Latches (classic `okayButtonEnabled` never flips back off), fed by [downloadCompletionListener].
     */
    private val hasBible = MutableStateFlow(false)

    /** Mirror of classic FirstDownload's JobManager WorkListener: enable OK once a Bible finishes downloading. */
    private val downloadCompletionListener = object : WorkListener {
        override fun workProgressed(workEvent: WorkEvent) {
            if (workEvent.job.isFinished) updateHasBible()
        }
        // Never called by JSword in practice, so all the work is done in workProgressed (classic note).
        override fun workStateChanged(workEvent: WorkEvent) {}
    }

    /** Latching check: once a Bible is installed, OK stays enabled (classic enableOkayButtonIfBibles). */
    private fun updateHasBible() {
        if (!hasBible.value) {
            hasBible.value = Books.installed().books.any { it.bookCategory == BookCategory.BIBLE }
        }
    }

    private val controller by lazy {
        DocumentSelectionController(
            // classic sortLanguages order (RelevantLanguageSorter): rank by the precomputed index.
            langComparator = Comparator { a, b ->
                (langRank[a.groupingKey] ?: Int.MAX_VALUE).compareTo(langRank[b.groupingKey] ?: Int.MAX_VALUE)
            },
            onSelect = ::handleDocumentSelection,
            onDelete = ::handleDelete,
            onDeleteIndex = ::handleDeleteIndex,
            onAbout = ::handleAbout,
            onUnlock = ::handleUnlock,
            onStickyLanguage = { lang -> CommonUtils.settings.setString("selected_language_code", lang?.code) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = DownloadManager { }
        repoFactory = RepoFactory(downloadManager)

        if (firstDownload) updateHasBible() // seed the OK gate once on create

        controller.setTypeFilter(initialTypeFilter())
        intent.getStringExtra("search")?.let { controller.setQuery(it) }

        lifecycleScope.launch {
            if (!askIfWantToProceed()) {
                finish()
                return@launch
            }
            CommonUtils.requestNotificationPermission(this@DownloadComposeActivity)

            downloadDocJson()

            val refresh = isRepoBookListOld
            loadDocuments(refresh)
            if (refresh) updateLastRepoRefreshDate()

            handleAutoDownloadExtras()

            // Observe the query and run the Room FTS off-main (same as ChooseDocument host).
            controller.query.collectLatest { q ->
                val ids = if (q.length >= 3) {
                    withContext(Dispatchers.IO) { runCatching { dao.search("$q*").toSet() }.getOrNull() }
                } else null
                currentSearchIds = ids
                controller.setSearchResults(ids)
            }
        }

        // Live per-row download progress: rebuild affected rows and re-push so the controller's
        // five-key sort floats being-installed rows to the top (classic notifyDataSetChanged parity).
        lifecycleScope.launch {
            bridge.statuses.collect { statusMap -> applyProgress(statusMap) }
        }

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
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
                    val isRefreshing by refreshing.collectAsState()

                    val firstSelected = displayed.firstOrNull { it.docId in selectedIds }
                    val bibleInstalled by hasBible.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                    DocumentSelectionScreen(
                        title = strings.downloadDocuments,
                        downloadMode = true,
                        loading = loading,
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            lifecycleScope.launch {
                                refreshing.value = true
                                try {
                                    controller.setQuery("")
                                    downloadDocJson()
                                    loadDocuments(refresh = true)
                                    updateLastRepoRefreshDate()
                                } finally {
                                    refreshing.value = false
                                }
                            }
                        },
                        displayed = displayed,
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
                        onLanguageChange = controller::setLanguage,
                        onTypeFilterChange = {
                            CommonUtils.settings.setInt("selected_document_filter_no", it.ordinal)
                            controller.setTypeFilter(it)
                        },
                        onRowClick = { row ->
                            if (selectionMode) controller.toggle(row.docId) else controller.select(row.docId)
                        },
                        onRowLongClick = { row ->
                            controller.enterSelection()
                            controller.toggle(row.docId)
                        },
                        onDownload = { controller.select(it.docId) },
                        onCancel = { row -> booksById[row.docId]?.let { downloadControl.cancelDownload(it) } },
                        onSelectionAbout = controller::about,
                        onSelectionDelete = controller::delete,
                        onSelectionDeleteIndex = controller::deleteIndex,
                        onSelectionUnlock = controller::unlock,
                        unlockVisible = firstSelected?.enciphered == true,
                        deleteVisible = firstSelected?.canDelete == true,
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                        onExitSelection = controller::clearSelection,
                    )

                    // FirstDownload onboarding OK gate: overlay a bottom button, enabled once a
                    // Bible is installed; returns DOWNLOAD_FINISH so StartupActivity proceeds.
                    if (firstDownload) {
                        Button(
                            onClick = { onOkay() },
                            enabled = bibleInstalled,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(strings.okay)
                        }
                    }
                    }
                }
            }
        }
    }

    /** Classic FirstDownload.onOkay: return DOWNLOAD_FINISH so StartupActivity advances to the main app. */
    private fun onOkay() {
        setResult(DownloadActivity.DOWNLOAD_FINISH)
        finish()
    }

    override fun onStart() {
        super.onStart()
        bridge.register()
        downloadControl.startMonitoringDownloads()
        if (firstDownload) {
            updateHasBible()
            JobManager.addWorkListener(downloadCompletionListener)
        }
    }

    override fun onStop() {
        super.onStop()
        bridge.unregister()
        downloadControl.stopMonitoringDownloads()
        if (firstDownload) {
            JobManager.removeWorkListener(downloadCompletionListener)
        }
    }

    // --- Network JSON (ported verbatim from classic DownloadActivity) -----------------------

    private suspend fun downloadDocJson() = coroutineScope {
        awaitAll(
            async { loadRecommendedDocuments() },
            async { loadDefaultDocuments() },
            async { loadPseudoBooks() },
            async { loadBadDocuments() },
        )
    }

    private suspend fun loadRecommendedDocuments() = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.RECOMMENDED_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.RECOMMENDED_JSON)
        genericFileDownloader.downloadFile(source, target, "Recommendations", reportError = !target.canRead())
        if (target.canRead()) {
            recommendedDocuments = json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG, "Could not load recommendations")
        }
    }

    private suspend fun loadBadDocuments() = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.BAD_DOCS_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.BAD_DOCS_JSON)
        genericFileDownloader.downloadFile(source, target, "Bad documents list", reportError = !target.canRead())
        if (target.canRead()) {
            badDocuments = json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG, "Could not load bad documents list")
        }
    }

    private suspend fun loadDefaultDocuments() = withContext(Dispatchers.IO) {
        if (!downloadDefaults) return@withContext
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.DEFAULT_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.DEFAULT_JSON)
        genericFileDownloader.downloadFile(source, target, "Defaults", reportError = !target.canRead())
        if (target.canRead()) {
            defaultDocuments = json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG, "Could not load default document list")
        }
    }

    private suspend fun loadPseudoBooks() = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.PSEUDO_BOOKS}")
        val target = File(SharedConstants.modulesDir, SharedConstants.PSEUDO_BOOKS)
        genericFileDownloader.downloadFile(source, target, "Pseudo books", reportError = !target.canRead())
        if (target.canRead()) {
            pseudoBooks = json.decodeFromString(serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG, "Could not load pseudo book list")
        }
    }

    private suspend fun askIfWantToProceed(): Boolean = withContext(Dispatchers.Main) {
        if (settings.getBoolean("download_do_not_ask", false)) {
            true
        } else {
            suspendCoroutine { cont ->
                AlertDialog.Builder(this@DownloadComposeActivity)
                    .setTitle(R.string.download_question_title)
                    .setMessage(getString(R.string.download_question_message))
                    .setPositiveButton(R.string.yes) { _, _ -> cont.resume(true) }
                    .setNegativeButton(R.string.do_not_ask_again) { _, _ ->
                        settings.setBoolean("download_do_not_ask", true)
                        cont.resume(true)
                    }
                    .setNeutralButton(R.string.cancel) { _, _ -> cont.resume(false) }
                    .setOnCancelListener { cont.resume(false) }
                    .show()
            }
        }
    }

    // --- Loading / DocRow construction ------------------------------------------------------

    /**
     * (Re)load downloadable Books off-main, rebuild the docId->Book + repoIdentity->docId maps and
     * the DocRow list, seed the FTS DAO (classic populateMasterDocumentList refresh=true), and push.
     */
    private suspend fun loadDocuments(refresh: Boolean) {
        try {
            val books = withContext(Dispatchers.Default) {
                downloadManager.refreshInstallManager()
                val docs = downloadControl.getDownloadableDocuments(repoFactory, refresh)
                if (docs.isNotEmpty()) docs + FakeBookFactory.pseudoDocuments(pseudoBooks) else docs
            }
            val rows = withContext(Dispatchers.Default) {
                val grouping = LanguageGrouping(books.mapNotNull { it.language })
                val langByKey: Map<String, LangOption> = grouping.representatives.mapNotNull { lang ->
                    val key = grouping.key(lang) ?: return@mapNotNull null
                    key to LangOption(lang.code ?: "", lang.name, key)
                }.toMap()
                // classic sortLanguages(representatives) order → groupingKey -> rank index.
                langRank = downloadControl.sortLanguages(grouping.representatives)
                    .mapIndexedNotNull { i, lang -> grouping.key(lang)?.let { it to i } }
                    .toMap()
                // Seed the FTS DAO (mirror classic populateMasterDocumentList dao.clear()/insertDocuments).
                runCatching {
                    dao.clear()
                    dao.insertDocuments(books.map {
                        DocumentSearch(
                            it.osisID, it.abbreviation, if (it.isPseudoBook) "" else it.name,
                            it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "",
                        )
                    })
                }
                // Bad documents flagged HIDE are excluded (classic filterDocuments); WARN → badWarn.
                books.filterNot { it.isBadDocument(badDocuments, BadDocumentAction.HIDE) }
                    .map { it.toDocRow(grouping, langByKey) }
            }
            allBooks = books
            // docId is the opaque repoIdentity ("repo--initials"), unique per repo+initials, so two
            // repos exposing the same initials produce two distinct, independently-addressable rows.
            booksById = books.associateBy { it.repoIdentity }
            // Progress events already carry repoIdentity as DocumentStatus.id, and docId == repoIdentity now,
            // so the bridge's repoIdentity -> docId map is the identity.
            bridge.setRepoIdentityMap(books.associate { it.repoIdentity to it.repoIdentity })
            currentRows = rows
            controller.setDocuments(rows, currentSearchIds)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading download documents", e)
            controller.showError()
        }
    }

    private fun Book.toDocRow(grouping: LanguageGrouping, langByKey: Map<String, LangOption>): DocRow {
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
            category = bookCategory.toDocCategory(),
            installStatus = status.documentInstallStatus.toDocInstallStatus(),
            percentDone = status.percentDone,
            recommended = isRecommended(recommendedDocuments),
            badWarn = isBadDocument(badDocuments, BadDocumentAction.WARN),
            locked = isLocked,
            enciphered = isEnciphered,
            canDelete = runCatching { canDelete }.getOrDefault(false),
            installSizeMb = sizeMb,
        )
    }

    // --- Live progress ----------------------------------------------------------------------

    private fun applyProgress(statusMap: Map<String, RowDownloadStatus>) {
        if (statusMap.isEmpty()) return
        // Push each row's live status straight into the controller, which re-sorts (floating
        // BEING_INSTALLED rows to the top, classic notifyDataSetChanged parity) WITHOUT clearing an
        // active multi-selection — unlike setDocuments()/refilter(), which would call clearSelection()
        // on every progress tick. Keep the host-side currentRows mirror in sync so a later
        // refreshRowStatus()/setDocuments() doesn't revert the in-progress status.
        var mirror = currentRows
        for ((docId, s) in statusMap) {
            controller.updateDownloadStatus(docId, s.status, s.percentDone)
            mirror = mirror.map { row ->
                if (row.docId == docId && (row.installStatus != s.status || row.percentDone != s.percentDone)) {
                    row.copy(installStatus = s.status, percentDone = s.percentDone)
                } else row
            }
        }
        currentRows = mirror
    }

    /** Refresh one row's status directly from getDocumentStatus (immediate feedback on download start). */
    private fun refreshRowStatus(book: Book) {
        val status = downloadControl.getDocumentStatus(book)
        val updated = currentRows.map { row ->
            if (row.docId == book.repoIdentity) {
                row.copy(
                    installStatus = status.documentInstallStatus.toDocInstallStatus(),
                    percentDone = status.percentDone,
                )
            } else row
        }
        if (updated != currentRows) {
            currentRows = updated
            controller.setDocuments(updated, currentSearchIds)
        }
    }

    // --- Controller seams (JSword side effects) ---------------------------------------------

    /** Classic DownloadActivity.handleDocumentSelection → start a download (browse-in-place). */
    private fun handleDocumentSelection(docId: String) {
        val book = booksById[docId] ?: return
        Log.i(TAG, "Document selected:" + book.initials)
        try {
            manageDownload(book)
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to download", e)
            Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    private fun manageDownload(documentToDownload: Book?) {
        if (documentToDownload != null &&
            downloadControl.getDocumentStatus(documentToDownload).documentInstallStatus != DocumentInstallStatus.BEING_INSTALLED &&
            !documentToDownload.isPseudoBook
        ) {
            if (documentToDownload.isInstalled && DatabaseContainer.ready &&
                bookmarksDao.genericBookmarkCountFor(documentToDownload) > 0
            ) {
                lifecycleScope.launch {
                    if (CommonUtils.documentUpgradeConfirmation(this@DownloadComposeActivity)) {
                        doDownload(documentToDownload)
                    }
                }
            } else {
                AlertDialog.Builder(this)
                    .setMessage(getText(R.string.download_document_confirm_prefix).toString() + " " + documentToDownload.name)
                    .setCancelable(false)
                    .setPositiveButton(R.string.okay) { _, _ -> doDownload(documentToDownload) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }.create().show()
            }
        }
    }

    private fun doDownload(document: Book) = downloadScope.launch(Dispatchers.Main) {
        try {
            downloadControl.downloadDocument(repoFactory, document)
            refreshRowStatus(document)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to download", e)
            Toast.makeText(this@DownloadComposeActivity, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    /** Classic DocumentSelectionBase.handleDelete. */
    private fun handleDelete(ids: Set<String>) {
        for (document in ids.mapNotNull { booksById[it] }) {
            if (documentControl.canDelete(document.installedDocument)) {
                val msg: CharSequence = getString(R.string.delete_doc, document.name)
                AlertDialog.Builder(this)
                    .setMessage(msg).setCancelable(true)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        try {
                            Log.i(TAG, "Deleting:$document")
                            documentControl.deleteDocument(document.installedDocument)
                            lifecycleScope.launch { loadDocuments(false) }
                            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
                        } catch (e: Exception) {
                            Log.e(TAG, "Deleting document crashed", e)
                            Dialogs.showErrorMsg(R.string.error_occurred, e)
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .create().show()
            } else {
                ABEventBus.post(ToastEvent(R.string.cant_delete_document))
            }
        }
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
                        net.bible.service.sword.SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
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
                CommonUtils.showAbout(this@DownloadComposeActivity, document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding SwordBookMetaData for $document", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun handleUnlock(docId: String) {
        val document = booksById[docId] ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            CommonUtils.unlockDocument(this@DownloadComposeActivity, document)
            loadDocuments(false)
        }
    }

    // --- Auto-download extras (ported from classic onCreate) --------------------------------

    private suspend fun handleAutoDownloadExtras() = withContext(Dispatchers.Main) {
        val bookStr = intent.extras?.getString(DownloadActivity.DOCUMENT_IDS_EXTRA)
        if (bookStr != null) {
            val booksToDownload: List<SwordDocumentInfo> = json.decodeFromString(serializer(), bookStr)
            downloadRequestedBooks(booksToDownload)
            if (booksNotFound.size > 0) {
                warnUserBooksNotDownloaded()
            }
        }
        val defaults = defaultDocuments
        if (downloadDefaults && defaults != null) {
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
                downloadRequestedBooks(l2)
            }
        }
    }

    /** Download the requested books, given a list of SwordDocumentInfo (classic downloadRequestedBooks). */
    private fun downloadRequestedBooks(osisIds: List<SwordDocumentInfo>?) {
        osisIds ?: return
        for (it in osisIds) {
            Log.i(TAG, "User request to download $it")
            val book: Book? = findBookByInitials(it.initials, if (it.repository == "") null else it.repository)
            if (book != null) {
                doDownload(book)
            } else {
                booksNotFound.add(it.initials)
            }
        }
    }

    private fun findBookByInitials(initials: String, repository: String?): Book? = allBooks.find {
        if (repository != null) {
            it.initials == initials && it.getProperty(DownloadManager.REPOSITORY_KEY) == repository
        } else {
            it.initials == initials
        }
    }

    /** Shows a dialog explaining that some books were not downloaded (classic warnUserBooksNotDownloaded). */
    private fun warnUserBooksNotDownloaded() {
        val books = booksNotFound.toTypedArray()
        lifecycleScope.launch {
            val notInstalled: Array<String> = books.mapNotNull { docDao.getBook(it)?.name }.toTypedArray()
            withContext(Dispatchers.Main) {
                val v = layoutInflater.inflate(R.layout.books_not_downloaded_dialog, null)
                val adapter = ArrayAdapter(this@DownloadComposeActivity, R.layout.books_not_downloaded_list_item, notInstalled)
                v.findViewById<ListView>(R.id.bookListView).adapter = adapter
                AlertDialog.Builder(this@DownloadComposeActivity)
                    .setView(v)
                    .setPositiveButton(R.string.okay, null)
                    .show()
            }
        }
    }

    // --- Staleness cache --------------------------------------------------------------------

    /** if repo list not refreshed in last [REPO_LIST_STALE_AFTER_DAYS] days then it is old */
    private val isRepoBookListOld: Boolean
        get() {
            val repoRefreshDate = settings.getLong(REPO_REFRESH_DATE, 0)
            return (Date().time - repoRefreshDate) / MILLISECS_IN_DAY > REPO_LIST_STALE_AFTER_DAYS
        }

    private fun updateLastRepoRefreshDate() {
        settings.setLong(REPO_REFRESH_DATE, Date().time)
    }

    // --- Overflow menu (classic DownloadActivity.onOptionsItemSelected) ----------------------

    @Composable
    private fun OverflowMenu() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis (Material icons aren't on the app-module classpath); sized to match the 28dp shared top-bar icons
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (hasErrors) {
                DropdownMenuItem(
                    text = { Text(getString(R.string.download_errors)) },
                    onClick = { expanded = false; showErrors() },
                )
            }
            if (!firstDownload) { // classic FirstDownload hides installZip
                DropdownMenuItem(
                    text = { Text(getString(R.string.install_zip)) },
                    onClick = { expanded = false; onInstallZip() },
                )
            }
            DropdownMenuItem(
                text = { Text(getString(R.string.custom_repositories)) },
                onClick = { expanded = false; onCustomRepositories() },
            )
            if (DocumentSyncSettings.enabled) {
                DropdownMenuItem(
                    text = { Text(getString(R.string.document_sync_manage_title)) },
                    onClick = { expanded = false; startActivity(ScreenLauncher.intentFor(this@DownloadComposeActivity, Screen.CloudDocuments)) },
                )
            }
        }
    }

    private fun showErrors() {
        var message = ""
        if (downloadManager.failedRepos.isNotEmpty()) {
            message += getString(R.string.failed_repositories_message, downloadManager.failedRepos.joinToString(",\n"))
        }
        if (genericFileDownloader.errors.isNotEmpty()) {
            message += getString(R.string.failed_downloads_message, genericFileDownloader.errors.joinToString(",\n"))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_errors))
            .setMessage(message)
            .setPositiveButton(R.string.okay, null)
            .create().show()
    }

    private fun onInstallZip() {
        val intent = ScreenLauncher.intentFor(this, Screen.InstallZip)
        lifecycleScope.launch {
            awaitIntent(intent)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        }
    }

    private fun onCustomRepositories() {
        val intent = ScreenLauncher.intentFor(this, Screen.CustomRepositories)
        lifecycleScope.launch {
            awaitIntent(intent)
            loadDocuments(true)
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

    private fun typeFilterLabels(strings: Strings): List<Pair<DocTypeFilter, String>> = listOf(
        DocTypeFilter.ALL to strings.docTypeAll,
        DocTypeFilter.BIBLE to strings.docTypeBible,
        DocTypeFilter.COMMENTARY to strings.docTypeCommentary,
        DocTypeFilter.DICTIONARY to strings.docTypeDictionary,
        DocTypeFilter.GENERAL_BOOK to strings.docTypeGeneralBook,
        DocTypeFilter.MAPS to strings.docTypeMaps,
        DocTypeFilter.ADDON to strings.docTypeAddon,
    )

    private fun BookCategory.toDocCategory(): DocCategory = when (this) {
        BookCategory.BIBLE -> DocCategory.BIBLE
        BookCategory.COMMENTARY -> DocCategory.COMMENTARY
        BookCategory.DICTIONARY -> DocCategory.DICTIONARY
        BookCategory.GENERAL_BOOK -> DocCategory.GENERAL_BOOK
        BookCategory.MAPS -> DocCategory.MAPS
        BookCategory.AND_BIBLE -> DocCategory.AND_BIBLE
        else -> DocCategory.OTHER
    }

    private fun DocumentInstallStatus.toDocInstallStatus(): DocInstallStatus = when (this) {
        DocumentInstallStatus.INSTALLED -> DocInstallStatus.INSTALLED
        DocumentInstallStatus.NOT_INSTALLED -> DocInstallStatus.NOT_INSTALLED
        DocumentInstallStatus.BEING_INSTALLED -> DocInstallStatus.BEING_INSTALLED
        DocumentInstallStatus.UPGRADE_AVAILABLE -> DocInstallStatus.UPGRADE_AVAILABLE
        DocumentInstallStatus.ERROR_DOWNLOADING -> DocInstallStatus.ERROR_DOWNLOADING
        DocumentInstallStatus.INSTALL_CANCELLED -> DocInstallStatus.INSTALL_CANCELLED
    }

    companion object {
        /** Intent extra: run the FirstDownload onboarding variant (OK gate + installZip hidden). */
        const val EXTRA_FIRST_DOWNLOAD = "firstDownload"
        private const val REPO_REFRESH_DATE = "repoRefreshDate"
        private const val REPO_LIST_STALE_AFTER_DAYS: Long = 1
        private const val MILLISECS_IN_DAY = 1000 * 60 * 60 * 24.toLong()
        private const val TAG = "DownloadCompose"
    }
}
