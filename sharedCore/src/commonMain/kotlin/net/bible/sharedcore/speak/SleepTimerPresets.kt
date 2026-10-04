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

/** The one-tap sleep-timer durations, in minutes. */
val SLEEP_TIMER_PRESETS: List<Int> = listOf(5, 10, 15, 30, 45, 60)

/** The custom slider's bounds — the same 1..120 the classic `NumberPicker` offered. */
const val SLEEP_TIMER_MIN = 1
const val SLEEP_TIMER_MAX = 120

sealed interface SleepTimerSelection {
    data object Off : SleepTimerSelection
    data class Preset(val minutes: Int) : SleepTimerSelection
    /** A previously-set value that matches no preset. The page must show it (and open the custom
     *  slider on it) rather than silently rendering as if nothing were set. */
    data class Custom(val minutes: Int) : SleepTimerSelection
}

fun sleepTimerSelectionFor(minutes: Int): SleepTimerSelection = when {
    minutes <= 0 -> SleepTimerSelection.Off
    minutes in SLEEP_TIMER_PRESETS -> SleepTimerSelection.Preset(minutes)
    else -> SleepTimerSelection.Custom(minutes)
}
