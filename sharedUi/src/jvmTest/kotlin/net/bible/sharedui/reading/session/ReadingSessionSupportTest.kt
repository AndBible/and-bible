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
    fun topOffsetIsTheSafeAreaOnlyWhenReported() {
        var changes = 0
        val offsets = ReadingOffsets { changes++ }
        assertEquals(0f, offsets.top)
        offsets.setSystemTopInset(59f) // full screen on an island iPhone
        assertEquals(59f, offsets.top)
        assertEquals(1, changes)
        offsets.setSystemTopInset(59f)
        assertEquals(1, changes)
        offsets.setSystemTopInset(0f) // left full screen: the toolbar pads again
        assertEquals(0f, offsets.top)
        assertEquals(2, changes)
    }

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

        offsets.setAgentLog(visible = true)
        assertEquals(0f, offsets.bottom)
        // The speak bar sits below the panes and pads the safe area itself.
        offsets.setSpeakBarVisible(true)
        assertEquals(0f, offsets.bottom)
        offsets.setAgentLog(visible = false)
        restore = true // minimised-window restore row (Android's windowButtonHeight, 40dp)
        assertEquals(40f, offsets.bottom)
        restore = false
        assertEquals(0f, offsets.bottom)
        offsets.setSpeakBarVisible(false)
        assertEquals(34f, offsets.bottom)
    }

    @Test
    fun visibleAgentPanelReservesOnlyTheRail() {
        for (inset in listOf(0f, 34f)) {
            for (restore in listOf(false, true)) {
                val offsets = ReadingOffsets(restoreButtonsVisible = { restore })
                offsets.setSystemBottomInset(inset)
                offsets.setAgentLog(visible = true)
                assertEquals(if (restore) 40f else 0f, offsets.bottom, "inset=$inset, restore=$restore")
                offsets.setAgentLog(visible = false)
                assertEquals(if (restore) inset + 40f else inset, offsets.bottom, "hidden panel restores the inset")
            }
        }
    }

    @Test
    fun eitherBottomBarOwnsTheSafeArea() {
        val offsets = ReadingOffsets(restoreButtonsVisible = { true })
        offsets.setSystemBottomInset(34f)
        offsets.setAgentLog(visible = true)
        offsets.setSpeakBarVisible(true)
        assertEquals(40f, offsets.bottom)
        offsets.setSpeakBarVisible(false)
        assertEquals(40f, offsets.bottom, "the agent panel takes inset ownership from the speak bar")
        offsets.setSpeakBarVisible(true)
        offsets.setAgentLog(visible = false)
        assertEquals(40f, offsets.bottom, "the speak bar owns the inset without the agent panel")
        offsets.setSpeakBarVisible(false)
        assertEquals(74f, offsets.bottom, "without either bar the pane must clear the safe area")
    }

    @Test
    fun agentVisibilityChangesRefreshOffsetsWithoutSpuriousRecompositionUpdates() {
        var changes = 0
        val offsets = ReadingOffsets(onChanged = { changes++ })
        offsets.setAgentLog(visible = true)
        assertEquals(1, changes)
        offsets.setAgentLog(visible = true)
        assertEquals(1, changes)
        offsets.setAgentLog(visible = false)
        assertEquals(2, changes)
        offsets.setAgentLog(visible = false)
        assertEquals(2, changes)
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

    /** iPad rotation: the inset is the same in both orientations, but the bottom window can change. */
    @Test
    fun aContainerSizeChangeResendsTheOffsetsEvenWhenNoTermChanged() {
        var changes = 0
        val offsets = ReadingOffsets { changes++ }
        offsets.setSystemBottomInset(20f)
        changes = 0
        offsets.setContainerSize(820, 1180) // the first report only records
        assertEquals(0, changes)
        offsets.setContainerSize(820, 1180) // unchanged
        assertEquals(0, changes)
        offsets.setContainerSize(1180, 820) // rotated
        assertEquals(1, changes)
        assertEquals(20f, offsets.bottom, "no term changed")
    }
}
