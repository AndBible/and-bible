/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.page.window

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.view.activity.page.BibleView
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse

class WindowChangedEvent(val window: Window)

open class Window (
    window: WorkspaceEntities.Window,
    val pageManager: CurrentPageManager,
    val windowRepository: WindowRepository
){
    var weight: Float
        get() =
            if(!isPinMode && !isLinksWindow) {
                if(windowRepository.unPinnedWeight == null) {
                    windowRepository.unPinnedWeight = windowLayout.weight
                }
                windowRepository.unPinnedWeight!!
            }
            else windowLayout.weight
        set(value) {
            if(!isPinMode && !isLinksWindow)
                windowRepository.unPinnedWeight = value
            else
                windowLayout.weight = value
        }

    protected val windowLayout: WindowLayout = WindowLayout(window.windowLayout)

    var id = window.id

    protected var workspaceId = window.workspaceId

    init {
        @Suppress("LeakingThis")
        pageManager.window = this
    }

    val entity get () =
        WorkspaceEntities.Window(
            workspaceId = workspaceId,
            isSynchronized = isSynchronised,
            isPinMode = isPinMode,
            isLinksWindow = isLinksWindow,
            windowLayout = WorkspaceEntities.WindowLayout(windowLayout.state.toString(), windowLayout.weight),
            id = id
        )
    var restoreOngoing: Boolean = false
    var displayedKey: Key? = null
    var displayedBook: Book? = null

    open var isSynchronised = window.isSynchronized
        set(value) {
            field = value
            ABEventBus.getDefault().post(WindowChangedEvent(this))
        }

    open var isPinMode: Boolean = window.isPinMode
        get() = if(windowRepository.workspaceSettings.autoPin) {
            true
        } else {
            field
        }
        set(value) {
            field = value
            ABEventBus.getDefault().post(WindowChangedEvent(this))
        }

    val isMinimised: Boolean
        get() = windowLayout.state == WindowState.MINIMISED

    val isSplit: Boolean
        get() = windowLayout.state == WindowState.SPLIT

    val isSyncable: Boolean
        get() = pageManager.currentPage.isSyncable

    val isClosed: Boolean
        get() = windowLayout.state == WindowState.CLOSED

    var windowState: WindowState
        get() = windowLayout.state
        set(value) {
            windowLayout.state = value
        }

    val isVisible: Boolean
        get() =
            if(!isLinksWindow && windowRepository.isMaximized) windowRepository.maximizedWindowId == id
            else windowLayout.state != WindowState.MINIMISED && windowLayout.state != WindowState.CLOSED


    val defaultOperation: WindowOperation
        get() = when {
            isLinksWindow -> WindowOperation.CLOSE
            else -> WindowOperation.MINIMISE
        }

    open val isLinksWindow = false

    var bibleView: BibleView? = null

    fun destroy() {
        bibleView?.destroy()
    }

    enum class WindowOperation {
        MINIMISE, RESTORE, CLOSE
    }

    override fun toString(): String {
        return "Window[$id]"
    }

    var lastUpdated
        get() = bibleView?.lastUpdated ?: 0L
        set(value) {
            bibleView?.lastUpdated = value
        }

    val initialized get() = lastUpdated != 0L

    fun updateText(notifyLocationChange: Boolean = false) {
        val isVisible = isVisible

        Log.i(TAG, "updateText, isVisible: $isVisible")

        if(!isVisible) return

        Log.i(TAG, "Loading OSIS xml in background")
        var verse: Verse? = null
        var anchorOrdinal: Int? = null
        val currentPage = pageManager.currentPage

        if(listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE).contains(currentPage.documentCategory)) {
            verse = pageManager.currentBibleVerse.verse
        } else {
            anchorOrdinal = currentPage.anchorOrdinal
        }
        displayedBook = currentPage.currentDocument
        displayedKey = currentPage.key
        Log.i(TAG, "updateText ${this.hashCode()}") // ${Log.getStackTraceString(Exception())}")

        GlobalScope.launch(Dispatchers.IO) {
            if (notifyLocationChange) {
                PassageChangeMediator.getInstance().contentChangeStarted()
            }
            val b = bibleView
            val adjusted = b?.adjustLoadingCount(1)?: false
            val doc = fetchDocument()
            if(adjusted) {
                b?.adjustLoadingCount(-1)
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
            waitForBibleView()

            lastUpdated = System.currentTimeMillis()
            lastChecksum = checksum

            if(notifyLocationChange) {
                bibleView?.loadDocument(doc, updateLocation = true)
            } else {
                bibleView?.loadDocument(doc, verse = verse, anchorOrdinal = anchorOrdinal)
            }

            if(notifyLocationChange)
                PassageChangeMediator.getInstance().contentChangeFinished()
            }
        }

    var lastChecksum = 0

    private suspend fun waitForBibleView() {
        var time = 0L
        val delayMillis = 50L
        val timeout = 5000L
        while(bibleView == null) {
            delay(delayMillis)
            time += delayMillis;
            if(time > timeout) {
                Log.e(TAG, "waitForBibleView timed out")
                return;
            }
        }
    }

    private suspend fun fetchDocument(): Document = withContext(Dispatchers.IO) {
        val currentPage = pageManager.currentPage
        return@withContext try {
            val document = currentPage.currentDocument
            Log.i(TAG, "Loading document:$document key:${currentPage.key}")
            currentPage.currentPageContent
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error", oom)
            System.gc()
            ErrorDocument(BibleApplication.application.resources.getString(R.string.error_page_too_large), ErrorSeverity.ERROR)
        }
    }

    fun hasChapterLoaded(chapter: Int): Boolean {
        return bibleView?.hasChapterLoaded(chapter) == true
    }

    private val TAG get() = "BibleView[${id}] WIN"
}
