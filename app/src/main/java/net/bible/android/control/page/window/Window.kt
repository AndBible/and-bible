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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentMyNotePage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.view.activity.page.BibleView
import net.bible.android.database.WorkspaceEntities
import net.bible.service.format.HtmlMessageFormatter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

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
        get() = if(windowRepository.windowBehaviorSettings.autoPin) {
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
//        if(pageManager.currentPage is CurrentMyNotePage) return

        val isVisible = isVisible

        Log.d(TAG, "updateText, isVisible: $isVisible")

        if(!isVisible) return

        Log.d(TAG, "Loading html in background")
        var chapterVerse: ChapterVerse? = null
        var yOffsetRatio: Float? = null
        val currentPage = pageManager.currentPage

        if(currentPage is CurrentBiblePage) {
            chapterVerse = currentPage.currentChapterVerse
        } else {
            yOffsetRatio = currentPage.currentYOffsetRatio
        }

        GlobalScope.launch(Dispatchers.IO) {
            if (notifyLocationChange) {
                PassageChangeMediator.getInstance().contentChangeStarted()
            }

            val text = fetchText()

            withContext(Dispatchers.Main) {
                lastUpdated = System.currentTimeMillis()

                if(notifyLocationChange) {
                    bibleView?.show(text, updateLocation = true)
                } else {
                    bibleView?.show(text, chapterVerse = chapterVerse, yOffsetRatio = yOffsetRatio)
                }
            }

            if(notifyLocationChange)
                PassageChangeMediator.getInstance().contentChangeFinished()
            }
        }

    private suspend fun fetchText(): String = withContext(Dispatchers.IO) {
        val currentPage = pageManager.currentPage
        return@withContext try {
            val document = currentPage.currentDocument
            Log.d(TAG, "Loading document:$document key:${currentPage.key}")
            currentPage.currentPageContent
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error", oom)
            System.gc()
            HtmlMessageFormatter.format(R.string.error_page_too_large)
        }
    }

    fun hasChapterLoaded(chapter: Int): Boolean {
        return bibleView?.hasChapterLoaded(chapter) == true
    }

    private val TAG get() = "BibleView[${id}] WIN"
}
