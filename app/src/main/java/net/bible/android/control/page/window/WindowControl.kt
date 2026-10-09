/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.sharedcore.log.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.PageChange
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.CommonUtils
import net.bible.service.common.firstBibleDoc
import net.bible.service.sword.BookAndKey

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseKey


/**
 * Central control of windows especially synchronization
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class WindowControl constructor() {
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
    fun windowPosition(windowId: IdType) = windowRepository.windowList.indexOf(windowRepository.getWindow(windowId))

    // Synchronous on the emitter's thread (JS bridge, content load), like the bus's on { }.
    private val pageChangeSubscription = PassageChangeMediator.changes.subscribe { change ->
        if (change is PageChange.VerseChanged && change.window.windowRepository == windowRepository) {
            windowSync.synchronizeWindows(change.window)
        }
    }

    @VisibleForTesting
    internal var forceResyncCountForTest = 0

    /** Marks every window for resync and reloads the visible ones (after a settings change). */
    fun forceResyncAndReloadAll() {
        forceResyncCountForTest++
        try {
            windowSync.reloadAllWindows(force = true)
        } catch (e: Throwable) {
            Log.e(TAG, "reloadAllWindows failed", e)
        }
    }

    fun isActiveWindow(window: Window): Boolean = window == windowRepository.activeWindow

    open fun defaultBibleDoc(useLinks: Boolean  = true): SwordBook {
        val activeWindowBibleDoc = windowRepository.activeWindow.pageManager.currentBible.currentDocument as SwordBook?
        return activeWindowBibleDoc ?: firstBibleDoc
    }

    fun showLink(document: Book?, key: Key) {
        val linksWindow = activeWindow.targetLinksWindow
        val linksWindowWasVisible = linksWindow.isVisible

        if (!linksWindowWasVisible) {
            windowRepository.activeWindow = linksWindow
            linksWindow.windowState = WindowState.VISIBLE
        }

        // For non-specific links (document == null) we keep the links window's current
        // Bible so it remembers the chosen version (#2502). Only fall back to a default
        // Bible when the link is a verse key and the links window has no Bible document
        // yet — otherwise the verse could not be displayed (e.g. cross references opened
        // from an EPUB into a fresh links window).
        // A Bible cross reference opened from an EPUB arrives as a BookAndKey wrapping a
        // verse key (with a null document), so unwrap it to detect the verse link.
        val verseKey = if (key is BookAndKey) key.key else key
        val actualDocument = document ?: if (verseKey is VerseKey<*> &&
            linksWindow.pageManager.currentBible.currentDocument == null) {
            defaultBibleDoc()
        } else null

        linksWindow.pageManager.setCurrentDocumentAndKey(actualDocument, key)

        if (!linksWindowWasVisible) {
            windowRepository.notifyWindowsChanged()
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
        window.isLinksWindow = false
        pageManager.setCurrentDocumentAndKey(document, key)
        if(!window.isPinMode) {
            for (it in windowRepository.windowList.filter { !it.isPinMode && !it.isLinksWindow && it.id != window.id }) {
                it.windowState = WindowState.MINIMISED
            }
        }
        windowRepository.notifyWindowsChanged()
        return window
    }

    fun minimiseWindow(window: Window, force: Boolean = false) {
        if(force || isWindowMinimizable(window)) {
            windowRepository.minimise(window)

            // redisplay the current page
            windowRepository.notifyWindowsChanged()
        }
    }

    fun closeWindow(window: Window) {
        if (isWindowRemovable(window)) {
            Log.i(TAG, "Closing window " + window.id)
            windowRepository.close(window)

            val visibleWindows = windowRepository.visibleWindows
            if (visibleWindows.count() == 1) visibleWindows[0].weight = 1.0F

            // redisplay the current page
            windowRepository.notifyWindowsChanged()
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

            if(!window.isPinMode && !window.isLinksWindow) {
                for (it in windowRepository.windowList.filter { !it.isPinMode && !it.isLinksWindow }) {
                    it.windowState = WindowState.MINIMISED
                }
            }

            window.windowState = WindowState.VISIBLE
            window.updateOrScroll()
            if (activeWindow.isSynchronised)
                windowRepository.lastSyncWindowId = activeWindow.id

            windowRepository.notifyWindowsChanged()
            activeWindow = window
        }
    }

    /*
	 * Move the current window to first
	 */

    /** screen orientation has changed  */
    fun orientationChange() {
        // causes BibleViews to be created and laid out
        windowRepository.notifyWindowsChanged()
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
        windowRepository.notifyWindowsChanged()
    }

    fun setPinMode(window: Window, value: Boolean) {
        window.isPinMode = value
        if(value && !window.isVisible) {
            restoreWindow(window)
        } else if(!value && window.isVisible && windowRepository.visibleWindows.filter {!it.isPinMode}.size > 1) {
            minimiseWindow(window, true)
        }
        windowRepository.notifyWindowsChanged()
    }

    fun maximiseWindow(window: Window) {
        windowRepository.maximizedWindowId = window.id
        windowSync.reloadAllWindows()
        windowRepository.notifyWindowsChanged()
    }

    fun unMaximise() {
        windowRepository.maximizedWindowId = null
        windowSync.reloadAllWindows()
        windowRepository.notifyWindowsChanged()
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
        windowRepository.notifyWindowsChanged()
    }

    val scope get() = CurrentActivityHolder.currentActivity!!.lifecycleScope

    /**
     * The copy-settings picker (spec §3.2 finding 9 — one of three hand-written copies of the same
     * select-all/none toggle). Converted to [Dialogs.multiselect] (Task 19): Cancel and "OK with
     * nothing checked" both now come back as an empty list, where the old `BooleanArray?` told them
     * apart (`null` on Cancel). Every caller below already treats an empty selection as "nothing to
     * copy" (or is made to, in this batch) — the `null` distinction bought no different USER-visible
     * behaviour, only an occasional redundant `updateAllWindowsTextDisplaySettings()` broadcast on an
     * explicit no-op selection, which this collapses away too.
     */
    private suspend fun chooseSettingsToCopy(window: Window): List<WorkspaceEntities.TextDisplaySettings.Types> {
        val context = CurrentActivityHolder.currentActivity!!
        val types = WorkspaceEntities.TextDisplaySettings.Types.values().toList()
        return Dialogs.multiselect(
            context,
            context.getString(R.string.copy_settings_title),
            types,
            itemToString = {
                // OptionsMenuItemInterface.title is nullable in general (a handful of item kinds
                // have none); every TextDisplaySettings.Types row does carry one in practice -- the
                // old AlertDialog.Builder.setMultiChoiceItems(Array<String?>, ...) call this replaces
                // was a Java API and never enforced the non-null Kotlin type multiselect()'s
                // itemToString needs, so this fallback is new only in the sense that it now has to be
                // written down, not in the sense that it can be reached.
                getPrefItem(SettingsBundle(level = SettingsLevel.WORKSPACE, workspaceId = windowRepository.id, workspaceName = windowRepository.name,
                    workspaceSettings = window.pageManager.textDisplaySettings, globalSettings = CommonUtils.globalTextDisplaySettings), it).title ?: it.name
            },
        )
    }


    fun copySettingsToWorkspace(window: Window)  = scope.launch(Dispatchers.Main) {
        val checkedTypes = chooseSettingsToCopy(window)
        if (checkedTypes.isEmpty()) return@launch
        val target = windowRepository.textDisplaySettings
        val source = window.pageManager.textDisplaySettings

        for (type in checkedTypes) {
            target.setValue(type, source.getValue(type))
        }

        windowRepository.updateAllWindowsTextDisplaySettings()
    }

    fun copySettingsToGlobal(window: Window) = scope.launch(Dispatchers.Main) {
        val dirtyTypes = chooseSettingsToCopy(window).toSet()
        // Reachable with nothing checked; an empty dirtyTypes would otherwise write the global row
        // back unchanged and run a full workspaces x windows x pageManager database scan for nothing.
        if (dirtyTypes.isEmpty()) return@launch
        val global = CommonUtils.globalTextDisplaySettings

        // Resolve through window -> workspace -> global before copying, so a window that INHERITS a
        // type cannot reset the global to the factory default by writing null into it.
        val resolved = WorkspaceEntities.TextDisplaySettings.actual(
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceSettings = windowRepository.textDisplaySettings,
            globalSettings = global,
        )
        val newGlobal = WorkspaceEntities.TextDisplaySettings.globalWithCopiedValues(global, resolved, dirtyTypes)
        CommonUtils.globalTextDisplaySettings = newGlobal

        windowRepository.propagateGlobalTextDisplaySettingsChange(dirtyTypes, newGlobal)
        windowRepository.updateAllWindowsTextDisplaySettings()
    }

    fun copySettingsToWindow(window: Window, order: Int) {
        val secondWindow = windowRepository.visibleWindows[order]

        scope.launch(Dispatchers.Main) {
            val checkedTypes = chooseSettingsToCopy(window)
            if (checkedTypes.isEmpty()) return@launch
            val target = secondWindow.pageManager.textDisplaySettings
            val source = window.pageManager.textDisplaySettings

            for (type in checkedTypes) {
                target.setValue(type, source.getValue(type))
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
        windowRepository.notifyWindowChanged(window)
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
        const val TAG = "WindowControl"
    }
}
