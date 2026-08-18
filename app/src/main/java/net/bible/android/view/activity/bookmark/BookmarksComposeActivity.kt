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
package net.bible.android.view.activity.bookmark

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.bookmark.BookmarksScreen
import org.koin.android.ext.android.inject

private const val TAG = "BookmarksCompose"

/**
 * Compose host for the Bookmarks list — the new-path twin of classic [Bookmarks]. Wires the
 * shared [net.bible.sharedcore.bookmark.BookmarksController] to [BookmarksServiceImpl] (`by
 * inject()` resolves the CONCRETE type, since the Room/JSword id -> entity map it exposes beyond
 * the portable [net.bible.sharedcore.bookmark.BookmarksService] interface is needed here to
 * resolve a selected/assigned/deleted row back to its bookmark).
 *
 * **Result-className parity.** [onSelectBookmark] builds the exact same result [Intent] classic
 * `Bookmarks.bookmarkSelected` does (Bookmarks.kt:295-323) — verse/key/book/ordinal + description +
 * labelNo + listPosition — and, crucially, targets it at the CLASSIC `Bookmarks::class.java`, not
 * this activity, so `MainBibleActivity`'s `className == Bookmarks::class.java.name` dispatch
 * (MainBibleActivity.kt:1922-2012) matches regardless of which host produced the result.
 */
class BookmarksComposeActivity : ActivityBase() {
    private val service: BookmarksServiceImpl by inject()
    private val bookmarkControl: BookmarkControl by inject()
    private val speakControl: SpeakControl by inject()
    private val windowControl: WindowControl by inject()

    /** classic: if the LabelNo extra is present and >= 0, pre-select that filter (else 0); the
     *  controller itself clamps into the actual `filterLabels` range (Bookmarks.kt:122-131). */
    private val initialFilterIndex: Int by lazy {
        val labelNo = intent.extras?.takeIf { it.containsKey(BookmarkControl.LABEL_NO_EXTRA) }
            ?.getInt(BookmarkControl.LABEL_NO_EXTRA) ?: -1
        if (labelNo >= 0) labelNo else 0
    }

