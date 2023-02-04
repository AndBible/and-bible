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

import android.app.AlertDialog
import android.util.Log
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
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
import net.bible.service.common.CommonUtils
import net.bible.service.common.firstBibleDoc

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
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
open class WindowControl @Inject constructor() {
    private var _windowRepository: WindowRepository? = null

    open var windowRepository: WindowRepository
        get() = _windowRepository ?: WindowRepository(CoroutineScope(Dispatchers.Main)) .apply { _windowRepository = this }
        set(value) {
            _windowRepository = value
        }

    val windowSync get() = windowRepository.windowSync
    val activeWindowPageManager: CurrentPageManager
        get() = activeWindow.pageManager

    val isMultiWindow: Boolean get() = windowRepository.isMultiWindow

    var activeWindow: Window
        get() = windowRepository.activeWindow
        set(currentActiveWindow) {
            windowRepository.activeWindow = currentActiveWindow
        }

    val activeWindowPosition get() = windowRepository.windowList.indexOf(activeWindow)
    fun windowPosition(windowId: Long) = windowRepository.windowList.indexOf(windowRepository.getWindow(windowId))
    val isSingleWindow get () = !windowRepository.isMultiWindow && windowRepository.minimisedWindows.isEmpty() && !windowRepository.isMaximized

    init {
        ABEventBus.register(this)
    }

    fun isActiveWindow(window: Window): Boolean = window == windowRepository.activeWindow

    /**
     * Show link using whatever is the current Bible in the Links window
     */
    fun showLinkUsingDefaultBible(key: Key) {
        showLink(defaultBibleDoc(true), key)
    }

    open fun defaultBibleDoc(useLinks: Boolean  = true): SwordBook {
        //val linksBiblePage = windowRepository.primaryLinksWindow?.pageManager?.currentBible
        val activeWindowBibleDoc = windowRepository.activeWindow.pageManager.currentBible.currentDocument as SwordBook?
        return activeWindowBibleDoc ?: firstBibleDoc

        //return if (useLinks && linksBiblePage.isCurrentDocumentSet) linksBiblePage.currentDocument!! as SwordBook
        //       else activeWindowBibleDoc ?: firstBibleDoc
    }

    fun showLink(document: Book, key: Key) {
        val linksWindow = activeWindow.targetLinksWindow
        val linksWindowWasVisible = linksWindow.isVisible

        //linksWindow.initialiseLinksWindowPageStateIfClosed(activeWindow)

        if (!linksWindowWasVisible) {
            windowRepository.activeWindow = linksWindow
            linksWindow.windowState = WindowState.VISIBLE
        }

        linksWindow.pageManager.setCurrentDocumentAndKey(document, key)

        if (!linksWindowWasVisible) {
            ABEventBus.post(NumberOfWindowsChangedEvent())
        }
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

        return window
    }

    fun minimiseWindow(window: Window, force: Boolean = false) {
        if(force || isWindowMinimizable(window)) {
            windowRepository.minimise(window)

            // redisplay the current page
            ABEventBus.post(NumberOfWindowsChangedEvent())
        }
    }

    fun closeWindow(window: Window) {
        if (isWindowRemovable(window)) {
            Log.i(TAG, "Closing window " + window.id)
            windowRepository.close(window)

            val visibleWindows = windowRepository.visibleWindows
            if (visibleWindows.count() == 1) visibleWindows[0].weight = 1.0F

            // redisplay the current page
            ABEventBus.post(NumberOfWindowsChangedEvent())
            windowSync.reloadAllWindows()
        }
    }

    fun isWindowMinimizable(window: Window): Boolean {
        val numWindows = windowRepository.visibleWindows.size
        val canMinimize =  numWindows > 1
        return !window.isMinimised && canMinimize
    }

    fun isWindowRemovable(window: Window): Boolean {
        val numWindows = windowRepository.sortedWindows.size
        return numWindows > 1
    }

    fun restoreWindow(window: Window, force: Boolean = false) {
        if(window.isVisible && !force) {
            if(window.isLinksWindow && window.linksWindowNumber == 0 && !window.isPrimaryLinksWindow)
                closeWindow(window)
            else
                minimiseWindow(window)
        } else {
            if (window == activeWindow) return

            if(!window.isPinMode) {
                for (it in windowRepository.windowList.filter { !it.isPinMode }) {
                    it.windowState = WindowState.MINIMISED
                }
            }

            window.windowState = WindowState.VISIBLE

            window.updateTextIfNeeded()

            if (activeWindow.isSynchronised)
                windowRepository.lastSyncWindowId = activeWindow.id

            ABEventBus.post(NumberOfWindowsChangedEvent())
            activeWindow = window
        }
    }

