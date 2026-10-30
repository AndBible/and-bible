/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedui.desktop.reading

import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.host.app.IosRootLinks
import net.bible.sharedui.host.reading.IosReadingWindows
import org.koin.mp.KoinPlatform

/**
 * The production [DesktopShortcutActions]: the active window and settings come from Koin, the reading actions go through
 * the hoisted [IosReadingWindows] (only for windows that have an assembly, so a shortcut never assembles one).
 *
 * The back and history seams are parameters so the fallback logic is testable without a Compose scene.
 */
class DesktopShortcutActionsImpl(
    private val dispatchBack: () -> Boolean = DesktopBackDispatcher::back,
    private val canGoBackInHistory: () -> Boolean = { KoinPlatform.getKoin().get<HistoryManager>().canGoBack() },
    private val goBackInHistory: () -> Unit = { KoinPlatform.getKoin().get<HistoryManager>().goBack() },
    closeModals: ((String?) -> Boolean)? = null,
) : DesktopShortcutActions {
    private val closeModalsSeam: (String?) -> Boolean = closeModals ?: { closePageModals(it) }

    private fun koin() = KoinPlatform.getKoin()

    override fun isOnReadingScreen() = IosRootLinks.isOnReadingScreen()

    override fun activeWindowId(): String? = runCatching { koin().get<WindowControl>().activeWindow.id.toString() }.getOrNull()

    private fun key(windowId: String, name: String) {
        if (IosReadingWindows.hasAssembly(windowId)) IosReadingWindows.bridgeFor(windowId).onKeyDown(name)
    }

    override fun search(windowId: String) = key(windowId, "CtrlKeyF")

    // The page's Ctrl+B: the key chooser ("go to reference").
    override fun goToReference(windowId: String) = key(windowId, "CtrlKeyB")

    override fun back(historyFallback: Boolean) {
        if (dispatchBack()) return
        if (historyFallback && canGoBackInHistory()) goBackInHistory()
    }

    override fun navigateBack(windowId: String?) {
        // A Compose overlay (settings, a sheet) on top owns Back; the reading screen passes it through to the page modals.
        if (dispatchBack()) return
        if (closeModalsSeam(windowId)) return
        if (canGoBackInHistory()) goBackInHistory()
    }

    override fun bookmarkSelection(windowId: String, selectionJson: String) {
        // Without a selection menu the key still does something useful: the page's Ctrl+B, the key chooser.
        val menu = IosReadingWindows.selectionMenu ?: return goToReference(windowId)
        menu.cacheSelection(windowId, selectionJson)
        menu.run(windowId, SelectionMenuItem.AddBookmarkSelection)
    }

    override fun changeFontSize(change: FontSizeChange, steps: Int) {
        val settings = koin().get<AppSettings>()
        val current = settings.getInt(FONT_SIZE_KEY, FontSizeChange.FONT_DEFAULT)
        val next = change.apply(current, steps)
        if (next == current) return
        settings.setInt(FONT_SIZE_KEY, next)
        IosReadingWindows.nightModeChanged()
    }

    override fun focusNextWindow() = koin().get<WindowControl>().focusNextWindow()

    override fun focusPreviousWindow() = koin().get<WindowControl>().focusPreviousWindow()

    override fun toggleFullScreen() {
        val id = activeWindowId() ?: return
        IosReadingWindows.gesturesFor(id)?.onDoubleTap()
    }

    override fun openSettings() = IosRootLinks.openRoute(NavRoutes.SETTINGS)

    override fun escapeAllowed(windowId: String): Boolean =
        !IosReadingWindows.hasAssembly(windowId) || !IosReadingWindows.sessionFor(windowId).editingTextInJs

    override fun closePageModals(windowId: String?): Boolean {
        val id = windowId ?: activeWindowId() ?: return false
        if (!IosReadingWindows.hasAssembly(id)) return false
        val session = IosReadingWindows.sessionFor(id)
        if (!session.modalOpen) return false
        session.emit(BibleViewEvents.closeModals())
        return true
    }

    companion object {
        const val FONT_SIZE_KEY = "font_size_multiplier"
    }
}
