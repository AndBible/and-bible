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
package net.bible.android.control

import net.bible.android.control.page.window.Window

/**
 * A change to what a window's page shows, emitted by [PassageChangeMediator.changes]:
 * verse selection, window verse changes and completed content loads
 * (spec `2026-10-07-abeventbus-phase-7-page-passage-history-design.md` §2.1).
 */
sealed interface PageChange {
    /** The window's verse or document changed (page change, scroll, entry change). */
    data class VerseChanged(val window: Window) : PageChange
    /** The shared Bible verse was selected without a following [VerseChanged] (spec §2.2). */
    data object BibleVerseChanged : PageChange
    /** A window finished loading its content. */
    data object ContentLoaded : PageChange
}
