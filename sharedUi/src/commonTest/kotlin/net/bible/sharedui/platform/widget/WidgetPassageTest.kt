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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetPassageTest {
    @Test fun resolveKeepsKeyAndOrdinal() {
        val target = assertNotNull(WidgetPassage.resolve(WidgetUrlAction.OpenPassage("Gen.3.16", null, "KJVA", 12)))
        assertEquals("Gen.3.16", target.key.getOsisRef())
        assertEquals(12, target.ordinal?.start)
        assertNull(target.ordinal?.end)
    }

    @Test fun openHandsTheResolvedTargetToTheActiveWindowCallback() {
        var seen: net.bible.service.sword.BookAndKey? = null
        val opened = WidgetPassage.open(WidgetUrlAction.OpenPassage("Gen.1.1", null, null, 5)) { _, key -> seen = key }
        assertTrue(opened)
        assertEquals(5, seen?.ordinal?.start)
        assertEquals("Gen.1.1", seen?.key?.getOsisRef())
    }

    @Test fun negativeOrdinalIsDropped() {
        val target = assertNotNull(WidgetPassage.resolve(WidgetUrlAction.OpenPassage("Gen.3.16", null, "KJVA", -1)))
        assertNull(target.ordinal)
    }

    @Test fun unresolvableKeyOpensNothing() {
        var called = false
        val opened = WidgetPassage.open(WidgetUrlAction.OpenPassage("zzz nonsense 99:99", null, null, null)) { _, _ -> called = true }
        assertFalse(opened)
        assertFalse(called)
        assertNull(WidgetPassage.resolve(WidgetUrlAction.OpenPassage("zzz nonsense 99:99", null, null, null)))
        // A key without a colon takes the other branch (no document derivation) and must fail just as cleanly.
        called = false
        assertFalse(WidgetPassage.open(WidgetUrlAction.OpenPassage("nonsense", null, null, null)) { _, _ -> called = true })
        assertFalse(called)
    }
}