    /*
	 * Move the current window to first
	 */

    /** screen orientation has changed  */
    fun orientationChange() {
        // causes BibleViews to be created and laid out
        ABEventBus.post(NumberOfWindowsChangedEvent())
    }

    fun onEvent(event: CurrentVerseChangedEvent) {
        if(event.window.windowRepository != windowRepository) return
        windowSync.synchronizeWindows(event.window)
    }

    fun onEvent(event: SynchronizeWindowsEvent) {
        if(event.forceSyncAll) {
            windowSync.setResyncRequired()
        } else {
            windowSync.setResyncBiblesRequired()
        }
        windowSync.reloadAllWindows()
    }

    var isSeparatorMoving = false
        set(value) {
        field = value
        val isMoveFinished = !value
        ABEventBus.post(WindowSizeChangedEvent(isMoveFinished))
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
            windowSync.synchronizeWindows(
                windowRepository.visibleWindows.firstOrNull { it.id != window.id && it.isSynchronised && it.isSyncable }
            )
        } else {
            window.isSynchronised = false
        }
    }

    fun moveWindow(window: Window, position: Int) {
        windowRepository.moveWindowToPosition(window, position)

        // redisplay the current page
        ABEventBus.post(NumberOfWindowsChangedEvent())
    }

    fun setPinMode(window: Window, value: Boolean) {
        window.isPinMode = value
        if(value && !window.isVisible) {
            restoreWindow(window)
        } else if(!value && window.isVisible && windowRepository.visibleWindows.filter {!it.isPinMode}.size > 1) {
            minimiseWindow(window, true)
        }
        ABEventBus.post(NumberOfWindowsChangedEvent())
    }

    fun maximiseWindow(window: Window) {
        windowRepository.maximizedWindowId = window.id
        windowSync.reloadAllWindows()
        ABEventBus.post(NumberOfWindowsChangedEvent())
    }

    fun unMaximise() {
        val maximizedWindow = windowRepository.maximizedWindow
        windowRepository.maximizedWindowId = null
        windowSync.synchronizeWindows(maximizedWindow, noDelay = true)
        windowSync.reloadAllWindows()
        ABEventBus.post(NumberOfWindowsChangedEvent())
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
        ABEventBus.post(NumberOfWindowsChangedEvent())
    }

    val scope get() = CurrentActivityHolder.currentActivity!!.lifecycleScope

    private suspend fun chooseSettingsToCopy(window: Window) = suspendCoroutine {
        val context = CurrentActivityHolder.currentActivity!!
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
            .setOnCancelListener {_ -> it.resume(null)}
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
        CommonUtils.fixAlertDialogButtons(dialog)
    }


    fun copySettingsToWorkspace(window: Window)  = scope.launch(Dispatchers.Main) {
        val types = WorkspaceEntities.TextDisplaySettings.Types.values()
        val checkedTypes = chooseSettingsToCopy(window) ?: return@launch
        val target = windowRepository.textDisplaySettings
        val source = window.pageManager.textDisplaySettings

        for ((tIdx, type) in types.withIndex()) {
            if(checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        windowRepository.updateAllWindowsTextDisplaySettings()
    }

    fun copySettingsToWindow(window: Window, order: Int) {
        val secondWindow = windowRepository.visibleWindows[order]

        scope.launch(Dispatchers.Main) {
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

    fun focusNextWindow() {
        val pos = windowRepository.visibleWindows.indexOf(activeWindow)
        activeWindow = windowRepository.visibleWindows[(pos + 1) % windowRepository.visibleWindows.size]
    }

    fun focusPreviousWindow() {
        val pos = windowRepository.visibleWindows.indexOf(activeWindow)
        val s = windowRepository.visibleWindows.size
        activeWindow = windowRepository.visibleWindows[(pos - 1 + s) % s]
    }

    fun changeSyncGroup(window: Window, groupNumber: Int) {
        window.isSynchronised = true
        window.syncGroup = groupNumber
        windowSync.synchronizeWindows(
            windowRepository.visibleWindows.firstOrNull { it.id != window.id && it.isSynchronised && it.isSyncable && it.syncGroup == window.syncGroup }
        )
        ABEventBus.post(WindowChangedEvent(window))
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
        const val TAG = "WindowControl"
    }
}
