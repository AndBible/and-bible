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
package net.bible.sharedui.platform.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosWidgetRefreshTest {
    @Test fun notReadyNeverRunsTheAction() {
        var ran = 0
        assertFalse(IosWidgetRefresh.runWhenReady(ready = { false }) { ran++ })
        assertEquals(0, ran, "resolving the writer before phase 2 reads labels and SWORD")
    }

    @Test fun readyRunsTheActionOnce() {
        var ran = 0
        assertTrue(IosWidgetRefresh.runWhenReady(ready = { true }) { ran++ })
        assertEquals(1, ran)
    }

    @Test fun aFailingActionDoesNotCrossTheSwiftBoundary() {
        assertFalse(IosWidgetRefresh.runWhenReady(ready = { true }) { error("Koin construction failed") })
    }
}
