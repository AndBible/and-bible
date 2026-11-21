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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.toolbar

import net.bible.android.control.page.CurrentPage
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.KeyChooserPage
import net.bible.sharedcore.reading.KeyChooserRoute
import org.crosswire.jsword.passage.Key

/** What a title tap opens: a quick sheet over the reading view, or the full-screen chooser. */
@kotlin.native.HiddenFromObjC
sealed interface KeyChooserOpening {
    /** The quick sheet of [kind] over [keys] (resolved once; a row's id is its index in them). */
    data class Sheet(val kind: KeyChooserKind, val keys: List<Key>) : KeyChooserOpening

    /** The page has no sheet, or the sheet would be empty: start the full-screen chooser. */
    data object FullScreen : KeyChooserOpening
}

/**
 * Android's `ReadingCommands.composeStartKeyChooser` decision, platform-free: [KeyChooserRoute.sheetFor] says whether
 * [page] has a quick sheet; its keys are resolved ONCE through [resolveKeys] (never again while its rows are on screen);
 * a sheet with no rows (the Grid has none by design) falls back to the full screen, as does a page nothing recognises.
 *
 * Returns null when [stillCurrent] says the page changed while the keys were being resolved: nothing is opened then.
 */
@kotlin.native.HiddenFromObjC
suspend fun resolveKeyChooserOpening(
    page: KeyChooserPage?,
    resolveKeys: suspend (KeyChooserKind) -> List<Key>,
    stillCurrent: () -> Boolean,
): KeyChooserOpening? {
    val sheet = page?.let { KeyChooserRoute.sheetFor(it) } ?: return KeyChooserOpening.FullScreen
    val keys = resolveKeys(sheet.kind)
    if (!stillCurrent()) return null
    return if (sheet.kind == KeyChooserKind.Grid || keys.isNotEmpty()) KeyChooserOpening.Sheet(sheet.kind, keys) else KeyChooserOpening.FullScreen
}

/**
 * Makes [page]'s window the active one, so a key-chooser quick sheet opened for it answers it: the sheets read and
 * apply to the active window. Every Bible/commentary key-chooser entry point opens the passage sheet (#4169), also one
 * asked for by a window that is not active (the page's Ctrl+B in another pane, `CurrentPageManager`'s auto-open).
 *
 * Returns whether the sheet may be shown. A window that is closed, minimised or hidden behind a maximised window is
 * not on screen: it is left alone (the user's active window does not change) and no sheet is opened for it, e.g. when
 * an AI tool switches a background window to another document.
 */
@kotlin.native.HiddenFromObjC
fun activateWindowOf(page: CurrentPage): Boolean {
    val window = page.pageManager.window
    val repository = window.windowRepository
    if (window !in repository.windowList || !window.isVisible) return false
    if (repository.activeWindow !== window) repository.activeWindow = window
    return true
}
