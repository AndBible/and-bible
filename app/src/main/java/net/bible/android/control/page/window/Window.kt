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

package net.bible.android.control.page.window

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.bible.sharedcore.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.sharedcore.event.Subscription
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange

class Window (
    entity: WorkspaceEntities.Window,
    val pageManager: CurrentPageManager,
    val windowRepository: WindowRepository,
    var isLinksWindow: Boolean = entity.isLinksWindow,
) : KoinComponent {
    private val advancedSpeakSettings: AdvancedSpeakSettings by inject()
    private val strings: net.bible.sharedcore.platform.CoreStrings by inject()
    private var targetLinksWindowId: IdType? = entity.targetLinksWindowId
    var savedEntity = entity.deepCopy()
    var syncGroup = entity.syncGroup

    val targetLinksWindow: Window
        get() {
            return ownTargetLinksWindow
                ?: if (isLinksWindow) windowRepository.addNewLinksWindow().also {
                    targetLinksWindowId = it.id
                    windowRepository.notifyWindowsChanged()
                } else windowRepository.primaryTargetLinksWindow
        }

    private val ownTargetLinksWindow get() = windowRepository.getWindow(targetLinksWindowId)
    val linksWindowNumber: Int get() {
        val target = ownTargetLinksWindow
        return if(target == null) 0
        else {
            target.linksWindowNumber + 1
        }
    }
    val isPrimaryLinksWindow get() = isLinksWindow && id == windowRepository.primaryTargetLinksWindowId

    val id = entity.id
    val displayId = id.toString().substring(0, 5)

    /**
     * Unpinned regular windows share one size slot (`WindowRepository.unPinnedWeight`): only one
     * of them is visible at a time (`WindowControl.restoreWindow` minimises the others), and the
     * one shown inherits the slot's size. A links window is shown BESIDE that slot, never instead
     * of it, so it keeps its own weight even when unpinned (always, with autoPin off) -- sharing
     * the slot made a separator drag between the two write it twice and snap back on release.
     */
    private val usesUnpinnedSlot get() = !isPinMode && !isLinksWindow

    var weight: Float
        get() =
            if(usesUnpinnedSlot) {
                if(windowRepository.unPinnedWeight == null) {
                    windowRepository.unPinnedWeight = windowLayout.weight
                }
                windowRepository.unPinnedWeight!!
            }
            else windowLayout.weight
        set(value) {
            if(usesUnpinnedSlot)
                windowRepository.unPinnedWeight = value
            else
                windowLayout.weight = value
        }

    private val windowLayout: WindowLayout = WindowLayout(entity.windowLayout)
    private var workspaceId = entity.workspaceId
    private val speakSubscription: Subscription

    private val documentUpdates: Subscription = MyDocumentBookManager.changes.subscribe { change ->
        if (change !is MyDocumentChange.DocumentUpdated) return@subscribe
        val doc = displayedBook ?: return@subscribe
        if (doc.isMyDocument && doc.initials == change.initials) {
            SwordContentFacade.evictBook(change.initials)
            loadText()
        }
    }

    init {
        pageManager.window = this
        speakSubscription = SpeakChanges.changes.subscribe { change ->
            val e = change as? SpeakChange.Progress ?: return@subscribe
            if(advancedSpeakSettings.synchronize || e.forceFollow) return@subscribe // handled in SpeakControl
            val speakKey = (e.key as? BookAndKey)?.key?: e.key
            val bookInitials = e.book.initials
            if (displayedBook != e.book) return@subscribe

            when(e.book.bookCategory) {
                BookCategory.COMMENTARY -> {
                    if (e.key !is BookAndKey) return@subscribe
                    val curPage = pageManager.currentPage
                    val commentaryRange = (curPage as? CurrentCommentaryPage)?.annotateKey
                    val osisRef = (commentaryRange ?: speakKey).getOsisRef()
                    bibleView?.highlightOrdinalRange(
                        bookInitials,
                        osisRef,
                        e.key.ordinal!!.start..(e.key.ordinal.end ?: e.key.ordinal.start)
                    )
                }
                BookCategory.BIBLE -> {
                    val loadedRange = bibleView?.verseRangeLoaded?: return@subscribe
                    if (!loadedRange.contains(speakKey)) return@subscribe

                    val range = e.key as? VerseRange ?: return@subscribe
                    bibleView?.highlightBibleOrdinalRange(range.start.ordinal .. range.end.ordinal)
                }
                else -> {
                    if(e.key is BookAndKey) {
                        bibleView?.highlightOrdinalRange(
                            bookInitials,
                            e.key.key.getOsisRef(),
                            e.key.ordinal!!.start .. (e.key.ordinal.end ?: e.key.ordinal.start)
                        )
                    }
                }
            }
        }
    }

    val entity get () =
        WorkspaceEntities.Window(
            workspaceId = workspaceId,
            isSynchronized = isSynchronised,
            isPinMode = isPinMode,
            windowLayout = WorkspaceEntities.WindowLayout(windowLayout.state.toString(), windowLayout.weight),
            id = id,
            targetLinksWindowId = targetLinksWindowId,
            isLinksWindow = isLinksWindow,
            syncGroup = syncGroup
        )

    private var displayedKey: Key? = null
    private var displayedBook: Book? = null

    var isSynchronised = entity.isSynchronized
        set(value) {
            field = value
            windowRepository.notifyWindowChanged(this)
        }

    var isPinMode: Boolean = entity.isPinMode
        get() {
            return when {
                isLinksWindow -> windowRepository.workspaceSettings.autoPin
                windowRepository.workspaceSettings.autoPin -> true
                else -> field
            }
        }
        set(value) {
            field = value
            windowRepository.notifyWindowChanged(this)
        }

    val isMinimised: Boolean
        get() = windowLayout.state == WindowState.MINIMISED

    val isSyncable: Boolean
        get() = pageManager.currentPage.isSyncable

    var windowState: WindowState
        get() = windowLayout.state
        set(value) {
            windowLayout.state = value
        }

    val isVisible: Boolean
        get() =
            if(windowRepository.isMaximized && windowRepository.maximizedWindow?.targetLinksWindowId != id)
                windowRepository.maximizedWindowId == id
            else windowLayout.state != WindowState.MINIMISED && windowLayout.state != WindowState.CLOSED


    @Volatile
    var bibleView: WindowView? = null

    /** Releases this owner even when its view will be rebound to a replacement window. */
    fun destroy(destroyView: Boolean = true) {
        speakSubscription.cancel()
        documentUpdates.cancel()
        pageManager.destroy()
        if (destroyView) bibleView?.destroy()
    }

    override fun toString(): String = "Window[${displayId}]"

    var lastUpdated
        get() = bibleView?.lastUpdated ?: 0L
        set(value) {
            bibleView?.lastUpdated = value
        }

    val initialized get() = lastUpdated != 0L
    private val updateScope get() = windowRepository.scope

    private val contentLoadSequence = java.util.concurrent.atomic.AtomicLong()
    internal val contentLoadGeneration: Long get() = contentLoadSequence.get()

    fun loadText(notifyLocationChange: Boolean = false) {
        val isVisible = isVisible

        Log.i(TAG, "updateText, isVisible: $isVisible")

        if(!isVisible) return

        Log.i(TAG, "Loading OSIS xml in background")
        var anchorOrdinal: OrdinalRange? = null
        val generation = contentLoadSequence.incrementAndGet()
        val currentPage = pageManager.currentPage
        val sourceDocument = currentPage.currentDocument
        val sourceKey = currentPage.key?.getOsisRef()
        suspend fun isCurrentRequest(): Boolean {
            // key can enter an independent blockingAtEdge bridge; do not start it after cancellation.
            currentCoroutineContext().ensureActive()
            return generation == contentLoadGeneration &&
                pageManager.currentPage === currentPage && currentPage.currentDocument === sourceDocument &&
                currentPage.key?.getOsisRef() == sourceKey
        }
        val key = if(listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE).contains(currentPage.documentCategory)) {
            pageManager.currentBibleVerse.verse
        } else {
            anchorOrdinal = currentPage.anchorOrdinal
            pageManager.currentPage.key
        }
        val htmlId = if(currentPage.currentDocument?.isEpub == true) {
            currentPage.htmlId
        } else {
            null
        }

        displayedBook = currentPage.currentDocument
        displayedKey = currentPage.key
        Log.i(TAG, "updateText ${this.hashCode()}")

        updateScope.launch(Dispatchers.IO) {
            val b = bibleView
            val adjusted = b?.adjustLoadingCount(1)?: false
            val doc = try {
                // The request owns the source before the first suspending page read.
                if (!isCurrentRequest()) return@launch
                fetchDocument(currentPage)
            } finally {
                if (adjusted) b?.adjustLoadingCount(-1)
            }
            if (!isCurrentRequest()) return@launch

            // null means request was superseded - don't load anything
            if (doc == null) {
                if(notifyLocationChange)
                    PassageChangeMediator.contentChangeFinished()
                return@launch
            }

            val checksum = if(pageManager.isCommentaryShown && doc is OsisDocument) {
                val checksum = doc.osisFragment.xmlStr.hashCode()
                if (lastChecksum == checksum && bibleView?.firstDocument != null) {
                    pageManager.currentCommentary.anchorOrdinal = pageManager.currentCommentary._anchorOrdinal
                    return@launch
                }
                checksum
            } else -1

            // BibleView initialization might take more time than loading OSIS, so let's wait for it.
            val viewReady = waitForBibleView()
            if (!isCurrentRequest()) return@launch

            lastUpdated = System.currentTimeMillis()
            lastChecksum = checksum

            onLoadDelivered(viewReady)
            bibleView?.loadDocument(doc, updateLocation = notifyLocationChange, key = key, anchorOrdinal = anchorOrdinal, htmlId = htmlId, windowContentGeneration = generation)

            if(notifyLocationChange)
                PassageChangeMediator.contentChangeFinished()
            }
        }

    private var lastChecksum = 0

    /**
     * True while this window's last [loadText] was dropped because no BibleView appeared within
     * [bibleViewWaitTimeoutMillis] (e.g. the reading destination was not composed after a database
     * restore). `BibleViewFactory` re-runs [loadText] when it creates the next view. Cleared by
     * [takeLoadPending] and by any successful document load.
     */
    private val loadPendingFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    var loadPending: Boolean
        get() = loadPendingFlag.get()
        internal set(value) = loadPendingFlag.set(value)

    /** Returns [loadPending] and clears it, atomically. */
    fun takeLoadPending(): Boolean = loadPendingFlag.getAndSet(false)

    /** A load that reached a BibleView supersedes any dropped one. */
    internal fun onLoadDelivered(viewReady: Boolean) {
        if (viewReady) loadPending = false
    }

    /** How long [waitForBibleView] polls before giving up; a var so tests can shorten it. */
    internal var bibleViewWaitTimeoutMillis = 5000L

    /** Waits for a BibleView; on timeout sets [loadPending] and returns false. */
    internal suspend fun waitForBibleView(): Boolean {
        var time = 0L
        val delayMillis = 50L
        while(bibleView == null) {
            delay(delayMillis)
            time += delayMillis
            if(time > bibleViewWaitTimeoutMillis) {
                // Set the flag first, then re-check: a view created in between either sees the flag
                // (and reloads) or is seen here (and the flag is cleared again).
                loadPendingFlag.set(true)
                if (bibleView != null) {
                    loadPendingFlag.set(false)
                    return true
                }
                Log.e(TAG, "waitForBibleView timed out")
                return false
            }
        }
        return true
    }

    fun updateText() {
        val document = pageManager.currentPage.currentDocument
        val verse = pageManager.currentVersePage.currentBibleVerse.verse
        val book = pageManager.currentVersePage.currentBibleVerse.currentBibleBook
        val key = pageManager.currentPage.singleKey

        val documentChanged = displayedBook != document

        if(documentChanged) {
            loadText(notifyLocationChange = true)
            return
        }

        val prevKey = displayedKey

        if( pageManager.isBibleShown
            && prevKey is VerseRange
            && prevKey.start.book == book
            && hasChapterLoaded(verse.chapter)
        ) {
            val originalKey = pageManager.currentBible.originalKey
            bibleView?.scrollOrJumpToVerse(originalKey ?: verse)
            PassageChangeMediator.contentChangeFinished()
            return
        }
        val anchorOrdinal = pageManager.currentPage.anchorOrdinal
        val htmlId = pageManager.currentPage.htmlId
        if(prevKey == key && (anchorOrdinal != null || htmlId != null)) {
            bibleView?.scrollOrJumpToOrdinal(anchorOrdinal, htmlId, document?.initials, pageManager.currentPage.displayKey?.getOsisRef())
            return
        }
        loadText(notifyLocationChange = true)
    }

    private suspend fun fetchDocument(currentPage: net.bible.android.control.page.CurrentPage): Document? = withContext(Dispatchers.IO) {
        return@withContext try {
            val document = currentPage.currentDocument
            Log.i(TAG, "Loading document:$document key:${currentPage.key}")
            currentPage.currentPageContent()
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error", oom)
            System.gc()
            ErrorDocument(strings.errorPageTooLarge, ErrorSeverity.ERROR)
        }
    }

    fun hasChapterLoaded(chapter: Int): Boolean {
        return bibleView?.hasChapterLoaded(chapter) == true
    }

    fun updateOrScroll() {
        if((displayedKey != pageManager.currentPage.key
            || displayedBook != pageManager.currentPage.currentDocument)
            || (windowRepository.windowSync.lastForceSyncAll > lastUpdated)
        ) {
            loadText()
        } else {
            scrollToText()
        }
    }

    private fun scrollToText() {
        val currentPage = pageManager.currentPage
        if(listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE).contains(currentPage.documentCategory)) {
            bibleView?.scrollOrJumpToVerse(
                pageManager.currentBibleVerse.verse,
                true
            )
        } else {
            bibleView?.scrollOrJumpToOrdinal(
                currentPage.anchorOrdinal,
                currentPage.htmlId,
                currentPage.currentDocument?.initials,
                currentPage.displayKey?.getOsisRef(),
                true
            )
        }

    }

    private val TAG get() = "BibleView[${displayId}] WIN"
}
