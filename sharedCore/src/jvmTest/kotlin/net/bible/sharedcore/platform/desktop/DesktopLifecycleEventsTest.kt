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
package net.bible.sharedcore.platform.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLifecycleEventsTest {
    @Test fun listenersHearEventsInOrderAndAThrowingOneIsIsolated() {
        val events = DesktopLifecycleEvents()
        val heard = mutableListOf<String>()
        val stopFirst = events.observe { heard += "1:$it" }
        events.observe { error("listener failure") }
        events.observe { heard += "3:$it" }
        events.emit(DesktopLifecycleEvent.ResignActive)
        stopFirst()
        events.emit(DesktopLifecycleEvent.Background)
        assertEquals(listOf("1:ResignActive", "3:ResignActive", "3:Background"), heard)
    }
}
