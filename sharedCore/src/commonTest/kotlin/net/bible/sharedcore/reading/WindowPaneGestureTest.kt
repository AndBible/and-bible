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
import kotlin.test.assertEquals

class WindowPaneGestureTest {
    @Test fun swipeUpPastThresholdMaximises() {
        assertEquals(PaneButtonAction.Maximise, paneButtonDragAction(dragDy = -30f, thresholdPx = 28f))
    }

    @Test fun swipeDownPastThresholdMinimises() {
        assertEquals(PaneButtonAction.Minimise, paneButtonDragAction(dragDy = 30f, thresholdPx = 28f))
    }

    @Test fun smallDragIsNone() {
        assertEquals(PaneButtonAction.None, paneButtonDragAction(dragDy = 10f, thresholdPx = 28f))
    }

    @Test fun exactlyNegativeThresholdMaximises() {
        assertEquals(PaneButtonAction.Maximise, paneButtonDragAction(dragDy = -28f, thresholdPx = 28f))
    }

    @Test fun exactlyPositiveThresholdMinimises() {
        assertEquals(PaneButtonAction.Minimise, paneButtonDragAction(dragDy = 28f, thresholdPx = 28f))
    }

    @Test fun zeroDragIsNone() {
        assertEquals(PaneButtonAction.None, paneButtonDragAction(dragDy = 0f, thresholdPx = 28f))
    }
}
