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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.PageChange
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.common.firstBibleDoc
import net.bible.sharedcore.platform.AppCoroutineScope
import org.koin.mp.KoinPlatform
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

    /**
     * Read-only peek at the repository currently held, without creating one (unlike [windowRepository]'s
     * lazy getter). Lets the test database reset own the scope `Window.loadText` launches into.
     */
    @VisibleForTesting
    val currentWindowRepositoryOrNull: WindowRepository? get() = _windowRepository

    /** Drops [expected] if it is still the installed repository, so the lazy getter builds a live one next. */
    @VisibleForTesting
    fun forgetWindowRepositoryIfCurrent(expected: WindowRepository) {
        if (_windowRepository === expected) _windowRepository = null
    }

    open var windowRepository: WindowRepository
        get() = _windowRepository ?: WindowRepository(CoroutineScope(Dispatchers.Main)) .apply { _windowRepository = this }
        set(value) {
            _windowRepository = value
        }

    /**
     * Makes [new] the shared repository, first saving the previous owner's unsaved edits (CR-1 I1).
     * Whoever takes ownership flushes the previous owner, so correctness does not depend on the order
     * of the hosts' focus callbacks: the old owner's own focus-loss save is skipped once it no
     * longer owns the repository. The lazily created fallback (not [WindowRepository.initialized])
     * is never saved.
     *
     * @return whether the owner changed.
     */
    fun takeWindowRepository(new: WindowRepository): Boolean {
        val previous = windowRepository
        if (previous === new) return false
        if (previous.initialized) previous.saveIntoDb(false)
        windowRepository = new
        return true
    }

    /**
     * Retires the current repository at the end of a test, so the next test starts from a fresh lazy fallback
     * on its own database. The test JVM keeps this Koin singleton across tests: without this, the next host's
     * [takeWindowRepository] flushed the previous test's windows into the new database (FOREIGN KEY failure on
     * their history), and the fallback's scope -- which, unlike a host's lifecycleScope, nothing else cancels --
     * kept running the previous test's loadText/fetchDocument work into later tests.
     */
    @VisibleForTesting
    fun retireWindowRepositoryForTest() {
        _windowRepository?.scope?.coroutineContext?.get(kotlinx.coroutines.Job)?.cancel()
        _windowRepository = null
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
    var forceResyncCountForTest = 0

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
        // Mid workspace load there is no active window (reading it would throw): same fallback as a window without a Bible.
        if (windowRepository.loadingFromDb) return firstBibleDoc
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

    /**
     * The copy-settings picker; the Android implementation is `AndroidWindowSettingsPicker`. Resolved on each use
     * (this object is built while Koin is starting, and tests replace the Koin objects per test); tests assign a fake.
     */
    @VisibleForTesting
    var settingsPicker: WindowSettingsPicker = WindowSettingsPicker { w, t -> koinPicker.chooseSettingsToCopy(w, t) }
    private val koinPicker: WindowSettingsPicker get() = KoinPlatform.getKoin().get()

    private val globalSettingsStore: GlobalTextDisplaySettingsStore get() = KoinPlatform.getKoin().get()

    /**
     * The copy-settings launches replace the former `CurrentActivityHolder.currentActivity!!.lifecycleScope`:
     *  - the dialog runs inside [WindowSettingsPicker], which owns the activity it needs;
     *  - the copies themselves are in-memory mutations plus ordered write-through (workspace persistence, the
     *    global store), so they run on [AppCoroutineScope] (Main), and complete even if the activity goes away
     *    while the dialog is open.
     */
    private val appScope: AppCoroutineScope get() = KoinPlatform.getKoin().get()

    private suspend fun chooseSettingsToCopy(window: Window): List<WorkspaceEntities.TextDisplaySettings.Types> =
        settingsPicker.chooseSettingsToCopy(window, WorkspaceEntities.TextDisplaySettings.Types.values().toList())

    fun copySettingsToWorkspace(window: Window)  = appScope.launch(Dispatchers.Main) {
        val checkedTypes = chooseSettingsToCopy(window)
        if (checkedTypes.isEmpty()) return@launch
        val target = windowRepository.textDisplaySettings
        val source = window.pageManager.textDisplaySettings

        for (type in checkedTypes) {
            target.setValue(type, source.getValue(type))
        }

        windowRepository.updateAllWindowsTextDisplaySettings()
    }

    fun copySettingsToGlobal(window: Window) = appScope.launch(Dispatchers.Main) {
        val dirtyTypes = chooseSettingsToCopy(window).toSet()
        // Reachable with nothing checked; an empty dirtyTypes would otherwise write the global row
        // back unchanged and run a full workspaces x windows x pageManager database scan for nothing.
        if (dirtyTypes.isEmpty()) return@launch
        val global = globalSettingsStore.current

        // Resolve through window -> workspace -> global before copying, so a window that INHERITS a
        // type cannot reset the global to the factory default by writing null into it.
        val resolved = WorkspaceEntities.TextDisplaySettings.actual(
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceSettings = windowRepository.textDisplaySettings,
            globalSettings = global,
        )
        val newGlobal = WorkspaceEntities.TextDisplaySettings.globalWithCopiedValues(global, resolved, dirtyTypes)
        globalSettingsStore.set(newGlobal)

        windowRepository.propagateGlobalTextDisplaySettingsChange(dirtyTypes, newGlobal)
        windowRepository.updateAllWindowsTextDisplaySettings()
    }

    fun copySettingsToWindow(window: Window, order: Int) {
        val secondWindow = windowRepository.visibleWindows[order]

        appScope.launch(Dispatchers.Main) {
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
