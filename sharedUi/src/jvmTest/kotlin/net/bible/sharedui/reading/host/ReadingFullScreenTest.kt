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
package net.bible.sharedui.reading.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingFullScreenTest {
    @Test
    fun toggleEntersAndLeaves() {
        val fullScreen = ReadingFullScreen()
        assertFalse(fullScreen.state.value)
        fullScreen.toggle()
        assertTrue(fullScreen.state.value)
        fullScreen.toggle()
        assertFalse(fullScreen.state.value)
    }

    @Test
    fun leavingClearsDoubleTapLock() {
        val fullScreen = ReadingFullScreen()
        fullScreen.set(true, byDoubleTap = true)
        assertTrue(fullScreen.lockedByDoubleTap)
        fullScreen.set(false)
        assertFalse(fullScreen.lockedByDoubleTap)
        // The next entry by another route does not inherit the lock.
        fullScreen.set(true)
        assertFalse(fullScreen.lockedByDoubleTap)
    }

    @Test
    fun enteringByAnotherRouteKeepsAnExistingLockUntilLeaving() {
        val fullScreen = ReadingFullScreen()
        fullScreen.set(true, byDoubleTap = true)
        fullScreen.set(true) // already in full screen: not a change, the lock stays
        assertTrue(fullScreen.lockedByDoubleTap)
        assertEquals(true, fullScreen.state.value)
    }

    @Test
    fun leavingWhenNotInFullScreenIsHarmless() {
        val fullScreen = ReadingFullScreen()
        fullScreen.set(false)
        assertFalse(fullScreen.state.value)
        assertFalse(fullScreen.lockedByDoubleTap)
    }
}
