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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.view.activity.page.screen.chapterReadHistoryRow
import net.bible.android.view.activity.page.screen.readHistoryApplyDeletes
import net.bible.sharedcore.history.HistoryController
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.workspaces.WorkspaceQuickController
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbQuickSheetFooterRow
import net.bible.sharedui.components.AbQuickSheetTab
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.nameWithoutDocument
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.history.HistoryListContent
import net.bible.sharedui.navigation.DocumentQuickContent
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.navigation.GridOptionsOverflow
import net.bible.sharedui.navigation.KeyListBody
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.reading.ShareVersesSheet
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.workspaces.WorkspaceQuickContent
import org.crosswire.jsword.passage.Key

/**
 * The reading view's quick sheets: ONE mount for every quick sheet: History, Workspaces, Documents, Share,
 * ReadHistory and the key choosers (`QuickSheetMountGuardTest`). A `ModalBottomSheet` renders in its own window, so
 * opening one never re-keys the pane subtree and destroys the BibleView WebViews.
 */
@Composable
internal fun QuickSheetContent(slots: ReadingHostSlots, sheet: ReadingQuickSheet) {
    when (sheet) {
        ReadingQuickSheet.History -> HistorySheet(slots, sheet)
        ReadingQuickSheet.Workspaces -> WorkspacesSheet(slots, sheet)
        is ReadingQuickSheet.Documents -> DocumentsSheet(slots, sheet)
        is ReadingQuickSheet.Share -> ShareSheet(slots, sheet)
        is ReadingQuickSheet.ReadHistory -> ReadHistorySheet(slots, sheet)
        is ReadingQuickSheet.KeyChooser -> when (sheet.kind) {
            KeyChooserKind.Grid -> GridKeyChooserSheet(slots, sheet)
            // Both are flat single-level lists with no page stack, so they take the shell's DEFAULT
            // dismiss contract (swipe, scrim and back all simply close). The grid is the only sheet
            // here that needs `dismissRoutesToBack = false`.
            KeyChooserKind.Map -> KeyListSheet(slots, sheet, KeyChooserKind.Map, slots.strings().docTypeMap)
            KeyChooserKind.GeneralBook ->
                KeyListSheet(slots, sheet, KeyChooserKind.GeneralBook, slots.strings().generalBook)
        }
    }
}

/**
 * The passage grid as a quick sheet (the key chooser for Bible/commentary pages).
 *
 * System back pops a grid step; swipe-down and scrim-tap close the sheet outright, as they do on every
 * other sheet. M3 cannot tell the three apart, so back is intercepted BEFORE M3 sees it with a
 * [PlatformBackHandler] mounted inside the sheet content, and `dismissRoutesToBack = false` makes
 * every M3 dismiss close. The handler wins over M3's own because both land on the sheet dialog's
 * dispatcher, which resolves the most recently added enabled handler first, and the sheet's content
 * composes after the dialog's own handler was registered.
 */
@Composable
private fun GridKeyChooserSheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet.KeyChooser) {
    val strings = slots.strings()
    val hostState = slots.hostState
    val gridScope = rememberCoroutineScope()
    val controller = remember(sheet) {
        slots.gridPassages.create(
            scope = gridScope,
            // The full-screen twin's own default title -- NOT `chooseBook`, which is the DOCUMENT
            // chooser's caption.
            baseTitle = strings.choosePassageBookName,
            // The Bible/commentary key choosers that route here both pass isScripture = true.
            isScripture = true,
            // Decided by the OPENING, not read here: the title tap follows the user's preference
            // while the JS reference chooser forces verse level.
            navigateToVerse = sheet.navigateToVerse,
            // Answers a pending JS request if there is one, and otherwise runs the one
            // parse-and-apply path shared with the onActivityResult arm.
            onFinish = hostState::onGridPassageChosen,
        )
    }
    GridKeyChooserSheetFor(controller, strings.choosePassageBookName) { hostState.closeQuickSheet() }
}

