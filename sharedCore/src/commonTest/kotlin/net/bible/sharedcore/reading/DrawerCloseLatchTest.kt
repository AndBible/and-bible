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

package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerCloseLatchTest {

    @Test
    fun seededClosed_replayOfClosed_doesNotFire() {
        val latch = DrawerCloseLatch(initiallyOpen = false)
        assertFalse(latch.observe(false))
        assertFalse(latch.observe(false))
        assertFalse(latch.observe(false))
    }

    @Test
    fun openThenClose_firesExactlyOnce() {
        val latch = DrawerCloseLatch(initiallyOpen = false)
        assertFalse(latch.observe(true))
        assertTrue(latch.observe(false))
        assertFalse(latch.observe(false))
    }

    @Test
    fun seededOpen_firstCloseFires() {
        val latch = DrawerCloseLatch(initiallyOpen = true)
        assertTrue(latch.observe(false))
    }

    @Test
    fun repeatedOpenObservations_doNotFire() {
        val latch = DrawerCloseLatch(initiallyOpen = false)
        assertFalse(latch.observe(true))
        assertFalse(latch.observe(true))
        assertTrue(latch.observe(false))
    }

    @Test
    fun openCloseOpenClose_firesTwice() {
        val latch = DrawerCloseLatch(initiallyOpen = false)
        latch.observe(true)
        assertTrue(latch.observe(false))
        latch.observe(true)
        assertTrue(latch.observe(false))
    }
}
