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

package net.bible.sharedui.reading.toolbar

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentPage
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.sharedcore.log.Log
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.strings.Strings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedui.reading.host.ReadingHostState
import org.crosswire.jsword.passage.Key

private const val TAG = "KeyListSheetOpening"

/**
 * Opens the key-list quick sheet (the one a title tap opens) for a plain general-book or map [page], from any
 * key-chooser entry point: the page's Ctrl+B, `CurrentPageManager`'s auto-open after a document switch, the
 * `KeyChooserLauncher` port and the title tap's fallback (#4172). There is no full-screen general-book or map chooser.
 *
 * Must be called on the main thread. The keys are resolved once, off-main, from [page] itself (an EPUB offers its table
 * of contents); a row's id is the index into them. The sheet reads and answers the ACTIVE window, so [page]'s window is
 * activated first ([activateWindowOf]); nothing opens when that window did not become active (closed, switched away
 * or refused) or when [page] changed while the keys were resolved. With no keys at all no sheet opens; an EPUB reports
 * [onEmptyEpub], and a page without a key tries its document's first key (the full-screen choosers' fallback).
 *
 * Suspends until the sheet is gone. Closed without a choice while [page] still has no key (an auto-open after switching
 * to a book that was never opened), [onCancelledWithoutKey] runs: the full-screen choosers' cancel guard, which goes
 * back in history rather than leave an empty page.
 *
 * @return whether the sheet was shown
 */
@kotlin.native.HiddenFromObjC
suspend fun openKeyListSheet(
    page: CurrentPage,
    hostState: ReadingHostState,
    onCancelledWithoutKey: () -> Unit,
    onEmptyEpub: (String) -> Unit,
): Boolean {
    val kind = keyListKindOf(page) ?: return false
    val document = page.currentDocument
    val keys = withContext(Dispatchers.Default) { keyListOf(page) }
    if (page.currentDocument != document) return false
    if (keys.isEmpty()) {
        // EPUB TOC lookup already falls back to spine fragments. An empty result really has no readable keys.
        if (document?.isEpub == true && page.pageManager.currentPage === page) onEmptyEpub(document.name)
        if (page.key == null && page.pageManager.currentPage === page) openFirstKeyOrGoBack(page, onCancelledWithoutKey)
        Log.w(TAG, "No keys to choose from in ${document?.initials}; no chooser shown.")
        return false
    }
    activateWindowOf(page)
    val window = page.pageManager.window
    if (window.windowRepository.activeWindow !== window || page.pageManager.currentPage !== page) {
        Log.w(TAG, "The window asking for a ${document?.initials} key is not the active one; no chooser shown.")
        return false
    }
    hostState.showKeyChooserSheet(kind, keys)
    val shown = hostState.quickSheet.value
    // Compose state, observed through its apply notifications (sent by the running Compose UI every frame). Only a
    // close counts: another sheet replacing this one (a second chooser opening) is not this chooser's cancel.
    val next = snapshotFlow { hostState.quickSheet.value }.first { it !== shown }
    // A picked key is applied in the same click that closes the sheet, so it is in place by now.
    if (next == null && page.key == null && page.pageManager.currentPage === page) onCancelledWithoutKey()
    return true
}

/** Uses the existing themed message dialog, with the book name kept in plain text rather than interpreted as HTML. */
@kotlin.native.HiddenFromObjC
fun emptyEpubMessage(strings: Strings, name: String): AppDialogRequest.Message = AppDialogRequest.Message(
    title = strings.epubNoReadableChapters(name),
    message = strings.epubNoReadableChaptersHelp,
    confirmText = strings.okay,
)

/** The removed full-screen choosers' empty-list answer: the document's first global key, else back in history. */
private fun openFirstKeyOrGoBack(page: CurrentPage, onCancelledWithoutKey: () -> Unit) {
    val document = page.currentDocument
    val first = try {
        document?.getGlobalKeyList()?.firstOrNull()
    } catch (e: Exception) {
        Log.w(TAG, "No global keys in ${document?.initials}", e)
        null
    }
    if (document != null && first != null) page.pageManager.setCurrentDocumentAndKey(document, first) else onCancelledWithoutKey()
}

/** The key-list sheet kind of [page]: a map or a general book; null for every other page. */
private fun keyListKindOf(page: CurrentPage): KeyChooserKind? = when (page) {
    is CurrentMapPage -> KeyChooserKind.Map
    is CurrentGeneralBookPage -> KeyChooserKind.GeneralBook
    else -> null
}

private suspend fun keyListOf(page: CurrentPage): List<Key> = when (page) {
    is CurrentMapPage -> page.keyChooserKeys()
    is CurrentGeneralBookPage -> page.keyChooserKeys()
    else -> emptyList()
}
