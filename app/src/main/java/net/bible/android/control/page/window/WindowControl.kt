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
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.EventManager
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.Logger

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil

import java.util.HashMap

import javax.inject.Inject

/**
 * Central control of windows especially synchronization
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class WindowControl @Inject constructor(
        val windowRepository: WindowRepository,
        private val eventManager: EventManager
) : ActiveWindowPageManagerProvider {

    private var isSeparatorMoving = false
    private var stoppedMovingTime: Long = 0
    val windowSync: WindowSync = WindowSync(windowRepository)

    private val logger = Logger(this.javaClass.name)

    override val activeWindowPageManager: CurrentPageManager
        get() = activeWindow.pageManager

    val isMultiWindow: Boolean
        get() = windowRepository.isMultiWindow

    var activeWindow: Window
        get() = windowRepository.activeWindow
        set(currentActiveWindow) {
            windowRepository.activeWindow = currentActiveWindow
        }

    val activeWindowPosition get() = windowRepository.windowList.indexOf(activeWindow)
    fun windowPosition(windowId: Long) = windowRepository.windowList.indexOf(windowRepository.getWindow(windowId))
    val isSingleWindow get () = !windowRepository.isMultiWindow && windowRepository.minimisedWindows.isEmpty()

    init {
        eventManager.register(this)
    }

    fun isActiveWindow(window: Window): Boolean {
        return window == windowRepository.activeWindow
    }

    /**
     * Show link using whatever is the current Bible in the Links window
     */
    fun showLinkUsingDefaultBible(key: Key) {
        val linksWindow = windowRepository.dedicatedLinksWindow
        val currentBiblePage = linksWindow.pageManager.currentBible

        val isBible = windowRepository.activeWindow.pageManager.isBibleShown
        val bibleDoc = windowRepository.activeWindow.pageManager.currentBible.currentDocument

        // default either to links window bible or if closed then active window bible
        val defaultBible = if (currentBiblePage.isCurrentDocumentSet) {
            currentBiblePage.currentDocument
        } else {
            windowRepository.activeWindow.pageManager.currentBible.currentDocument
        }
        if(defaultBible == null) {
            Log.e(TAG, "Default bible is null! Can't show link.")
            return
        }
        showLink(if (isBible && bibleDoc != null) bibleDoc else defaultBible, key)
    }

    fun showLink(document: Book, key: Key) {
        val linksWindow = windowRepository.dedicatedLinksWindow
        linksWindow.restoreOngoing = true
        val linksWindowWasVisible = linksWindow.isVisible

        linksWindow.initialisePageStateIfClosed(activeWindow)

        //TODO do not set links window active -  currently need to set links window to active
        // window otherwise BibleContentMediator logic does not refresh that window
        windowRepository.activeWindow = linksWindow


        // redisplay the current page
        if (!linksWindowWasVisible) {
            linksWindow.windowState = WindowState.SPLIT
        }

        linksWindow.pageManager.setCurrentDocumentAndKey(document, key)

        if (!linksWindowWasVisible) {
            eventManager.post(NumberOfWindowsChangedEvent())
        }
        linksWindow.restoreOngoing = false
    }


    fun addNewWindow(): Window {
        val window = windowRepository.addNewWindow()

        restoreWindow(window)

        return window
    }

    fun addNewWindow(document: Book, key: Key): Window {
        val window = windowRepository.addNewWindow()
        val pageManager = window.pageManager
        window.isSynchronised = false
        pageManager.setCurrentDocumentAndKey(document, key)

        restoreWindow(window)

        return window
    }

    fun minimiseWindow(window: Window, force: Boolean = false) {
        if(force || isWindowMinimisable(window)) {
            windowRepository.minimise(window)

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent())
        }
    }

    fun closeWindow(window: Window) {

        if (isWindowRemovable(window)) {
            logger.debug("Closing window " + window.id)
            windowRepository.close(window)

            val visibleWindows = windowRepository.visibleWindows
            if (visibleWindows.count() == 1) visibleWindows[0].weight = 1.0F

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent())
            windowSync.reloadAllWindows()
        }
    }

    fun isWindowMinimisable(window: Window): Boolean {
        var normalWindows = windowRepository.visibleWindows.size
        if (windowRepository.dedicatedLinksWindow.isVisible) {
            normalWindows--
        }

        val canMinimize =  normalWindows > 1

        return !window.isMinimised && canMinimize && !window.isLinksWindow
    }

    fun isWindowRemovable(window: Window): Boolean {
        var normalWindows = windowRepository.windows.size
        if (windowRepository.dedicatedLinksWindow.isVisible) {
            normalWindows--
        }

        return window.isLinksWindow || normalWindows > 1
    }

    fun restoreWindow(window: Window) {
        if (window == activeWindow) return
        window.restoreOngoing = true
        if(windowRepository.windowBehaviorSettings.autoPin)
            window.isPinMode = true

        for (it in windowRepository.windowList.filter { !it.isPinMode }) {
            it.windowState = WindowState.MINIMISED
        }

        window.windowState = WindowState.SPLIT

        // causes BibleViews to be created and laid out
        windowSync.synchronizeWindows()
        windowSync.reloadAllWindows()

        if (activeWindow.isSynchronised)
            windowRepository.lastSyncWindowId = activeWindow.id

        activeWindow = window

        eventManager.post(NumberOfWindowsChangedEvent())

        window.restoreOngoing = false
    }

    /*
	 * Move the current window to first
	 */

    /** screen orientation has changed  */
    fun orientationChange() {
        // causes BibleViews to be created and laid out
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    fun onEvent(event: CurrentVerseChangedEvent) {
        windowSync.synchronizeWindows(event.window)
    }

    fun onEvent(event: SynchronizeWindowsEvent) {
        if(event.forceSyncAll) {
            windowSync.setResyncRequired()
        } else {
            windowSync.setResyncBiblesRequired()
        }
        if(activeWindowPageManager.isMyNoteShown) return
        windowSync.reloadAllWindows()
    }

    fun isSeparatorMoving(): Boolean {
        // allow 1 sec for screen to settle after window separator drag
        if (stoppedMovingTime > 0) {
            // allow a second after stopping for screen to settle
            if (stoppedMovingTime + SCREEN_SETTLE_TIME_MILLIS > System.currentTimeMillis()) {
                return true
            }
            stoppedMovingTime = 0
        }
        return isSeparatorMoving
    }

    fun setSeparatorMoving(isSeparatorMoving: Boolean) {
        if (!isSeparatorMoving) {
            // facilitate time for the screen to settle
            this.stoppedMovingTime = System.currentTimeMillis()
        }
        this.isSeparatorMoving = isSeparatorMoving

        val isMoveFinished = !isSeparatorMoving

        eventManager.post(WindowSizeChangedEvent(isMoveFinished))
    }

    fun windowSizesChanged() {
        if (isMultiWindow) {
            // need to layout multiple windows differently
            orientationChange()
        }
    }

    fun setSynchronised(window: Window, value: Boolean) {
        if(value == window.isSynchronised) return
        if(value) {
            window.isSynchronised = true
            windowSync.synchronizeWindows()
        } else {
            window.isSynchronised = false
        }
    }

    fun moveWindow(window: Window, position: Int) {
        windowRepository.moveWindowToPosition(window, position)

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    fun setPinMode(window: Window, value: Boolean) {
        window.isPinMode = value
        if(value && !window.isVisible) {
            restoreWindow(window)
        } else if(!value && window.isVisible && windowRepository.visibleWindows.size > 1) {
            minimiseWindow(window, true)
        }
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
        const val TAG = "WindowControl"
    }
}
