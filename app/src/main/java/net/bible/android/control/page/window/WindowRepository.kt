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
import org.crosswire.jsword.versification.BookName
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
    var unPinnedWeight: Float? = null
    var orderNumber: Int = 0
    val lastSyncWindow: Window? get() = getWindow(lastSyncWindowId)
    var windowList: MutableList<Window> = ArrayList()
    var busyCount: Int = 0
        set(value) {
            synchronized(this) {
                field = value
            }
        }
    var textDisplaySettings = WorkspaceEntities.TextDisplaySettings.default
    var windowBehaviorSettings = WorkspaceEntities.WindowBehaviorSettings.default
    var maximizedWindowId: Long? = null

    val isMaximized get() = maximizedWindowId != null
    val maximizedWindow get() = getWindow(maximizedWindowId)
    val isBusy get() = busyCount > 0


    fun onEvent(event: IncrementBusyCount) {
        busyCount ++
    }

    fun onEvent(event: DecrementBusyCount) {
        busyCount --
    }

    var id: Long = 0
    var name = ""
        set(value) {
            SharedActivityState.currentWorkspaceName = value
            field = value
        }

    private val dao get() = DatabaseContainer.db.workspaceDao()

    private val logger = Logger(this.javaClass.name)

    val windows: List<Window>
        get() {
            val windows = ArrayList(windowList)
            addLinksWindow(windows)
            return windows.sortedWith(compareBy { !it.isPinMode })
        }

    init {
        ABEventBus.getDefault().safelyRegister(this)
    }

    fun initialize() {
        if(initialized) return
        if(id == 0L) {
            id = sharedPreferences.getLong("current_workspace_id", 0)
            if (id == 0L || dao.workspace(id) == null) {
                id = dao.insertWorkspace(WorkspaceEntities.Workspace(getResourceString(R.string.workspace_number, 1)))
                sharedPreferences.edit().putLong("current_workspace_id", id).apply()
            }
        }
        loadFromDb(id)
    }

    private var _activeWindow: Window? = null

    // 1 based screen no
    var activeWindow: Window
        get() {
            if(!initialized) initialize()
            return _activeWindow!!
        }
        set (newActiveWindow) {
            if (!initialized || newActiveWindow != this._activeWindow) {
                _activeWindow = newActiveWindow
                Log.d(TAG, "Active window: ${newActiveWindow}")
                ABEventBus.getDefault().post(CurrentWindowChangedEvent(newActiveWindow))
            }
        }

    private val initialized get() = _activeWindow != null

    // When in maximized mode, keep track of last used
    // window that was synchronized
    var lastSyncWindowId: Long? = null

    lateinit var dedicatedLinksWindow: LinksWindow
        private set

    val visibleWindows: List<Window> get() {
        if (isMaximized) {
            val maxWindow = getWindow(maximizedWindowId)
            if (maxWindow != null) {
                return listOf(maxWindow)
            } else {
                maximizedWindowId = null
            }
        }
        return getWindows(WindowState.SPLIT)
    }

    val minimisedWindows  get() = getWindows(WindowState.MINIMISED)

    val isMultiWindow get() = visibleWindows.size > 1

    private val defaultState = WindowState.SPLIT

    val firstVisibleWindow: Window get() = visibleWindows.first()
    val lastVisibleWindow: Window get() = visibleWindows.last()

    private fun getDefaultActiveWindow() =
        windows.find { it.isVisible } ?: createNewWindow(null, true)

    private fun setDefaultActiveWindow(): Window {
        val newWindow = getDefaultActiveWindow()
        activeWindow = newWindow
        return newWindow
    }

    private fun addLinksWindow(windows: MutableList<Window>) {
        if (!dedicatedLinksWindow.isClosed && !windows.contains(dedicatedLinksWindow)) {
            windows.add(dedicatedLinksWindow)
        }
    }

    private fun getWindows(state: WindowState)= windows.filter { it.windowState === state}

    fun getWindow(windowId: Long?): Window? = if(windowId == null) null else windows.find {it.id == windowId}

    fun addNewWindow(sourceWindow: Window? = null): Window {
        val newWindow = createNewWindow(sourceWindow)
        newWindow.weight = (sourceWindow?: activeWindow).weight

        activeWindow.windowState = WindowState.SPLIT

        return newWindow
    }

    fun getWindowsToSynchronise(sourceWindow: Window?): List<Window> {
        val windows = ArrayList(visibleWindows)
        if (sourceWindow != null) {
            windows.remove(sourceWindow)
        }

        return windows
    }

    fun minimise(window: Window) {
        window.windowState = WindowState.MINIMISED

        // has the active screen been minimised?
        if (activeWindow == window) {
            setDefaultActiveWindow()
        }
    }

    fun close(window: Window) {
        window.windowState = WindowState.CLOSED
        val currentPos = windowList.indexOf(window)

        // links window is just closed not deleted
        if (!window.isLinksWindow) {
            dao.deleteWindow(window.id)
            destroy(window)
            if(visibleWindows.isEmpty()) {
                activeWindow = windowList[min(currentPos, windowList.size - 1)]
                activeWindow.windowState = WindowState.SPLIT
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
        val pinnedWindows = windowList.filter {it.isPinMode}.toMutableList()
        val unPinnedWindows = windowList.filter {!it.isPinMode}.toMutableList()
        val windowList = if(window.isPinMode) pinnedWindows else unPinnedWindows

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
        this.windowList.clear()
        this.windowList.addAll(pinnedWindows)
        this.windowList.addAll(unPinnedWindows)
    }

    private fun createNewWindow(sourceWindow: Window?, first: Boolean = false): Window {
        val sourceWindow = sourceWindow?: if(initialized) activeWindow else null
        val pageManager = currentPageManagerProvider.get()
        val winEntity =
            (sourceWindow?.entity?.copy()
                ?: WorkspaceEntities.Window(
                    isLinksWindow = false,
                    isPinMode = false,
                    isSynchronized = true,
                    windowLayout = WorkspaceEntities.WindowLayout(defaultState.toString()),
                    workspaceId = id
                )).apply {
                    id = 0
                    id = dao.insertWindow(this)
                }

        val newWindow = Window(winEntity, pageManager, this)
        if(sourceWindow != null) {
            pageManager.restoreFrom(sourceWindow.pageManager.entity)
        }
        dao.insertPageManager(pageManager.entity)
        val pos =
            if(first) 0
            else if(sourceWindow?.isLinksWindow == true) windowList.size
            else windowList.indexOf(sourceWindow) + 1
        windowList.add(pos, newWindow)
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

    private val contentText: String get() {
        val keyTitle = ArrayList<String>()
        synchronized(BookName::class) {
            val prevFullBookNameValue = BookName.isFullBookName()
            BookName.setFullBookName(false)

            windowList.forEach {
                keyTitle.add("${it.pageManager.currentPage.singleKey?.name} (${it.pageManager.currentPage.currentDocument?.abbreviation})")
            }

            BookName.setFullBookName(prevFullBookNameValue)
        }
        return keyTitle.joinToString(", ")
    }

    fun saveIntoDb() {
        Log.d(TAG, "saveIntoDb")
        dao.updateWorkspace(WorkspaceEntities.Workspace(
            name = name,
            contentsText = contentText,
            id = id,
            orderNumber = orderNumber,
            textDisplaySettings = textDisplaySettings,
            windowBehaviorSettings = windowBehaviorSettings,
            unPinnedWeight = unPinnedWeight,
            maximizedWindowId = maximizedWindowId
        ))

        val historyManager = historyManagerProvider.get()
        val allWindows = ArrayList(windowList)
        if(::dedicatedLinksWindow.isInitialized) {
            allWindows.add(dedicatedLinksWindow)
        }

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
        Log.d(TAG, "onLoadDb for workspaceId=$workspaceId")
        val entity = dao.workspace(workspaceId) ?: dao.firstWorkspace()
            ?: WorkspaceEntities.Workspace("").apply{
                id = dao.insertWorkspace(this)
            }
        clear()

        orderNumber = entity.orderNumber
        id = entity.id
        name = entity.name
        unPinnedWeight = entity.unPinnedWeight
        maximizedWindowId = entity.maximizedWindowId

        textDisplaySettings = entity.textDisplaySettings?: WorkspaceEntities.TextDisplaySettings.default
        windowBehaviorSettings = entity.windowBehaviorSettings?: WorkspaceEntities.WindowBehaviorSettings.default

        val linksWindowEntity = dao.linksWindow(id) ?: WorkspaceEntities.Window(
            id,
            isSynchronized = false,
            isPinMode = false,
            isLinksWindow = true,
            windowLayout = WorkspaceEntities.WindowLayout(WindowState.CLOSED.toString())
        ).apply {
            id = dao.insertWindow(this)
        }

        val linksPageManagerEntity = dao.pageManager(linksWindowEntity.id)

        if(!::dedicatedLinksWindow.isInitialized) {
            val pageManager = currentPageManagerProvider.get()
            pageManager.restoreFrom(linksPageManagerEntity, textDisplaySettings)
            dedicatedLinksWindow = LinksWindow(linksWindowEntity, pageManager, this)
        } else {
            dedicatedLinksWindow.restoreFrom(linksWindowEntity, linksPageManagerEntity, textDisplaySettings)
        }
        val historyManager = historyManagerProvider.get()
        for (it in dao.windows(id)) {
            val pageManager = currentPageManagerProvider.get()
            pageManager.restoreFrom(dao.pageManager(it.id), textDisplaySettings)
            val window = Window(it, pageManager, this)
            windowList.add(window)
            historyManager.restoreFrom(window, dao.historyItems(it.id))
        }
        setDefaultActiveWindow()
    }

    fun clear(destroy: Boolean = false) {
        _activeWindow = null
        maximizedWindowId = null
        unPinnedWeight = null
        orderNumber = 0
        id = 0
        lastSyncWindowId = null
        for (it in windowList) {
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

    fun updateWindowTextDisplaySettingsValues(types: Set<WorkspaceEntities.TextDisplaySettings.Types>, settings: WorkspaceEntities.TextDisplaySettings) {
        for (it in windowList) {
            for (t in types) {
                val winValue = it.pageManager.textDisplaySettings.getValue(t)
                if (winValue == settings.getValue(t)) {
                    it.pageManager.textDisplaySettings.setNonSpecific(t)
                }
            }
        }
    }

    fun updateVisibleWindowsTextDisplaySettings() {
        for (it in visibleWindows) {
            it.bibleView?.updateTextDisplaySettings()
        }
    }

    companion object {
        private const val TAG = "WinRep BibleView"
    }
}
