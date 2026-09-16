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
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.service.common.CommonUtils

/**
 * Create Views for displaying documents
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 *
 * R5 (reading-host re-typing): retyped off `MainBibleActivity` onto the narrow
 * [ReadingHostActivity]. `windowControl` is genuine process-wide state (the `WindowControl` Koin
 * singleton) so it now reads through [CommonUtils] instead. `composeReadingViewHost` and
 * `bibleViewFactory` are per-host state that [ReadingHostActivity] deliberately does not expose
 * (spec: no `ReadingCommands`/`binding` on the interface) -- this class is only ever constructed
 * with a real `MainBibleActivity` today (`MainBibleActivity.kt`'s `DocumentViewManager(this)`),
 * so the two reads downcast to it. R6 replaces the downcasts when it wires a real host for these.
 */
class DocumentViewManager(val mainBibleActivity: ReadingHostActivity) {
    private val windowControl get() = CommonUtils.windowControl

    /**
     * Batch Z-late epilogue (spec 10.3): with the classic split gone, the only rebuild this class
     * still performs is forwarding a forced update to the Compose host. `documentView` survives
     * because six unguarded Compose-path callers read it.
     */
    fun buildView(forceUpdate: Boolean = false) {
        if (forceUpdate) (mainBibleActivity as MainBibleActivity).composeReadingViewHost?.rebuild()
    }

    val documentView: BibleView get() = getDocumentView(windowControl.activeWindow)

    /**
     * Takes an explicit `window` rather than reading the active one itself: a specific screen is
     * specified to prevent content going to the wrong screen if the active screen is changed fast.
     */
    private fun getDocumentView(window: Window): BibleView =
        (mainBibleActivity as MainBibleActivity).bibleViewFactory.getOrCreateBibleView(window)
}
