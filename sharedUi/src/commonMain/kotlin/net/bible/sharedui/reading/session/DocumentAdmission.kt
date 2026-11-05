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

package net.bible.sharedui.reading.session

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineStart
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.common.toV11n
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.VersePage
import net.bible.android.control.page.window.Window
import net.bible.android.database.bookmarks.KJVA
import net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook

/**
 * What a window shows, as far as admission needs it: the window, its current page object, the module, and the
 * document context inside the module ([context]). Immutable: read from the window once ([of]) and compared later (#3975).
 *
 * Contexts: a Bible page's book + chapter in the MODULE's versification (the window's verse may be in another one, the
 * page's content is not), a MyNotes page's in KJVA (its document's); a book introduction (chapter 0) belongs to
 * chapter 1, which loads it. A commentary page's Bible book. Every other page (general book, EPUB, MyDocument / AI
 * page, StudyPad, multi-reference, dictionary, map) is its key: an entry that replaces the page is another document,
 * and infinite scroll adds the entries it brings in ([DocumentAdmission.addContext]).
 */
@kotlin.native.HiddenFromObjC
class DocumentSource(val window: Window?, val page: CurrentPage?, val book: Book?, val context: String?) {
    override fun toString() = "DocumentSource(${window?.displayId}, ${book?.initials}, $context)"

