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
package net.bible.android.view.activity.page.screen

import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity

/**
 * Create Views for displaying documents
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentViewManager(val mainBibleActivity: MainBibleActivity) {
    private val windowControl get() = mainBibleActivity.windowControl

    fun destroy() {}

    /**
     * Batch Z-late epilogue (spec 10.3): with the classic split gone, the only rebuild this class
     * still performs is forwarding a forced update to the Compose host. `documentView` survives
     * because six unguarded Compose-path callers read it.
     */
    fun buildView(forceUpdate: Boolean = false) {
        if (forceUpdate) mainBibleActivity.composeReadingViewHost?.rebuild()
    }

    val documentView: BibleView get() = getDocumentView(windowControl.activeWindow)

    /**
     * Takes an explicit `window` rather than reading the active one itself: a specific screen is
     * specified to prevent content going to the wrong screen if the active screen is changed fast.
     */
    private fun getDocumentView(window: Window): BibleView =
        mainBibleActivity.bibleViewFactory.getOrCreateBibleView(window)
}
