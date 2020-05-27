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

import android.app.AlertDialog
import android.util.Log
import android.widget.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.EventManager
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.Logger

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    val isSingleWindow get () = !windowRepository.isMultiWindow && windowRepository.minimisedWindows.isEmpty() && !windowRepository.isMaximized

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


    fun addNewWindow(sourceWindow: Window): Window {
        val window = windowRepository.addNewWindow(sourceWindow)

        restoreWindow(window, true)

        return window
    }

    fun addNewWindow(document: Book, key: Key): Window {
        val window = windowRepository.addNewWindow()
        val pageManager = window.pageManager
        window.isSynchronised = false
        pageManager.setCurrentDocumentAndKey(document, key)

        restoreWindow(window, true)

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
        if (windowRepository.dedicatedLinksWindow.isVisible && !window.isLinksWindow) {
            normalWindows--
        }

        val canMinimize =  normalWindows > 1

        return !window.isMinimised && canMinimize
    }

    fun isWindowRemovable(window: Window): Boolean {
        var normalWindows = windowRepository.windows.size
        if (windowRepository.dedicatedLinksWindow.isVisible) {
            normalWindows--
        }

        return window.isLinksWindow || normalWindows > 1
    }

    fun restoreWindow(window: Window, force: Boolean = false) {
        if(window.isVisible && !force) {
            minimiseWindow(window)
        } else {
            if (window == activeWindow) return
            window.restoreOngoing = true

            if(!window.isPinMode && !window.isLinksWindow) {
                for (it in windowRepository.windowList.filter { !it.isPinMode }) {
                    it.windowState = WindowState.MINIMISED
                }
            }

            window.windowState = WindowState.SPLIT

            windowSync.synchronizeWindows()
            windowSync.reloadAllWindows()

            if (activeWindow.isSynchronised)
                windowRepository.lastSyncWindowId = activeWindow.id

            eventManager.post(NumberOfWindowsChangedEvent())
            activeWindow = window
            window.restoreOngoing = false
        }
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

    var isSeparatorMoving = false
        set(value) {
        field = value
        val isMoveFinished = !value
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
        } else if(!value && window.isVisible && windowRepository.visibleWindows.filter {!it.isPinMode}.size > 1) {
            minimiseWindow(window, true)
        }
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    fun maximiseWindow(window: Window) {
        windowRepository.maximizedWindowId = window.id
        windowSync.reloadAllWindows()
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    fun unMaximise() {
        windowRepository.maximizedWindowId = null
        windowSync.reloadAllWindows()
        eventManager.post(NumberOfWindowsChangedEvent())
    }

    fun hasMoveItems(window: Window): Boolean {
        return windowRepository.windowList.filter {it.isPinMode == window.isPinMode}.size > 1
    }

    fun autoPinChanged() {
        val unpinnedWindows = windowRepository.windowList.filter {!it.isPinMode}
        if(unpinnedWindows.size > 1) {
            for (i in 1 until unpinnedWindows.size) {
                windowRepository.minimise(unpinnedWindows[i])
            }
        }
        ABEventBus.getDefault().post(NumberOfWindowsChangedEvent())
    }

    private suspend fun chooseSettingsToCopy(window: Window) = suspendCoroutine<BooleanArray?> {
        val context = CurrentActivityHolder.getInstance().currentActivity
        val items = WorkspaceEntities.TextDisplaySettings.Types.values().map {
            getPrefItem(SettingsBundle(windowRepository.id, windowRepository.name,
                window.pageManager.textDisplaySettings), it).title
        }.toTypedArray()

        val checkedItems = items.map { false }.toBooleanArray()
        val dialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.okay) { d, _ ->
                it.resume(checkedItems)
            }
            .setMultiChoiceItems(items, checkedItems) { _, pos, value ->
                checkedItems[pos] = value
            }
            .setNeutralButton(R.string.select_all) { _, _ ->  it.resume(null) }
            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(null)}
            .setTitle(context.getString(R.string.copy_settings_title))
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allSelected = checkedItems.find { !it } == null
                val newValue = !allSelected
                val v = dialog.listView
                for(i in 0 until v.count) {
                    v.setItemChecked(i, newValue)
                    checkedItems[i] = newValue
                }
                (it as Button).text = context.getString(if(allSelected) R.string.select_all else R.string.select_none)
            }
        }
        dialog.show()
    }


    fun copySettingsToWorkspace(window: Window)  = GlobalScope.launch(Dispatchers.Main) {
        val types = WorkspaceEntities.TextDisplaySettings.Types.values()
        val checkedTypes = chooseSettingsToCopy(window) ?: return@launch
        val target = windowRepository.textDisplaySettings
        val source = window.pageManager.textDisplaySettings

        for ((tIdx, type) in types.withIndex()) {
            if(checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        windowRepository.updateVisibleWindowsTextDisplaySettings()
    }

    fun copySettingsToWindow(window: Window, order: Int) {
        val secondWindow = windowRepository.visibleWindows[order]

        GlobalScope.launch(Dispatchers.Main) {
            val types = WorkspaceEntities.TextDisplaySettings.Types.values()
            val checkedTypes = chooseSettingsToCopy(window) ?: return@launch
            val target = secondWindow.pageManager.textDisplaySettings
            val source = window.pageManager.textDisplaySettings

            for ((tIdx, type) in types.withIndex()) {
                if (checkedTypes[tIdx]) {
                    target.setValue(type, source.getValue(type))
                }
            }

            secondWindow.bibleView?.updateTextDisplaySettings()
        }
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
        const val TAG = "WindowControl"
    }
}
