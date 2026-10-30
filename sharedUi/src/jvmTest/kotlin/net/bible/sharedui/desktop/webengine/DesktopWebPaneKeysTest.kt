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
package net.bible.sharedui.desktop.webengine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWebPaneKeysTest {
    private val a = Any()
    private val b = Any()

    @AfterTest fun clear() { DesktopWebPaneKeys.focusLost(a); DesktopWebPaneKeys.focusLost(b) }

    @Test fun ownsKeysWhileAPaneHoldsFocus() {
        assertFalse(DesktopWebPaneKeys.ownsKeys)
        DesktopWebPaneKeys.focusGained(a)
        assertTrue(DesktopWebPaneKeys.ownsKeys)
        DesktopWebPaneKeys.focusLost(a)
        assertFalse(DesktopWebPaneKeys.ownsKeys)
    }

    @Test fun blurOfAnotherPaneDoesNotClearTheCurrentOwner() {
        DesktopWebPaneKeys.focusGained(a)
        DesktopWebPaneKeys.focusGained(b) // focus moved a -> b; a's blur arrives after b's gain
        DesktopWebPaneKeys.focusLost(a)
        assertTrue(DesktopWebPaneKeys.ownsKeys)
        DesktopWebPaneKeys.focusLost(b)
        assertFalse(DesktopWebPaneKeys.ownsKeys)
    }

    @Test fun windowHandlerRunsOnlyWhenNoPaneOwnsKeys() {
        var calls = 0
        assertTrue(DesktopWebPaneKeys.windowKey { calls++; true })
        assertEquals(1, calls)
        DesktopWebPaneKeys.focusGained(a)
        assertFalse(DesktopWebPaneKeys.windowKey { calls++; true })
        assertEquals(1, calls)
    }
}
