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
 * Admits the [BridgeGenerationPolicy.guarded] calls of the page whose document a window currently holds (#3975).
 *
 * The page stamps every call with the generation of the document it received with its `clear_document`
 * ([BridgeMessage.documentGeneration]). [BridgeDispatcher] asks [isAdmitted] on the calling thread (Android: the
 * JavaBridge thread) and then hands the call to [runAdmitted], which runs it on the thread that owns the window's
 * state (main) only if the generation is STILL admitted there for the same window: a navigation or view rebind that
 * happened in between drops it.
 *
 * A bridge that does not implement the gate gets [ClosedDocumentGate]: its guarded calls are always dropped (fail
 * closed). A bridge with no window documents to protect must say so explicitly with [OpenDocumentGate].
 */
interface DocumentGenerationGate {
    /** Whether a guarded call of [generation] belongs to the document admitted right now. */
    fun isAdmitted(generation: Long?): Boolean

    /**
     * Runs [call] where window state is changed (the main thread) if [generation] is still admitted there; otherwise
     * runs [drop]. Exactly one of them runs, once. Neither may throw into the caller's thread.
     */
    fun runAdmitted(generation: Long?, call: () -> Unit, drop: () -> Unit)
}

/** Drops every guarded call: the gate of a bridge that has none (see [DocumentGenerationGate]). */
object ClosedDocumentGate : DocumentGenerationGate {
    override fun isAdmitted(generation: Long?) = false
    override fun runAdmitted(generation: Long?, call: () -> Unit, drop: () -> Unit) = drop()
}

/** Admits every call on the calling thread: only for bridges that serve no window document (the PoC controller). */
object OpenDocumentGate : DocumentGenerationGate {
    override fun isAdmitted(generation: Long?) = true
    override fun runAdmitted(generation: Long?, call: () -> Unit, drop: () -> Unit) = call()
}

/** The gate [BridgeDispatcher] uses for this bridge by default: the bridge itself, or [ClosedDocumentGate]. */
fun BibleBridge.documentGenerationGate(): DocumentGenerationGate = this as? DocumentGenerationGate ?: ClosedDocumentGate

/**
 * Which bridge calls [BridgeDispatcher] drops when they come from a page whose document is not the admitted one.
 *
 * A page reports and acts asynchronously, and a web view outlives its document: after a chapter change, and above all
 * when a workspace switch hands the outgoing web view to an incoming window (#3950), the old page stays on screen and
 * keeps calling until the new document replaces it (osTicket 3508, #3975). Such a call reaches the bridge of the window
 * that now owns the view.
 *
 * The rule fails closed: a call is [guarded] unless dropping it would lose user data or wedge the page.
 *
 * - [guarded]: the call navigates, or acts on the window's (or the active window's, or workspace's) CURRENT document or
 *   state implicitly: its position, paging, restore state, navigation from a link or a verse action (`LinkControl`
 *   resolves the active window and can replace its document), speech that follows the text, the prompt selector of the
 *   active window's document category, keyboard shortcuts, workspace settings. From a replaced page it would change the
 *   wrong window, so it is dropped (a deferred one is answered with null). Dropping one loses at most a tap.
 * - [unguarded]: the call writes user data addressed explicitly (bookmark/label/StudyPad/MyDocument id, book +
 *   ordinals), answers the page itself, only opens UI that does not depend on the window's document, or reports the
 *   state of the live page itself (modal, editing, focus, client ready, console). Dropping it would lose user input (a
 *   note save of an editor the replacement closed) or wedge the page's UI state.
 *
 * Accepted residue: a guarded tap the user makes between native requesting a new document and the page clearing the
 * old one is dropped without feedback; an unguarded call of a replaced page still shows its UI (note editor, reading
 * progress, help) for, or credits reading progress to, the window that now owns the view.
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
        // Navigate the window or the active window: links in the page's document (in-app osis:, sword:, MyBible and
        // MySword links; a browser link is only a tap), verse actions that open a document.
        "openEpubLink", "openStudyPad", "openMyNotes", "openAiDocPage", "openExternalLink", "compare", "memorize",
        // Speech follows the spoken text in the window.
        "speak", "speakMemorizationLoop", "speakGeneric",
        // The prompt selector takes the active window's document category.
        "llmAction", "llmActionGeneric", "noteEditorLlmAction",
        // Shortcuts act on this view's window or the active one.
        "onKeyDown",
        // Act on the window's current document or the window itself.
        "shareHtml", "deleteMyDocumentPage", "regenerateMyDocumentPage",
    )

    val unguarded: Set<String> = setOf(
        // The live page's own lifecycle and UI state.
        "setClientReady", "console", "selectionCleared", "reportInputFocus", "setEditing", "reportModalState", "toast",
        "crash",
        // Deferred reads answering the page itself.
        "parseRef", "refChooserDialog", "getMyDocumentPageRawContent",
        // Bookmark, label and StudyPad data, addressed by id.
        "saveBookmarkNote", "saveGenericBookmarkNote", "removeBookmark", "removeGenericBookmark", "assignLabels",
        "genericAssignLabels", "setBookmarkEditAction", "createNewStudyPadEntry", "deleteStudyPadEntry",
        "removeBookmarkLabel", "removeGenericBookmarkLabel", "updateOrderNumber", "updateStudyPadTextEntry",
        "updateStudyPadTextEntryText", "updateBookmarkToLabel", "updateGenericBookmarkToLabel", "setAsPrimaryLabel",
        "setAsPrimaryLabelGeneric", "toggleBookmarkLabel", "toggleGenericBookmarkLabel", "setBookmarkCustomIcon",
        "setGenericBookmarkCustomIcon", "setBookmarkWholeVerse", "setGenericBookmarkWholeVerse",
        // Bookmark and memorization data and verse sharing, addressed by book + ordinals.
        "addBookmark", "addGenericBookmark", "addParagraphBreakBookmark", "addGenericParagraphBreakBookmark",
        "createWholePageBookmark", "shareBookmarkVerse", "shareVerse", "copyVerse",
        "markAsMemorized", "addMemorizationTarget", "unmarkMemorized", "removeMemorizationTarget", "recordChapterRead",
        // MyDocument data, addressed by page id or book.
        "saveMyDocumentPageContent", "reloadMyDocumentPage", "shareMyDocumentContent", "copyMyDocumentContent",
        // Global settings and UI that does not depend on the window's document.
        "setReadingProgressSettings", "openReadingProgress", "openReadingProgressSettings", "openChapterReadHistory",
        "showHelpDialog", "helpDialog", "helpBookmarks", "openDownloads", "openPromptEditor",
    )
}