    companion object {
        /** What [window] shows now. Reads the window's page state on the calling thread. */
        fun of(window: Window?): DocumentSource {
            window ?: return DocumentSource(null, null, null, null)
            val page = window.pageManager.currentPage
            return DocumentSource(window, page, page.currentDocument, contextOf(window, page))
        }

        /** A Bible chapter's context; the book introduction (chapter 0) is part of chapter 1's document. */
        fun chapterContext(book: BibleBook, chapter: Int) = "${book.osis}.${maxOf(chapter, 1)}"

        /** The chapter context of [verse], in the versification [verse] is in. */
        fun chapterContext(verse: Verse) = chapterContext(verse.book, verse.chapter)

        /**
         * Contexts of a single-chapter Bible / MyNotes load, including chapters introduced by versification conversion.
         * Same-book ranges are enumerated fully. A cross-book range has only its endpoints represented: conversion can
         * cross books (e.g. Catholic Daniel 3 maps partly to PrAzar; see Catholic.properties), so this is defensive,
         * not a complete enumeration of an arbitrary cross-book range.
         */
        fun chapterContexts(range: VerseRange): Set<String?> {
            val start = range.start
            val end = range.end
            if (start.book != end.book) return setOf(chapterContext(start), chapterContext(end))
            return (maxOf(start.chapter, 1)..maxOf(end.chapter, 1)).mapTo(mutableSetOf()) { chapterContext(start.book, it) }
        }

        private fun contextOf(window: Window, page: CurrentPage): String? = try {
            when (page.documentCategory) {
                // The page's content is in the module's versification; the window's verse may not be (a module
                // switch keeps the verse of the previous module's versification).
                DocumentCategory.BIBLE -> chapterContext(window.pageManager.currentBibleVerse.getVerseSelected((page as VersePage).versification))
                DocumentCategory.MYNOTE -> chapterContext(window.pageManager.currentBibleVerse.verse.toV11n(KJVA))
                DocumentCategory.COMMENTARY -> page.key?.let { KeyUtil.getVerse(it).book.osis }
                else -> page.key?.getOsisRef()
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Admission of one window's page: which bridge calls, links and chapter requests of the page may act on the window
 * (osTicket 3508, #3975). It is ONE state ([State]) that only the main thread replaces, holding
 *
 * - the admitted document: its generation (sent with its `clear_document`, echoed by every page call), its immutable
 *   [DocumentSource] and the contexts the page holds (the loaded chapter plus what infinite scroll added);
 * - the pending request of another document or key, if any, and whether it changes the document;
 * - the generation the page reports it shows (`documentShown`).
 *
 * Everything else is derived from that state when a call is about to act, on the main thread ([admits]): the call's
 * generation must be the admitted one, no request of a different document may be pending, and the window must still
 * show the admitted source ([DocumentSource.of] compared with it). The last check is what makes a page change that
 * has not been requested yet (an off-main sync or restore, the gap between `setCurrentDocument` and `loadText`) safe.
 *
 * Chapter requests ([runPaging]) that cannot run yet wait in [waitingPaging] and are settled by the next transition:
 * they run when the page's document stands again, and are dropped (answered null) when a new document replaces it.
 */
@kotlin.native.HiddenFromObjC
class DocumentAdmission internal constructor(private val session: BibleWindowSession) {
    /**
     * The document the page holds and the contexts in it: [loaded] are those of the document as delivered (what a
     * restarted page is sent again), [contexts] adds what infinite scroll brought in. [isError]: an error document
     * standing for [source] after its load failed.
     */
    class Admitted(
        val generation: Long,
        val source: DocumentSource,
        val contexts: Set<String?>,
        val loaded: Set<String?> = contexts,
        val isError: Boolean = false,
    ) {
        fun withContexts(contexts: Set<String?>) = Admitted(generation, source, contexts, loaded, isError)

        /** Whether [target] is (still) shown by this document: same window, page object, module and a loaded context. */
        fun shows(target: DocumentSource): Boolean =
            target.window === source.window && target.page === source.page && target.book == source.book &&
                target.context in contexts
    }

    /** A request ([request]) of [target]; [changesDocument] when the page's document does not show it. */
    class Pending(val token: Long, val target: DocumentSource, val changesDocument: Boolean)

    class State(val admitted: Admitted? = null, val pending: Pending? = null, val shown: Long = NO_DOCUMENT_GENERATION)

    /** Replaced only on the main thread; any thread may read a consistent snapshot. */
    @Volatile var state: State = State()
        private set

    /**
     * The generation guarded calls are admitted with right now ([NO_DOCUMENT_GENERATION] while none is, also while a
     * request of a different document is pending); derived from [state]. The dispatcher's first, off-main check.
     */
    val generation: Long get() = state.let { s ->
        val admitted = s.admitted ?: return NO_DOCUMENT_GENERATION
        if (s.pending?.changesDocument == true) NO_DOCUMENT_GENERATION else admitted.generation
    }

    /** The generation of the document the page holds or is about to receive, even while another one is requested. */
    val admittedGeneration: Long get() = state.admitted?.generation ?: NO_DOCUMENT_GENERATION

    private val requestTokens = atomic(0L)

    /** A waiting chapter request; main thread only. */
    private class WaitingPaging(val generation: Long, val call: () -> Unit, val drop: () -> Unit)
    private val waitingPaging = mutableListOf<WaitingPaging>()
    private var recheckJob: kotlinx.coroutines.Job? = null
    private var recheckDelay = PAGING_RECHECK

    private fun resetRecheck() {
        recheckJob?.cancel()
        recheckJob = null
        recheckDelay = PAGING_RECHECK
    }

    /** Set by [DocumentLoader]: shows an error document for [target] after its request failed (main thread). */
    internal var onLoadFailed: (target: DocumentSource, failure: Throwable) -> Unit = { _, _ -> }

    /**
     * Runs [block] on the main thread, once, even if the session's scope is cancelled meanwhile. Called there, it
     * usually runs inline (`Main.immediate`); inside another immediate dispatch it may be queued behind it. From
     * another thread it is posted, in order.
     */
    internal fun onMain(block: () -> Unit) {
        session.scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.ATOMIC) { block() }
    }

    private fun live() = DocumentSource.of(session.windowOrNull)

    /**
     * Main thread: whether a guarded call of [generation] may act now (see the class comment). A scroll report is
     * stricter: it also waits for no request at all (`ReadingBridge.scrolledToOrdinal`).
     */
    fun admits(generation: Long?): Boolean {
        if (session.isDisposed || generation == null || generation == NO_DOCUMENT_GENERATION) return false
        val s = state
        val admitted = s.admitted ?: return false
        return generation == admitted.generation && s.pending?.changesDocument != true && admitted.shows(live())
    }

    /** Main thread: whether the admitted document (if any) shows what the window shows now. */
    fun showsWindow(): Boolean = state.admitted?.shows(live()) ?: true

    /** Main thread: whether the page reports it shows the document whose calls are admitted now (links, selection). */
    fun pageShowsAdmittedDocument(): Boolean {
        val shown = state.shown
        return shown != NO_DOCUMENT_GENERATION && admits(shown)
    }

    /** The page reports it shows [generation] (`documentShown`, from the bridge thread). */
    fun shown(generation: Long) = onMain {
        state = State(state.admitted, state.pending, generation)
    }

    /**
     * Main thread: the page (re)started; it shows nothing until it reports the document it is sent. It is sent the
     * admitted document as delivered, so the contexts infinite scroll added are gone with the old page.
     */
    fun pageRestarted() {
        val admitted = state.admitted
        state = State(admitted?.withContexts(admitted.loaded), state.pending, NO_DOCUMENT_GENERATION)
        // Calls belong to the old JS page; even answering them would target the new page's call IDs.
        waitingPaging.clear()
        resetRecheck()
    }

    /**
     * The window requests a document or key ([net.bible.android.control.page.window.WindowView.documentRequested]).
     * What it requests is read now, on the calling thread, right after the key change; the state changes on the main
     * thread (inline there, which is the usual case). Returns the token for [requestEnded].
     */
    fun request(): Long {
        val token = requestTokens.incrementAndGet()
        val target = live()
        onMain {
            val admitted = state.admitted
            state = State(admitted, Pending(token, target, admitted?.shows(target) != true), state.shown)
        }
        return token
    }

    /**
     * The request [token] ended without admitting a document, or after it (then a newer state already replaced it).
     * [unchanged]: the page's content stands for the request's target (unchanged commentary). [failure]: it failed; if
     * it was a request of a different document, or the page shows an error document (a retry that failed again), an
     * error document with a retry replaces the page's ([onLoadFailed]), so the window is never left silently closed. Otherwise the page's document is admitted again if the window still
     * shows it; if not (a newer page change is on its way) the page stays closed until the next transition.
     */
    fun requestEnded(token: Long, unchanged: Boolean, failure: Throwable?) = onMain {
        val pending = state.pending
        if (pending == null || pending.token != token) return@onMain
        var admitted = state.admitted
        if (admitted != null && unchanged) {
            admitted = if (pending.target.window === admitted.source.window && pending.target.page === admitted.source.page &&
                pending.target.book == admitted.source.book) {
                admitted.withContexts(admitted.contexts + pending.target.context)
            } else {
                Admitted(admitted.generation, pending.target, setOf(pending.target.context))
            }
        }
        state = State(admitted, null, state.shown)
        if (failure != null && (pending.changesDocument || admitted?.isError == true)) {
            Log.e(TAG, "Load of ${pending.target} failed; showing an error document", failure)
            onLoadFailed(pending.target, failure)
        } else if (admitted != null && !admitted.shows(live())) {
            Log.w(TAG, "Request ended without a document; ${admitted.source} not shown by the window, closed until the next load")
        }
        settleWaitingPaging()
    }

    /**
     * Main thread: [generation], loaded from [source], replaces the page's document; [contexts] are the ones it holds
     * (a Bible document: the chapters of its range). [isError]: an error document standing for [source]. Waiting
     * chapter requests drop.
     */
    fun admit(generation: Long, source: DocumentSource, contexts: Set<String?> = setOf(source.context), isError: Boolean = false) {
        state = State(Admitted(generation, source, contexts, isError = isError), null, state.shown)
        dropWaitingPaging()
    }

    /**
     * Main thread, for tests that do not drive a real load: admits [generation] for what the window shows now and, if
     * [shown], records that the page shows it (as a delivered load plus the page's `documentShown` would).
     */
    fun admitCurrentForTest(generation: Long, shown: Boolean = false) {
        admit(generation, live())
        if (shown) state = State(state.admitted, state.pending, generation)
    }

    /** Main thread: the page's view was handed to another window; nothing of the old page is admitted any more. */
    fun forget() {
        state = State(null, null, state.shown)
        dropWaitingPaging()
    }

    /** Main thread: infinite scroll added [context] to the page of [generation]. */
    fun addContext(generation: Long, context: String?) {
        val admitted = state.admitted ?: return
        if (admitted.generation != generation) return
        state = State(admitted.withContexts(admitted.contexts + context), state.pending, state.shown)
        settleWaitingPaging()
    }

    /** Main thread: the read of [context], reserved by [addContext], failed; the page does not hold it. */
    fun removeContext(generation: Long, context: String?) {
        val admitted = state.admitted ?: return
        if (admitted.generation != generation || context in admitted.loaded) return
        state = State(admitted.withContexts(admitted.contexts - context), state.pending, state.shown)
    }

    /**
     * A chapter request of the page of [generation] (any thread). On the main thread: dropped at once if that is not
     * the page's document; waits while a request of another document is pending or the window does not show the
     * page's document (a page change not requested yet); otherwise [call] runs there, so the check and the request's
     * synchronous effects (the chapter range, the source it reads) are one main-thread step. Exactly one of [call] and
     * [drop] runs, once.
     */
    fun runPaging(generation: Long?, call: () -> Unit, drop: () -> Unit) = onMain { evaluatePaging(generation, call, drop) }

    private fun evaluatePaging(generation: Long?, call: () -> Unit, drop: () -> Unit) {
        val s = state
        val admitted = s.admitted
        when {
            session.isDisposed || admitted == null || generation == null || generation != admitted.generation -> drop()
            s.pending?.changesDocument == true || !admitted.shows(live()) -> {
                waitingPaging += WaitingPaging(generation, call, drop)
                scheduleRecheck()
            }
            else -> call()
        }
    }

    /**
     * Waiting chapter requests are settled by every transition, and also re-checked periodically (250 ms, backing off
     * to 5 s while they keep waiting): the window can show the page's document again without any transition (an
     * off-main page change undone before its request). They are never dropped while the page's document may still
     * stand, since a null answer ends the page's infinite scroll; the next document drops them.
     */
    private fun scheduleRecheck() {
        if (recheckJob != null) return
        recheckJob = session.scope.launch(Dispatchers.Main) {
            delay(recheckDelay)
            recheckJob = null
            val nextDelay = minOf(recheckDelay * 2, MAX_PAGING_RECHECK)
            if (waitingPaging.isNotEmpty() && recheckDelay < MAX_PAGING_RECHECK && nextDelay == MAX_PAGING_RECHECK) {
                Log.w(TAG, "Waiting paging requests reached the maximum 5 second recheck interval")
            }
            recheckDelay = nextDelay
            settleWaitingPaging()
            if (waitingPaging.isEmpty()) recheckDelay = PAGING_RECHECK
        }
    }

    private fun settleWaitingPaging() {
        val waiting = waitingPaging.toList()
        waitingPaging.clear()
        waiting.forEach { evaluatePaging(it.generation, it.call, it.drop) }
        if (waitingPaging.isEmpty()) resetRecheck()
    }

    private fun dropWaitingPaging() {
        val waiting = waitingPaging.toList()
        waitingPaging.clear()
        resetRecheck()
        waiting.forEach { it.drop() }
    }

    private companion object {
        const val TAG = "DocumentAdmission"
        val PAGING_RECHECK = 250.milliseconds
        val MAX_PAGING_RECHECK = 5.seconds
    }
}
