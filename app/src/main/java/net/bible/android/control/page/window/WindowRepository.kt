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
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.common.Logger
import net.bible.service.db.DatabaseContainer
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.history.HistoryManager
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.min

class IncrementBusyCount
class DecrementBusyCount

@ApplicationScope
open class WindowRepository @Inject constructor(
        // Each window has its own currentPageManagerProvider to store the different state e.g.
        // different current Bible module, so must create new cpm for each window
    val currentPageManagerProvider: Provider<CurrentPageManager>,
    private val historyManagerProvider: Provider<HistoryManager>
)
{
    var windowList: MutableList<Window> = ArrayList()
    private var busyCount: Int = 0
    var textDisplaySettings = WorkspaceEntities.TextDisplaySettings.default
    var windowBehaviorSettings = WorkspaceEntities.WindowBehaviorSettings.default

    val isBusy get() = busyCount > 0


    fun onEvent(event: IncrementBusyCount) {
        synchronized(this) {
            busyCount ++
        }
    }

    fun onEvent(event: DecrementBusyCount) {
        synchronized(this) {
            busyCount --
        }
    }

    var id: Long = 0
    var name = ""
        set(value) {
            SharedActivityState.currentWorkspaceName = value
            field = value
        }

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
        if(::_activeWindow.isInitialized) return
        id = sharedPreferences.getLong("current_workspace_id", 0)
        if(id == 0L || dao.workspace(id) == null) {
            id = dao.insertWorkspace(WorkspaceEntities.Workspace(getResourceString(R.string.workspace_number, 1)))
            sharedPreferences.edit().putLong("current_workspace_id", id).apply()
        }
        loadFromDb(id)
    }

    private lateinit var _activeWindow: Window

    // 1 based screen no
    var activeWindow: Window
        get() {
            initialize()
            return _activeWindow
        }
        set (newActiveWindow) {
            if (!::_activeWindow.isInitialized || newActiveWindow != this.activeWindow) {
                _activeWindow = newActiveWindow
                Log.d(TAG, "Active window: ${newActiveWindow}")
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
        windows.find { it.isVisible } ?: createNewWindow(true)

    fun setDefaultActiveWindow(): Window {
        val newWindow = getDefaultActiveWindow()
        activeWindow = newWindow
        return newWindow
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
        newWindow.windowLayout.weight = activeWindow.windowLayout.weight

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
        val currentPos = windowList.indexOf(window)

        // links window is just closed not deleted
        if (!window.isLinksWindow) {
            dao.deleteWindow(window.id)
            destroy(window)
            if(wasMaximized) {
                activeWindow = windowList[min(currentPos, windowList.size - 1)]
                activeWindow.isMaximised = windowList.size > 1
            } else setDefaultActiveWindow()
        } else setDefaultActiveWindow()
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

    private fun createNewWindow(first: Boolean = false): Window {
        val pageManager = currentPageManagerProvider.get()
        val winEntity = WorkspaceEntities.Window(
            id, true, false, false,
            WorkspaceEntities.WindowLayout(defaultState.toString())
        ).apply {
            id = dao.insertWindow(this)
        }

        val newWindow = Window(winEntity, pageManager, this)
        dao.insertPageManager(pageManager.entity)
        windowList.add(if(first) 0 else windowList.indexOf(activeWindow) + 1, newWindow)
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
        Log.d(TAG, "saveIntoDb")
        dao.updateWorkspace(WorkspaceEntities.Workspace(name, id, textDisplaySettings, windowBehaviorSettings))

        val historyManager = historyManagerProvider.get()
        val allWindows = ArrayList(windowList)
        allWindows.add(dedicatedLinksWindow)

        val windowEntities = allWindows.mapIndexed { i, it ->
            dao.updateHistoryItems(it.id, historyManager.getEntities(it.id))
            it.entity.apply {
                orderNumber = i
            }
        }

        val pageManagers = allWindows.map {
            val currentPosition = it.bibleView?.currentPosition
            if(currentPosition != null) {
                it.pageManager.currentPage.currentYOffsetRatio = currentPosition
            }
            it.pageManager.entity
        }

        dao.updateWindows(windowEntities)
        dao.updatePageManagers(pageManagers)
    }

    /** called during app start-up to restore previous state
     *
     * @param inState
     */

    fun loadFromDb(workspaceId: Long) {
        Log.d(TAG, "onLoadDb ${workspaceId}")
        val entity = dao.workspace(workspaceId) ?: dao.firstWorkspace()
            ?: WorkspaceEntities.Workspace("").apply{
                id = dao.insertWorkspace(this)
            }
        clear()

        id = entity.id
        name = entity.name

        textDisplaySettings = entity.textDisplaySettings?: WorkspaceEntities.TextDisplaySettings.default
        windowBehaviorSettings = entity.windowBehaviorSettings?: WorkspaceEntities.WindowBehaviorSettings.default

        val linksWindowEntity = dao.linksWindow(id) ?: WorkspaceEntities.Window(
            id, false, false, true,
            WorkspaceEntities.WindowLayout(WindowState.CLOSED.toString())
        ).apply {
            id = dao.insertWindow(this)
        }

        val linksPageManagerEntity = dao.pageManager(linksWindowEntity.id)

        if(!::dedicatedLinksWindow.isInitialized) {
            val pageManager = currentPageManagerProvider.get()
            pageManager.restoreFrom(linksPageManagerEntity)
            dedicatedLinksWindow = LinksWindow(linksWindowEntity, pageManager, this)
        } else {
            dedicatedLinksWindow.restoreFrom(linksWindowEntity, linksPageManagerEntity)
        }
        val historyManager = historyManagerProvider.get()
        dao.windows(id).forEach {
            val pageManager = currentPageManagerProvider.get()
            pageManager.restoreFrom(dao.pageManager(it.id))
            val window = Window(it, pageManager, this)
            windowList.add(window)
            historyManager.restoreFrom(window, dao.historyItems(it.id))
        }
        setDefaultActiveWindow()
    }

    fun clear(destroy: Boolean = false) {
        windowList.forEach {
            it.bibleView?.listenEvents = false
            if(destroy)
                it.destroy()
        }
        if(::dedicatedLinksWindow.isInitialized) {
            dedicatedLinksWindow.bibleView?.listenEvents = false
        }
        windowList.clear()
        historyManagerProvider.get().clear()
        name = ""
    }

    fun updateWindowTextDisplaySettings(type: WorkspaceEntities.TextDisplaySettings.Id, value: Boolean) {
        windowList.forEach {
            val winValue = it.pageManager.textDisplaySettings.getBooleanValue(type)
            if (winValue == value) {
                it.pageManager.textDisplaySettings.setNonSpecific(type)
            }
        }
    }

    fun updateWindowFontSizes(fontSize: Int) {
        windowList.forEach {
            val winValue = it.pageManager.textDisplaySettings.fontSize
            if (winValue == fontSize) {
                it.pageManager.textDisplaySettings.fontSize = null
            }
        }
    }

    companion object {
        private const val TAG = "WinRep BibleView"
    }
}
