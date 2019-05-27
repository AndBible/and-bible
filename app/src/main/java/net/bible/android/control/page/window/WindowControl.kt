/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.view.Menu
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.PassageChangeMediator
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

    /**
     * Get current chapter.verse for each window displaying a Bible
     *
     * @return Map of window num to verse num
     */
    private// get page offsets to maintain for each window
    val windowChapterVerseMap: Map<Window, ChapterVerse>
        get() {
            val windowVerseMap = HashMap<Window, ChapterVerse>()
            for (window in windowRepository.windows) {
                val currentPage = window.pageManager.currentPage
                if (BookCategory.BIBLE == currentPage.currentDocument?.bookCategory) {
                    val chapterVerse = ChapterVerse.fromVerse(KeyUtil.getVerse(currentPage.singleKey))
                    windowVerseMap[window] = chapterVerse
                }
            }
            return windowVerseMap
        }

    init {

        eventManager.register(this)
    }

    /**
     * Add the Window sub-menu resource which is not included in the main.xml for the main menu
     * Set the synchronised checkbox in the app menu before displayed
     * Disable various menu items if links window selected
     */
    fun updateOptionsMenu(menu: Menu) {
        // when updating main menu rather than Window options menu

        val synchronisedMenuItem = menu.findItem(R.id.windowSynchronise)
        val moveFirstMenuItem = menu.findItem(R.id.windowMoveFirst)
        val closeMenuItem = menu.findItem(R.id.windowClose)
        val minimiseMenuItem = menu.findItem(R.id.windowMinimise)
        val maximiseMenuItem = menu.findItem(R.id.windowMaximise)
        val window = activeWindow

        if (synchronisedMenuItem != null && moveFirstMenuItem != null) {
            // set synchronised & maximised checkbox state
            synchronisedMenuItem.isChecked = window.isSynchronised
            maximiseMenuItem.isChecked = window.isMaximised

            // the dedicated links window cannot be treated as a normal window
            val isDedicatedLinksWindowActive = isActiveWindow(windowRepository.dedicatedLinksWindow)
            synchronisedMenuItem.isEnabled = !isDedicatedLinksWindowActive
            moveFirstMenuItem.isEnabled = !isDedicatedLinksWindowActive

            // cannot close last normal window
            closeMenuItem.isEnabled = isWindowRemovable(window)
            minimiseMenuItem.isEnabled = isWindowMinimisable(window)

            // if window is already first then cannot promote
            val visibleWindows = windowRepository.visibleWindows
            if (visibleWindows.size > 0 && window == visibleWindows[0]) {
                moveFirstMenuItem.isEnabled = false
            }
        }
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

        val defaultBible: Book

        // default either to links window bible or if closed then active window bible
        defaultBible = if (currentBiblePage.isCurrentDocumentSet) {
            currentBiblePage.currentDocument
        } else {
            windowRepository.firstWindow.pageManager.currentBible.currentDocument
        }

        showLink(defaultBible, key)
    }

    fun showLink(document: Book, key: Key) {
        val linksWindow = windowRepository.dedicatedLinksWindow
        val linksWindowWasVisible = linksWindow.isVisible

        linksWindow.initialisePageStateIfClosed(activeWindow)

        //TODO do not set links window active -  currently need to set links window to active
        // window otherwise BibleContentMediator logic does not refresh that window
        windowRepository.activeWindow = linksWindow

        linksWindow.pageManager.setCurrentDocumentAndKey(document, key)

        // redisplay the current page
        if (!linksWindowWasVisible) {
            linksWindow.windowLayout.state = WindowState.SPLIT
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
        }
    }


    fun addNewWindow(): Window {
        val window = windowRepository.addNewWindow()

        // default state to active window
        if (!isActiveWindow(window)) {
            val activeWindow = activeWindow
            val activeWindowPageState = activeWindow.pageManager.stateJson
            window.pageManager.restoreState(activeWindowPageState)
            window.isSynchronised = activeWindow.isSynchronised
        }

        windowSync.setResynchRequired(true)
        windowSync.synchronizeScreens()

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        return window
    }

    fun addNewWindow(document: Book, key: Key): Window {
        val window = windowRepository.addNewWindow()
        val pageManager = window.pageManager
        window.isSynchronised = false
        pageManager.setCurrentDocumentAndKey(document, key)

        windowSync.setResynchRequired(true)
        windowSync.synchronizeScreens()

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        return window
    }


    /**
     * Minimise window if possible
     */
    fun minimiseCurrentWindow() {
        minimiseWindow(activeWindow)
    }

    fun minimiseWindow(window: Window) {
        if (isWindowMinimisable(window)) {
            windowRepository.minimise(window)

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
        }
    }

    fun maximiseWindow(window: Window) {
        windowRepository.minimisedScreens.forEach {
            it.wasMinimized = true
        }
        windowRepository.visibleWindows.forEach {
            if (it != window) it.windowLayout.state = WindowState.MINIMISED
        }

        window.isMaximised = true
        activeWindow = window

        // also remove the links window because it may possibly displayed even though a window is
        // maximised if a link is pressed
        if (!window.isLinksWindow) {
            windowRepository.dedicatedLinksWindow.windowLayout.state = WindowState.CLOSED
        }

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }

    fun unmaximiseWindow(window: Window) {
        window.isMaximised = false

        windowRepository.minimisedScreens.forEach {
            it.windowLayout.state = if(it.wasMinimized) WindowState.MINIMISED else WindowState.SPLIT
            it.wasMinimized = false
        }

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        windowSync.setResynchRequired(true)
        windowSync.synchronizeScreens()
    }

    fun closeCurrentWindow() {
        closeWindow(activeWindow)
    }

    fun closeWindow(window: Window) {

        if (isWindowRemovable(activeWindow)) {
            logger.debug("Closing window " + window.screenNo)
            windowRepository.close(window)

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
            if (!activeWindow.initialized)
                PassageChangeMediator.getInstance().forcePageUpdate()
        }
    }

    private fun isWindowMinimisable(window: Window): Boolean {
        return !windowRepository.isMaximisedState && isWindowRemovable(window) && !window.isLinksWindow
    }

    private fun isWindowRemovable(window: Window): Boolean {
        if(windowRepository.isMaximisedState && windowRepository.minimisedAndMaximizedScreens.size > 1) return true
        var normalWindows = windowRepository.visibleWindows.size
        if (windowRepository.dedicatedLinksWindow.isVisible) {
            normalWindows--
        }

        return window.isLinksWindow || normalWindows > 1 || !window.isVisible
    }

    fun restoreWindow(window: Window) {
        if (window == activeWindow) return
        window.justRestored = true

        var switchingMaximised = false
        windowRepository.maximisedScreens.forEach {
            switchingMaximised = true
            it.windowLayout.state = WindowState.MINIMISED
        }

        window.isMaximised = switchingMaximised

        // causes BibleViews to be created and laid out
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
        windowSync.setResynchRequired(true)

        windowSync.synchronizeScreens()

        if (switchingMaximised) {
            activeWindow = window
            if (!activeWindow.initialized)
                PassageChangeMediator.getInstance().forcePageUpdate()
        }
    }

    fun synchroniseCurrentWindow() {
        activeWindow.isSynchronised = true

        windowSync.setResynchRequired(true)
        windowSync.synchronizeScreens()
    }

    fun unsynchroniseCurrentWindow() {
        activeWindow.isSynchronised = false
    }

    /*
	 * Move the current window to first
	 */
    fun moveCurrentWindowToFirst() {
        val window = activeWindow

        windowRepository.moveWindowToPosition(window, 0)

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }


    /** screen orientation has changed  */
    fun orientationChange() {
        // causes BibleViews to be created and laid out
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }

    fun onEvent(event: CurrentVerseChangedEvent) {
        windowSync.synchronizeScreens(event.window)
    }

    fun onEvent(event: SynchronizeWindowsEvent) {
        if (event.syncAll) {
            windowSync.synchronizeAllScreens()
        } else {
            windowSync.setResynchRequired(true)
            windowSync.synchronizeScreens()
        }
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
        if (isMoveFinished) {
            windowSync.setResynchRequired(true)
        }

        eventManager.post(WindowSizeChangedEvent(isMoveFinished, windowChapterVerseMap))
    }

    fun windowSizesChanged() {
        if (isMultiWindow) {
            // need to layout multiple windows differently
            orientationChange()
        }
        // essentially if the current page is Bible then we need to recalculate verse offsets
        // if not then don't redisplay because it would force the page to the top which would be annoying if you are half way down a gen book page
        else if (!activeWindowPageManager.currentPage.isSingleKey) {
            // force a recalculation of verse offsets
            PassageChangeMediator.getInstance().forcePageUpdate()
        }
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
    }
}
