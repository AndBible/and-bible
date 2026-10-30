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
package net.bible.sharedui.desktop.webengine.kcef

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter

/**
 * Keeps Chromium's own context menu (back, reload, view source...) from ever showing: the menu model is emptied before
 * it is built into a menu. The reading pages draw AndBible's selection menu themselves, from the page's `contextmenu`
 * event.
 */
internal class SuppressChromiumMenuHandler : CefContextMenuHandlerAdapter() {
    override fun onBeforeContextMenu(browser: CefBrowser?, frame: CefFrame?, params: CefContextMenuParams?, model: CefMenuModel?) {
        model?.clear()
    }
}
