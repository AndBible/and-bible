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
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.link.BibleLink
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.database.IdType
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.mybible.myBibleIntToBibleBook
import net.bible.service.sword.mysword.mySwordIntToBibleBook
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedui.reading.session.BibleWindowSession
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.NoSuchKeyException

/** What a JS external link resolves to. */
internal sealed interface ExternalLinkAction {
    /** An in-app link, loaded through `LinkControl.loadApplicationUrl`. */
    data class AppLink(val link: BibleLink) : ExternalLinkAction

    /** Anything else: handed to the platform's URL opener. */
    data class Browser(val url: String) : ExternalLinkAction

    /** A foreign-module link whose book number has no mapping: nothing happens. */
    data object Ignored : ExternalLinkAction
}

/**
 * Classifies a link the JS side asks the app to open (MyBible and MySword shapes, `sword://`/`osis:`,
 * document-independent `strongs://` and `morphology://`); everything else is a [ExternalLinkAction.Browser] link.
 */
internal fun classifyExternalLink(link: String): ExternalLinkAction = when {
    link.startsWith("B:") -> {
        // MyBible links
        val (book, rest) = link.split(" ", limit = 2)
        val bookInt = book.split(":")[1].toInt()
        val bibleBook = myBibleIntToBibleBook[bookInt]
        if (bibleBook == null) ExternalLinkAction.Ignored
        else ExternalLinkAction.AppLink(BibleLink("content", target = "${bibleBook.osis} $rest"))
    }
    link.startsWith("S:") -> {
        // MyBible strongs
        val (_, rest) = link.split(":", limit = 2)
        ExternalLinkAction.AppLink(BibleLink("strong", target = rest))
    }
    link.startsWith("#b") -> {
        // MySword bible links
        val rest = link.substring(2)
        val (bookInt, chapInt, verInt) = rest.split(".").map { it.toInt() }
        val bibleBook = mySwordIntToBibleBook[bookInt]
        if (bibleBook == null) ExternalLinkAction.Ignored
        else ExternalLinkAction.AppLink(BibleLink("content", target = "${bibleBook.osis}.$chapInt.$verInt"))
    }
    link.startsWith("#s") || link.startsWith("#d") -> {
        // MySword strongs links
        ExternalLinkAction.AppLink(BibleLink("strong", target = link.substring(2)))
    }
    link.startsWith("sword://") || link.startsWith("osis:") -> {
        // Internal app links (e.g. sword://CalvinCommentaries/Eph.1.11)
        ExternalLinkAction.AppLink(BibleLink("sword", target = link))
    }
    link.startsWith("strongs://") -> {
        // Document-independent Strong's links (e.g. strongs://G2316, strongs://H430)
        ExternalLinkAction.AppLink(BibleLink("strong", target = link.removePrefix("strongs://")))
    }
    link.startsWith("morphology://") -> {
        // Document-independent morphology links (e.g. morphology://robinson/V-PAI-3S)
        val rest = link.removePrefix("morphology://")
        val slashIdx = rest.indexOf('/')
        val morphType = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
        val code = if (slashIdx >= 0) rest.substring(slashIdx + 1) else ""
        ExternalLinkAction.AppLink(BibleLink(morphType, target = code))
    }
    else -> ExternalLinkAction.Browser(link)
}

/**
 * The link-opening and document-opening calls of the JS bridge (EPUB, external, StudyPad, MyNotes,
 * AI document page, add-on downloads) and the compare-document toggle. UI and navigation calls are
 * skipped for a disposed session.
 *
 * @param viewIsCurrent false when the platform view that owns the session was replaced; a pending
 *   EPUB link then does not navigate
 */
internal class LinkBridgeFamily(
    private val session: BibleWindowSession,
    private val bookmarkJsActions: BookmarkJsActions,
    private val viewIsCurrent: () -> Boolean = { true },
) {
    private val epubLinks by lazy { OrderedLauncher(session.scope) }
    private val windowId get() = session.window.id

    fun openEpubLink(bookInitials: String, toKeyStr: String, toId: String) {
        if (session.isDisposed) return
        val book = Books.installed().getBook(bookInitials) as SwordGenBook
        val backend = book.backend as EpubBackend
        openEpubLink(book, backend, toKeyStr, toId)
    }

    fun openEpubLink(book: SwordGenBook, backend: EpubBackend, keyStr: String, idStr: String) {
        val linkControl = session.linkControl
        val windowMode = linkControl.windowMode
        val page = session.window.pageManager.currentPage
        val sourceDocument = page.currentDocument
        epubLinks.launch("epub-links") {
            val key = withContext(Dispatchers.IO) { backend.getKey(keyStr, idStr) }
            withContext(Dispatchers.Main) {
                if (!viewIsCurrent() || session.window.pageManager.currentPage !== page || page.currentDocument != sourceDocument) return@withContext
                key?.let { linkControl.showLink(book, BookAndKey(it, book, htmlId = idStr), windowMode = windowMode) }
            }
        }
    }

    fun openExternalLink(link: String) {
        if (session.isDisposed) return
        when (val action = classifyExternalLink(link)) {
            is ExternalLinkAction.AppLink -> session.scope.launch(Dispatchers.Main) {
                session.linkControl.loadApplicationUrl(action.link)
            }
            is ExternalLinkAction.Browser -> session.navigator.openExternalUrl(action.url)
            ExternalLinkAction.Ignored -> {}
        }
    }

    fun openDownloads() {
        if (session.isDisposed) return
        // DL-8: called on the JavaBridge thread; the navigator posts the launch to the main thread.
        if (!session.downloadControl.checkDownloadOkay()) return
        session.navigator.openDownloads(addons = true)
    }

    fun openStudyPad(labelId: String, bookmarkId: String) {
        if (session.isDisposed) return
        // After this window's queued writes (e.g. the note save JS sent just before), so the opened
        // document is loaded with them.
        bookmarkJsActions.afterQueuedWrites(windowId, session.scope, Dispatchers.Main) {
            session.linkControl.openStudyPad(IdType(labelId), IdType(bookmarkId))
        }
    }

    fun openMyNotes(v11n: String, ordinal: Int) {
        if (session.isDisposed) return
        bookmarkJsActions.afterQueuedWrites(windowId, session.scope, Dispatchers.Main) {
            session.linkControl.openMyNotes(v11n, ordinal)
        }
    }

    fun openAiDocPage(documentInitials: String, pageKey: String) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(documentInitials) ?: return@launch
            val key = try {
                book.getKey(pageKey)
            } catch (e: NoSuchKeyException) {
                Log.w(TAG, "AI document page not found: $pageKey in $documentInitials", e)
                return@launch
            } ?: return@launch
            session.linkControl.showLink(book, key)
        }
    }

    fun toggleCompareDocument(documentId: String) {
        Log.i(TAG, "toggleCompareDocument")
        val hideDocs = session.workspaceSettings.hideCompareDocuments
        if(hideDocs.contains(documentId)) {
            hideDocs.remove(documentId)
        } else {
            hideDocs.add(documentId)
        }
        WorkspaceChanges.notifySettingsEdited()
    }

    private companion object {
        const val TAG = "LinkBridgeFamily"
    }
}
