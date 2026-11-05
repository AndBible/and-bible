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
import net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.database.IdType
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedui.reading.bridge.ReadingKeyActions
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.android.control.page.Document
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.PromptContext
import org.koin.mp.KoinPlatform
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange

/**
 * Platform-independent state and seams of one reading window (what used to live directly in Android's
 * `BibleView`). Controllers are passed as providers so `:sharedUi` tests can leave them unresolved;
 * `:app` passes its own instances.
 *
 * [window] is rebound by the owner when window objects are re-created from the database.
 */
@kotlin.native.HiddenFromObjC
class BibleWindowSession(
    window: Window?,
    val jsChannel: BibleViewJsChannel,
    hostScope: () -> CoroutineScope,
    val surface: ReadingWindowSurface,
    val navigator: ReadingNavigator,
    windowControlProvider: () -> WindowControl = { KoinPlatform.getKoin().get() },
    bookmarkControlProvider: () -> BookmarkControl = { KoinPlatform.getKoin().get() },
    linkControlProvider: () -> LinkControl = { KoinPlatform.getKoin().get() },
    downloadControlProvider: () -> DownloadControl = { KoinPlatform.getKoin().get() },
    searchControlProvider: () -> SearchControl = { KoinPlatform.getKoin().get() },
    pageControlProvider: () -> PageControl = { KoinPlatform.getKoin().get() },
) {
    private val hostScopeProvider = hostScope

    private val sessionScope = lazy {
        val parent = hostScopeProvider()
        CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    }

    /**
     * The host's own scope (Android: the activity's lifecycle scope). It is NOT cancelled by [dispose], so
     * use it only for a user-visible flow that must finish its data write even if the view is torn down
     * meanwhile (e.g. the label chooser). Everything else belongs on [scope].
     */
    val hostLifetimeScope: CoroutineScope get() = hostScopeProvider()

    /**
     * Session-owned scope: a child of the host scope that [dispose] cancels. Resolved on first use
     * because Android's lifecycle scope is not available while the view is still being constructed.
     * After [dispose] this is a cancelled scope, so a late launch never runs.
     */
    val scope: CoroutineScope
        get() = if (isDisposed) cancelledScope else sessionScope.value

    private val cancelledScope: CoroutineScope by lazy { CoroutineScope(Job().apply { cancel() }) }

    val windowControl: WindowControl by lazy(windowControlProvider)
    val bookmarkControl: BookmarkControl by lazy(bookmarkControlProvider)
    val linkControl: LinkControl by lazy(linkControlProvider)
    val downloadControl: DownloadControl by lazy(downloadControlProvider)
    val searchControl: SearchControl by lazy(searchControlProvider)
    val pageControl: PageControl by lazy(pageControlProvider)

    private var windowOrNullField: Window? = window

    /** The window this session shows (rebound by the owner when the window object is re-created). */
    var window: Window
        get() = windowOrNullField!!
        set(value) {
            windowOrNullField = value
        }

    val windowOrNull: Window? get() = windowOrNullField

    var firstDocument: Document? = null

    /** Setting the initial key also resets [firstKey] and [lastKey]. */
    var initialKey: Key? = null
        set(value) {
            firstKey = value
            lastKey = value
            field = value
        }
    var firstKey: Key? = null
    var lastKey: Key? = null

    /**
     * The generation of the document [DocumentLoader] last admitted ([firstDocument]); [NO_DOCUMENT_GENERATION] when
     * none is (also while the view is parked for reuse). It is sent to JS with the document's `clear_document`, and JS
     * echoes it with every bridge call; the dispatcher drops guarded calls of any other generation (osTicket 3508, #3975).
     */
    @Volatile var documentGeneration: Long = NO_DOCUMENT_GENERATION

    /**
     * The document and window this session holds now. A guarded bridge call that acts later (after queued writes, an
     * IO read, a hop to the main thread) takes one when it runs and acts only if [holdsDocument] still holds then: a
     * navigation, a new document request or a view rebind in between makes it stale (#3975). A native caller (selection
     * menu, hardware key) takes one the same way.
     */
    /**
     * The generation of the document the page reports it shows (`documentShown`, sent once the content of that
     * generation's `clear_document` has replaced the old one in the DOM); [NO_DOCUMENT_GENERATION] for a (re)started
     * page that shows none yet. A link navigation carries no generation, so it is admitted only while the page shows
     * the admitted document ([pageShowsAdmittedDocument]).
     */
    @Volatile var shownGeneration: Long = NO_DOCUMENT_GENERATION

    /**
     * Whether what the page shows is the document whose calls the window admits now: false for a page that still
     * shows a replaced document (also an adopted or parked view's old page), and while a new document is requested.
     */
    fun pageShowsAdmittedDocument(): Boolean =
        !isDisposed && shownGeneration != NO_DOCUMENT_GENERATION && shownGeneration == documentGeneration

    fun documentTicket(): DocumentTicket = DocumentTicket(documentGeneration, windowOrNull)

    /** Whether [ticket]'s document is still the one this session's window holds (see [documentTicket]). */
    fun holdsDocument(ticket: DocumentTicket): Boolean =
        !isDisposed && ticket.generation == documentGeneration && windowOrNull === ticket.window

    @Volatile var minChapter: Int = -1
    @Volatile var maxChapter: Int = -1

    var modalOpen: Boolean = false
    var editingTextInJs: Boolean = false
    var contentVisible: Boolean = false

    val workspaceSettings get() = windowControl.windowRepository.workspaceSettings

    /** Set by the owner; [BibleView] implements it until paging moves here. */
    lateinit var paging: SessionPaging

    /** Set by the owner; handles keyboard shortcuts that need the host. */
    var keyActions: ReadingKeyActions? = null

    /** Set by the reading host. */
    var hostSheets: ReadingSheets? = null

    /** Set by the reading host; a null result means the chooser was dismissed. */
    var verseChooser: (suspend () -> String?)? = null

    @Volatile var isDisposed: Boolean = false
        private set

    /** Sends [call] to JS; false if dropped (session disposed, or the channel dropped it). */
    fun emit(call: JsCall): Boolean = !isDisposed && jsChannel.execute(call)

    fun hasChapterLoaded(chapter: Int): Boolean = chapter in minChapter..maxChapter

    /** Cancels session jobs; later [emit] calls are dropped. */
    fun dispose() {
        isDisposed = true
        if (sessionScope.isInitialized()) sessionScope.value.cancel()
    }
}

