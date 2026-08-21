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

import kotlin.test.Test
import kotlin.test.assertEquals

class SleepTimerPresetsTest {
    @Test fun zero_is_off() =
        assertEquals(SleepTimerSelection.Off, sleepTimerSelectionFor(0))

    @Test fun a_negative_value_is_also_off() =
        assertEquals(SleepTimerSelection.Off, sleepTimerSelectionFor(-5))

    @Test fun every_preset_selects_itself() {
        SLEEP_TIMER_PRESETS.forEach {
            assertEquals(SleepTimerSelection.Preset(it), sleepTimerSelectionFor(it))
        }
    }

    @Test fun the_preset_list_is_exactly_the_expected_set() =
        assertEquals(listOf(5, 10, 15, 30, 45, 60), SLEEP_TIMER_PRESETS)

    @Test fun a_non_preset_value_is_custom_and_keeps_its_minutes() =
        assertEquals(SleepTimerSelection.Custom(37), sleepTimerSelectionFor(37))

    @Test fun presets_are_ascending_and_inside_the_slider_range() {
        assertEquals(SLEEP_TIMER_PRESETS.sorted(), SLEEP_TIMER_PRESETS)
        SLEEP_TIMER_PRESETS.forEach { check(it in SLEEP_TIMER_MIN..SLEEP_TIMER_MAX) }
    }
}
