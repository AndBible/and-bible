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
    private val sessionScope = lazy {
        val parent = hostScope()
        CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    }

    /**
     * Session-owned scope: a child of the host scope that [dispose] cancels. Resolved on first use
     * because Android's lifecycle scope is not available while the view is still being constructed.
     */
    val scope: CoroutineScope get() = sessionScope.value

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
        if (sessionScope.isInitialized()) scope.cancel()
    }
}

/** Paging requests coming from JS. */
@kotlin.native.HiddenFromObjC
interface SessionPaging {
    fun requestMoreToBeginning(callId: Long)
    fun requestMoreToEnd(callId: Long)
    fun setClientReady()
    fun parseRef(callId: Long, s: String)
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