/** Paging requests coming from JS. */
@kotlin.native.HiddenFromObjC
interface SessionPaging {
    fun requestMoreToBeginning(callId: Long)
    fun requestMoreToEnd(callId: Long)
    fun setClientReady()
    fun parseRef(callId: Long, s: String)

    /**
     * Runs a chapter request ([call]) of the page whose document has [generation] if that is the document the page
     * holds; waits while the window has requested a newer one (#3975). Exactly one of [call] and [drop] runs, once. The
     * default drops: a paging seam that cannot tell the page's document fails closed.
     */
    fun runForPageDocument(generation: Long?, call: () -> Unit, drop: () -> Unit) = drop()
}

/** Sheets the reading host shows on behalf of the session. */
@kotlin.native.HiddenFromObjC
interface ReadingSheets {
    fun showReadHistory(bookId: String, chapter: Int)
    fun showCompare(range: VerseRange)
    fun showShare(input: ShareVersesInput)
    fun showPromptSelector(selection: Selection, context: PromptContext, category: DocumentCategory?)
    /** [windowId] is the window whose page asked, so the host regenerates against THAT window's view. */
    fun showRegenerate(pageId: IdType, windowId: IdType)
    fun showHelp(title: String?, html: String)

    /** Asks whether the AI document page should be deleted; [onConfirm] runs only on "yes". */
    fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit)
}

/** See [BibleWindowSession.documentTicket]. */
@kotlin.native.HiddenFromObjC
class DocumentTicket internal constructor(val generation: Long, val window: Window?)
