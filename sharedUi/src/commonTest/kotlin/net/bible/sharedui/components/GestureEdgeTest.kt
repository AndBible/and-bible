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
package net.bible.sharedui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureEdgeTest {
    private val edges = HorizontalGestureEdges(leftPx = 60f, rightPx = 40f)

    @Test fun aPressInsideTheLeftEdgeIsInTheEdge() = assertTrue(isInHorizontalGestureEdge(10f, 1000f, edges))
    @Test fun aPressInsideTheRightEdgeIsInTheEdge() = assertTrue(isInHorizontalGestureEdge(975f, 1000f, edges))
    @Test fun aPressInTheMiddleIsNot() = assertFalse(isInHorizontalGestureEdge(500f, 1000f, edges))
    @Test fun theEdgeBoundariesBelongToTheContent() {
        assertFalse(isInHorizontalGestureEdge(60f, 1000f, edges))
        assertFalse(isInHorizontalGestureEdge(960f, 1000f, edges))
    }
    @Test fun zeroEdgesNeverReject() = assertFalse(isInHorizontalGestureEdge(0f, 1000f, HorizontalGestureEdges(0f, 0f)))
}
