/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.crosswire.jsword.book.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slice 8 §4: "at least one UNLOCKED Bible" -- the one predicate all three gates read. */
class UsableBibleTest {
    private fun bible(locked: Boolean): Book = mock { on { isLocked } doReturn locked }

    @Test fun noBibleIsNotUsable() = assertFalse(hasUsableBible(emptyList()))
    @Test fun onlyLockedBiblesAreNotUsable() = assertFalse(hasUsableBible(listOf(bible(true), bible(true))))
    @Test fun oneUnlockedBibleIsEnough() = assertTrue(hasUsableBible(listOf(bible(true), bible(false))))
}
