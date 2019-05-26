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

import android.content.SharedPreferences

import net.bible.android.BibleApplication
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.Logger
import net.bible.service.history.HistoryManager

import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList

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
    var dedicatedLinksWindow = LinksWindow(WindowState.CLOSED, currentPageManagerProvider.get())
        private set

    private var maxWindowNoUsed = 0

    var name = ""

    private val logger = Logger(this.javaClass.name)

    //TODO if user presses a link then should also show links window
    val windows: List<Window>
        get() {
            val windows = ArrayList(windowList)
            addLinksWindowIfVisible(windows)
            return windows
        }

    // 1 based screen no
    var activeWindow = getDefaultActiveWindow()
        set(newActiveWindow) {
            if (newActiveWindow != this.activeWindow) {
                field = newActiveWindow
                ABEventBus.getDefault().post(CurrentWindowChangedEvent(this.activeWindow))
            }
        }

    init {
        //restoreState()
        ABEventBus.getDefault().safelyRegister(this)
    }

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

    val minimisedScreens  get() = getWindows(WindowState.MINIMISED)

    val minimisedAndMaximizedScreens: List<Window>
        get() = windows.filter {
            val state = it.windowLayout.state
            state === WindowState.MAXIMISED || state === WindowState.MINIMISED
        }

    val isMaximisedState get() = windows.find{ it.windowLayout.state === WindowState.MAXIMISED } !== null

    val isMultiWindow get() = visibleWindows.size > 1

    private val defaultState get() = WindowState.SPLIT

    val firstWindow get() = windowList[0]

    /**
     * Return window no larger than any windows created during this session and larger than 0
     */
    private val nextWindowNo get() = maxWindowNoUsed + 1

    private fun getDefaultActiveWindow() = windows.find { it.isVisible } ?: addNewWindow(nextWindowNo)

    private fun addLinksWindowIfVisible(windows: MutableList<Window>) {
        if (dedicatedLinksWindow.isVisible) {
            windows.add(dedicatedLinksWindow)
        }
    }

    private fun getWindows(state: WindowState)= windows.filter { it.windowLayout.state === state}

    fun getWindow(screenNo: Int): Window? = windows.find {it.screenNo == screenNo}

    fun addNewWindow(): Window {
        val newWindow = addNewWindow(nextWindowNo)

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
            windows.addAll(minimisedScreens)
        if (sourceWindow != null) {
            windows.remove(sourceWindow)
        }

        return windows
    }

    fun minimise(window: Window) {
        window.windowLayout.state = WindowState.MINIMISED

        // has the active screen been minimised?
        if (activeWindow == window) {
            activeWindow = getDefaultActiveWindow()
        }
    }

    fun close(window: Window) {
        window.windowLayout.state = WindowState.CLOSED

        // links window is just closed not deleted
        if (!window.isLinksWindow) {

            if (!windowList.remove(window)) {
                logger.error("Failed to close window " + window.screenNo)
            }
        }

        // has the active screen been minimised?
        if (activeWindow == window) {
            activeWindow = getDefaultActiveWindow()
        }

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

    private fun addNewWindow(screenNo: Int): Window {
        val newScreen = Window(screenNo, defaultState, currentPageManagerProvider.get())
        maxWindowNoUsed = Math.max(maxWindowNoUsed, screenNo)
        windowList.add(newScreen)
        return newScreen
    }

    /**
     * If app moves to background then save current state to allow continuation after return
     *
     * @param appToBackgroundEvent Event info
     */
    fun onEvent(appToBackgroundEvent: AppToBackgroundEvent) {
        if (appToBackgroundEvent.isMovedToBackground) {
            saveState()
        }
    }

    /** restore current page and document state  */
    fun restoreState() {
        try {
            logger.info("Restore instance state for screens")
            val application = BibleApplication.application
            val settings = application.appStateSharedPreferences
            val stateJsonString = settings.getString("windowRepositoryState", null)
            if(stateJsonString != null)
                restoreState(stateJsonString)
        } catch (e: Exception) {
            logger.error("Restore error", e)
        }
        activeWindow = getDefaultActiveWindow()
    }

    /** called during app close down to save state
     *
     * @param outState
     */
    private fun saveState(outState: SharedPreferences = BibleApplication.application.appStateSharedPreferences) {
        logger.info("save state")
        try {
            val editor = outState.edit()
            editor.putString("windowRepositoryState", dumpState())
            editor.apply()
        } catch (je: JSONException) {
            logger.error("Saving window state", je)
        }

    }

    fun dumpState(): String {
        val windowRepositoryStateObj = JSONObject()
        val windowStateArray = JSONArray()
        for (window in windowList) {
            try {
                if (window.windowLayout.state !== WindowState.CLOSED) {
                    windowStateArray.put(window.stateJson)
                }
            } catch (je: JSONException) {
                logger.error("Error saving screen state", je)
            }

        }
        windowRepositoryStateObj.put("windowState", windowStateArray)
        windowRepositoryStateObj.put("name", name)
        windowRepositoryStateObj.put("history", historyManagerProvider.get().dumpString)
        return windowRepositoryStateObj.toString()
    }

    /** called during app start-up to restore previous state
     *
     * @param inState
     */
    fun restoreState(stateJsonString: String) {
        logger.info("restore state")
        if (StringUtils.isNotEmpty(stateJsonString)) {
            try {
                val windowRepositoryState = JSONObject(stateJsonString)
                val windowState = windowRepositoryState.getJSONArray("windowState")
                name = windowRepositoryState.optString("name")
                if (windowState.length() > 0) {

                    // remove current (default) state before restoring
                    windowList.clear()

                    for (i in 0 until windowState.length()) {
                        try {
                            val window = Window(currentPageManagerProvider.get())
                            window.restoreState(windowState.getJSONObject(i))
                            maxWindowNoUsed = Math.max(maxWindowNoUsed, window.screenNo)
                            windowList.add(window)
                        } catch (je: JSONException) {
                            logger.error("Error restoring screen state", je)
                        }

                    }
                }
                historyManagerProvider.get().dumpString = windowRepositoryState.optString("history")

            } catch (je: JSONException) {
                logger.error("Error restoring screen state", je)
            }
        }
        activeWindow = getDefaultActiveWindow()
    }

    fun clear() {
        windowList.clear()
        activeWindow = getDefaultActiveWindow()
    }
}
