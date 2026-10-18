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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.view.activity.page.screen.chapterReadHistoryRow
import net.bible.android.view.activity.page.screen.readHistoryApplyDeletes
import net.bible.sharedcore.history.HistoryController
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.workspaces.WorkspaceQuickController
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbQuickSheetFooterRow
import net.bible.sharedui.components.AbQuickSheetTab
import net.bible.sharedui.history.HistoryListContent
import net.bible.sharedui.navigation.DocumentQuickContent
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.reading.ShareVersesSheet
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.workspaces.WorkspaceQuickContent

/**
 * The reading view's quick sheets: ONE mount for the History, Workspaces, Documents, Share and
 * ReadHistory sheets (`QuickSheetMountGuardTest`). A `ModalBottomSheet` renders in its own window, so
 * opening one never re-keys the pane subtree and destroys the BibleView WebViews. The key-chooser
 * sheets (Map, GeneralBook, Grid) are still mounted by the platform host.
 */
@Composable
internal fun QuickSheetContent(slots: ReadingHostSlots, sheet: ReadingQuickSheet) {
    when (sheet) {
        ReadingQuickSheet.History -> HistorySheet(slots, sheet)
        ReadingQuickSheet.Workspaces -> WorkspacesSheet(slots, sheet)
        is ReadingQuickSheet.Documents -> DocumentsSheet(slots, sheet)
        is ReadingQuickSheet.Share -> ShareSheet(slots, sheet)
        is ReadingQuickSheet.ReadHistory -> ReadHistorySheet(slots, sheet)
        is ReadingQuickSheet.KeyChooser -> Unit
    }
}

@Composable
private fun HistorySheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet) {
    val strings = slots.strings()
    // Built per opening: the controller reads the history once at construction, so a stale
    // instance would show a stale list.
    val controller = remember(sheet) {
        HistoryController(
            loadEntries = { slots.hostState.historyEntriesForActiveWindow() },
            onRevert = { id -> slots.hostState.revertToHistoryItem(id); slots.hostState.closeQuickSheet() },
        )
    }
    val entries by controller.entries.collectAsState()
    val error by controller.error.collectAsState()
    val listState = rememberLazyListState()
    AbQuickSheet(
        open = true,
        // The format string is "History (%1$s: Window %2$d)" and the goldens depend on both.
        title = strings.historyFor(slots.workspaceName(), slots.activeWindowPosition() + 1),
        onDismiss = { slots.hostState.closeQuickSheet() },
        canScrollForward = { listState.canScrollForward },
    ) {
        HistoryListContent(entries = entries, onSelect = { controller.onSelect(it) }, listState = listState)
    }
    if (error != null) {
        AbErrorDialog(
            message = strings.errorOccurred,
            confirmText = strings.okay,
            onDismiss = { controller.dismissError() },
        )
    }
}

@Composable
private fun WorkspacesSheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet) {
    val strings = slots.strings()
    val controller = remember(sheet) {
        WorkspaceQuickController(slots.workspaceService(), slots.scope) { id ->
            slots.hostState.closeQuickSheet()
            // The quick sheet never pauses the activity, so nothing else flushes the outgoing
            // workspace's windows/page-managers/history before switching: the platform's quick
            // switch saves first, then switches.
            slots.quickActions.quickSwitchToWorkspace(id)
        }
    }
    val rows by controller.rows.collectAsState()
    val listState = rememberLazyListState()
    AbQuickSheet(
        open = true,
        title = strings.switchToWorkspace,
        onDismiss = { slots.hostState.closeQuickSheet() },
        canScrollForward = { listState.canScrollForward },
        footer = {
            AbQuickSheetFooterRow(text = strings.manageWorkspaces) {
                slots.hostState.closeQuickSheet()
                slots.navigator.openWorkspaceSelector()
            }
        },
    ) {
        WorkspaceQuickContent(rows = rows, onSelect = { controller.select(it) }, listState = listState)
    }
}