    private val controller: BookmarksController by lazy {
        BookmarksController(
            service = service,
            scope = lifecycleScope,
            initialFilterIndex = initialFilterIndex,
            onSelectBookmark = ::onSelectBookmark,
            onAssignLabels = ::onAssignLabels,
            onDeleteSelected = ::onDelete,
            onExportCsv = ::onExportCsv,
            onImportCsv = ::onImportCsv,
            onManageLabels = ::onManageLabels,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonUtils.settings.setLong("bookmarks-last-used", System.currentTimeMillis())

        // mirrors classic Bookmarks.onCreate (Bookmarks.kt:113-117): refresh the list when a
        // device sync completes while Bookmarks is open, instead of leaving it stale.
        ABEventBus.register(this) { onMain<BookmarksUpdatedViaSyncEvent> { controller.refresh() } }

        setContent {
            AbAppTheme {
                    val rows by controller.rows.collectAsState()
                    val filterLabels by controller.filterLabels.collectAsState()
                    val selectedFilterIndex by controller.selectedFilterIndex.collectAsState()
                    val sortMode by controller.sortMode.collectAsState()
                    val searchText by controller.searchText.collectAsState()
                    val searchModeActive by controller.searchModeActive.collectAsState()
                    val showNotes by controller.showNotes.collectAsState()
                    val selection by controller.selection.collectAsState()
                    val expandedIds by controller.expandedIds.collectAsState()
                    val loading by controller.loading.collectAsState()

                    BookmarksScreen(
                        title = getString(R.string.bookmarks_and_mynotes_title),
                        rows = rows,
                        filterLabels = filterLabels,
                        selectedFilterIndex = selectedFilterIndex,
                        sortMode = sortMode,
                        searchText = searchText,
                        showNotes = showNotes,
                        selection = selection,
                        expandedIds = expandedIds,
                        loading = loading,
                        onSelectFilter = controller::setFilter,
                        onCycleSort = controller::cycleSort,
                        onSearch = controller::setSearch,
                        searchModeActive = searchModeActive,
                        onOpenSearch = controller::openSearch,
                        onCloseSearch = controller::closeSearch,
                        onToggleShowNotes = controller::toggleShowNotes,
                        onRowClick = controller::selectRow,
                        onRowLongClick = controller::enterSelection,
                        onToggleSelected = controller::toggleSelection,
                        onToggleExpand = controller::toggleExpanded,
                        onAssignSelected = controller::assignSelected,
                        onDeleteSelected = controller::deleteSelected,
                        onClearSelection = controller::clearSelection,
                        onManageLabels = controller::manageLabels,
                        onExportCsv = controller::exportCsv,
                        onImportCsv = controller::importCsv,
                        onUp = { finish() },
                    )
            }
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back dismisses what is visually on top: the selection bar covers the search bar
        // (AbSelectionScaffold's precedence), so selection goes first. Closing search underneath a
        // visible selection bar would clear the query and re-filter the list invisibly.
        if (controller.selection.value.isNotEmpty()) { controller.clearSelection(); return }
        if (controller.searchModeActive.value) { controller.closeSearch(); return }
        super.onBackPressed()
    }

    // --- select (mirrors classic Bookmarks.bookmarkSelected, Bookmarks.kt:295-323) ---

    private fun onSelectBookmark(id: String, listPosition: Int) {
        val bookmark = service.bookmarkById(id) ?: return
        Log.i(TAG, "Bookmark selected:$bookmark")
        try {
            if (bookmark is BookmarkEntities.BibleBookmarkWithNotes && bookmarkControl.isSpeakBookmark(bookmark)) {
                speakControl.speakFromBookmark(bookmark)
            }
            // Target the CLASSIC activity so MainBibleActivity's className dispatch matches
            // regardless of which host (classic/Compose) produced this result.
            val resultIntent = Intent(this, Bookmarks::class.java)
            when (bookmark) {
                is BookmarkEntities.BibleBookmarkWithNotes -> {
                    resultIntent.putExtra("verse", bookmark.verseRange.start.osisID)
                }
                is BookmarkEntities.GenericBookmarkWithNotes -> {
                    resultIntent.putExtra("key", bookmark.key)
                    resultIntent.putExtra("book", bookmark.book?.initials)
                    resultIntent.putExtra("ordinal", bookmark.ordinalStart)
                }
            }
            resultIntent.putExtra("description", title)
            resultIntent.putExtra(BookmarkControl.LABEL_NO_EXTRA, controller.selectedFilterIndex.value)
            resultIntent.putExtra("listPosition", listPosition)

            historyTraversal.historyManager.addHistoryItem(null, resultIntent)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error on bookmarkSelected", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    // --- assign labels (mirrors classic Bookmarks.assignLabels, Bookmarks.kt:203-226) ---

    private fun onAssignLabels(ids: List<String>) = lifecycleScope.launch(Dispatchers.IO) {
        val bookmarks = service.bookmarksByIds(ids)
        val labels = mutableSetOf<IdType>()
        for (b in bookmarks) {
            labels.addAll(bookmarkControl.labelsForBookmark(b).map { it.id })
        }

        val intent = ScreenLauncher.intentFor(this@BookmarksComposeActivity, Screen.ManageLabels)
        intent.putExtra(
            "data",
            ManageLabels.ManageLabelsData(
                mode = ManageLabels.Mode.ASSIGN,
                selectedLabels = labels,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON(),
        )
        val result = awaitIntent(intent)
        if (result.resultCode == RESULT_OK) {
            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
            for (b in bookmarks) {
                bookmarkControl.changeLabelsForBookmark(b, resultData.selectedLabels.toList())
            }
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            withContext(Dispatchers.Main) { controller.refresh() }
        }
    }

    // --- delete (mirrors classic Bookmarks.delete, Bookmarks.kt:228-240) ---

    private fun onDelete(ids: List<String>) {
        val bookmarks = service.bookmarksByIds(ids)
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_bookmarks, bookmarks.size))
            .setPositiveButton(R.string.yes) { _, _ ->
                for (bookmark in bookmarks) {
                    bookmarkControl.deleteBookmark(bookmark)
                }
                controller.refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .show()
    }

    // --- CSV export/import (mirrors classic Bookmarks.kt:400-417) ---

    private fun onExportCsv() = lifecycleScope.launch {
        val bibleBookmarks = service.loadedBookmarks().filterIsInstance<BookmarkEntities.BibleBookmarkWithNotes>()
        bookmarkControl.exportBookmarksToCSV(this@BookmarksComposeActivity, bibleBookmarks)
        controller.refresh()
    }

    private fun onImportCsv() = lifecycleScope.launch(Dispatchers.Main) {
        bookmarkControl.importBookmarksFromCSV(this@BookmarksComposeActivity)
        controller.refresh()
    }

    // --- manage labels (mirrors classic Bookmarks.kt:382-399) ---

    private fun onManageLabels() = lifecycleScope.launch(Dispatchers.Main) {
        val intent = ScreenLauncher.intentFor(this@BookmarksComposeActivity, Screen.ManageLabels)
        intent.putExtra(
            "data",
            ManageLabels.ManageLabelsData(
                mode = ManageLabels.Mode.WORKSPACE,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON(),
        )
        val result = awaitIntent(intent)
        if (result.resultCode == RESULT_OK) {
            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            controller.refresh()
        }
    }
}
