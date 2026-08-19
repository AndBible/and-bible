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
package net.bible.android.view.activity.backup

import kotlinx.coroutines.test.runTest
import net.bible.android.control.backup.SaveOrShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The destination chooser is a suspend seam so a Compose host can supply its own dialog while the
 * classic path keeps the platform one. This test pins the seam's contract — a chooser returning
 * null means "cancelled" and must not be confused with SAVE — without booting an Activity, which
 * is what makes it worth having at all.
 */
class SaveOrShareDestinationTest {

    private suspend fun run(chooser: (suspend () -> SaveOrShare?)?): SaveOrShare? =
        chooser?.invoke()

    @Test
    fun `a chooser returning SHARE yields SHARE`() = runTest {
        assertEquals(SaveOrShare.SHARE, run { SaveOrShare.SHARE })
    }

    @Test
    fun `a chooser returning null means cancelled`() = runTest {
        assertNull(run { null })
    }

    @Test
    fun `no chooser at all falls through to the platform dialog path`() = runTest {
        assertNull(run(null))
    }
}
