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

package net.bible.sharedui.ios.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.android.control.page.CurrentPageManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.history.HistoryManager
import net.bible.service.sword.BookAndKeySerialized
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.nav.ReadingResultCollector
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.VerseFactory

/** The chooser screens whose answers the reading view applies. */
@kotlin.native.HiddenFromObjC
enum class ChooserKind { KeyChooser, Passage, Document, MyDocumentPages, MyDocuments }

/** Where the navigator tells the results which window opened a chooser. */
@kotlin.native.HiddenFromObjC
interface ChooserRequests {
    /** A chooser of [kind] was opened by [asker] (the page manager of the window that asked; null: the active window). */
    fun asked(kind: ChooserKind, asker: Any?)

    /** The asker of a request that names none: the window that is active now. */
    fun activeAsker(): Any?

    companion object {
        /** Records nothing (a navigator without result handling). */
        val None = object : ChooserRequests {
            override fun asked(kind: ChooserKind, asker: Any?) = Unit
            override fun activeAsker(): Any? = null
        }
    }
}

/** What a chooser answer does to the window that asked; the production one wraps that window's page manager. */
@kotlin.native.HiddenFromObjC
interface ChooserTarget {
    /** Android's `KeyChooserResults.applyChosenDocument`. */
    fun applyDocument(bookInitials: String?)

    /** Android's `applyChosenVerse` for a passage-grid answer (a malformed OSIS id only toasts). */
    fun applyVerse(osisId: String)

    /** Android's `KeyChooserResults.apply` for a general-book, map or dictionary key. */
    fun applyKey(result: KeyChooserResult)

    /** Android's `KeyChooserResults.openMyDocumentPage` / the My Documents arm; a null [pageKey] opens the document alone. */
    fun openMyDocumentPage(documentInitials: String, pageKey: String?)

    /** The chooser was left without an answer: a page left without a key goes back in history (Android's cancel guard). */
    fun cancelled()
}

/**
 * Applies the answers of the document, key, passage and My Documents choosers to the window that asked, as Android's
 * `ReadingNavDeps.results` collectors do through `ReadingCommands`/`KeyChooserResults`. The navigator records each
 * request with the asking window ([asked]); the collectors ([collectors], composed by the reading destination) claim an
 * answer only for a recorded request and drop an unasked one (logged). The answer goes to the window that ASKED, also
 * when another window became active while the chooser was open; a request that was never answered, because the chooser
 * was left, is closed by [readingReached] (Android's cancel arm).
 *
 * [targetFor] turns an asker into its [ChooserTarget] (null: that window is gone, the answer is dropped, logged).
 * Main thread only (composition effects and the destination listener).
 */
