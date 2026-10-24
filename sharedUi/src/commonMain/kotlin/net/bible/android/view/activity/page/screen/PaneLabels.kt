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

package net.bible.android.view.activity.page.screen

import net.bible.android.control.page.window.Window
import net.bible.service.download.isStudyPad
import net.bible.service.sword.StudyPadKey

/**
 * The rail/tab button's label of [window]: the document's abbreviation, or the StudyPad's name (Android's
 * `ReadingCommands.windowLabelFor`). A blank `" "` when the page has no document or cannot be read.
 */
@kotlin.native.HiddenFromObjC
fun paneWindowLabel(window: Window?): String {
    window ?: return ""
    return try {
        val curdoc = window.pageManager.currentPage.currentDocument ?: return " "
        if (curdoc.isStudyPad) {
            (window.pageManager.currentPage.key as? StudyPadKey)?.getName() ?: " "
        } else {
            curdoc.abbreviation
        }
    } catch (e: Exception) { " " }
}

/** The button's tiny top row: the page title (Android's `windowTopLabelFor`); null renders no top row. */
@kotlin.native.HiddenFromObjC
fun paneWindowTopLabel(window: Window?): String? {
    window ?: return null
    return try {
        window.pageManager.titleText.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }
}
