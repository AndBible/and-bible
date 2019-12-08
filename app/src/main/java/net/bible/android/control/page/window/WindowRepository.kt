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

import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.common.Logger
import net.bible.service.db.DatabaseContainer
import net.bible.android.database.workspaces.WorkspaceEntities
import net.bible.service.history.HistoryManager
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

@ApplicationScope
open class WindowRepository @Inject constructor(
        // Each window has its own currentPageManagerProvider to store the different state e.g.
        // different current Bible module, so must create new cpm for each window
    val currentPageManagerProvider: Provider<CurrentPageManager>,
    private val historyManagerProvider: Provider<HistoryManager>
)
{
    private var windowList: MutableList<Window> = ArrayList()

    var id: Long = 0
    var name = ""

    val dao get() = DatabaseContainer.db.workspaceDao()

    private val logger = Logger(this.javaClass.name)

    //TODO if user presses a link then should also show links window
    val windows: List<Window>
        get() {
            val windows = ArrayList(windowList)
            addLinksWindowIfVisible(windows)
            return windows
        }

    init {
        ABEventBus.getDefault().safelyRegister(this)
    }

    fun initialize() {
        id = sharedPreferences.getLong("current_workspace_id", 0)
        if(id == 0L || dao.workspace(id) == null) {
            id = dao.insertWorkspace(WorkspaceEntities.Workspace(name))
            sharedPreferences.edit().putLong("current_workspace_id", id).apply()
        }
        loadFromDb(id)
    }

    // 1 based screen no
    lateinit var activeWindow: Window
        private set

    fun setActiveWindow(newActiveWindow: Window) {
        if (!::activeWindow.isInitialized || newActiveWindow != this.activeWindow) {
            activeWindow = newActiveWindow
            ABEventBus.getDefault().post(CurrentWindowChangedEvent(this.activeWindow))
        }
    }

    lateinit var dedicatedLinksWindow: LinksWindow
        private set

    // links window is still displayable in maximised mode but does not have the requested MAXIMIZED state
    // should only ever be one maximised window
    val visibleWindows: MutableList<Window>
        get() {
            val maximisedWindows = ArrayList(getWindows(WindowState.MAXIMISED))
            return if (maximisedWindows.isNotEmpty()) {
                if (!maximisedWindows.contains(dedicatedLinksWindow as Window)) {
                    addLinksWindowIfVisible(maximisedWindows)
                }
                maximisedWindows
            } else {
                ArrayList(getWindows(WindowState.SPLIT))
            }
        }

    val maximisedScreens get() = getWindows(WindowState.MAXIMISED)

    val minimisedWindows  get() = getWindows(WindowState.MINIMISED)

    val minimisedAndMaximizedScreens: List<Window>
        get() = windows.filter {
            val state = it.windowLayout.state
            state === WindowState.MAXIMISED || state === WindowState.MINIMISED
        }

    val isMaximisedState get() = windows.find{ it.windowLayout.state === WindowState.MAXIMISED } !== null

    val isMultiWindow get() = visibleWindows.size > 1

    private val defaultState = WindowState.SPLIT

    val firstVisibleWindow: Window get() = windowList.find { it.isVisible }!!

    private fun getDefaultActiveWindow() =
        windows.find { it.isVisible } ?: createNewWindow()

    fun setDefaultActiveWindow() {
        setActiveWindow(getDefaultActiveWindow())
    }

    private fun addLinksWindowIfVisible(windows: MutableList<Window>) {
        if (dedicatedLinksWindow.isVisible) {
            windows.add(dedicatedLinksWindow)
        }
    }

    private fun getWindows(state: WindowState)= windows.filter { it.windowLayout.state === state}

    fun getWindow(windowId: Long): Window? = windows.find {it.id == windowId}

    fun addNewWindow(): Window {
        val newWindow = createNewWindow()

        if(isMaximisedState) {
            activeWindow.windowLayout.state = WindowState.MINIMISED
            newWindow.windowLayout.state = WindowState.MAXIMISED

        } else {
            activeWindow.windowLayout.state = WindowState.SPLIT
        }

        return newWindow
    }

    fun getWindowsToSynchronise(sourceWindow: Window?): List<Window> {
        val windows = visibleWindows
        if(isMaximisedState)
            windows.addAll(minimisedWindows)
        if (sourceWindow != null) {
            windows.remove(sourceWindow)
        }

        return windows
    }

    fun minimise(window: Window) {
        window.windowLayout.state = WindowState.MINIMISED

        // has the active screen been minimised?
        if (activeWindow == window) {
            setDefaultActiveWindow()
        }
    }

    fun close(window: Window) {
        val wasMaximized = isMaximisedState

        window.windowLayout.state = WindowState.CLOSED

        // links window is just closed not deleted
        if (!window.isLinksWindow) {
            dao.deleteWindow(window.id)
            destroy(window)
            if(wasMaximized) {
                val lastWindow = minimisedWindows.last()
                lastWindow.isMaximised = true
                setActiveWindow(lastWindow)
            }
        }
        if (!wasMaximized) setDefaultActiveWindow()
    }

    private fun destroy(window: Window) {
        if (!windowList.remove(window)) {
            logger.error("Failed to remove window " + window.id)
        }
        window.destroy()
    }

    fun moveWindowToPosition(window: Window, position: Int) {
        val originalWindowIndex = windowList.indexOf(window)

        if (originalWindowIndex == -1) {
            logger.warn("Attempt to move missing window")
            return
        }
        if (position > windowList.size) {
            logger.warn("Attempt to move window beyond end of window list")
            return
        }

        windowList.removeAt(originalWindowIndex)

        windowList.add(position, window)
    }

    private fun createNewWindow(): Window {
        val winEntity = WorkspaceEntities.Window(
            id, true, false, false,
            WorkspaceEntities.WindowLayout(defaultState.toString())
        ).apply {
            id = dao.insertWindow(this)
        }

        val newWindow = Window(winEntity, currentPageManagerProvider.get())
        windowList.add(newWindow)
        return newWindow
    }

    /**
     * If app moves to background then save current state to allow continuation after return
     *
     * @param appToBackgroundEvent Event info
     */
    fun onEvent(appToBackgroundEvent: AppToBackgroundEvent) {
        if (appToBackgroundEvent.isMovedToBackground) {
            saveIntoDb()
        }
    }

    fun saveIntoDb() {
        dao.updateWorkspace(WorkspaceEntities.Workspace(name, id))

        val historyManager = historyManagerProvider.get()
        val windows = windowList.mapIndexed { i, it ->
            dao.updateHistoryItems(it.id, historyManager.getEntities(it.id))
            it.entity.apply {
                orderNumber = i
            }
        }
        val pageManagers = windowList.map {
            it.pageManager.entity
        }

        dao.updateWindows(windows)
        dao.updatePageManagers(pageManagers)
    }

    /** called during app start-up to restore previous state
     *
     * @param inState
     */

    fun loadFromDb(workspaceId: Long) {
        val entity = dao.workspace(workspaceId)
            ?: WorkspaceEntities.Workspace("").apply{
                id = dao.insertWorkspace(this)
            }
        clear()

        id = entity.id
        name = entity.name

        val linksWindowEntity = dao.linksWindow(id) ?: WorkspaceEntities.Window(
            id, false, false, true,
            WorkspaceEntities.WindowLayout(WindowState.CLOSED.toString())
        ).apply {
            id = dao.insertWindow(this)
        }

        val linksPageManager = dao.pageManager(linksWindowEntity.id)

        if(!::dedicatedLinksWindow.isInitialized) {
            dedicatedLinksWindow = LinksWindow(linksWindowEntity, currentPageManagerProvider.get())
        }

        dedicatedLinksWindow.restoreFrom(linksWindowEntity, linksPageManager)
        val historyManager = historyManagerProvider.get()
        historyManager.clear()
        dao.windows(id).forEach {
            val pageManager = currentPageManagerProvider.get()
            pageManager.restoreFrom(dao.pageManager(it.id))
            val window = Window(it, pageManager)
            windowList.add(window)
            historyManager.restoreFrom(window, dao.historyItems(it.id))
        }
        setDefaultActiveWindow()
    }

    fun clear() {
        windowList.toList().forEach { w -> destroy(w)}
        historyManagerProvider.get().clear()
        name = ""
    }
}
