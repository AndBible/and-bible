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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.database.IdType
import net.bible.service.common.bookmarksMyNotesPlaylist
import net.bible.service.common.displayName
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.NoteEditorEntityType
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.positiveOrNull
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.reading.session.BibleWindowSession
import org.koin.mp.KoinPlatform

/** The JSON the note editor's AI button sends to `noteEditorLlmAction`. */
@Serializable
internal data class NoteEditorLlmContext(
    val entityType: String,
    val entityId: String,
    val currentText: String,
    val contentType: String,
)

/**
 * The share, copy, help, LLM, MyDocument and prompt-editor calls of the JS bridge.
 *
 * Everything that opens UI, shares or copies is skipped for a disposed session. The MyDocument page
 * writes ([saveMyDocumentPageContent], [reloadMyDocumentPage]) and the deletion itself are data writes
 * keyed by page, not by window state, so they run even if the window closed meanwhile; only the
 * window bookkeeping that follows the deletion is skipped.
 */
internal class ContentBridgeFamily(
    private val session: BibleWindowSession,
    private val bookmarkJsActions: BookmarkJsActions,
    private val facade: () -> SwordContentFacade = { KoinPlatform.getKoin().get() },
    private val strings: () -> CoreStrings = { KoinPlatform.getKoin().get() },
    private val settings: () -> AppSettings = { KoinPlatform.getKoin().get() },
    private val dialogs: () -> AppDialogController = { KoinPlatform.getKoin().get() },
) {
    private val windowId get() = session.window.id

    fun shareBookmarkVerse(bookmarkId: String) {
        if (session.isDisposed) return
        bookmarkJsActions.launch(windowId) {
            val bookmark = session.bookmarkControl.bibleBookmarkById(IdType(bookmarkId)) ?: return@launch
            session.scope.launch(Dispatchers.Main) {
                if (session.isDisposed) return@launch
                session.hostSheets?.showShare(facade().buildShareVersesInput(Selection(bookmark)))
            }
        }
    }

    fun shareVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            if (session.isDisposed) return@launch
            session.hostSheets?.showShare(
                facade().buildShareVersesInput(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
            )
        }
    }

    /** Copies the text of the verse range itself (never the user's current text selection). */
    fun copyVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            if (session.isDisposed) return@launch
            val selection = Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal))
            session.surface.copyText(selection.verseRange?.getName(), shareableSelectionText(selection, facade(), settings()))
        }
    }

    /**
     * Shows a help dialog for a Vue-side view. The [scopeKey] string is resolved here to a
     * (title, message, helpUrl) triple so that the JS side cannot inject arbitrary URLs.
     */
    fun showHelpDialog(scopeKey: String) {
        if (session.isDisposed) return
        val s = strings()
        val (title, message, helpUrl) = when (scopeKey) {
            "memorize" -> Triple(s.help, s.helpMemorizeText, DocsLinks.page("memorize"))
            else -> {
                Log.w(TAG, "Unknown help scope: $scopeKey")
                return
            }
        }
        session.scope.launch(Dispatchers.Main) {
            if (session.isDisposed) return@launch
            val messageHtml = message + "<br><br><i><a href=\"$helpUrl\">${s.helpReadMoreLink}</a></i>"
            dialogs().post(
                AppDialogRequest.Message(
                    title = title,
                    message = messageHtml,
                    confirmText = s.okay,
                    cancellable = true,
                ),
            )
        }
    }

    /** [content] is parsed as the app's small HTML subset, not shown as literal plain text. */
    fun helpDialog(content: String, title: String?) {
        if (session.isDisposed) return
        session.hostSheets?.showHelp(title, content)
    }

    fun helpBookmarks() {
        if (session.isDisposed) return
        val s = strings()
        val message = "<i><a href=\"$bookmarksMyNotesPlaylist\">${s.watchTutorialVideo}</a></i>" +
            "<br><br><b>${s.verseTip}</b><br><br>${s.helpBookmarksText}"
        session.hostSheets?.showHelp(s.bookmarksAndMyNotesTitle, message)
    }

    fun shareHtml(html: String) {
        if (session.isDisposed) return
        val s = strings()
        val docName = when (val firstDoc = session.firstDocument) {
            is StudyPadDocument -> firstDoc.label.displayName
            is MultiFragmentDocument -> s.multiDescription
            is MyNotesDocument -> s.myNotesAbbreviation
            else -> throw RuntimeException("Illegal doc type")
        }
        session.surface.shareHtml(html, docName)
    }

    fun llmAction(bookInitials: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            showPromptSelector(
                Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    fun llmActionGeneric(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            showPromptSelector(
                Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    /** Opens the prompt selector with the document category of the active window's page. Main thread. */
    private fun showPromptSelector(selection: Selection, context: PromptContext) {
        if (session.isDisposed) return
        val category = session.windowControl.windowRepository.activeWindow.pageManager.currentPage.documentCategory
        session.hostSheets?.showPromptSelector(selection, context, category)
    }

    /**
     * Triggers the LLM prompt selector for the note editor context. Called from the Vue.js
     * MarkdownEditor/HtmlEditor AI button. A malformed [contextJson] is logged and ignored.
     */
    fun noteEditorLlmAction(contextJson: String) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            val ctx = try {
                AppJson.decodeFromString<NoteEditorLlmContext>(contextJson)
            } catch (e: kotlinx.serialization.SerializationException) {
                Log.e(TAG, "noteEditorLlmAction: bad context", e)
                return@launch
            }

            // Try to get verse context from the bookmark for better AI context
            var bookInitials: String? = null
            var startOrdinal = 0
            var endOrdinal = 0
            if (ctx.entityType == NoteEditorEntityType.BOOKMARK_NOTE.name) {
                val bookmark = session.bookmarkControl.bibleBookmarkById(IdType(ctx.entityId))
                if (bookmark != null) {
                    bookInitials = bookmark.book?.initials
                    startOrdinal = bookmark.ordinalStart
                    endOrdinal = bookmark.ordinalEnd
                }
            }

            val selection = Selection(
                bookInitials = bookInitials,
                startOrdinal = startOrdinal,
                startOffset = null,
                endOrdinal = endOrdinal,
                endOffset = null,
                bookmarks = emptyList(),
                noteEditorEntityType = try { NoteEditorEntityType.valueOf(ctx.entityType) } catch (_: IllegalArgumentException) { null },
                noteEditorEntityId = ctx.entityId,
                noteEditorContent = ctx.currentText,
                noteEditorContentType = ctx.contentType,
            )
            showPromptSelector(selection, PromptContext.NOTE_EDITOR)
        }
    }

    /**
     * Answers the JS promise [callId] with the page's raw content, or `null` for a missing page or a
     * failing lookup. The caller has already answered `null` for a disposed session.
     */
    fun getMyDocumentPageRawContent(callId: Long, bookInitials: String, pageKey: String) {
        session.scope.launch {
            val result = try {
                MyDocumentBookManager.getPageRawContent(bookInitials, pageKey)
            } catch (e: CancellationException) {
                session.jsChannel.execute(BibleViewEvents.response(callId, null))
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "getMyDocumentPageRawContent failed", e)
                null
            }
            val jsonResult = if (result != null) AppJson.encodeToString(result) else "null"
            session.emit(BibleViewEvents.response(callId, JsExpr(jsonResult)))
        }
    }

    fun shareMyDocumentContent(bookInitials: String, pageKey: String) {
        if (session.isDisposed) return
        session.scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                if (!session.isDisposed) session.surface.shareText(result.content, result.title)
            }
        }
    }

    fun copyMyDocumentContent(bookInitials: String, pageKey: String) {
        if (session.isDisposed) return
        session.scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                if (!session.isDisposed) session.surface.copyText(result.title, result.content)
            }
        }
    }

    fun saveMyDocumentPageContent(pageId: String, content: String, title: String?) {
        MyDocumentBookManager.launcher.launch(documentQueue()) {
            MyDocumentBookManager.savePageContent(IdType(pageId), content, title)
        }
    }

    fun reloadMyDocumentPage(bookInitials: String) {
        MyDocumentBookManager.launcher.launch(documentQueue()) {
            MyDocumentBookManager.refreshDocument(bookInitials)
        }
    }

    fun regenerateMyDocumentPage(pageId: String) {
        if (session.isDisposed) return
        val id = IdType(pageId)
        // The asking window travels with the request: the host must not guess it (the active window can differ).
        // `windowOrNull` is never null for a live Android BibleView (its session is built with the window).
        val windowId = session.windowOrNull?.id ?: return
        session.scope.launch(Dispatchers.Main) {
            if (session.isDisposed) return@launch
            session.hostSheets?.showRegenerate(id, windowId)
        }
    }

    /** Asks first; on "yes" deletes the page and closes this window if it is removable. */
    fun deleteMyDocumentPage(pageId: String) {
        if (session.isDisposed) return
        val id = IdType(pageId)
        session.scope.launch(Dispatchers.Main) {
            if (session.isDisposed) return@launch
            session.hostSheets?.showDeleteDocumentPageConfirm {
                MyDocumentBookManager.launcher.launch(documentQueue()) {
                    MyDocumentBookManager.deleteAIDocumentPage(id)
                    withContext(Dispatchers.Main) {
                        if (session.isDisposed) return@withContext
                        val window = session.window
                        if (session.windowControl.isWindowRemovable(window)) {
                            session.windowControl.closeWindow(window)
                        } else {
                            window.pageManager.setCurrentDocument(window.pageManager.currentBible.currentDocument)
                        }
                    }
                }
            }
        }
    }

    fun openPromptEditor(promptId: String) {
        if (session.isDisposed) return
        session.navigator.openPromptEditor(promptId)
    }

    private fun documentQueue() = "mydocument-window-$windowId"

    private companion object {
        const val TAG = "ContentBridgeFamily"
    }
}
