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
package net.bible.sharedui.desktop.assembly

import okio.Sink
import net.bible.sharedui.app.ports.DocumentRef

/**
 * Small host seams that the hoisted iOS classes take as function parameters and that have an owner in wave 3. They are
 * ports bound in Koin so that [buildDesktopHostPorts] stays frozen: the owning stream file binds the real one.
 */
interface DesktopBookmarkCsvFiles {
    /** A sink for the CSV export to [ref], null when it cannot be opened. */
    fun openSink(ref: DocumentRef): Sink?

    /** The file name stamp of a bookmark export, e.g. `2026-10-29_12-30`. */
    fun timestamp(): String
}

/** The bug-report flow of the reading drawer ("Report a bug"). Owner: D3d. */
fun interface DesktopBugReport {
    suspend fun report(cause: Throwable?)
}
