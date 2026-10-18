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


package net.bible.sharedui.reading.bridge

/** Platform seam for the per-window actions of a reading view surface (clipboard, share, selection, toasts). */
interface ReadingWindowSurface {
    fun copyText(label: String?, text: String)

    fun shareText(text: String, subject: String? = null)

    fun shareHtml(html: String, title: String)

    fun stopSelection(removeRanges: Boolean = false)

    fun toast(text: String)

    fun openWebSearch(query: String)
}
