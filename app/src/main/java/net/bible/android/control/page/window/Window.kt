/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
import net.bible.android.view.activity.page.windowControl
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

class WindowChangedEvent(val window: Window)

class Window (
    window: WorkspaceEntities.Window,
    val pageManager: CurrentPageManager,
    val windowRepository: WindowRepository,
    var isLinksWindow: Boolean = window.isLinksWindow,
){
    private var targetLinksWindowId: Long? = window.targetLinksWindowId
    var syncGroup = window.syncGroup

    val targetLinksWindow: Window
        get() {
            return ownTargetLinksWindow
                ?: if (isLinksWindow) windowRepository.addNewLinksWindow().also {
                    targetLinksWindowId = it.id
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

    val id = window.id
    var weight: Float
        get() =
            if(!isPinMode) {
                if(windowRepository.unPinnedWeight == null) {
                    windowRepository.unPinnedWeight = windowLayout.weight
                }
                windowRepository.unPinnedWeight!!
            }
            else windowLayout.weight
        set(value) {
            if(!isPinMode)
                windowRepository.unPinnedWeight = value
            else
                windowLayout.weight = value
        }

    private val windowLayout: WindowLayout = WindowLayout(window.windowLayout)
    private var workspaceId = window.workspaceId

    init {
        pageManager.window = this
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
    var displayedKey: Key? = null
        private set
    var displayedBook: Book? = null
        private set

    var isSynchronised = window.isSynchronized
        set(value) {
            field = value
            ABEventBus.post(WindowChangedEvent(this))
        }

    var isPinMode: Boolean = window.isPinMode
        get() {
            return when {
                isLinksWindow -> windowRepository.workspaceSettings.autoPin
                windowRepository.workspaceSettings.autoPin -> true
                else -> field
            }
        }
        set(value) {
            field = value
            ABEventBus.post(WindowChangedEvent(this))
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


    var bibleView: BibleView? = null

    fun destroy() = bibleView?.destroy()

    override fun toString(): String = "Window[$id]"

    var lastUpdated
        get() = bibleView?.lastUpdated ?: 0L
        set(value) {
            bibleView?.lastUpdated = value
        }

    val initialized get() = lastUpdated != 0L
    private val updateScope get() = windowRepository.scope

    fun loadText(notifyLocationChange: Boolean = false) {
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
        displayedKey = currentPage.singleKey
        Log.i(TAG, "updateText ${this.hashCode()}")

        updateScope.launch {
            if (notifyLocationChange) {
                PassageChangeMediator.contentChangeStarted()
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
                PassageChangeMediator.contentChangeFinished()
            }
        }

    private var lastChecksum = 0

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

    fun updateText() {
        val document = pageManager.currentPage.currentDocument
        val verse = pageManager.currentVersePage.currentBibleVerse.verse
        val book = pageManager.currentVersePage.currentBibleVerse.currentBibleBook

        val documentChanged = displayedBook != document

        if(documentChanged) {
            loadText(true)
            return
        }

        val prevKey = displayedKey

        if( pageManager.isBibleShown
            && prevKey is Verse
            && prevKey.book == book
            && hasChapterLoaded(verse.chapter)
        ) {
            val originalKey = pageManager.currentBible.originalKey
            bibleView?.scrollOrJumpToVerse(originalKey ?: verse)
            PassageChangeMediator.contentChangeFinished()
            return
        }
        if(displayedKey == verse) return
        loadText(notifyLocationChange = true)
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

    fun updateTextIfNeeded() {
        if((displayedKey != pageManager.currentPage.singleKey || displayedBook != pageManager.currentPage.currentDocument)
            || (windowControl.windowSync.lastForceSyncAll > lastUpdated))
        {
            loadText()
        }
    }

    private val TAG get() = "BibleView[${id}] WIN"
}
