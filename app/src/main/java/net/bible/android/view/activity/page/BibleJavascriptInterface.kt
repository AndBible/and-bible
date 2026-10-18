/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import okio.Path.Companion.toOkioPath
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.android.view.activity.search.searchIntent
import net.bible.android.platform.AndroidBackupHost

import android.content.ClipData
import android.content.Intent
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.bibleview.BibleBridge
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.link.BibleLink
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ReadingSource
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.service.common.ReadingProgressSettings
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.sword.SwordContentFacade
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.bookmarksMyNotesPlaylist
import net.bible.service.common.displayName
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mybible.myBibleIntToBibleBook
import net.bible.service.sword.mysword.mySwordIntToBibleBook
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.NoteEditorEntityType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.lang.ClassCastException


class BibleJavascriptInterface(
	private val bibleView: BibleView
) : KoinComponent, BibleBridge by bibleView.readingBridge {
    private val swordContentFacade: SwordContentFacade by inject()
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    private val bookmarkJsActions: BookmarkJsActions by inject()
    private val orderedLauncher: net.bible.sharedcore.platform.OrderedLauncher by inject()
    val linkControl get() = bibleView.linkControl
    val bookmarkControl get() = bibleView.bookmarkControl
    val downloadControl get() = bibleView.downloadControl

    /**
     * R6a: the host as a plain Android Activity. This class used to hold `bibleView
     * .mainBibleActivity` and reach 13 distinct members through it; nine of those are the ordinary
     * Activity API (`startActivity`, `startActivityForResult`, `getString`, `lifecycleScope`, an
     * `AlertDialog.Builder` receiver) or library helpers that demand an `Activity`/`ActivityBase`
     * BY SIGNATURE — `CommonUtils.showHelpDialog`, `BackupControl.saveOrShare`,
     * `SearchControl.getSearchIntent`, `CurrentPage.startKeyChooser`. Those keep their spelling
     * against [ActivityBase], which BOTH reading hosts really are, so nothing is cast and nothing
     * can fail at Task 8. The four
     * that were genuinely `MainBibleActivity`-only went into [BibleViewHostCallbacks]; two more
     * (`speakControl`, `searchControl`) are Koin singletons and are injected here instead of
     * borrowed off the Activity, which is R1's move.
     */
    private val hostActivity: ActivityBase get() = bibleView.hostCallbacks.hostActivity
    private val hostCallbacks: BibleViewHostCallbacks get() = bibleView.hostCallbacks
    private val searchControl: SearchControl by inject()
    var notificationsEnabled = false
    val scope get() = hostActivity.lifecycleScope

    fun getActiveLanguages(): String {
        //Get the languages for each of the installed bibles and return the language codes as a json list.
        val languages = SwordDocumentFacade.bibles.map { "\"" + it.bookMetaData.language.code + "\""}
        return "[" + languages.distinct().joinToString(",") + "]"
    }

    override fun shareBookmarkVerse(bookmarkId: String) {
        bookmarkJsActions.launch(bibleView.window.id) {
            val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId)) ?: return@launch
            scope.launch(Dispatchers.Main) {
                hostCallbacks.composeReadingViewHost()?.showShareSheet(
                    swordContentFacade.buildShareVersesInput(Selection(bookmark))
                )
            }
        }
    }

    override fun shareVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.composeReadingViewHost()?.showShareSheet(
                swordContentFacade.buildShareVersesInput(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
            )
        }
    }

    override fun copyVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            bibleView.copySelectionToClipboard(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    /**
     * Show a help dialog for a Vue-side view. The [scopeKey] string is
     * resolved server-side to a (title, message, helpUrl) triple so
     * that the JS side cannot inject arbitrary URLs.
     */
    override fun showHelpDialog(scopeKey: String) {
        val (titleRes, messageRes, helpUrl) = when (scopeKey) {
            "memorize" -> Triple(R.string.help, R.string.help_memorize_text, DocsLinks.page("memorize"))
            else -> {
                Log.w(TAG, "Unknown help scope: $scopeKey")
                return
            }
        }
        scope.launch(Dispatchers.Main) {
            CommonUtils.showHelpDialog(hostActivity, titleRes, messageRes, helpUrl)
        }
    }

    // Platform-dialog removal Task 10: `openAiDocPageChooser` (a native `AlertDialog` list picker
    // over multiple AI doc-marker pages) is deleted -- its TS caller was never wired up
    // (`app/bibleview-js/src/composables/android.ts:127` declared it with no call site; grep
    // confirmed), so this was dead on both sides of the JS bridge.

    /** [content] is now parsed as the app's small HTML subset (`AbHtmlText`/`parseHtmlRuns`) rather
     *  than shown as literal plain text -- harmless for the callers today (no `<`/`&` in their
     *  strings), but a caller adding one later would see it interpreted, not escaped. */
    override fun helpDialog(content: String, title: String?) {
        // Platform-dialog removal Task 18: the reading view owns this dialog now (a `ReadingDialog`
        // over the pane, not a `ReadingOverlay` -- see `ComposeReadingViewHost.ReadingDialog`'s kdoc).
        hostCallbacks.composeReadingViewHost()?.showHelp(title, content)
    }

    override fun helpBookmarks() {
        val verseTip = hostActivity.getString(R.string.verse_tip)
        val bookmarksMyNotesHelp = hostActivity.getString(R.string.help_bookmarks_text)
        val message = "<i><a href=\"$bookmarksMyNotesPlaylist\">${hostActivity.getString(R.string.watch_tutorial_video)}</a></i>" +
            "<br><br><b>$verseTip</b><br><br>$bookmarksMyNotesHelp"

        // Platform-dialog removal Task 18: same reading-view-owned dialog state as [helpDialog].
        hostCallbacks.composeReadingViewHost()?.showHelp(hostActivity.getString(R.string.bookmarks_and_mynotes_title), message)
    }

    override fun shareHtml(html: String) {
        val docName = when(val firstDoc = bibleView.firstDocument) {
            is StudyPadDocument -> firstDoc.label.displayName
            is MultiFragmentDocument -> hostActivity.getString(R.string.multi_description)
            is MyNotesDocument -> hostActivity.getString(R.string.my_notes_abbreviation)
            else -> throw RuntimeException("Illegal doc type")
        }
        bibleView.surface.shareHtml(html, docName)
    }

    override fun llmAction(bookInitials: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showLlmPromptSelector(
                Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    override fun llmActionGeneric(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showLlmPromptSelector(
                Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    @Serializable
    data class NoteEditorLlmContext(
        val entityType: String,
        val entityId: String,
        val currentText: String,
        val contentType: String
    )

    /**
     * Trigger LLM prompt selector for the note editor context.
     * Called from Vue.js MarkdownEditor/HtmlEditor AI button.
     */
    override fun noteEditorLlmAction(contextJson: String) {
        scope.launch(Dispatchers.Main) {
            val ctx = json.decodeFromString<NoteEditorLlmContext>(serializer(), contextJson)

            // Try to get verse context from the bookmark for better AI context
            var bookInitials: String? = null
            var startOrdinal = 0
            var endOrdinal = 0
            if (ctx.entityType == NoteEditorEntityType.BOOKMARK_NOTE.name) {
                val bookmark = bookmarkControl.bibleBookmarkById(IdType(ctx.entityId))
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
            hostCallbacks.showLlmPromptSelector(selection, PromptContext.NOTE_EDITOR)
        }
    }

    private val windowControl get() = bibleView.windowControl

    override fun getMyDocumentPageRawContent(callId: Long, bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey)
            val jsonResult = if (result != null) {
                json.encodeToString(serializer(), result)
            } else {
                "null"
            }
            bibleView.emit(BibleViewEvents.response(callId, JsExpr(jsonResult)))
        }
    }

    override fun shareMyDocumentContent(bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                bibleView.surface.shareText(result.content, result.title)
            }
        }
    }

    override fun copyMyDocumentContent(bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                bibleView.surface.copyText(result.title, result.content)
            }
        }
    }

    override fun saveMyDocumentPageContent(bookInitials: String, pageId: String, content: String, title: String?) {
        MyDocumentBookManager.launcher.launch("mydocument-window-${bibleView.window.id}") {
            MyDocumentBookManager.savePageContent(IdType(pageId), content, title)
        }
    }

    override fun reloadMyDocumentPage(bookInitials: String) {
        MyDocumentBookManager.launcher.launch("mydocument-window-${bibleView.window.id}") {
            MyDocumentBookManager.refreshDocument(bookInitials)
        }
    }

    override fun regenerateMyDocumentPage(pageId: String) {
        val id = IdType(pageId)
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showRegenerate(id, bibleView)
        }
    }

    override fun deleteMyDocumentPage(pageId: String) {
        val id = IdType(pageId)
        scope.launch(Dispatchers.Main) {
            // Platform-dialog removal Task 18: the QUESTION moves to the reading view's own dialog
            // state; the deletion + window bookkeeping stays here, since it needs [windowControl] and
            // the triggering [bibleView] (see `ComposeReadingViewHost.showDeleteDocumentPageConfirm`'s
            // kdoc).
            hostCallbacks.composeReadingViewHost()?.showDeleteDocumentPageConfirm {
                MyDocumentBookManager.launcher.launch("mydocument-window-${bibleView.window.id}") {
                    MyDocumentBookManager.deleteAIDocumentPage(id)
                    withContext(Dispatchers.Main) {
                        if (hostActivity.isDestroyed || hostActivity.isFinishing ||
                            !scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) return@withContext
                        val window = bibleView.window
                        if (windowControl.isWindowRemovable(window)) {
                            windowControl.closeWindow(window)
                        } else {
                            window.pageManager.setCurrentDocument(window.pageManager.currentBible.currentDocument)
                        }
                    }
                }
            }
        }
    }

    override fun openPromptEditor(promptId: String) = bibleView.navigator.openPromptEditor(promptId)

    private val TAG get() = "BibleView[${bibleView.windowRef.get()?.displayId}] JSInt"
}
