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
package net.bible.sharedui.host.reading

/**
 * The shared reading host's back chain (Esc, Alt+Left and the mouse back button on desktop; the hardware back where the
 * host has one). It closes the topmost of: the drawer, then the search sheet (the results or the "index needed"
 * prompt), then search mode itself, as the Android back does; only when none of them is open the press is passed on
 * to what is below the reading view.
 */
internal class ReadingBackChain(
    private val isDrawerOpen: () -> Boolean,
    private val closeDrawer: () -> Unit,
    private val isSearchSheetVisible: () -> Boolean,
    private val closeSearchSheet: () -> Unit,
    private val isSearchBarOpen: () -> Boolean,
    private val closeSearchBar: () -> Unit,
) {
    /** One back press: closes the topmost of the three, or calls [passThrough] when none is open. */
    fun onBack(passThrough: () -> Unit) {
        when {
            isDrawerOpen() -> closeDrawer()
            isSearchSheetVisible() -> closeSearchSheet()
            isSearchBarOpen() -> closeSearchBar()
            else -> passThrough()
        }
    }
}
