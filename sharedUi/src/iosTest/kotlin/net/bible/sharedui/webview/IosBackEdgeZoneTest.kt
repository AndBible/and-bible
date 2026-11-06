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
package net.bible.sharedui.webview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #4003 review: only the leading screen edge starts Compose's back edge-swipe (the end-edge gesture is disabled by
 * default), so only a touch there may stop a chapter swipe. A trailing-edge drag must still turn the chapter.
 */
class IosBackEdgeZoneTest {
    private val width = 400.0

    @Test
    fun leftToRightLeadingEdgeIsTheLeftEdge() {
        assertTrue(isInBackEdgeZone(x = 5.0, windowWidth = width, rightToLeft = false))
        assertFalse(isInBackEdgeZone(x = width - 5.0, windowWidth = width, rightToLeft = false))
    }

    @Test
    fun rightToLeftLeadingEdgeIsTheRightEdge() {
        assertTrue(isInBackEdgeZone(x = width - 5.0, windowWidth = width, rightToLeft = true))
        assertFalse(isInBackEdgeZone(x = 5.0, windowWidth = width, rightToLeft = true))
    }

    @Test
    fun theMiddleIsNeverAnEdge() {
        assertFalse(isInBackEdgeZone(x = width / 2, windowWidth = width, rightToLeft = false))
        assertFalse(isInBackEdgeZone(x = width / 2, windowWidth = width, rightToLeft = true))
    }

    @Test
    fun zoneBoundaryIsExclusive() {
        assertTrue(isInBackEdgeZone(x = 23.9, windowWidth = width, rightToLeft = false))
        assertFalse(isInBackEdgeZone(x = 24.0, windowWidth = width, rightToLeft = false))
        assertTrue(isInBackEdgeZone(x = width - 23.9, windowWidth = width, rightToLeft = true))
        assertFalse(isInBackEdgeZone(x = width - 24.0, windowWidth = width, rightToLeft = true))
    }
}
