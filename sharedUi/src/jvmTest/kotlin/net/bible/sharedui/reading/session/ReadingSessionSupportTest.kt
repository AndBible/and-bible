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
import kotlin.test.assertNull

/** The platform-free parts of the iOS session assembly: document offsets, active languages, the chooser guard. */
class ReadingSessionSupportTest {
    @Test
    fun offsetsFollowTheToolbarAndSafeArea() {
        var changes = 0
        var restore = false
        val offsets = ReadingOffsets({ restore }) { changes++ }
        // The toolbar is in-flow above the panes (and pads the status bar itself): nothing to reserve at the top.
        assertEquals(0f, offsets.top)
        assertEquals(0f, offsets.bottom)

        offsets.setSystemBottomInset(34f) // home indicator, in dp = CSS px
        assertEquals(34f, offsets.bottom)
        assertEquals(0f, offsets.top)
        assertEquals(1, changes)

        offsets.setSystemBottomInset(34f) // unchanged: no spurious set_offsets
        assertEquals(1, changes)

        offsets.setSearchSheet(visible = true, heightPx = 600, density = 3f) // 200 css px
        assertEquals(34f + 200f, offsets.bottom)
        offsets.setAgentLog(visible = true, heightDp = 48f)
        assertEquals(34f + 200f + 48f, offsets.bottom)
        // The speak bar sits below the panes and pads the safe area itself.
        offsets.setSpeakBarVisible(true)
        assertEquals(200f + 48f, offsets.bottom)
        offsets.setSearchSheet(visible = false, heightPx = 600, density = 3f)
        offsets.setAgentLog(visible = false, heightDp = 48f)
        restore = true // minimised-window restore row (Android's windowButtonHeight, 40dp)
        assertEquals(40f, offsets.bottom)
        restore = false
        assertEquals(0f, offsets.bottom)
        offsets.setSpeakBarVisible(false)
        assertEquals(34f, offsets.bottom)
    }

    @Test
    fun activeLanguagesFromInstalledBooks() {
        assertEquals("[\"en\",\"fi\"]", activeLanguagesJson(listOf("en", "fi", "en")))
        assertEquals("[]", activeLanguagesJson(emptyList()))
        assertEquals("[\"x\\\\\\\"y\"]", activeLanguagesJson(listOf("x\\\"y")))
    }

    @Test
    fun aClosedWindowsChooserTargetIsGone() {
        val open = mutableSetOf<Any?>("w1")
        val built = mutableListOf<Any?>()
        val targetFor = guardedChooserTarget(isOpen = { it in open }) { asker -> built += asker; "target-$asker" }
        assertEquals("target-w1", targetFor("w1"))
        open -= "w1" // the window was closed while the chooser was open
        assertNull(targetFor("w1"))
        assertNull(targetFor(null))
        assertEquals(listOf<Any?>("w1"), built) // never built a target over a dead page manager
    }
}
