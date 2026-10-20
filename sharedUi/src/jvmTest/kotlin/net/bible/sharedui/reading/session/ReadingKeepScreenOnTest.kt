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

package net.bible.sharedui.reading.session

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingKeepScreenOnTest {
    private val applied = mutableListOf<Boolean>()
    private var pref = true

    private fun make() = ReadingKeepScreenOn(read = { pref }, apply = { applied += it })

    @Test
    fun keepScreenOnOnlyWhileReading() {
        val k = make()
        k.refresh()
        assertEquals(listOf(false), applied, "not reading: the timer stays enabled")
        k.setReading(true)
        assertEquals(listOf(false, true), applied)
        k.setReading(false)
        assertEquals(listOf(false, true, false), applied)
    }

    @Test
    fun backgroundingResetsAndForegroundRestores() {
        val k = make()
        k.setReading(true)
        k.setActive(false)
        assertEquals(listOf(true, false), applied)
        k.setActive(true)
        assertEquals(listOf(true, false, true), applied)
    }

    @Test
    fun preferenceChangeAppliesOnlyWhileReadingAndNoDuplicateCalls() {
        val k = make()
        pref = false
        k.setReading(true)
        k.setReading(true)
        pref = true
        k.refresh()
        k.refresh()
        assertEquals(listOf(false, true), applied)
    }

    @Test
    fun leavingReadingWithPreferenceOffAppliesNothingNew() {
        pref = false
        val k = make()
        k.setReading(true)
        k.setReading(false)
        assertEquals(listOf(false), applied)
    }
}