@Composable
private fun DocumentsSheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet.Documents) {
    val strings = slots.strings()
    val hostState = slots.hostState
    val scope = sheet.scope
    // Built OFF the composition pass, unlike History and Workspaces: enumerating every installed
    // book, asking the download control for each one's status and sorting the result is a
    // hundreds-of-rows job that would stall the sheet's first frame.
    val tabsState by produceState<DocumentQuickTabs?>(initialValue = null, sheet) {
        // Clear first: a direct scope change (BIBLE -> COMMENTARY) re-runs this producer and must
        // not show the previous scope's tabs under the new title meanwhile.
        value = null
        value = withContext(Dispatchers.Default) { hostState.buildDocumentQuickTabsForHost(scope) }
    }
    // `null` means STILL LOADING, deliberately distinguished from "loaded, nothing to offer" below:
    // routing away on a not-yet-loaded list would send the user to the full screen every time the
    // sheet opened faster than the books.
    val tabs = tabsState
    // rememberSaveable, so the pick survives this composable being torn down and rebuilt when the
    // user closes and reopens the sheet within the same session. A rotation recreates the Activity
    // and closes every quick sheet; what restores the tab after THAT is the persisted
    // `document_quick_tab` setting (persistQuickDocTab/restoreQuickDocTab).
    var pickedTabId by rememberSaveable(sheet) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // Memoised on `tabs`: `restoreQuickDocTab` is a synchronous settings read and must not run on
    // every recomposition until the user taps a tab.
    val restoredTabId = remember(scope, tabs) { tabs?.let { hostState.restoreQuickDocTab(scope, it.visible) } }
    if (tabs != null && tabs.visible.isEmpty()) {
        // Nothing to offer: go straight to the full screen rather than an empty sheet with a footer.
        LaunchedEffect(Unit) { hostState.closeQuickSheet(); slots.navigator.openChooseDocument(scope.chooserType) }
    } else {
        val selectedTabId = tabs?.let { loaded ->
            pickedTabId?.takeIf { id -> loaded.visible.any { it.name == id } } ?: restoredTabId
        }
        AbQuickSheet(
            open = true,
            title = when (scope) {
                DocumentSheetScope.ALL -> strings.chooseDocumentTitle
                DocumentSheetScope.BIBLE -> strings.docTypeBible
                DocumentSheetScope.COMMENTARY -> strings.docTypeCommentary
            },
            onDismiss = { hostState.closeQuickSheet() },
            // Identity-keyed tabs: the enum's own `name` is the stable id, which lets a hidden tab
            // and an async tab list coexist with a persisted selection.
            tabs = tabs?.visible.orEmpty().map { AbQuickSheetTab(it.name, quickDocTabLabel(strings, it)) },
            selectedTabId = selectedTabId,
            onTabSelected = { id -> pickedTabId = id; hostState.persistQuickDocTab(scope, id) },
            canScrollForward = { listState.canScrollForward },
            footer = {
                AbQuickSheetFooterRow(text = strings.allDocuments) {
                    hostState.closeQuickSheet()
                    slots.navigator.openChooseDocument(scope.chooserType)
                }
            },
        ) {
            // While the load is in flight the shell renders header + footer with an empty body.
            if (tabs != null && selectedTabId != null) {
                DocumentQuickContent(
                    rows = tabs.rowsByTab.getValue(DocumentQuickTab.valueOf(selectedTabId)),
                    currentDocId = slots.currentDocumentInitials(),
                    onSelect = { docId ->
                        hostState.closeQuickSheet()
                        // The SAME body the `ChooseDocument` activity-result arm runs; the sheet
                        // returns no Intent, so it cannot use that arm itself.
                        slots.quickActions.applyChosenDocument(docId)
                    },
                    listState = listState,
                )
            }
        }
    }
}

private fun quickDocTabLabel(strings: Strings, tab: DocumentQuickTab): String = when (tab) {
    DocumentQuickTab.RECENT -> strings.documentTabRecent
    DocumentQuickTab.FOR_VERSE -> strings.documentTabForVerse
    DocumentQuickTab.LAST_FILTER -> strings.documentTabLastFilter
    DocumentQuickTab.ALL -> strings.documentTabAll
}

@Composable
private fun ShareSheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet.Share) {
    val strings = slots.strings()
    val hostState = slots.hostState
    ShareVersesSheet(
        open = true,
        title = strings.shareSheetTitle,
        options = hostState.shareVersesOptions,
        onOptionsChange = hostState::updateShareVersesOptions,
        input = sheet.input,
        // Share and Copy close the sheet, as the classic dialog's buttons dismissed it.
        onShare = { text ->
            slots.textShare.shareText(text, strings.shareVerseMenuTitle)
            hostState.closeQuickSheet()
        },
        // The clip's invisible description only; `referenceFull` is the same JSword call this input
        // already resolved for the "abbreviate reference" toggle.
        onCopy = { text ->
            slots.clipboard.write(sheet.input.referenceFull, text)
            slots.toast(strings.textCopiedToClipboard)
            hostState.closeQuickSheet()
        },
        onDismiss = hostState::closeQuickSheet,
    )
}

@Composable
private fun ReadHistorySheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet.ReadHistory) {
    val strings = slots.strings()
    val service = slots.readingProgressService()
    // Loaded once per opening: the sheet never appears while the query runs, so `rows == null`
    // renders nothing rather than an empty shell.
    var rows by remember(sheet) { mutableStateOf<List<ReadHistoryRow>?>(null) }
    var cycle by remember(sheet) { mutableIntStateOf(0) }
    LaunchedEffect(sheet) {
        val activeCycle = service.currentCycle()
        val entries = service.readHistoryForChapter(sheet.bookId, sheet.chapter, activeCycle)
        val versionUnknownText = strings.readingProgressHistoryVersionUnknown
        cycle = activeCycle
        rows = entries.map { entry ->
            chapterReadHistoryRow(
                entry = entry,
                date = service.formatEntryDate(entry.readAt),
                time = service.formatEntryTime(entry.readAt),
                versionUnknownText = versionUnknownText,
            )
        }
    }
    val loadedRows = rows
    if (loadedRows != null) {
        AbReadHistorySheet(
            title = strings.readingProgressHistoryFor("${service.bookShortName(sheet.bookId)} ${sheet.chapter}"),
            rows = loadedRows,
            onApplyDeletes = readHistoryApplyDeletes(slots.scope, service, cycle),
            onDismiss = slots.hostState::closeQuickSheet,
        )
    }
}
