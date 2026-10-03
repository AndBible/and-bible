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

package net.bible.sharedcore.speak

import net.bible.sharedcore.ui.SheetPageStack

/**
 * One page of the Speak bottom sheet. A page names **what** is being shown, never a snapshot of a
 * value: values are always re-read from live state when the page renders.
 */
sealed interface SpeakSheetPage {
    /** The whole Speak settings list. */
    data object Settings : SpeakSheetPage

    /** Advanced (rarely-changed) settings — classic SpeakSettingsActivity's content. */
    data object Advanced : SpeakSheetPage

    /** Start/end overview for the repeat-passage verse range. */
    data object RepeatRange : SpeakSheetPage

    /** The passage grid, picking one endpoint. `end = false` picks the START verse. */
    data class PickVerse(val end: Boolean) : SpeakSheetPage

    /** Sleep-timer duration. */
    data object SleepTimer : SpeakSheetPage
}

class SpeakSheetStack : SheetPageStack<SpeakSheetPage>()
