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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.reading.readingDrawerWindowInsets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window controls of the iOS app on a Mac: every top bar ([topBarWindowInsets]) clears them sideways, and the
 * reading drawer, which opens under their corner, keeps their band on top.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarWindowControlsTest {
    private val leftControls = WindowControlsInsets(top = 28.0, left = 78.0, right = 0.0)

    /** Left, top and right of [insets] in dp, read inside a composition that provides [controls]. */
    private fun measure(controls: WindowControlsInsets?, insets: @Composable () -> WindowInsets): Triple<Float, Float, Float> {
        var result = Triple(-1f, -1f, -1f)
        runComposeUiTest {
            setContent {
                val content = @Composable {
                    val i = insets()
                    val density = LocalDensity.current
                    val dir = LocalLayoutDirection.current
                    with(density) {
                        result = Triple(i.getLeft(density, dir).toDp().value, i.getTop(density).toDp().value, i.getRight(density, dir).toDp().value)
                    }
                }
                if (controls == null) content()
                else CompositionLocalProvider(LocalWindowControls provides controls) { content() }
            }
            waitForIdle()
        }
        return result
    }

    @Test fun withoutWindowControlsTheTopBarHasNoInsets() =
        assertEquals(Triple(0f, 0f, 0f), measure(null) { topBarWindowInsets() })

    @Test fun windowControlsAtTheLeftPadTheTopBarLeftNotTop() =
        assertEquals(Triple(78f, 0f, 0f), measure(leftControls) { topBarWindowInsets() })

    @Test fun windowControlsAtTheRightPadTheTopBarRight() =
        assertEquals(Triple(0f, 0f, 78f), measure(WindowControlsInsets(28.0, 0.0, 78.0)) { topBarWindowInsets() })

    @Test fun theReadingDrawerKeepsTheControlsBandOnTop() =
        assertEquals(Triple(0f, 28f, 0f), measure(leftControls) { readingDrawerWindowInsets() })

    @Test fun withoutWindowControlsTheReadingDrawerHasNoTopInset() =
        assertEquals(Triple(0f, 0f, 0f), measure(null) { readingDrawerWindowInsets() })

    // Full-screen reading has no toolbar to clear the controls sideways, so the text and pane buttons keep the band.
    @Test fun fullScreenReadingKeepsTheControlsBandOnTop() =
        assertEquals(Triple(0f, 28f, 0f), measure(leftControls) { fullScreenReadingTopInsets() })

    @Test fun withoutWindowControlsFullScreenReadingHasNoTopInset() =
        assertEquals(Triple(0f, 0f, 0f), measure(null) { fullScreenReadingTopInsets() })
}
