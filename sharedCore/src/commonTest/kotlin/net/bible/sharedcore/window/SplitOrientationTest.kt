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

package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A/B F6-B1. The split used to flip from stacked to side-by-side the moment the keyboard opened,
 * because the orientation is decided from the MEASURED height and the IME shrinks it (on API 35+ via
 * `MainBibleActivity`'s padding on the Compose container, below it via `ADJUST_RESIZE`). Classic never
 * had this: it read the configuration orientation, which no keyboard can change.
 */
class SplitOrientationTest {
    @Test
    fun landscapeSplitsSideBySide() {
        assertTrue(splitIsHorizontal(1000f, 500f, reverseSplitMode = false, imeVisible = false, previous = null))
    }

    @Test
    fun portraitStacks() {
        assertFalse(splitIsHorizontal(500f, 1000f, reverseSplitMode = false, imeVisible = false, previous = null))
    }

    @Test
    fun reverseSplitModeInvertsBoth() {
        assertFalse(splitIsHorizontal(1000f, 500f, reverseSplitMode = true, imeVisible = false, previous = null))
        assertTrue(splitIsHorizontal(500f, 1000f, reverseSplitMode = true, imeVisible = false, previous = null))
    }

    // THE BUG. Portrait 500x1000 stacked; the keyboard eats 600px of height, so the measured box is
    // 500x400 and the raw formula would say "side by side". The latch must hold the stacked answer.
    @Test
    fun aVisibleImeHoldsTheStackedAnswerEvenWhenTheBoxBecomesWiderThanTall() {
        assertFalse(splitIsHorizontal(500f, 400f, reverseSplitMode = false, imeVisible = true, previous = false))
    }

    // The mirror case, and it needs `reverseSplitMode` to exist at all: losing height can only flip
    // stacked -> side-by-side, so with reverse OFF there is no pair where the raw formula would turn a
    // side-by-side split into a stacked one. With reverse ON, portrait 500x1000 computes side-by-side
    // and the IME shrink to 500x400 would compute stacked — so this is what the latch has to hold.
    @Test
    fun aVisibleImeHoldsTheSideBySideAnswerToo() {
        assertTrue(splitIsHorizontal(500f, 400f, reverseSplitMode = true, imeVisible = true, previous = true))
    }

    // First composition with the keyboard ALREADY up (e.g. the activity was recreated while search
    // was open). There is nothing to latch, so it must compute rather than pick a default.
    @Test
    fun withNoPreviousAnswerAVisibleImeStillComputes() {
        assertTrue(splitIsHorizontal(500f, 400f, reverseSplitMode = false, imeVisible = true, previous = null))
    }

    // With the keyboard hidden a genuine size change must be honoured, latch or no latch — otherwise
    // rotating with a stale latch would freeze the split in the old orientation.
    @Test
    fun withTheImeHiddenAGenuineSizeChangeOverridesThePreviousAnswer() {
        assertTrue(splitIsHorizontal(1000f, 500f, reverseSplitMode = false, imeVisible = false, previous = false))
        assertFalse(splitIsHorizontal(500f, 1000f, reverseSplitMode = false, imeVisible = false, previous = true))
    }

    // A square box is not "wider than tall", so it stacks. Pinned because the comparison is strict.
    @Test
    fun anExactlySquareBoxStacks() {
        assertFalse(splitIsHorizontal(800f, 800f, reverseSplitMode = false, imeVisible = false, previous = null))
    }

    @Test
    fun aFreshLatchAfterARotationRecomputesRatherThanHoldingTheOldAnswer() {
        // Keyboard up, previously stacked: the latch holds.
        val heldWhileTyping = splitIsHorizontal(
            widthPx = 1200f, heightPx = 370f, reverseSplitMode = false,
            imeVisible = true, previous = false,
        )
        assertFalse(heldWhileTyping, "the latch holds while the keyboard is up")

        // The caller reset the latch because the window rotated: same geometry, null previous.
        val afterRotation = splitIsHorizontal(
            widthPx = 1200f, heightPx = 370f, reverseSplitMode = false,
            imeVisible = true, previous = null,
        )
        assertTrue(afterRotation, "with the latch reset by a rotation, the new geometry decides (F65)")
    }
}