/**
 * The grid key-chooser sheet around an existing [controller]: [fallbackTitle] shows until the first
 * step has built, [onClose] runs when the sheet is dismissed (swipe, scrim, the close button, system
 * back at the book step, or back past the last grid step). Public (and objc-hidden) so a platform test
 * can mount it with a hand-built controller; the slot builds the real one.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun GridKeyChooserSheetFor(controller: GridChoosePassageController, fallbackTitle: String, onClose: () -> Unit) {
    val ui by controller.ui.collectAsState()
    val rendered = ui
    val step by controller.step.collectAsState()
    val options by controller.options.collectAsState()
    // The grid always overflows its 400dp bound, so it most needs the bottom fade; the fade must
    // read the grid's own LazyGridState.
    val gridState = rememberLazyGridState()
    AbQuickSheet(
        open = true,
        // Tracks the step: Genesis -> Genesis 1.
        title = ui?.title ?: fallbackTitle,
        onDismiss = { onClose() },
        // Still the header's back arrow: it pops a grid step until the stack is empty.
        canGoBack = { step != GridStep.BOOK },
        onBack = { if (!controller.back()) onClose() },
        canScrollForward = { gridState.canScrollForward },
        dismissRoutesToBack = false,
        actions = if (ui?.step == GridStep.BOOK) {
            {
                // The options are functional: DEUTEROCANONICAL rebuilds the book list.
                GridOptionsOverflow(requireNotNull(ui), options) { controller.toggle(it) }
            }
        } else null,
    ) {
        PlatformBackHandler(enabled = step != GridStep.BOOK) { controller.back() }
        GridChoosePassageContent(
            rendered, { id -> rendered?.let { controller.pick(id, it) } }, Modifier.fillMaxSize(), state = gridState,
        )
    }
}

/**
 * The map / general-book key chooser: one composable for both, since the two differ only in title and
 * in which page they read.
 *
 * The key list is the one the opening handed to [ReadingHostState.showKeyChooserSheet], held for the
 * life of the sheet, because a [KeyRow]'s id is the INDEX into it: re-resolving between building the
 * rows and handling the selection would silently re-number them. `remember(sheet)` keys it per
 * opening. The body's own [rememberLazyListState] feeds `canScrollForward` (the bottom fade).
 */
@Composable
private fun KeyListSheet(slots: ReadingHostSlots, sheet: ReadingQuickSheet, kind: KeyChooserKind, title: String) {
    val hostState = slots.hostState
    val keys = remember(sheet) { hostState.keyChooserKeys }
    val rows = remember(keys) { keys.mapIndexed { i, k -> KeyRow(i.toString(), k.nameWithoutDocument) } }
    val currentKeyId = remember(keys) {
        slots.keyChooserPage.currentKey(kind)?.let { current -> keys.indexOf(current).takeIf { it >= 0 }?.toString() }
    }
    val listState = rememberLazyListState()
    AbQuickSheet(
        open = true,
        title = title,
        onDismiss = { hostState.closeQuickSheet() },
        canScrollForward = { listState.canScrollForward },
    ) {
        KeyListBody(
            rows = rows,
            currentKeyId = currentKeyId,
            onSelect = { keyId ->
                val key = keys.getOrNull(keyId.toIntOrNull() ?: -1)
                hostState.closeQuickSheet()
                if (key != null) applyKeyChooserKey(slots, kind, key)
            },
            listState = listState,
        )
    }
}

/**
 * Applies a key picked in a key-chooser sheet through [ReadingQuickActions.applyChosenGenBookKey], the
 * same function the full-screen chooser's result runs. The `Key` goes across as itself. Which book it
 * belongs to follows each chooser's own result: an EPUB table-of-contents entry is a [BookAndKey]
 * carrying its own document, everything else belongs to the page's current document.
 */
private fun applyKeyChooserKey(slots: ReadingHostSlots, kind: KeyChooserKind, key: Key) {
    val actions = slots.quickActions
    when (kind) {
        KeyChooserKind.Map -> actions.applyChosenGenBookKey(slots.keyChooserPage.currentDocument(kind), key)
        KeyChooserKind.GeneralBook ->
            if (key is BookAndKey) {
                actions.applyChosenGenBookKey(key.document, key)
            } else {
                actions.applyChosenGenBookKey(slots.keyChooserPage.currentDocument(kind), key)
            }
        KeyChooserKind.Grid -> Unit
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
