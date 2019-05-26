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

import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.view.activity.page.BibleView
import net.bible.service.common.Logger

import org.json.JSONException
import org.json.JSONObject

open class Window (var windowLayout: WindowLayout, var pageManager: CurrentPageManager, var screenNo: Int) {

    constructor (currentPageManager: CurrentPageManager) :
            this(WindowLayout(WindowState.SPLIT), currentPageManager, 0)
    constructor(screenNo: Int, windowState: WindowState, currentPageManager: CurrentPageManager) :
            this(WindowLayout(windowState), currentPageManager, screenNo)

    init {
        pageManager.window = this
    }

    private var _justRestored = false

    var justRestored: Boolean
        get() {
            if(_justRestored) {
                justRestored = false
                return true
            }
            return false
        }
        set(value) {
            _justRestored = value
        }

    var isSynchronised = true
    var initialized = false
    var wasMinimized = false

    private val logger = Logger(this.javaClass.name)

    val isClosed: Boolean
        get() = windowLayout.state == WindowState.CLOSED

    var isMaximised: Boolean
        get() = windowLayout.state == WindowState.MAXIMISED
        set(maximise) = if (maximise) {
            windowLayout.state = WindowState.MAXIMISED
        } else {
            windowLayout.state = WindowState.SPLIT
        }

    val isVisible: Boolean
        get() = windowLayout.state != WindowState.MINIMISED && windowLayout.state != WindowState.CLOSED


    // if window is maximised then default operation is always to unmaximise
    val defaultOperation: WindowOperation
        get() = when {
            isMaximised -> WindowOperation.MAXIMISE
            isLinksWindow -> WindowOperation.CLOSE
            else -> WindowOperation.MINIMISE
        }

    val stateJson: JSONObject
        @Throws(JSONException::class)
        get() {
            val obj = JSONObject().apply {
                put("screenNo", screenNo)
                put("isSynchronised", isSynchronised)
                put("windowLayout", windowLayout.stateJson)
                put("pageManager", pageManager.stateJson)
            }
            return obj
        }

    open val isLinksWindow: Boolean
        get() = false

    var bibleView: BibleView? = null

    enum class WindowOperation {
        MAXIMISE, MINIMISE, RESTORE, CLOSE
    }

    @Throws(JSONException::class)
    fun restoreState(jsonObject: JSONObject) {
        try {
            this.screenNo = jsonObject.getInt("screenNo")
            this.isSynchronised = jsonObject.getBoolean("isSynchronised")
            this.windowLayout.restoreState(jsonObject.getJSONObject("windowLayout"))
            this.pageManager.restoreState(jsonObject.getJSONObject("pageManager"))
        } catch (e: Exception) {
            logger.warn("Window state restore error:" + e.message, e)
        }

    }

    override fun toString(): String {
        return "Window [screenNo=$screenNo]"
    }
}
