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
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.bible.sharedui.desktop.webengine.kcef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageEditableFocusTest {
    @Test fun editableReportsAreParsed() {
        assertEquals(true, PageEditableFocus.parse("\u0000kcef:editable:1"))
        assertEquals(false, PageEditableFocus.parse("\u0000kcef:editable:0"))
    }

    @Test fun everythingElseIsLeftToTheOtherRouters() {
        for (t in listOf("", "kcef:editable:1", "\u0000kcef:editable:", "\u0000kcef:editable:2", PaneFocusRequest.REQUEST, "{\"method\":\"x\"}")) {
            assertNull(PageEditableFocus.parse(t), t)
        }
    }

    @Test fun theScriptUsesTheReservedPrefixAndWatchesFocus() {
        val s = PageEditableFocus.SCRIPT
        assertTrue("\\u0000kcef:editable:" in s, s)
        assertTrue("'focusin'" in s && "'focusout'" in s && "isContentEditable" in s && "TEXTAREA" in s)
        assertTrue(!s.contains("preventDefault") && !s.contains("stopPropagation"))
    }

    @Test fun theReservedRequestsAreDisjoint() {
        assertNull(PageEditableFocus.parse(PaneFocusRequest.REQUEST))
        assertTrue(!PaneFocusRequest.handle(PageEditableFocus.PREFIX + "1", {}, {}))
    }
}
