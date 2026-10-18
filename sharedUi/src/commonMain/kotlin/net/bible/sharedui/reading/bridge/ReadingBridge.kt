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

package net.bible.sharedui.reading.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.page.refChooserVerseName
import net.bible.android.view.activity.page.Selection
import net.bible.service.sword.epub.EpubBackend
import net.bible.sharedcore.bibleview.BibleBridge
import org.crosswire.jsword.book.sword.SwordGenBook
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.BookmarkCreation
import org.crosswire.jsword.passage.Verse
import org.koin.mp.KoinPlatform

/**
 * Platform-independent implementation of the JS bridge ([BibleBridge]) for one reading window.
 *
 * Calls arrive on any thread (Android: the WebView JavaBridge thread). Every method drops the call
 * when the session is disposed (window closed or document replaced); deferred calls first answer the
 * JS promise with null so it never hangs. A failing deferred handler also answers null/empty.
 *
 * Families not yet moved from the platform adapter throw [NotImplementedError]; the adapter
 * overrides them.
 */
class ReadingBridge(
    private val session: BibleWindowSession,
    viewIsCurrent: () -> Boolean = { true },
    private val progressJsActionsProvider: () -> ProgressJsActions = { KoinPlatform.getKoin().get() },
    private val speakerProvider: () -> ReadingSpeaker = { KoinPlatform.getKoin().get<SpeakControl>().asReadingSpeaker() },
    private val bookmarkJsActionsProvider: () -> BookmarkJsActions = { KoinPlatform.getKoin().get() },
) : BibleBridge {
    private val orderedLauncher: OrderedLauncher by lazy { KoinPlatform.getKoin().get() }
    private val speakControl: SpeakControl by lazy { KoinPlatform.getKoin().get() }
    private val currentPageManager get() = session.window.pageManager

    private val bookmarks by lazy {
        BookmarkBridgeFamily(session, bookmarkJsActionsProvider(), session.bookmarkControl)
    }

    private val progressSpeak by lazy {
        ProgressSpeakBridgeFamily(session, progressJsActionsProvider(), speakerProvider())
    }

    private val links by lazy { LinkBridgeFamily(session, bookmarkJsActionsProvider(), viewIsCurrent) }

    /** Opens the compare document for [selection] (the platform view's selection menu action). */
    fun compareSelection(selection: Selection?) = progressSpeak.compareSelection(selection)

    /** Opens the memorize document for [selection] (the platform view's selection menu action). */
    fun memorizeSelection(selection: Selection?) = progressSpeak.memorizeSelection(selection)

    /** Opens an EPUB link for an already resolved [book] (the platform view's `epub-ref:` links). */
    fun openEpubLink(book: SwordGenBook, backend: EpubBackend, keyStr: String, idStr: String) =
        links.openEpubLink(book, backend, keyStr, idStr)

    /** Bookmark creation shared with the platform view (selection menu actions). */
    val bookmarkCreation: BookmarkCreation get() = bookmarks.creation

    private fun notYetMoved(name: String): Nothing =
        throw NotImplementedError("BibleBridge.$name has not moved to ReadingBridge yet")

    /** Answers a deferred call with null directly on the channel (works after dispose). */
    private fun answerNull(callId: Long) {
        session.jsChannel.execute(BibleViewEvents.response(callId, null))
    }

    /** Runs a deferred [block]; a disposed session or a failing handler answers null. */
    private inline fun deferred(callId: Long, name: String, block: () -> Unit) {
        if (session.isDisposed) {
            answerNull(callId)
            return
        }
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "$name failed", e)
            answerNull(callId)
        }
    }

    override fun scrolledToOrdinal(keyStr: String, ordinal: Int) {
        if (session.isDisposed) return
        val doc = session.firstDocument
        if (doc is BibleDocument || doc is MyNotesDocument) {
            currentPageManager.currentBible.setCurrentVerseOrdinal(ordinal,
                when (doc) {
                    is BibleDocument -> (session.initialKey as Verse).versification
                    is MyNotesDocument -> KJVA
                    else -> throw RuntimeException("Unsupported doc")
                }, session.window)
        } else if (doc is OsisDocument || doc is StudyPadDocument) {
            val curPage = currentPageManager.currentPage
            // Commentaries and general books are addressed by a single osisRef key. When infinite
            // scroll brings a new block/entry into view its document carries a different osisRef, so
            // update the page key and notify listeners (title bar / synced windows). Bible & MyNotes
            // are handled above by ordinal.
            if ((curPage is CurrentGeneralBookPage || curPage is CurrentCommentaryPage) && doc is OsisDocument) {
                if (curPage.updateKeyFromScrolledOsisRef(keyStr)) {
                    PassageChangeMediator.onCurrentVerseChanged(session.window)
                }
            }
            curPage.anchorOrdinal = OrdinalRange(ordinal)
        }
    }

    override fun setClientReady() {
        if (session.isDisposed) return
        Log.i(TAG, "set client ready")
        session.paging.setClientReady()
    }

    override fun setLimitAmbiguousModalSize(value: Boolean) {
        if (session.isDisposed) return
        Log.i(TAG, "setLimitAmbiguousModalSize")
        session.workspaceSettings.limitAmbiguousModalSize = value
        WorkspaceChanges.notifySettingsEdited()
    }

    override fun requestMoreToBeginning(callId: Long) = deferred(callId, "requestMoreToBeginning") {
        Log.i(TAG, "Request more text at top")
        session.paging.requestMoreToBeginning(callId)
    }

    override fun requestMoreToEnd(callId: Long) = deferred(callId, "requestMoreToEnd") {
        Log.i(TAG, "Request more text at end")
        session.paging.requestMoreToEnd(callId)
    }

    override fun goToNextChapter() {
        if (session.isDisposed) return
        Log.i(TAG, "Go to next chapter")
        navigateChapter("next") { next() }
    }

    override fun goToPreviousChapter() {
        if (session.isDisposed) return
        Log.i(TAG, "Go to previous chapter")
        navigateChapter("previous") { previous() }
    }

    /**
     * Captures page, document and selection generation now and moves only if all are unchanged when
     * the ordered task runs on the main thread (a quick swipe must not move a replaced page).
     */
    private fun navigateChapter(what: String, move: suspend net.bible.android.control.page.CurrentPage.() -> Unit) {
        val manager = currentPageManager
        val page = manager.currentPage
        val document = page.currentDocument
        val generation = (page as? CurrentGeneralBookPage)?.selectionGeneration
        orderedLauncher.launch("page-nav-${manager.window.id}") {
            try {
                withContext(Dispatchers.Main) {
                    if (manager.currentPage === page && page.currentDocument === document &&
                        (page as? CurrentGeneralBookPage)?.selectionGeneration == generation) page.move()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to $what chapter", e)
            }
        }
    }

    override fun parseRef(callId: Long, s: String) = deferred(callId, "parseRef") {
        Log.i(TAG, "parseRef")
        session.paging.parseRef(callId, s)
    }

    /**
     * The JS reference chooser: awaits the host-provided [BibleWindowSession.verseChooser] and answers
     * its verse name, or the empty string when it was dismissed or no chooser is installed.
     */
    override fun refChooserDialog(callId: Long) {
        if (session.isDisposed) {
            answerNull(callId)
            return
        }
        session.scope.launch {
            val verseName = try {
                refChooserVerseName(session.verseChooser?.invoke())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refChooserDialog failed", e)
                ""
            }
            session.emit(BibleViewEvents.responseString(callId, verseName))
        }
    }

    override fun console(loggerName: String, message: String) {
        if (session.isDisposed) return
        Log.i(TAG, "Console[$loggerName] $message")
    }

    override fun selectionCleared() {
        if (session.isDisposed) return
        Log.i(TAG, "Selection cleared!")
        session.surface.stopSelection()
    }

    override fun reportInputFocus(newValue: Boolean) {
        if (session.isDisposed) return
        Log.i(TAG, "Focus mode now $newValue")
    }

    override fun setEditing(enabled: Boolean) {
        if (session.isDisposed) return
        session.editingTextInJs = enabled
    }

    override fun toast(text: String) {
        if (session.isDisposed) return
        session.surface.toast(text)
    }

    override fun saveState(newState: String) {
        if (session.isDisposed) return
        session.window.pageManager.jsState = newState
    }

    override fun reportModalState(value: Boolean) {
        if (session.isDisposed) return
        session.modalOpen = value
    }

    override fun onKeyDown(key: String) {
        if (session.isDisposed) return
        Log.i(TAG, "key $key")
        session.scope.launch(Dispatchers.Main) {
            val keys = session.keyActions
            when (key) {
                "AltArrowDown", "AltArrowRight" -> session.windowControl.focusNextWindow()
                "AltArrowUp", "AltArrowLeft" -> session.windowControl.focusPreviousWindow()
                // "AltKeyW" (focus the window restore-buttons strip) is retired: the Compose reading
                // view has no such strip, so the shortcut was a silent no-op.
                "AltKeyM" -> keys?.openDrawer()
                "AltKeyO" -> keys?.openOverflowMenu()
                // Deliberately acts on this view's window, not the active one.
                "CtrlKeyB" -> session.navigator.startKeyChooser(session.window.pageManager.currentPage)
                "CtrlKeyW" -> if (keys?.showWorkspaceSheetIfHosted() != true) session.navigator.openWorkspaceSelector()
                "CtrlKeyC" -> keys?.copySelection()
                "CtrlKeyF" -> if (keys?.searchIfHosted() != true) {
                    session.navigator.openSearch(session.windowControl.activeWindowPageManager.currentPage.currentDocument)
                }
                "Space" -> if (!speakControl.isStopped) speakControl.toggleSpeak(true)
            }
        }
    }

    override fun crash() {
        if (session.isDisposed) return
        session.scope.launch { session.keyActions?.crashAllBibleViews() }
    }

    // Not moved yet: the platform adapter overrides these.
    override fun saveBookmarkNote(bookmarkId: String, note: String?) = bookmarks.saveBookmarkNote(bookmarkId, note)
    override fun saveGenericBookmarkNote(bookmarkId: String, note: String?) = bookmarks.saveGenericBookmarkNote(bookmarkId, note)
    override fun removeBookmark(bookmarkId: String) = bookmarks.removeBookmark(bookmarkId)
    override fun removeGenericBookmark(bookmarkId: String) = bookmarks.removeGenericBookmark(bookmarkId)
    override fun assignLabels(bookmarkId: String) = bookmarks.assignLabels(bookmarkId)
    override fun genericAssignLabels(bookmarkId: String) = bookmarks.genericAssignLabels(bookmarkId)
    override fun setBookmarkEditAction(bookmarkId: String, valueStr: String) = bookmarks.setBookmarkEditAction(bookmarkId, valueStr)
    override fun openEpubLink(bookInitials: String, toKeyStr: String, toId: String) = links.openEpubLink(bookInitials, toKeyStr, toId)
    override fun openExternalLink(link: String) = links.openExternalLink(link)
    override fun openDownloads() = links.openDownloads()
    override fun createNewStudyPadEntry(labelId: String, entryType: String, afterEntryId: String) = bookmarks.createNewStudyPadEntry(labelId, entryType, afterEntryId)
    override fun deleteStudyPadEntry(studyPadId: String) = bookmarks.deleteStudyPadEntry(studyPadId)
    override fun removeBookmarkLabel(bookmarkId: String, labelId: String) = bookmarks.removeBookmarkLabel(bookmarkId, labelId)
    override fun removeGenericBookmarkLabel(bookmarkId: String, labelId: String) = bookmarks.removeGenericBookmarkLabel(bookmarkId, labelId)
    override fun updateOrderNumber(labelId: String, data: String) = bookmarks.updateOrderNumber(labelId, data)
    override fun setStudyPadCursor(labelId: String, orderNumber: Int) = bookmarks.setStudyPadCursor(labelId, orderNumber)
    override fun updateStudyPadTextEntry(data: String) = bookmarks.updateStudyPadTextEntry(data)
    override fun updateStudyPadTextEntryText(id: String, text: String) = bookmarks.updateStudyPadTextEntryText(id, text)
    override fun updateBookmarkToLabel(data: String) = bookmarks.updateBookmarkToLabel(data)
    override fun updateGenericBookmarkToLabel(data: String) = bookmarks.updateGenericBookmarkToLabel(data)
    override fun shareBookmarkVerse(bookmarkId: String): Unit = notYetMoved("shareBookmarkVerse")
    override fun shareVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int): Unit = notYetMoved("shareVerse")
    override fun copyVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int): Unit = notYetMoved("copyVerse")
    override fun addBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) = bookmarks.addBookmark(bookInitials, startOrdinal, endOrdinal, addNote)
    override fun addGenericBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) = bookmarks.addGenericBookmark(bookInitials, osisRef, startOrdinal, endOrdinal, addNote)
    override fun addParagraphBreakBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int) = bookmarks.addParagraphBreakBookmark(bookInitials, startOrdinal, endOrdinal)
    override fun addGenericParagraphBreakBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int) = bookmarks.addGenericParagraphBreakBookmark(bookInitials, osisRef, startOrdinal, endOrdinal)
    override fun createWholePageBookmark(bookInitials: String, bookKey: String) = bookmarks.createWholePageBookmark(bookInitials, bookKey)
    override fun compare(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) = progressSpeak.compare(bookInitials, verseOrdinal, endOrdinal)
    override fun memorize(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) = progressSpeak.memorize(bookInitials, verseOrdinal, endOrdinal)
    override fun markAsMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) = progressSpeak.markAsMemorized(bookInitials, startOrdinal, endOrdinal)
    override fun addMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) = progressSpeak.addMemorizationTarget(bookInitials, startOrdinal, endOrdinal)
    override fun unmarkMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) = progressSpeak.unmarkMemorized(bookInitials, startOrdinal, endOrdinal)
    override fun removeMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) = progressSpeak.removeMemorizationTarget(bookInitials, startOrdinal, endOrdinal)
    override fun setReadingProgressSettings(json: String) = progressSpeak.setReadingProgressSettings(json)
    override fun openReadingProgress(tab: Int) = progressSpeak.openReadingProgress(tab)
    override fun openReadingProgressSettings() = progressSpeak.openReadingProgressSettings()
    override fun showHelpDialog(scopeKey: String): Unit = notYetMoved("showHelpDialog")
    override fun recordChapterRead(bookInitials: String, startOrdinal: Int, chapter: Int, source: String) = progressSpeak.recordChapterRead(bookInitials, startOrdinal, chapter, source)
    override fun openChapterReadHistory(bookInitials: String, startOrdinal: Int, chapter: Int) = progressSpeak.openChapterReadHistory(bookInitials, startOrdinal, chapter)
    override fun openStudyPad(labelId: String, bookmarkId: String) = links.openStudyPad(labelId, bookmarkId)
    override fun openMyNotes(v11n: String, ordinal: Int) = links.openMyNotes(v11n, ordinal)
    override fun openAiDocPage(documentInitials: String, pageKey: String) = links.openAiDocPage(documentInitials, pageKey)
    override fun speak(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) = progressSpeak.speak(bookInitials, v11nName, ordinal, endOrdinal)
    override fun speakMemorizationLoop(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) = progressSpeak.speakMemorizationLoop(bookInitials, v11nName, ordinal, endOrdinal)
    override fun speakGeneric(bookInitials: String, osisRef: String, ordinal: Int, endOrdinal: Int) = progressSpeak.speakGeneric(bookInitials, osisRef, ordinal, endOrdinal)
    override fun setAsPrimaryLabel(bookmarkId: String, labelId: String) = bookmarks.setAsPrimaryLabel(bookmarkId, labelId)
    override fun setAsPrimaryLabelGeneric(bookmarkId: String, labelId: String) = bookmarks.setAsPrimaryLabelGeneric(bookmarkId, labelId)
    override fun toggleBookmarkLabel(bookmarkId: String, labelId: String) = bookmarks.toggleBookmarkLabel(bookmarkId, labelId)
    override fun toggleGenericBookmarkLabel(bookmarkId: String, labelId: String) = bookmarks.toggleGenericBookmarkLabel(bookmarkId, labelId)
    override fun setBookmarkCustomIcon(bookmarkId: String, value: String?) = bookmarks.setBookmarkCustomIcon(bookmarkId, value)
    override fun setGenericBookmarkCustomIcon(bookmarkId: String, value: String?) = bookmarks.setGenericBookmarkCustomIcon(bookmarkId, value)
    override fun setBookmarkWholeVerse(bookmarkId: String, value: Boolean) = bookmarks.setBookmarkWholeVerse(bookmarkId, value)
    override fun setGenericBookmarkWholeVerse(bookmarkId: String, value: Boolean) = bookmarks.setGenericBookmarkWholeVerse(bookmarkId, value)
    override fun toggleCompareDocument(documentId: String) = links.toggleCompareDocument(documentId)
    override fun helpDialog(content: String, title: String?): Unit = notYetMoved("helpDialog")
    override fun helpBookmarks(): Unit = notYetMoved("helpBookmarks")
    override fun shareHtml(html: String): Unit = notYetMoved("shareHtml")
    override fun llmAction(bookInitials: String, startOrdinal: Int, endOrdinal: Int, text: String): Unit = notYetMoved("llmAction")
    override fun llmActionGeneric(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, text: String): Unit = notYetMoved("llmActionGeneric")
    override fun noteEditorLlmAction(contextJson: String): Unit = notYetMoved("noteEditorLlmAction")
    override fun getMyDocumentPageRawContent(callId: Long, bookInitials: String, pageKey: String): Unit = notYetMoved("getMyDocumentPageRawContent")
    override fun shareMyDocumentContent(bookInitials: String, pageKey: String): Unit = notYetMoved("shareMyDocumentContent")
    override fun copyMyDocumentContent(bookInitials: String, pageKey: String): Unit = notYetMoved("copyMyDocumentContent")
    override fun saveMyDocumentPageContent(bookInitials: String, pageId: String, content: String, title: String?): Unit = notYetMoved("saveMyDocumentPageContent")
    override fun reloadMyDocumentPage(bookInitials: String): Unit = notYetMoved("reloadMyDocumentPage")
    override fun regenerateMyDocumentPage(pageId: String): Unit = notYetMoved("regenerateMyDocumentPage")
    override fun deleteMyDocumentPage(pageId: String): Unit = notYetMoved("deleteMyDocumentPage")
    override fun openPromptEditor(promptId: String): Unit = notYetMoved("openPromptEditor")

    private companion object {
        const val TAG = "ReadingBridge"
    }
}

/**
 * Host-owned reactions to reading-view keyboard shortcuts and a crash request; set on
 * [BibleWindowSession.keyActions] by the reading host. Calls arrive on the main thread.
 */
interface ReadingKeyActions {
    /** Alt+M: open the navigation drawer and focus it. */
    fun openDrawer()

    /** Alt+O: open the overflow menu. */
    fun openOverflowMenu()

    /** Ctrl+W: show the workspace sheet; false when no host is mounted (caller falls back). */
    fun showWorkspaceSheetIfHosted(): Boolean

    /** Ctrl+C: copy the current selection. */
    fun copySelection()

    /** Ctrl+F: open the in-view search; false when no host is mounted (caller falls back). */
    fun searchIfHosted(): Boolean

    /** Debug crash request from JS. */
    fun crashAllBibleViews()
}
