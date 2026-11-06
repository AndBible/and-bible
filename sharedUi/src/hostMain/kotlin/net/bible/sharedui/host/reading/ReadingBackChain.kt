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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The shared reading host's back chain (Esc, Alt+Left and the mouse back button on desktop; the hardware back where the
 * host has one). It closes the topmost of: the drawer, then the search sheet (the results or the "index needed"
 * prompt), then reopens results after a result tap, then closes search mode, as Android does; only when none applies the press is passed on
 * to what is below the reading view.
 */
internal class ReadingBackChain(
    private val isDrawerOpen: () -> Boolean,
    private val closeDrawer: () -> Unit,
    private val isSearchSheetVisible: () -> Boolean,
    private val closeSearchSheet: () -> Unit,
    private val isSearchBarOpen: () -> Boolean,
    private val closeSearchBar: () -> Unit,
    private val reopenSearchResults: () -> Boolean = { false },
    private val observeOpen: @Composable () -> Boolean = { true },
) {
    /**
     * Whether a back press would close something here, observed in composition: the drawer, the search sheet or search
     * mode (a pending results reopen exists only while search mode is on). The reading destination enables its back
     * handler only then, so the iOS edge-swipe back recognizer stays off over the plain Bible text.
     */
    @Composable
    fun hasOpenLayer(): Boolean = observeOpen()

    /** One back press: closes the topmost of the three, or calls [passThrough] when none is open. */
    fun onBack(passThrough: () -> Unit) {
        when {
            isDrawerOpen() -> closeDrawer()
            isSearchSheetVisible() -> closeSearchSheet()
            reopenSearchResults() -> Unit
            isSearchBarOpen() -> closeSearchBar()
            else -> passThrough()
        }
    }
}

/**
 * The reading destination's `onBack`, bound late: the destination's deps are built before the [HostReadingHost] exists
 * (it is created on first composition of the destination), so they call this route, which passes the press through until
 * the host has set [chain].
 */
internal class ReadingBackRoute {
    /** Snapshot state, so a destination composed before the host exists re-reads [hasOpenLayer] once it is bound. */
    var chain: ReadingBackChain? by mutableStateOf(null)

    /** False until the host exists (a press would only pass through), then the chain's [ReadingBackChain.hasOpenLayer]. */
    @Composable
    fun hasOpenLayer(): Boolean = chain?.hasOpenLayer() ?: false

    fun onBack(passThrough: () -> Unit) {
        val c = chain
        if (c == null) passThrough() else c.onBack(passThrough)
    }
}