@kotlin.native.HiddenFromObjC
class IosReadingResults(
    private val keyChooser: NavResultChannel<KeyChooserResult>,
    private val passage: NavResultChannel<PassageResult>,
    private val document: NavResultChannel<DocumentResult>,
    private val myDocumentPages: NavResultChannel<MyDocumentPagesResult>,
    private val myDocuments: NavResultChannel<MyDocumentsResult>,
    private val targetFor: (asker: Any?) -> ChooserTarget?,
    private val activeAskerProvider: () -> Any?,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : ChooserRequests {
    // Keyed by kind: a second request of the same kind replaces the first asker (as on Android).
    private val open = mutableMapOf<ChooserKind, Any?>()

    override fun asked(kind: ChooserKind, asker: Any?) {
        open[kind] = asker ?: activeAskerProvider()
    }

    override fun activeAsker(): Any? = activeAskerProvider()

    /** Whether [kind] has an unanswered request. */
    internal fun isAwaiting(kind: ChooserKind): Boolean = open.containsKey(kind)

    /** The asker of [kind]'s request, forgotten in the same breath (an answer is spent once). */
    private fun claim(kind: ChooserKind): Pair<Boolean, Any?> {
        if (!open.containsKey(kind)) return false to null
        return true to open.remove(kind)
    }

    private fun <T> deliver(kind: ChooserKind, result: T, apply: (ChooserTarget, T) -> Unit) {
        val (found, asker) = claim(kind)
        if (!found) return dropped(kind)
        val target = targetFor(asker)
        if (target == null) log("A $kind answer arrived but the window that asked is gone; not applied.") else apply(target, result)
    }

    private fun dropped(kind: ChooserKind) =
        log("A $kind answer reached the reading destination that nothing asked for. Dropped.")

    internal fun onKeyChooser(result: KeyChooserResult) = deliver(ChooserKind.KeyChooser, result) { t, r -> t.applyKey(r) }

    internal fun onPassage(result: PassageResult) = deliver(ChooserKind.Passage, result) { t, r -> t.applyVerse(r.verse) }

    internal fun onDocument(result: DocumentResult) = deliver(ChooserKind.Document, result) { t, r -> t.applyDocument(r.book) }

    internal fun onMyDocumentPages(result: MyDocumentPagesResult) = deliver(ChooserKind.MyDocumentPages, result) { t, r ->
        when (r) {
            is MyDocumentPagesResult.Selected -> t.openMyDocumentPage(r.documentInitials, r.pageKey)
            MyDocumentPagesResult.Saved -> Unit
            MyDocumentPagesResult.Cancelled -> t.cancelled()
        }
    }

    /**
     * The My Documents list answers the pages editor's switch-document flow as well as a direct pick, so its answer is
     * claimed by a pending list request or by an open pages request (the editor was left through the list).
     */
    internal fun onMyDocuments(result: MyDocumentsResult) {
        val kind = if (isAwaiting(ChooserKind.MyDocuments)) ChooserKind.MyDocuments else ChooserKind.MyDocumentPages
        if (kind == ChooserKind.MyDocuments) open.remove(ChooserKind.MyDocumentPages)
        deliver(kind, result) { t, r ->
            when (r) {
                is MyDocumentsResult.Selected -> t.openMyDocumentPage(r.documentInitials, r.pageKey)
                MyDocumentsResult.Saved -> Unit
                MyDocumentsResult.Cancelled -> t.cancelled()
            }
        }
    }

    /**
     * The graph is back on the reading destination: a chooser request still open with no answer pending was left
     * without choosing; it is closed and its window's cancel arm runs (Android's `claimAbandoned`).
     */
    fun readingReached() {
        val pending = mapOf(
            ChooserKind.KeyChooser to (keyChooser.pending.value != null),
            ChooserKind.Passage to (passage.pending.value != null),
            ChooserKind.Document to (document.pending.value != null),
            ChooserKind.MyDocumentPages to (myDocumentPages.pending.value != null || myDocuments.pending.value != null),
            ChooserKind.MyDocuments to (myDocuments.pending.value != null),
        )
        val abandoned = open.keys.filter { pending[it] != true }
        abandoned.forEach { kind ->
            val asker = open.remove(kind)
            targetFor(asker)?.cancelled()
        }
    }

    /** The collectors the reading destination composes (`ReadingNavDeps.results`). */
    fun collectors(): List<ReadingResultCollector<*>> = listOf(
        ReadingResultCollector(keyChooser, { isAwaiting(ChooserKind.KeyChooser) }, ::onKeyChooser) { dropped(ChooserKind.KeyChooser) },
        ReadingResultCollector(passage, { isAwaiting(ChooserKind.Passage) }, ::onPassage) { dropped(ChooserKind.Passage) },
        ReadingResultCollector(document, { isAwaiting(ChooserKind.Document) }, ::onDocument) { dropped(ChooserKind.Document) },
        ReadingResultCollector(myDocumentPages, { isAwaiting(ChooserKind.MyDocumentPages) }, ::onMyDocumentPages) { dropped(ChooserKind.MyDocumentPages) },
        ReadingResultCollector(
            myDocuments,
            { isAwaiting(ChooserKind.MyDocuments) || isAwaiting(ChooserKind.MyDocumentPages) },
            ::onMyDocuments,
        ) { dropped(ChooserKind.MyDocuments) },
    )

    private companion object {
        const val TAG = "IosReadingResults"
    }
}

/**
 * The production [ChooserTarget]: [pageManager] is the page manager of the window that asked (Android applies every
 * answer to the page's own manager, never to whichever window is active at answer time).
 */
@kotlin.native.HiddenFromObjC
class PageManagerChooserTarget(
    private val pageManager: CurrentPageManager,
    private val fakeBooks: () -> FakeBookFactory,
    private val versification: () -> org.crosswire.jsword.versification.Versification,
    private val notifier: () -> UserNotifier,
    private val verseNotFoundText: () -> String,
    private val history: () -> HistoryManager,
    private val scope: CoroutineScope,
    /** Android's `onToolbarStateMayHaveChanged()`: called after a document answer (document chooser, My Documents). */
    private val onToolbarMayHaveChanged: () -> Unit = {},
) : ChooserTarget {
    override fun applyDocument(bookInitials: String?) {
        val book = Books.installed().getBook(bookInitials) ?: fakeBooks().pseudoDocuments.first { it.initials == bookInitials }
        pageManager.setCurrentDocument(book)
        onToolbarMayHaveChanged()
    }

    override fun applyVerse(osisId: String) {
        val verse = try {
            VerseFactory.fromString(versification(), osisId)!!
        } catch (e: NoSuchVerseException) {
            notifier().showInfo(verseNotFoundText(), false)
            return
        }
        pageManager.currentPage.setKey(verse, true)
    }

    override fun applyKey(result: KeyChooserResult) {
        val json = result.bookAndKeyJson
        if (json != null) {
            val bookAndKey = BookAndKeySerialized.fromJSON(json).resolve(fakeBooks())
            pageManager.setCurrentDocumentAndKey(bookAndKey.document, bookAndKey)
            return
        }
        val initials = result.book ?: return
        val book = Books.installed().getBook(initials) ?: fakeBooks().giveDoesNotExist(initials)
        pageManager.setCurrentDocumentAndKey(book, book.getKey(result.key))
    }

    override fun openMyDocumentPage(documentInitials: String, pageKey: String?) {
        val book = Books.installed().getBook(documentInitials) ?: return
        if (pageKey == null) {
            pageManager.setCurrentDocument(book)
        } else {
            openPage(book, pageKey)
        }
        onToolbarMayHaveChanged()
    }

    /**
     * `KeyChooserResults.openMyDocumentPage`: a book's key map is a snapshot built when JSword activated it and can be out
     * of date, so a missing key rebuilds the document's map and retries once before opening the document without a key.
     */
    private fun openPage(book: Book, pageKey: String) {
        val key = try {
            book.getKey(pageKey)
        } catch (e: NoSuchKeyException) {
            Log.w("IosReadingResults", "Page key '$pageKey' missing from ${book.initials} key map, rebuilding it", e)
            scope.launch {
                MyDocumentBookManager.refreshDocument(book.initials)
                try {
                    pageManager.setCurrentDocumentAndKey(book, book.getKey(pageKey))
                } catch (e2: NoSuchKeyException) {
                    Log.e("IosReadingResults", "Page key '$pageKey' not found in ${book.initials}, opening book without key", e2)
                    pageManager.setCurrentDocument(book)
                }
            }
            return
        }
        pageManager.setCurrentDocumentAndKey(book, key)
    }

    override fun cancelled() {
        if (pageManager.currentPage.key == null) history().goBack()
    }
}
