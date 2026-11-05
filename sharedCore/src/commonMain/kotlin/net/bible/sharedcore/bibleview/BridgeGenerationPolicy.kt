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

package net.bible.sharedcore.bibleview

/** The document generation of a bridge with no admitted document; the page also starts with it. Never admitted. */
const val NO_DOCUMENT_GENERATION = 0L

/**
 * A [BibleBridge] that serves one window's document. [admittedDocumentGeneration] is the generation of the document it
 * last admitted ([NO_DOCUMENT_GENERATION] when none, e.g. a web view parked for reuse), the one the page receives with
 * that document's `clear_document` and echoes in every call ([BridgeMessage.documentGeneration]).
 */
interface DocumentGenerationGate {
    val admittedDocumentGeneration: Long
}

/**
 * Which bridge calls [BridgeDispatcher] drops when they come from a page whose document is not the admitted one.
 *
 * A page reports and acts asynchronously, and a web view outlives its document: after a chapter change, and above all
 * when a workspace switch hands the outgoing web view to an incoming window (#3950), the old page stays on screen and
 * keeps calling until the new document replaces it (osTicket 3508, #3975). Such a call reaches the bridge of the window
 * that now owns the view.
 *
 * - [guarded]: the call acts on the window's (or workspace's) CURRENT document or state implicitly: its position,
 *   paging, restore state, navigation of the window, workspace settings. From a replaced page it would change the wrong
 *   window, so it is dropped (a deferred one is answered with null).
 * - [unguarded]: the call addresses its target explicitly (bookmark/label/page id, book + ordinals), only opens UI, or
 *   reports the state of the live page itself (modal, editing, focus, client ready, console). Dropping it would lose user
 *   input (a note save of an editor the replacement closed) or wedge the page's UI state.
 *
 * Accepted residue: a guarded tap the user makes in the few milliseconds between native admitting a document and the
 * page clearing the old one is dropped without feedback; an unguarded call of a replaced page still shows its UI (note
 * editor, prompt selector) for, or credits reading progress to, the window that now owns the view.
 *
 * Every bridge method is in exactly one set (`BridgeGenerationPolicyTest`); classify a new method when adding it.
 */
object BridgeGenerationPolicy {
    val guarded: Set<String> = setOf(
        // Position and paging of the window's document.
        "scrolledToOrdinal", "requestMoreToBeginning", "requestMoreToEnd", "goToNextChapter", "goToPreviousChapter",
        // The page's restore state is stored on the window.
        "saveState",
        // Workspace settings of the current workspace.
        "setStudyPadCursor", "toggleCompareDocument", "setLimitAmbiguousModalSize",
        // Navigate the window (or the active window) from a link in the page's document.
        "openEpubLink", "openStudyPad", "openMyNotes", "openAiDocPage",
        // Act on the window's current document or the window itself.
        "shareHtml", "deleteMyDocumentPage", "regenerateMyDocumentPage",
    )

    val unguarded: Set<String> = setOf(
        // The live page's own lifecycle and UI state.
        "setClientReady", "console", "selectionCleared", "reportInputFocus", "setEditing", "reportModalState", "toast",
        "onKeyDown", "crash",
        // Deferred reads answering the page itself.
        "parseRef", "refChooserDialog", "getMyDocumentPageRawContent",
        // Bookmark, label and StudyPad data, addressed by id.
        "saveBookmarkNote", "saveGenericBookmarkNote", "removeBookmark", "removeGenericBookmark", "assignLabels",
        "genericAssignLabels", "setBookmarkEditAction", "createNewStudyPadEntry", "deleteStudyPadEntry",
        "removeBookmarkLabel", "removeGenericBookmarkLabel", "updateOrderNumber", "updateStudyPadTextEntry",
        "updateStudyPadTextEntryText", "updateBookmarkToLabel", "updateGenericBookmarkToLabel", "setAsPrimaryLabel",
        "setAsPrimaryLabelGeneric", "toggleBookmarkLabel", "toggleGenericBookmarkLabel", "setBookmarkCustomIcon",
        "setGenericBookmarkCustomIcon", "setBookmarkWholeVerse", "setGenericBookmarkWholeVerse",
        // Bookmark creation and verse actions, addressed by book + ordinals.
        "addBookmark", "addGenericBookmark", "addParagraphBreakBookmark", "addGenericParagraphBreakBookmark",
        "createWholePageBookmark", "shareBookmarkVerse", "shareVerse", "copyVerse", "compare", "memorize",
        "markAsMemorized", "addMemorizationTarget", "unmarkMemorized", "removeMemorizationTarget", "recordChapterRead",
        "openChapterReadHistory", "speak", "speakMemorizationLoop", "speakGeneric", "llmAction", "llmActionGeneric",
        "noteEditorLlmAction",
        // MyDocument data, addressed by page id or book.
        "saveMyDocumentPageContent", "reloadMyDocumentPage", "shareMyDocumentContent", "copyMyDocumentContent",
        // Global settings and UI that does not depend on the window's document.
        "setReadingProgressSettings", "openReadingProgress", "openReadingProgressSettings", "showHelpDialog",
        "helpDialog", "helpBookmarks", "openExternalLink", "openDownloads", "openPromptEditor",
    )
}
