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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleReferenceOverlayTest {
    @Test fun shownOnlyWhenAllGatesOpen() {
        assertTrue(bibleReferenceOverlayVisible(fullScreen = true, activeIsBibleShown = true, buttonsShown = true, hideSetting = false))
    }
    @Test fun hiddenWhenSettingDisablesIt() {
        assertFalse(bibleReferenceOverlayVisible(fullScreen = true, activeIsBibleShown = true, buttonsShown = true, hideSetting = true))
    }
    @Test fun hiddenWhenNotFullScreen() {
        assertFalse(bibleReferenceOverlayVisible(fullScreen = false, activeIsBibleShown = true, buttonsShown = true, hideSetting = false))
    }
    @Test fun hiddenWhenActiveWindowIsNotBible() {
        assertFalse(bibleReferenceOverlayVisible(fullScreen = true, activeIsBibleShown = false, buttonsShown = true, hideSetting = false))
    }
    @Test fun hiddenWhenButtonsAutoHidden() {
        assertFalse(bibleReferenceOverlayVisible(fullScreen = true, activeIsBibleShown = true, buttonsShown = false, hideSetting = false))
    }
}
