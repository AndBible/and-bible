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

class PaneButtonFadeTest {
    @Test
    fun dayModeFadesToTwoTenths() {
        assertEquals(0.2f, paneButtonHiddenAlpha(nightMode = false, disableAnimations = false, monochrome = false))
    }

    @Test
    fun nightModeFadesToHalf() {
        assertEquals(0.5f, paneButtonHiddenAlpha(nightMode = true, disableAnimations = false, monochrome = false))
    }

    @Test
    fun disabledAnimationsNeverFade() {
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = false, disableAnimations = true, monochrome = false))
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = true, disableAnimations = true, monochrome = false))
    }

    @Test
    fun monochromeNeverFades() {
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = false, disableAnimations = false, monochrome = true))
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = true, disableAnimations = false, monochrome = true))
    }

    @Test
    fun bothSuppressorsTogetherStillNeverFade() {
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = false, disableAnimations = true, monochrome = true))
        assertEquals(1f, paneButtonHiddenAlpha(nightMode = true, disableAnimations = true, monochrome = true))
    }

    @Test
    fun fadeDurationIsZeroOnlyWhenAnimationsDisabled() {
        assertEquals(0, paneButtonFadeMillis(disableAnimations = true))
        assertEquals(PANE_BUTTON_FADE_MILLIS, paneButtonFadeMillis(disableAnimations = false))
    }
}
