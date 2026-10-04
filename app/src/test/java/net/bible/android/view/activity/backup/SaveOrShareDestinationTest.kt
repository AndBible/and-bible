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
package net.bible.android.view.activity.backup

import kotlinx.coroutines.test.runTest
import net.bible.android.control.backup.SaveOrShare
import net.bible.android.control.backup.resolveDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The destination chooser is a suspend seam so a Compose host can supply its own dialog while the
 * classic path keeps the platform one. This test pins the seam's contract against the REAL
 * [resolveDestination] (not a re-implementation of it) — the C1 whole-branch review defect was
 * exactly that the previous version of this test asserted on its own copy of the bug instead of
 * on production code, so it stayed green while the app re-opened the platform dialog after the
 * Compose one was cancelled.
 */
class SaveOrShareDestinationTest {

    @Test
    fun `a chooser returning SHARE yields SHARE and does not run the platform prompt`() = runTest {
        var platformRan = false
        val result = resolveDestination(
            chooseDestination = { SaveOrShare.SHARE },
            platformPrompt = { platformRan = true; SaveOrShare.SAVE },
        )
        assertEquals(SaveOrShare.SHARE, result)
        assertFalse("a supplied chooser must not fall through to the platform prompt", platformRan)
    }

    @Test
    fun `a chooser returning SAVE yields SAVE and does not run the platform prompt`() = runTest {
        var platformRan = false
        val result = resolveDestination(
            chooseDestination = { SaveOrShare.SAVE },
            platformPrompt = { platformRan = true; SaveOrShare.SHARE },
        )
        assertEquals(SaveOrShare.SAVE, result)
        assertFalse("a supplied chooser must not fall through to the platform prompt", platformRan)
    }

    @Test
    fun `a cancelled chooser does not fall through to the platform prompt`() = runTest {
        var platformRan = false
        val result = resolveDestination(
            chooseDestination = { null },
            platformPrompt = { platformRan = true; SaveOrShare.SAVE },
        )
        assertNull(result)
        assertFalse("cancelling the Compose dialog must abort, not re-ask", platformRan)
    }

    @Test
    fun `a null chooser falls through to the platform dialog path`() = runTest {
        val result = resolveDestination(
            chooseDestination = null,
            platformPrompt = { SaveOrShare.SHARE },
        )
        assertEquals(SaveOrShare.SHARE, result)
    }
}
